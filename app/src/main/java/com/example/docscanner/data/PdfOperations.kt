package com.example.docscanner.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class CompressionLevel(val label: String, val scaleFactor: Float, val jpegQuality: Int, val description: String) {
    STRONG("High Compression", 0.6f, 40, "Smallest file size, good for email & sharing"),
    BALANCED("Balanced", 0.8f, 65, "Good quality & moderate size reduction"),
    LIGHT("Light Compression", 1.0f, 82, "High quality with gentle compression")
}

object PdfOperations {

    /**
     * Merges multiple PDF files into a single PDF in the given order.
     */
    suspend fun mergePdfs(
        context: Context,
        sourceFiles: List<File>,
        outputTitle: String
    ): File = withContext(Dispatchers.IO) {
        val scansDir = File(context.filesDir, "scans").apply { if (!exists()) mkdirs() }
        val sanitized = sanitizeTitle(outputTitle, "Merged_Document")
        val outputFile = getUniqueFile(scansDir, sanitized, "pdf")

        val mergedDoc = PdfDocument()
        var totalPagesAdded = 0

        for (file in sourceFiles) {
            if (!file.exists()) continue
            var pfd: ParcelFileDescriptor? = null
            var renderer: PdfRenderer? = null
            try {
                pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                renderer = PdfRenderer(pfd)
                val count = renderer.pageCount

                for (i in 0 until count) {
                    val page = renderer.openPage(i)
                    totalPagesAdded++

                    // Match page dimension
                    val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, totalPagesAdded).create()
                    val pdfPage = mergedDoc.startPage(pageInfo)

                    val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    val canvas = Canvas(bitmap)
                    canvas.drawColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                    pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                    mergedDoc.finishPage(pdfPage)

                    page.close()
                    bitmap.recycle()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                renderer?.close()
                pfd?.close()
            }
        }

        FileOutputStream(outputFile).use { out ->
            mergedDoc.writeTo(out)
        }
        mergedDoc.close()

        // Generate thumbnail for the first page of merged document
        generateThumbnail(context, outputFile)

        outputFile
    }

    /**
     * Extracts pages [fromPage] to [toPage] (inclusive, 1-indexed) into a new PDF.
     */
    suspend fun splitPdf(
        context: Context,
        sourceFile: File,
        fromPage: Int,
        toPage: Int,
        outputTitle: String
    ): File = withContext(Dispatchers.IO) {
        val scansDir = File(context.filesDir, "scans").apply { if (!exists()) mkdirs() }
        val sanitized = sanitizeTitle(outputTitle, "${sourceFile.nameWithoutExtension}_p${fromPage}-${toPage}")
        val outputFile = getUniqueFile(scansDir, sanitized, "pdf")

        val newDoc = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val maxPages = renderer.pageCount

            val start = (fromPage - 1).coerceIn(0, maxPages - 1)
            val end = (toPage - 1).coerceIn(start, maxPages - 1)
            var pageIndexInNewDoc = 0

            for (i in start..end) {
                val page = renderer.openPage(i)
                pageIndexInNewDoc++

                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, pageIndexInNewDoc).create()
                val pdfPage = newDoc.startPage(pageInfo)

                val bitmap = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                canvas.drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)

                pdfPage.canvas.drawBitmap(bitmap, 0f, 0f, null)
                newDoc.finishPage(pdfPage)

                page.close()
                bitmap.recycle()
            }
        } finally {
            renderer?.close()
            pfd?.close()
        }

        FileOutputStream(outputFile).use { out ->
            newDoc.writeTo(out)
        }
        newDoc.close()

        generateThumbnail(context, outputFile)
        outputFile
    }

    /**
     * Compresses a PDF by downsampling page resolutions and applying JPEG compression.
     */
    suspend fun compressPdf(
        context: Context,
        sourceFile: File,
        level: CompressionLevel,
        outputTitle: String
    ): File = withContext(Dispatchers.IO) {
        val scansDir = File(context.filesDir, "scans").apply { if (!exists()) mkdirs() }
        val sanitized = sanitizeTitle(outputTitle, "${sourceFile.nameWithoutExtension}_compressed")
        val outputFile = getUniqueFile(scansDir, sanitized, "pdf")

        val compressedDoc = PdfDocument()
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null

        try {
            pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val total = renderer.pageCount

            for (i in 0 until total) {
                val page = renderer.openPage(i)
                val renderWidth = (page.width * level.scaleFactor).toInt().coerceAtLeast(300)
                val renderHeight = (page.height * level.scaleFactor).toInt().coerceAtLeast(400)

                // Render page at scaled dimensions
                val highResBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                val highResCanvas = Canvas(highResBitmap)
                highResCanvas.drawColor(Color.WHITE)
                page.render(highResBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                // Compress via JPEG stream
                val stream = ByteArrayOutputStream()
                highResBitmap.compress(Bitmap.CompressFormat.JPEG, level.jpegQuality, stream)
                val compressedBytes = stream.toByteArray()
                highResBitmap.recycle()

                val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size)

                // Draw compressed image onto page with original dimensions
                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, i + 1).create()
                val pdfPage = compressedDoc.startPage(pageInfo)

                val destRect = android.graphics.Rect(0, 0, page.width, page.height)
                pdfPage.canvas.drawBitmap(compressedBitmap, null, destRect, null)
                compressedDoc.finishPage(pdfPage)

                page.close()
                compressedBitmap.recycle()
            }
        } finally {
            renderer?.close()
            pfd?.close()
        }

        FileOutputStream(outputFile).use { out ->
            compressedDoc.writeTo(out)
        }
        compressedDoc.close()

        generateThumbnail(context, outputFile)
        outputFile
    }

    private fun generateThumbnail(context: Context, pdfFile: File) {
        try {
            val scansDir = File(context.filesDir, "scans")
            val thumbFile = File(scansDir, "Thumb_${pdfFile.nameWithoutExtension}.jpg")
            val pfd = ParcelFileDescriptor.open(pdfFile, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            if (renderer.pageCount > 0) {
                val page = renderer.openPage(0)
                val width = 300
                val height = (page.height.toFloat() / page.width.toFloat() * width).toInt()
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                FileOutputStream(thumbFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 85, out)
                }
                bmp.recycle()
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun sanitizeTitle(input: String, fallback: String): String {
        val clean = input.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        return if (clean.isEmpty()) fallback else clean
    }

    private fun getUniqueFile(dir: File, baseName: String, extension: String): File {
        var file = File(dir, "$baseName.$extension")
        var counter = 1
        while (file.exists()) {
            file = File(dir, "${baseName}_$counter.$extension")
            counter++
        }
        return file
    }
}

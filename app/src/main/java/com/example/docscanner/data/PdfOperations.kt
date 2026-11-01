package com.example.docscanner.data

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

/**
 * PDF24-inspired compression presets mapping directly to Ghostscript / PDFBox standard DPI profiles:
 * - SCREEN (72 DPI): Maximum compression for web, email & screen reading.
 * - BALANCED (120 DPI): The classic PDF24 sweet spot balancing text crispness with strong size reduction.
 * - LIGHT (150 DPI): High detail retention with structure optimization.
 */
enum class CompressionLevel(
    val label: String,
    val dpi: Int,
    val maxDimension: Int,
    val jpegQuality: Int,
    val description: String
) {
    STRONG("Screen / Small (72 DPI)", 72, 850, 42, "PDF24 Screen: Maximum reduction, ideal for email"),
    BALANCED("Balanced / eBook (120 DPI)", 120, 1200, 60, "PDF24 eBook: Crisp text with strong compression"),
    LIGHT("High Quality (150 DPI)", 150, 1600, 72, "PDF24 Print: Preserves detail while optimizing structure")
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
        val sanitized = sanitizeTitle(outputTitle, "${sourceFile.nameWithoutExtension}_split")
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
     * PDF24-Style Compression Mechanism:
     * 1. Downsamples pages based on target DPI / max pixel dimensions.
     * 2. Supports optional Grayscale color-space conversion (PDF24's #1 secret for dropping 60% size on document scans).
     * 3. Compresses to JPEG buffer and renders via memory-efficient RGB_565 config.
     * 4. Strict verification: Never outputs a file larger than the original. Retries with higher compression or informs user if file is already maximally optimized.
     */
    suspend fun compressPdf(
        context: Context,
        sourceFile: File,
        level: CompressionLevel,
        convertToGrayscale: Boolean,
        outputTitle: String
    ): File = withContext(Dispatchers.IO) {
        val originalSize = sourceFile.length()
        val scansDir = File(context.filesDir, "scans").apply { if (!exists()) mkdirs() }
        val sanitized = sanitizeTitle(outputTitle, "${sourceFile.nameWithoutExtension}_compressed")
        val outputFile = getUniqueFile(scansDir, sanitized, "pdf")

        // First attempt with requested preset
        var success = performCompressionPass(
            sourceFile = sourceFile,
            targetFile = outputFile,
            maxDim = level.maxDimension,
            jpegQuality = level.jpegQuality,
            grayscale = convertToGrayscale
        )

        // If the output file is not smaller than original (e.g. source was already highly compressed),
        // try an aggressive PDF24 fallback pass (72 DPI, 42% quality, and Grayscale if needed)
        if (success && outputFile.length() >= (originalSize * 0.96f)) {
            outputFile.delete()
            success = performCompressionPass(
                sourceFile = sourceFile,
                targetFile = outputFile,
                maxDim = 800,
                jpegQuality = 38,
                grayscale = true
            )
        }

        // If still not smaller than original, delete and inform user truthfully
        if (!success || outputFile.length() >= originalSize) {
            if (outputFile.exists()) outputFile.delete()
            val formattedOriginal = formatBytes(originalSize)
            throw IllegalStateException(
                "This document ($formattedOriginal) is already at maximum compression and cannot be reduced further."
            )
        }

        generateThumbnail(context, outputFile)
        outputFile
    }

    private fun performCompressionPass(
        sourceFile: File,
        targetFile: File,
        maxDim: Int,
        jpegQuality: Int,
        grayscale: Boolean
    ): Boolean {
        var pfd: ParcelFileDescriptor? = null
        var renderer: PdfRenderer? = null
        val compressedDoc = PdfDocument()

        try {
            pfd = ParcelFileDescriptor.open(sourceFile, ParcelFileDescriptor.MODE_READ_ONLY)
            renderer = PdfRenderer(pfd)
            val total = renderer.pageCount
            if (total == 0) return false

            val grayscalePaint = if (grayscale) {
                val matrix = ColorMatrix().apply { setSaturation(0f) }
                Paint().apply { colorFilter = ColorMatrixColorFilter(matrix) }
            } else {
                null
            }

            for (i in 0 until total) {
                val page = renderer.openPage(i)

                // Calculate scaled dimension constrained to maxDim (PDF24 downsampling)
                val scale = if (page.width > page.height) {
                    maxDim.toFloat() / page.width.toFloat()
                } else {
                    maxDim.toFloat() / page.height.toFloat()
                }.coerceAtMost(1.5f)

                val renderWidth = (page.width * scale).toInt().coerceAtLeast(300)
                val renderHeight = (page.height * scale).toInt().coerceAtLeast(400)

                // Render page to bitmap
                val renderBitmap = Bitmap.createBitmap(renderWidth, renderHeight, Bitmap.Config.ARGB_8888)
                val renderCanvas = Canvas(renderBitmap)
                renderCanvas.drawColor(Color.WHITE)
                page.render(renderBitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)

                // Compress to JPEG stream (lossy DCT compression like Ghostscript)
                val stream = ByteArrayOutputStream()
                renderBitmap.compress(Bitmap.CompressFormat.JPEG, jpegQuality, stream)
                val compressedBytes = stream.toByteArray()
                renderBitmap.recycle()

                // Decode using memory-efficient RGB_565 (eliminates 16-bit alpha channel overhead)
                val options = BitmapFactory.Options().apply {
                    inPreferredConfig = Bitmap.Config.RGB_565
                }
                val compressedBitmap = BitmapFactory.decodeByteArray(compressedBytes, 0, compressedBytes.size, options)

                // Draw onto page with original PDF point coordinates
                val pageInfo = PdfDocument.PageInfo.Builder(page.width, page.height, i + 1).create()
                val pdfPage = compressedDoc.startPage(pageInfo)

                val destRect = Rect(0, 0, page.width, page.height)
                pdfPage.canvas.drawBitmap(compressedBitmap, null, destRect, grayscalePaint)
                compressedDoc.finishPage(pdfPage)

                page.close()
                compressedBitmap?.recycle()
            }

            FileOutputStream(targetFile).use { out ->
                compressedDoc.writeTo(out)
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        } finally {
            try {
                compressedDoc.close()
                renderer?.close()
                pfd?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
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
                val bmp = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
                val c = Canvas(bmp)
                c.drawColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                page.close()

                FileOutputStream(thumbFile).use { out ->
                    bmp.compress(Bitmap.CompressFormat.JPEG, 80, out)
                }
                bmp.recycle()
            }
            renderer.close()
            pfd.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
            else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
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

package com.example.docscanner.data

import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DocumentRepository(private val context: Context) {

    private val scansDir: File
        get() {
            val dir = File(context.filesDir, "scans")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    suspend fun saveScan(
        sourcePdfUri: Uri?,
        pageCount: Int,
        firstPageUri: Uri?
    ): ScannedDocument = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
        val displayTitle = "Scan " + SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()).format(Date(timestamp))
        val pdfFileName = "Scan_$dateString.pdf"
        val destinationPdfFile = File(scansDir, pdfFileName)

        sourcePdfUri?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationPdfFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        var destinationThumbFile: File? = null
        firstPageUri?.let { thumbUri ->
            val thumbFileName = "Thumb_$dateString.jpg"
            val thumbFile = File(scansDir, thumbFileName)
            try {
                context.contentResolver.openInputStream(thumbUri)?.use { input ->
                    FileOutputStream(thumbFile).use { output ->
                        input.copyTo(output)
                    }
                }
                destinationThumbFile = thumbFile
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val actualPages = if (pageCount > 0) pageCount else getPdfPageCount(destinationPdfFile)

        ScannedDocument(
            id = destinationPdfFile.name,
            title = displayTitle,
            file = destinationPdfFile,
            pageCount = actualPages,
            timestamp = timestamp,
            thumbnailFile = destinationThumbFile
        )
    }

    suspend fun getDocuments(): List<ScannedDocument> = withContext(Dispatchers.IO) {
        val files = scansDir.listFiles { file -> file.extension.lowercase() == "pdf" } ?: return@withContext emptyList()
        files.sortedByDescending { it.lastModified() }.map { file ->
            val baseName = file.nameWithoutExtension
            val thumbName = if (baseName.startsWith("Scan_")) {
                "Thumb_" + baseName.removePrefix("Scan_") + ".jpg"
            } else {
                "Thumb_$baseName.jpg"
            }
            val thumbCandidate = File(scansDir, thumbName)
            val thumb = if (thumbCandidate.exists()) thumbCandidate else null

            val title = if (baseName.startsWith("Scan_")) {
                baseName.replace('_', ' ')
            } else {
                baseName
            }

            ScannedDocument(
                id = file.name,
                title = title,
                file = file,
                pageCount = getPdfPageCount(file),
                timestamp = file.lastModified(),
                thumbnailFile = thumb
            )
        }
    }

    suspend fun mergeDocuments(
        docs: List<ScannedDocument>,
        outputTitle: String
    ): ScannedDocument = withContext(Dispatchers.IO) {
        val mergedFile = PdfOperations.mergePdfs(
            context = context,
            sourceFiles = docs.map { it.file },
            outputTitle = outputTitle
        )
        val thumbFile = File(scansDir, "Thumb_${mergedFile.nameWithoutExtension}.jpg")
        ScannedDocument(
            id = mergedFile.name,
            title = mergedFile.nameWithoutExtension,
            file = mergedFile,
            pageCount = getPdfPageCount(mergedFile),
            timestamp = mergedFile.lastModified(),
            thumbnailFile = if (thumbFile.exists()) thumbFile else null
        )
    }

    suspend fun splitDocument(
        doc: ScannedDocument,
        fromPage: Int,
        toPage: Int,
        outputTitle: String
    ): ScannedDocument = withContext(Dispatchers.IO) {
        val splitFile = PdfOperations.splitPdf(
            context = context,
            sourceFile = doc.file,
            fromPage = fromPage,
            toPage = toPage,
            outputTitle = outputTitle
        )
        val thumbFile = File(scansDir, "Thumb_${splitFile.nameWithoutExtension}.jpg")
        ScannedDocument(
            id = splitFile.name,
            title = splitFile.nameWithoutExtension,
            file = splitFile,
            pageCount = getPdfPageCount(splitFile),
            timestamp = splitFile.lastModified(),
            thumbnailFile = if (thumbFile.exists()) thumbFile else null
        )
    }

    suspend fun compressDocument(
        doc: ScannedDocument,
        level: CompressionLevel,
        outputTitle: String
    ): ScannedDocument = withContext(Dispatchers.IO) {
        val compressedFile = PdfOperations.compressPdf(
            context = context,
            sourceFile = doc.file,
            level = level,
            outputTitle = outputTitle
        )
        val thumbFile = File(scansDir, "Thumb_${compressedFile.nameWithoutExtension}.jpg")
        ScannedDocument(
            id = compressedFile.name,
            title = compressedFile.nameWithoutExtension,
            file = compressedFile,
            pageCount = getPdfPageCount(compressedFile),
            timestamp = compressedFile.lastModified(),
            thumbnailFile = if (thumbFile.exists()) thumbFile else null
        )
    }

    suspend fun renameDocument(document: ScannedDocument, newTitle: String): ScannedDocument = withContext(Dispatchers.IO) {
        val sanitized = newTitle.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_")
        if (sanitized.isEmpty() || sanitized == document.title) return@withContext document

        var targetFile = File(scansDir, "$sanitized.pdf")
        var counter = 1
        while (targetFile.exists() && targetFile.absolutePath != document.file.absolutePath) {
            targetFile = File(scansDir, "${sanitized}_$counter.pdf")
            counter++
        }

        if (document.file.renameTo(targetFile)) {
            var updatedThumb = document.thumbnailFile
            if (document.thumbnailFile != null && document.thumbnailFile.exists()) {
                val newThumbFile = File(scansDir, "Thumb_${targetFile.nameWithoutExtension}.jpg")
                document.thumbnailFile.renameTo(newThumbFile)
                updatedThumb = newThumbFile
            }

            document.copy(
                id = targetFile.name,
                title = targetFile.nameWithoutExtension,
                file = targetFile,
                thumbnailFile = updatedThumb
            )
        } else {
            document
        }
    }

    suspend fun deleteDocument(document: ScannedDocument): Boolean = withContext(Dispatchers.IO) {
        document.thumbnailFile?.delete()
        document.file.delete()
    }

    private fun getPdfPageCount(file: File): Int {
        if (!file.exists()) return 1
        return try {
            val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            val renderer = PdfRenderer(pfd)
            val count = renderer.pageCount
            renderer.close()
            pfd.close()
            if (count > 0) count else 1
        } catch (e: Exception) {
            1
        }
    }
}

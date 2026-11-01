package com.example.docscanner.data

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class DocumentRepository(private val context: Context) {

    private val scansDir: File
        get() {
            val dir = File(context.filesDir, "scans")
            if (!dir.exists()) {
                dir.mkdirs()
            }
            return dir
        }

    /**
     * Copies the scanned PDF and optional first page image from the ML Kit temporary URI
     * to persistent app storage.
     */
    suspend fun saveScan(
        sourcePdfUri: Uri?,
        pageCount: Int,
        firstPageUri: Uri?
    ): ScannedDocument = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val dateString = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(timestamp))
        val scanId = UUID.randomUUID().toString()
        val pdfFileName = "Scan_$dateString.pdf"
        val destinationPdfFile = File(scansDir, pdfFileName)

        // Copy PDF stream
        sourcePdfUri?.let { uri ->
            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destinationPdfFile).use { output ->
                    input.copyTo(output)
                }
            }
        }

        // Copy first page as thumbnail if available
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

        ScannedDocument(
            id = scanId,
            title = "Document $dateString",
            file = destinationPdfFile,
            pageCount = if (pageCount > 0) pageCount else 1,
            timestamp = timestamp,
            thumbnailFile = destinationThumbFile
        )
    }

    suspend fun getDocuments(): List<ScannedDocument> = withContext(Dispatchers.IO) {
        val files = scansDir.listFiles { file -> file.extension.lowercase() == "pdf" } ?: return@withContext emptyList()
        files.sortedByDescending { it.lastModified() }.map { file ->
            val baseName = file.nameWithoutExtension.removePrefix("Scan_")
            val thumbCandidate = File(scansDir, "Thumb_$baseName.jpg")
            val thumb = if (thumbCandidate.exists()) thumbCandidate else null

            ScannedDocument(
                id = file.name,
                title = file.nameWithoutExtension.replace('_', ' '),
                file = file,
                pageCount = 1, // Default or parse if needed
                timestamp = file.lastModified(),
                thumbnailFile = thumb
            )
        }
    }

    suspend fun deleteDocument(document: ScannedDocument): Boolean = withContext(Dispatchers.IO) {
        document.thumbnailFile?.delete()
        document.file.delete()
    }
}

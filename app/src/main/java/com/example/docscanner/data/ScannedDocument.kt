package com.example.docscanner.data

import android.net.Uri
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ScannedDocument(
    val id: String,
    val title: String,
    val file: File,
    val pageCount: Int,
    val timestamp: Long = System.currentTimeMillis(),
    val thumbnailFile: File? = null
) {
    val formattedDate: String
        get() = SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(timestamp))

    val formattedSize: String
        get() {
            val bytes = file.length()
            return when {
                bytes < 1024 -> "$bytes B"
                bytes < 1024 * 1024 -> String.format(Locale.getDefault(), "%.1f KB", bytes / 1024f)
                else -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
            }
        }
}

package com.example.docscanner.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.DocumentRepository
import com.example.docscanner.data.ScannedDocument
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.RESULT_FORMAT_PDF
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions.SCANNER_MODE_FULL
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DocumentScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DocumentRepository(application.applicationContext)

    private val _documents = MutableStateFlow<List<ScannedDocument>>(emptyList())
    val documents: StateFlow<List<ScannedDocument>> = _documents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadDocuments()
    }

    fun loadDocuments() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                _documents.value = repository.getDocuments()
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load documents: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Builds scanner options matching Adobe Scan capabilities:
     * - SCANNER_MODE_FULL gives automatic border detection + manual 8-point corner/edge
     *   border adjustments with magnifying loupe + perspective warp + cleanup filters.
     * - Enables both JPEG and PDF result formats.
     * - Supports importing existing images from the gallery.
     */
    fun buildScannerOptions(pageLimit: Int = 50): GmsDocumentScannerOptions {
        return GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(pageLimit)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
    }

    /**
     * Handles the result returned by the Document Scanner UI.
     */
    fun handleScanResult(result: GmsDocumentScanningResult?) {
        if (result == null) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val pdf = result.pdf
                val pdfUri = pdf?.uri
                val pageCount = pdf?.pageCount ?: result.pages?.size ?: 1

                // First page image if available for thumbnail preview
                val firstPageUri = result.pages?.firstOrNull()?.imageUri

                if (pdfUri != null) {
                    val savedDoc = repository.saveScan(
                        sourcePdfUri = pdfUri,
                        pageCount = pageCount,
                        firstPageUri = firstPageUri
                    )
                    // Prepend newly scanned document
                    _documents.value = listOf(savedDoc) + _documents.value
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save scanned document: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteDocument(document: ScannedDocument) {
        viewModelScope.launch {
            try {
                repository.deleteDocument(document)
                _documents.value = _documents.value.filter { it.id != document.id }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete document: ${e.localizedMessage}"
            }
        }
    }

    fun clearError() {
        _errorMessage.value = null
    }
}

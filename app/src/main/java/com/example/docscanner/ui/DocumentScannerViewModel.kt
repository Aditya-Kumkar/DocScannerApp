package com.example.docscanner.ui

import android.app.Application
import android.content.Context
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

enum class ThemeMode {
    SYSTEM, LIGHT, DARK
}

class DocumentScannerViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("pdfbutter_prefs", Context.MODE_PRIVATE)
    private val repository = DocumentRepository(application.applicationContext)

    private val _documents = MutableStateFlow<List<ScannedDocument>>(emptyList())
    val documents: StateFlow<List<ScannedDocument>> = _documents.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _themeMode = MutableStateFlow(loadSavedTheme())
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        loadDocuments()
    }

    private fun loadSavedTheme(): ThemeMode {
        val saved = prefs.getString("theme_mode", ThemeMode.SYSTEM.name)
        return try {
            ThemeMode.valueOf(saved ?: ThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            ThemeMode.SYSTEM
        }
    }

    fun toggleDarkMode() {
        val nextMode = when (_themeMode.value) {
            ThemeMode.SYSTEM -> ThemeMode.DARK
            ThemeMode.DARK -> ThemeMode.LIGHT
            ThemeMode.LIGHT -> ThemeMode.SYSTEM
        }
        _themeMode.value = nextMode
        prefs.edit().putString("theme_mode", nextMode.name).apply()
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

    fun buildScannerOptions(pageLimit: Int = 50): GmsDocumentScannerOptions {
        return GmsDocumentScannerOptions.Builder()
            .setGalleryImportAllowed(true)
            .setPageLimit(pageLimit)
            .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
            .setScannerMode(SCANNER_MODE_FULL)
            .build()
    }

    fun handleScanResult(result: GmsDocumentScanningResult?) {
        if (result == null) return

        viewModelScope.launch {
            _isLoading.value = true
            try {
                val pdf = result.pdf
                val pdfUri = pdf?.uri
                val pageCount = pdf?.pageCount ?: result.pages?.size ?: 1
                val firstPageUri = result.pages?.firstOrNull()?.imageUri

                if (pdfUri != null) {
                    val savedDoc = repository.saveScan(
                        sourcePdfUri = pdfUri,
                        pageCount = pageCount,
                        firstPageUri = firstPageUri
                    )
                    _documents.value = listOf(savedDoc) + _documents.value
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to save scanned document: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun renameDocument(document: ScannedDocument, newTitle: String) {
        viewModelScope.launch {
            try {
                val updated = repository.renameDocument(document, newTitle)
                _documents.value = _documents.value.map { if (it.id == document.id) updated else it }
            } catch (e: Exception) {
                _errorMessage.value = "Could not rename document: ${e.localizedMessage}"
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

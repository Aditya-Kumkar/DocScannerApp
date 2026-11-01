package com.example.docscanner.ui

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.docscanner.data.CompressionLevel
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

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _userNotice = MutableStateFlow<String?>(null)
    val userNotice: StateFlow<String?> = _userNotice.asStateFlow()

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

    // Selection management
    fun toggleSelection(id: String) {
        val current = _selectedIds.value.toMutableSet()
        if (current.contains(id)) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _selectedIds.value = current
    }

    fun selectAll() {
        if (_selectedIds.value.size == _documents.value.size) {
            _selectedIds.value = emptySet()
        } else {
            _selectedIds.value = _documents.value.map { it.id }.toSet()
        }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    // Merge operation
    fun mergeSelected(outputTitle: String) {
        val selected = _documents.value.filter { _selectedIds.value.contains(it.id) }
        if (selected.size < 2) return

        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Merging ${selected.size} PDFs..."
            try {
                val mergedDoc = repository.mergeDocuments(selected, outputTitle)
                _documents.value = listOf(mergedDoc) + _documents.value
                clearSelection()
                _userNotice.value = "Merged ${selected.size} PDFs successfully"
            } catch (e: Exception) {
                _errorMessage.value = "Merge failed: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
                _statusMessage.value = null
            }
        }
    }

    // Split operation
    fun splitDocument(document: ScannedDocument, fromPage: Int, toPage: Int, outputTitle: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Splitting pages $fromPage to $toPage..."
            try {
                val splitDoc = repository.splitDocument(document, fromPage, toPage, outputTitle)
                _documents.value = listOf(splitDoc) + _documents.value
                clearSelection()
                _userNotice.value = "Extracted pages $fromPage to $toPage"
            } catch (e: Exception) {
                _errorMessage.value = "Split failed: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
                _statusMessage.value = null
            }
        }
    }

    // Compress operation (PDF24 Ghostscript-style engine)
    fun compressDocument(
        document: ScannedDocument,
        level: CompressionLevel,
        convertToGrayscale: Boolean,
        outputTitle: String
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _statusMessage.value = "Applying PDF24 compression (${level.label})..."
            try {
                val compressedDoc = repository.compressDocument(document, level, convertToGrayscale, outputTitle)
                val diffBytes = (document.file.length() - compressedDoc.file.length()).coerceAtLeast(0L)
                val pct = if (document.file.length() > 0) {
                    (diffBytes.toFloat() / document.file.length().toFloat() * 100f).toInt().coerceAtLeast(1)
                } else 0
                _documents.value = listOf(compressedDoc) + _documents.value
                clearSelection()
                _userNotice.value = "Compressed by $pct% (${formatBytes(diffBytes)} saved)"
            } catch (e: Exception) {
                _errorMessage.value = e.localizedMessage ?: "Compression failed"
            } finally {
                _isLoading.value = false
                _statusMessage.value = null
            }
        }
    }

    fun deleteSelected() {
        val toDelete = _documents.value.filter { _selectedIds.value.contains(it.id) }
        viewModelScope.launch {
            _isLoading.value = true
            try {
                toDelete.forEach { repository.deleteDocument(it) }
                _documents.value = _documents.value.filterNot { _selectedIds.value.contains(it.id) }
                clearSelection()
            } catch (e: Exception) {
                _errorMessage.value = "Delete failed: ${e.localizedMessage}"
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
            _statusMessage.value = "Saving scan..."
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
                _errorMessage.value = "Failed to save scan: ${e.localizedMessage}"
            } finally {
                _isLoading.value = false
                _statusMessage.value = null
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

    fun clearUserNotice() {
        _userNotice.value = null
    }

    private fun formatBytes(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(java.util.Locale.getDefault(), "%.1f KB", bytes / 1024f)
            else -> String.format(java.util.Locale.getDefault(), "%.1f MB", bytes / (1024f * 1024f))
        }
    }
}

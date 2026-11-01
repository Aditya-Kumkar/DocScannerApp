package com.example.docscanner

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.*
import androidx.core.content.FileProvider
import com.example.docscanner.data.ScannedDocument
import com.example.docscanner.ui.DocumentScannerViewModel
import com.example.docscanner.ui.HomeScreen
import com.example.docscanner.ui.PdfViewerScreen
import com.example.docscanner.ui.ThemeMode
import com.example.docscanner.ui.theme.PdfButterTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MainActivity : ComponentActivity() {

    private val viewModel: DocumentScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val themeMode by viewModel.themeMode.collectAsState()
            val documents by viewModel.documents.collectAsState()

            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            var viewingDocumentId by remember { mutableStateOf<String?>(null) }
            val currentViewingDocument = documents.find { it.id == viewingDocumentId }

            PdfButterTheme(darkTheme = isDark) {
                val scannerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) { activityResult ->
                    if (activityResult.resultCode == Activity.RESULT_OK) {
                        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
                        viewModel.handleScanResult(result)
                    } else if (activityResult.resultCode != Activity.RESULT_CANCELED) {
                        Toast.makeText(this, "Scanning cancelled", Toast.LENGTH_SHORT).show()
                    }
                }

                if (currentViewingDocument != null) {
                    // In-app PDF Viewer
                    PdfViewerScreen(
                        document = currentViewingDocument,
                        onBack = { viewingDocumentId = null },
                        onShare = { shareDocument(currentViewingDocument) },
                        onRename = { newName ->
                            viewModel.renameDocument(currentViewingDocument, newName)
                        }
                    )
                } else {
                    // Home Screen
                    HomeScreen(
                        viewModel = viewModel,
                        onStartScan = {
                            val options = viewModel.buildScannerOptions(pageLimit = 50)
                            val scannerClient = GmsDocumentScanning.getClient(options)

                            scannerClient.getStartScanIntent(this)
                                .addOnSuccessListener { intentSender ->
                                    val request = IntentSenderRequest.Builder(intentSender).build()
                                    scannerLauncher.launch(request)
                                }
                                .addOnFailureListener { error ->
                                    Toast.makeText(
                                        this,
                                        "Failed to start scanner: ${error.localizedMessage}",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        },
                        onOpenDocument = { doc ->
                            viewingDocumentId = doc.id
                        }
                    )
                }
            }
        }
    }

    private fun shareDocument(document: ScannedDocument) {
        try {
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.provider",
                document.file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, document.title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share PDF"))
        } catch (e: Exception) {
            Toast.makeText(this, "Could not share PDF: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }
}

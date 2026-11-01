package com.example.docscanner

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.example.docscanner.ui.DocumentScannerViewModel
import com.example.docscanner.ui.HomeScreen
import com.example.docscanner.ui.theme.DocScannerTheme
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult

class MainActivity : ComponentActivity() {

    private val viewModel: DocumentScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            DocScannerTheme {
                // Register activity launcher for ML Kit Document Scanner IntentSender
                val scannerLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) { activityResult ->
                    if (activityResult.resultCode == Activity.RESULT_OK) {
                        val result = GmsDocumentScanningResult.fromActivityResultIntent(activityResult.data)
                        viewModel.handleScanResult(result)
                    } else if (activityResult.resultCode != Activity.RESULT_CANCELED) {
                        Toast.makeText(this, "Scanning cancelled or encountered an issue", Toast.LENGTH_SHORT).show()
                    }
                }

                HomeScreen(
                    viewModel = viewModel,
                    onStartScan = {
                        val options = viewModel.buildScannerOptions(pageLimit = 50)
                        val scannerClient = GmsDocumentScanning.getClient(options)

                        scannerClient.getStartScanIntentSender(this)
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
                    }
                )
            }
        }
    }
}

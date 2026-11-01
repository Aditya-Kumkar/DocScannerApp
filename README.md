# DocScanner - Android Document Scanner with Adobe Scan-Style Border Adjustments

A native Android application built with **Kotlin**, **Jetpack Compose**, and the **Google Play Services ML Kit Document Scanner API**.

---

## ✨ Features

- **Automatic Edge Detection**: Real-time detection of document boundaries using on-device ML models.
- **Adobe Scan-Style Border Adjustment**: Draggable 8-point corner and edge handles with a magnifying loupe for pixel-perfect document boundary adjustments.
- **Perspective Deskewing & Flattening**: Automatically transforms angled photos into top-down, rectified document pages.
- **Image Enhancement & Filters**: Built-in auto enhancement, grayscale, shadow removal, and binarization filters.
- **Multi-page Scanning**: Scan up to 50 pages in a single session with re-ordering and page deletion.
- **Multi-Format Export**: Generates high-quality PDF files and JPEG thumbnails.
- **Sharing & Viewing**: Open directly in any PDF viewer or share via WhatsApp, Gmail, Drive via Android `FileProvider`.

---

## 🏗️ Architecture

```
app/
├── src/main/
│   ├── AndroidManifest.xml             # Declares FileProvider for safe PDF sharing
│   ├── java/com/example/docscanner/
│   │   ├── MainActivity.kt             # Launches scanner IntentSender via ActivityResultContract
│   │   ├── data/
│   │   │   ├── ScannedDocument.kt      # Document model (PDF URI, thumbnail, page count, timestamp)
│   │   │   └── DocumentRepository.kt   # Persistent storage handling for PDFs and thumbnails
│   │   └── ui/
│   │       ├── DocumentScannerViewModel.kt # State management & scanner options builder
│   │       ├── HomeScreen.kt           # Jetpack Compose UI (document cards, scan actions, viewer)
│   │       └── theme/                  # Theme, colors, typography
│   └── res/
│       ├── xml/file_paths.xml          # Content URI mapping for FileProvider
│       └── values/                     # Strings, colors, themes
```

---

## ⚡ How the Border Adjustment Works

The scanner uses `SCANNER_MODE_FULL` from `com.google.android.gms:play-services-mlkit-document-scanner`:

```kotlin
val options = GmsDocumentScannerOptions.Builder()
    .setGalleryImportAllowed(true)
    .setPageLimit(50)
    .setResultFormats(RESULT_FORMAT_JPEG, RESULT_FORMAT_PDF)
    .setScannerMode(SCANNER_MODE_FULL) // Activates the 8-point draggable border adjustment UI with magnifying loupe
    .build()

val scanner = GmsDocumentScanning.getClient(options)
scanner.getStartScanIntentSender(activity)
    .addOnSuccessListener { intentSender ->
        val request = IntentSenderRequest.Builder(intentSender).build()
        scannerLauncher.launch(request)
    }
```

When scanning:
1. The camera automatically highlights document boundaries with a colored quad box.
2. After capture, an interactive editing screen opens with draggable corners and edges.
3. Dragging any handle displays a magnified loupe showing exact paper boundaries.
4. The user selects cleanup filters (Color, Auto, Grayscale, B&W).
5. The completed document is saved to local storage as a standard PDF.

---

## 🚀 How to Run

1. Open **Android Studio** (Hedgehog, Iguana, Jellyfish, or newer).
2. Select **Open** and choose this directory:
   `/home/aditya/.gemini/antigravity/scratch/DocScannerApp`
3. Wait for Gradle Sync to complete.
4. Connect a physical Android device or start an emulator with **Google Play Store** support.
5. Click **Run (`Shift + F10`)**.

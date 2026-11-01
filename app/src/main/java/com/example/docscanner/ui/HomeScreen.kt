package com.example.docscanner.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.example.docscanner.data.CompressionLevel
import com.example.docscanner.data.ScannedDocument

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: DocumentScannerViewModel,
    onStartScan: () -> Unit,
    onOpenDocument: (ScannedDocument) -> Unit
) {
    val documents by viewModel.documents.collectAsState()
    val selectedIds by viewModel.selectedIds.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val statusMessage by viewModel.statusMessage.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
    val context = LocalContext.current

    val isSelectionMode = selectedIds.isNotEmpty()

    // Dialog states
    var documentToRename by remember { mutableStateOf<ScannedDocument?>(null) }
    var documentToDelete by remember { mutableStateOf<ScannedDocument?>(null) }
    var renameInputText by remember { mutableStateOf("") }

    var showMergeDialog by remember { mutableStateOf(false) }
    var showSplitDialog by remember { mutableStateOf<ScannedDocument?>(null) }
    var showCompressDialog by remember { mutableStateOf<ScannedDocument?>(null) }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Intercept back button when in selection mode
    BackHandler(enabled = isSelectionMode) {
        viewModel.clearSelection()
    }

    LaunchedEffect(errorMessage) {
        errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearError()
        }
    }

    Scaffold(
        topBar = {
            if (isSelectionMode) {
                // Selection Top Bar
                TopAppBar(
                    title = {
                        Text(
                            text = "${selectedIds.size} selected",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 17.sp
                            )
                        )
                    },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.clearSelection() }) {
                            Icon(Icons.Default.Close, contentDescription = "Cancel selection")
                        }
                    },
                    actions = {
                        IconButton(onClick = { viewModel.selectAll() }) {
                            Icon(
                                imageVector = if (selectedIds.size == documents.size) Icons.Default.SelectAll else Icons.Outlined.SelectAll,
                                contentDescription = "Select all"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                )
            } else {
                // Default Top Bar
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.size(34.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(text = "🧈", fontSize = 17.sp)
                                }
                            }
                            Text(
                                text = "pdfbutter",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontSize = 19.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = (-0.3).sp
                                )
                            )
                        }
                    },
                    actions = {
                        IconButton(
                            onClick = { viewModel.toggleDarkMode() },
                            modifier = Modifier.padding(end = 4.dp)
                        ) {
                            val (icon, description) = when (themeMode) {
                                ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto to "Theme: System"
                                ThemeMode.LIGHT -> Icons.Outlined.LightMode to "Theme: Light"
                                ThemeMode.DARK -> Icons.Outlined.DarkMode to "Theme: Dark"
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = description,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            }
        },
        bottomBar = {
            // Calm Contextual Operations Bar
            AnimatedVisibility(
                visible = isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 6.dp,
                    shadowElevation = 10.dp,
                    modifier = Modifier.border(
                        0.5.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                        RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp)
                            .navigationBarsPadding(),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Merge (needs >= 2 selected)
                        val canMerge = selectedIds.size >= 2
                        OperationActionItem(
                            icon = Icons.Outlined.CallMerge,
                            label = "Merge",
                            enabled = canMerge,
                            onClick = { showMergeDialog = true }
                        )

                        // Split (needs exactly 1 selected)
                        val canSplit = selectedIds.size == 1
                        OperationActionItem(
                            icon = Icons.Outlined.CallSplit,
                            label = "Split",
                            enabled = canSplit,
                            onClick = {
                                val doc = documents.find { selectedIds.contains(it.id) }
                                showSplitDialog = doc
                            }
                        )

                        // Compress (needs 1 selected)
                        val canCompress = selectedIds.size == 1
                        OperationActionItem(
                            icon = Icons.Outlined.Compress,
                            label = "Compress",
                            enabled = canCompress,
                            onClick = {
                                val doc = documents.find { selectedIds.contains(it.id) }
                                showCompressDialog = doc
                            }
                        )

                        // Share
                        OperationActionItem(
                            icon = Icons.Outlined.Share,
                            label = "Share",
                            enabled = selectedIds.isNotEmpty(),
                            onClick = {
                                val selectedDocs = documents.filter { selectedIds.contains(it.id) }
                                shareMultiplePdfs(context, selectedDocs)
                            }
                        )

                        // Delete
                        OperationActionItem(
                            icon = Icons.Outlined.Delete,
                            label = "Delete",
                            enabled = selectedIds.isNotEmpty(),
                            tint = MaterialTheme.colorScheme.error,
                            onClick = { showDeleteConfirmDialog = true }
                        )
                    }
                }
            }
        },
        floatingActionButton = {
            if (!isSelectionMode) {
                ExtendedFloatingActionButton(
                    onClick = onStartScan,
                    icon = {
                        Icon(
                            imageVector = Icons.Default.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    text = {
                        Text(
                            text = "Scan Document",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    shape = RoundedCornerShape(16.dp),
                    elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 2.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            if (documents.isEmpty() && !isLoading) {
                CalmEmptyState(onStartScan = onStartScan)
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 4.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Documents",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "${documents.size} ${if (documents.size == 1) "file" else "files"}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    items(documents, key = { it.id }) { doc ->
                        SpaciousDocumentCard(
                            document = doc,
                            isSelected = selectedIds.contains(doc.id),
                            isSelectionMode = isSelectionMode,
                            onClick = {
                                if (isSelectionMode) {
                                    viewModel.toggleSelection(doc.id)
                                } else {
                                    onOpenDocument(doc)
                                }
                            },
                            onLongClick = {
                                viewModel.toggleSelection(doc.id)
                            },
                            onShare = { shareMultiplePdfs(context, listOf(doc)) },
                            onRename = {
                                documentToRename = doc
                                renameInputText = doc.title
                            },
                            onDelete = { documentToDelete = doc }
                        )
                    }
                }
            }

            // Processing overlay
            AnimatedVisibility(
                visible = isLoading,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                        RoundedCornerShape(16.dp)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                        Text(
                            text = statusMessage ?: "Processing document...",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }

    // Merge Dialog
    if (showMergeDialog) {
        val selectedDocs = documents.filter { selectedIds.contains(it.id) }
        var outputTitle by remember { mutableStateOf("Merged_${selectedDocs.size}_Docs") }

        AlertDialog(
            onDismissRequest = { showMergeDialog = false },
            title = {
                Text(
                    text = "Merge ${selectedDocs.size} PDFs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Combines the selected files in order into a single clean PDF.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = outputTitle,
                        onValueChange = { outputTitle = it },
                        singleLine = true,
                        label = { Text("Output PDF Name") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.mergeSelected(outputTitle.trim())
                        showMergeDialog = false
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Merge", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showMergeDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Split Dialog
    showSplitDialog?.let { doc ->
        var fromPageText by remember { mutableStateOf("1") }
        var toPageText by remember { mutableStateOf(doc.pageCount.toString()) }
        var outputTitle by remember { mutableStateOf("${doc.title}_split") }

        AlertDialog(
            onDismissRequest = { showSplitDialog = null },
            title = {
                Text(
                    text = "Split PDF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Total pages: ${doc.pageCount}. Select the page range to extract.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = fromPageText,
                            onValueChange = { fromPageText = it },
                            label = { Text("From") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = toPageText,
                            onValueChange = { toPageText = it },
                            label = { Text("To") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    OutlinedTextField(
                        value = outputTitle,
                        onValueChange = { outputTitle = it },
                        singleLine = true,
                        label = { Text("New File Name") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val from = fromPageText.toIntOrNull() ?: 1
                        val to = toPageText.toIntOrNull() ?: doc.pageCount
                        viewModel.splitDocument(doc, from, to, outputTitle.trim())
                        showSplitDialog = null
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Extract", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSplitDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Compress Dialog
    showCompressDialog?.let { doc ->
        var selectedLevel by remember { mutableStateOf(CompressionLevel.BALANCED) }
        var outputTitle by remember { mutableStateOf("${doc.title}_compressed") }

        AlertDialog(
            onDismissRequest = { showCompressDialog = null },
            title = {
                Text(
                    text = "Compress PDF",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Current size: ${doc.formattedSize}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )

                    CompressionLevel.values().forEach { level ->
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedLevel == level) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { selectedLevel = level }
                                .padding(vertical = 2.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = selectedLevel == level,
                                    onClick = { selectedLevel = level }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = level.label,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = level.description,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = outputTitle,
                        onValueChange = { outputTitle = it },
                        singleLine = true,
                        label = { Text("Output Name") },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.compressDocument(doc, selectedLevel, outputTitle.trim())
                        showCompressDialog = null
                    },
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Compress", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showCompressDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Delete Bulk Confirmation
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete ${selectedIds.size} files?") },
            text = { Text("This will permanently remove the selected PDF files from your device.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.deleteSelected()
                        showDeleteConfirmDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Single Rename Dialog
    documentToRename?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToRename = null },
            title = {
                Text(
                    text = "Rename Document",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            text = {
                OutlinedTextField(
                    value = renameInputText,
                    onValueChange = { renameInputText = it },
                    singleLine = true,
                    label = { Text("File name") },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = renameInputText.trim()
                        if (trimmed.isNotEmpty()) {
                            viewModel.renameDocument(doc, trimmed)
                        }
                        documentToRename = null
                    }
                ) {
                    Text("Save", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Single Delete Dialog
    documentToDelete?.let { doc ->
        AlertDialog(
            onDismissRequest = { documentToDelete = null },
            title = { Text("Delete Document") },
            text = { Text("Are you sure you want to delete '${doc.title}'?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteDocument(doc)
                        documentToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete", fontWeight = FontWeight.SemiBold)
                }
            },
            dismissButton = {
                TextButton(onClick = { documentToDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SpaciousDocumentCard(
    document: ScannedDocument,
    isSelected: Boolean,
    isSelectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onShare: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
    }

    val cardBg = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
    } else {
        MaterialTheme.colorScheme.surface
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = cardBg,
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(14.dp)
            )
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 16.dp, vertical = 14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Checkbox when in selection mode
            if (isSelectionMode) {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onClick() },
                    colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.padding(end = 8.dp)
                )
            }

            // Spacious thumbnail
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                if (document.thumbnailFile != null && document.thumbnailFile.exists()) {
                    AsyncImage(
                        model = document.thumbnailFile,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Spacious & informative metadata: Title + Date, Time, Size (No pages)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // Date, Time & Size only
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = document.formattedDate,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = document.formattedSize,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 12.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Options menu (only when not in multi-selection mode)
            if (!isSelectionMode) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        DropdownMenuItem(
                            text = { Text("Open") },
                            onClick = {
                                menuExpanded = false
                                onClick()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Visibility, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = {
                                menuExpanded = false
                                onRename()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Share") },
                            onClick = {
                                menuExpanded = false
                                onShare()
                            },
                            leadingIcon = {
                                Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        DropdownMenuItem(
                            text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                menuExpanded = false
                                onDelete()
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Outlined.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun OperationActionItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    enabled: Boolean,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    val alpha = if (enabled) 1f else 0.35f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint.copy(alpha = alpha),
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
            color = tint.copy(alpha = alpha),
            fontWeight = if (enabled) FontWeight.Medium else FontWeight.Normal
        )
    }
}

@Composable
fun CalmEmptyState(onStartScan: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(72.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Outlined.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(32.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Text(
            text = "No documents yet",
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp
            ),
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Scanned documents and imported PDFs\nwill be listed here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp
        )

        Spacer(modifier = Modifier.height(22.dp))

        Button(
            onClick = onStartScan,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp)
        ) {
            Icon(Icons.Default.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Scan Document", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        }
    }
}

private fun shareMultiplePdfs(context: Context, documents: List<ScannedDocument>) {
    if (documents.isEmpty()) return
    try {
        if (documents.size == 1) {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.provider",
                documents.first().file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/pdf"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, documents.first().title)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDF"))
        } else {
            val uris = ArrayList(documents.map {
                FileProvider.getUriForFile(context, "${context.packageName}.provider", it.file)
            })
            val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
                type = "application/pdf"
                putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Share PDFs"))
        }
    } catch (e: Exception) {
        Toast.makeText(context, "Could not share: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

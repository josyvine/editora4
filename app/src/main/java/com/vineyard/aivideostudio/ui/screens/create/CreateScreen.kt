package com.vineyard.aivideostudio.ui.screens.create

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vineyard.aivideostudio.core.util.FileUtils
import com.vineyard.aivideostudio.core.util.TimeUtils
import com.vineyard.aivideostudio.ui.components.AppTopBar
import com.vineyard.aivideostudio.ui.components.VideoPreviewPlayer
import com.vineyard.aivideostudio.ui.theme.AmberAccent
import com.vineyard.aivideostudio.ui.theme.BorderSubtle
import com.vineyard.aivideostudio.ui.theme.EmeraldSuccess
import com.vineyard.aivideostudio.ui.theme.RoseError
import com.vineyard.aivideostudio.ui.theme.StudioCardBg
import com.vineyard.aivideostudio.ui.theme.StudioDarkBg
import com.vineyard.aivideostudio.ui.theme.StudioSurface
import com.vineyard.aivideostudio.ui.theme.StudioSurfaceElevated
import com.vineyard.aivideostudio.ui.theme.TextPrimary
import com.vineyard.aivideostudio.ui.theme.TextSecondary
import com.vineyard.aivideostudio.ui.theme.TextTertiary
import com.vineyard.aivideostudio.ui.theme.VioletAccent
import com.vineyard.aivideostudio.ui.theme.VioletPrimary

@Composable
fun CreateScreen(
    viewModel: CreateViewModel,
    onProjectCreated: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboardManager.current

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            viewModel.onVideoSelected(uri)
        }
    }

    val jsonPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.onJsonFileSelected(uri)
        }
    }

    LaunchedEffect(state.createdProjectId) {
        state.createdProjectId?.let { id ->
            viewModel.resetCreatedState()
            onProjectCreated(id)
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "New Production",
                subtitle = "Autonomous Video Editor & Copyright Transformation"
            )
        },
        containerColor = StudioDarkBg
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            // Mode Selector: Auto AI vs. Import Recipe
            item {
                TabRow(
                    selectedTabIndex = if (state.isRecipeMode) 1 else 0,
                    containerColor = StudioSurfaceElevated,
                    contentColor = TextPrimary,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[if (state.isRecipeMode) 1 else 0]),
                            color = VioletAccent
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                ) {
                    Tab(
                        selected = !state.isRecipeMode,
                        onClick = { viewModel.onModeToggled(false) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Auto AI Pipeline", fontWeight = FontWeight.Bold)
                            }
                        },
                        selectedContentColor = VioletAccent,
                        unselectedContentColor = TextSecondary
                    )
                    Tab(
                        selected = state.isRecipeMode,
                        onClick = { viewModel.onModeToggled(true) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Import Master Recipe", fontWeight = FontWeight.Bold)
                            }
                        },
                        selectedContentColor = VioletAccent,
                        unselectedContentColor = TextSecondary
                    )
                }
            }

            // Project Name Input
            item {
                OutlinedTextField(
                    value = state.projectName,
                    onValueChange = { viewModel.onProjectNameChanged(it) },
                    label = { Text("Project Title") },
                    placeholder = { Text("e.g. Highlight Commentary Edit") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_project_name_input"),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = VioletPrimary,
                        unfocusedBorderColor = BorderSubtle,
                        focusedLabelColor = VioletAccent,
                        unfocusedLabelColor = TextSecondary,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary
                    ),
                    singleLine = true
                )
            }

            // Copyright Transformation Banner
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurfaceElevated),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = "Copyright Safety",
                            tint = EmeraldSuccess,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Automated Copyright Transformation",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Original audio will be purged. Dead air trimmed, concealer subtitles burned in, and replacement AI voiceover mixed.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary,
                                lineHeight = 16.sp
                            )
                        }
                    }
                }
            }

            // STEP 1: Choose Video File from Device
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (state.selectedVideoUri != null) EmeraldSuccess.copy(alpha = 0.5f) else BorderSubtle
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(if (state.selectedVideoUri != null) EmeraldSuccess.copy(alpha = 0.2f) else VioletPrimary.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "1",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = if (state.selectedVideoUri != null) EmeraldSuccess else VioletAccent
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = "Source Video File",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "(Shorts or Long-Form)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (state.selectedVideoUri != null) EmeraldSuccess else AmberAccent,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                                Text(
                                    text = "The local video on device for transformative cuts and rendering",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                            if (state.selectedVideoUri != null) {
                                Icon(
                                    Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = EmeraldSuccess,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        if (state.selectedVideoUri != null && state.videoMetadata != null) {
                            VideoPreviewPlayer(
                                videoUriString = state.selectedVideoUri.toString(),
                                muteOriginalAudio = false,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(180.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    val isVertical = (state.videoMetadata?.height ?: 0) > (state.videoMetadata?.width ?: 0)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(VioletPrimary.copy(alpha = 0.2f))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = if (isVertical) "SHORTS (9:16)" else "STANDARD (16:9)",
                                            color = VioletAccent,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${state.videoMetadata?.width}x${state.videoMetadata?.height}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
                                    )
                                }

                                Text(
                                    text = TimeUtils.formatDuration(state.videoMetadata?.durationSeconds ?: 0.0),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = VioletAccent,
                                    fontWeight = FontWeight.Bold
                                )

                                Text(
                                    text = FileUtils.formatBytes(state.videoMetadata?.fileSize ?: 0L),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }

                            if (state.selectedVideoFileName != null) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "File: ${state.selectedVideoFileName}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextTertiary,
                                    maxLines = 1
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))
                            Button(
                                onClick = { videoPickerLauncher.launch("video/*") },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Change Video File", color = TextPrimary)
                            }
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(StudioCardBg)
                                    .border(1.dp, BorderSubtle, RoundedCornerShape(10.dp))
                                    .clickable { videoPickerLauncher.launch("video/*") }
                                    .padding(16.dp)
                                    .testTag("create_select_video_box"),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(
                                        imageVector = Icons.Filled.Upload,
                                        contentDescription = null,
                                        tint = VioletAccent,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Choose video from device (Shorts or Long-Form)",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Supports MP4, MOV, MKV video files",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // STEP 2: Context URL vs. Master Recipe JSON Ingestion
            item {
                if (state.isRecipeMode) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StudioSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (state.parsedRecipe != null) EmeraldSuccess.copy(alpha = 0.5f) else BorderSubtle
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (state.parsedRecipe != null) EmeraldSuccess.copy(alpha = 0.2f) else VioletPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "2",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (state.parsedRecipe != null) EmeraldSuccess else VioletAccent
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Upload Master Recipe JSON",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = "Generated from Google AI Studio Web (skips in-app AI analysis)",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                if (state.parsedRecipe != null) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            if (state.parsedRecipe != null) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(EmeraldSuccess.copy(alpha = 0.1f))
                                        .border(1.dp, EmeraldSuccess.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Filled.DataObject, contentDescription = null, tint = EmeraldSuccess, modifier = Modifier.size(18.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = "Recipe Loaded: ${state.masterRecipeFileName ?: "Master_Recipe.json"}",
                                                fontWeight = FontWeight.Bold,
                                                color = EmeraldSuccess,
                                                style = MaterialTheme.typography.bodySmall
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = "• Highlights to Splice: ${state.parsedRecipe?.editingPlan?.segmentsToKeep?.size ?: 0} scenes\n" +
                                                    "• Captions to Burn: ${state.parsedRecipe?.captions?.size ?: 0} lines\n" +
                                                    "• Commentary Tone: ${state.parsedRecipe?.commentary?.tone ?: "Dynamic"}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextPrimary,
                                            lineHeight = 16.sp
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(10.dp))
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Button(
                                    onClick = {
                                        jsonPickerLauncher.launch(
                                            arrayOf("application/json", "text/plain", "text/json", "application/octet-stream", "*/*")
                                        )
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Upload .json", fontSize = 12.sp)
                                }

                                Button(
                                    onClick = {
                                        clipboardManager.getText()?.text?.let { clipText ->
                                            viewModel.onJsonPasted(clipText)
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = StudioSurfaceElevated),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Filled.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Paste Script", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StudioSurface),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (state.isYoutubeUrlValid) EmeraldSuccess.copy(alpha = 0.5f) else BorderSubtle
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(if (state.isYoutubeUrlValid) EmeraldSuccess.copy(alpha = 0.2f) else VioletPrimary.copy(alpha = 0.2f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "2",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = if (state.isYoutubeUrlValid) EmeraldSuccess else VioletAccent
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text(
                                            text = "Source Context URL",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = TextPrimary
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "(Context Link)",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (state.isYoutubeUrlValid) EmeraldSuccess else AmberAccent,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Text(
                                        text = "Gemini analyzes this reference URL for scene context, dialogue & scriptwriting",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                if (state.isYoutubeUrlValid) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            OutlinedTextField(
                                value = state.youtubeUrl,
                                onValueChange = { viewModel.onYoutubeUrlChanged(it) },
                                label = { Text("Public Source Context URL") },
                                placeholder = { Text("https://example.com/shorts/... or https://example.com/watch?v=...") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("create_youtube_url_input"),
                                shape = RoundedCornerShape(8.dp),
                                singleLine = true,
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Filled.SmartDisplay,
                                        contentDescription = null,
                                        tint = if (state.isYoutubeUrlValid) EmeraldSuccess else VioletAccent
                                    )
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            clipboardManager.getText()?.text?.let { clipText ->
                                                viewModel.onYoutubeUrlChanged(clipText)
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.ContentPaste,
                                            contentDescription = "Paste from clipboard",
                                            tint = TextSecondary
                                        )
                                    }
                                },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (state.isYoutubeUrlValid) EmeraldSuccess else VioletPrimary,
                                    unfocusedBorderColor = if (state.isYoutubeUrlValid) EmeraldSuccess.copy(alpha = 0.6f) else BorderSubtle,
                                    focusedTextColor = TextPrimary,
                                    unfocusedTextColor = TextPrimary
                                )
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (state.isYoutubeUrlValid) {
                                    Icon(
                                        Icons.Filled.CheckCircle,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Valid source reference URL linked for Gemini semantic analysis",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = EmeraldSuccess
                                    )
                                } else if (state.youtubeUrl.isNotBlank()) {
                                    Icon(
                                        Icons.Filled.ErrorOutline,
                                        contentDescription = null,
                                        tint = AmberAccent,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Enter a valid video reference link",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = AmberAccent
                                    )
                                } else {
                                    Icon(
                                        Icons.AutoMirrored.Filled.HelpOutline,
                                        contentDescription = null,
                                        tint = TextTertiary,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Paste the reference URL of the downloaded video above",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextTertiary
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Target Aspect Ratio
            item {
                Text(
                    text = "TARGET PRODUCTION ASPECT RATIO",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val ratios = listOf("ORIGINAL", "9:16", "16:9", "1:1")
                    ratios.forEach { ratio ->
                        val selected = state.targetAspectRatio == ratio
                        FilterChip(
                            selected = selected,
                            onClick = { viewModel.onAspectRatioChanged(ratio) },
                            label = { Text(ratio) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = VioletPrimary,
                                selectedLabelColor = TextPrimary,
                                containerColor = StudioCardBg,
                                labelColor = TextSecondary
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = BorderSubtle,
                                selectedBorderColor = VioletAccent
                            )
                        )
                    }
                }
            }

            // Readiness Summary Checklist
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = StudioCardBg),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "PRODUCTION PIPELINE READINESS",
                            style = MaterialTheme.typography.labelSmall,
                            color = VioletAccent,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.selectedVideoUri != null) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = null,
                                tint = if (state.selectedVideoUri != null) EmeraldSuccess else AmberAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.selectedVideoUri != null) {
                                    "Local Media: ${state.selectedVideoFileName ?: "Ready"} (${TimeUtils.formatDuration(state.videoMetadata?.durationSeconds ?: 0.0)})"
                                } else {
                                    "Local Media: Required (Select video file from device)"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = if (state.selectedVideoUri != null) TextPrimary else TextSecondary
                            )
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        if (state.isRecipeMode) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (state.parsedRecipe != null) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = null,
                                    tint = if (state.parsedRecipe != null) EmeraldSuccess else AmberAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state.parsedRecipe != null) {
                                        "Master Recipe: Ingested (${state.parsedRecipe?.editingPlan?.segmentsToKeep?.size ?: 0} scenes ready)"
                                    } else {
                                        "Master Recipe: Required (Upload or paste JSON script)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.parsedRecipe != null) TextPrimary else TextSecondary
                                )
                            }
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (state.isYoutubeUrlValid) Icons.Filled.CheckCircle else Icons.AutoMirrored.Filled.HelpOutline,
                                    contentDescription = null,
                                    tint = if (state.isYoutubeUrlValid) EmeraldSuccess else AmberAccent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = if (state.isYoutubeUrlValid) {
                                        "Source Context: Linked for AI reasoning"
                                    } else {
                                        "Source Context: Required (Paste video reference link)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (state.isYoutubeUrlValid) TextPrimary else TextSecondary
                                )
                            }
                        }
                    }
                }
            }

            // Error Message
            if (state.errorMessage != null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(RoseError.copy(alpha = 0.15f))
                            .border(1.dp, RoseError.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                            .padding(12.dp)
                    ) {
                        Text(
                            text = state.errorMessage!!,
                            color = RoseError,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // Submit Button
            item {
                Button(
                    onClick = { viewModel.createProject() },
                    enabled = state.isReadyToCreate,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .testTag("create_submit_button"),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (state.isRecipeMode) EmeraldSuccess else VioletPrimary,
                        disabledContainerColor = StudioSurfaceElevated
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    if (state.isLoading) {
                        CircularProgressIndicator(color = TextPrimary, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Configuring Studio Pipeline...", color = TextPrimary)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (state.isRecipeMode) Icons.Filled.Bolt else Icons.Filled.AutoAwesome,
                                contentDescription = null,
                                tint = if (state.isReadyToCreate) TextPrimary else TextTertiary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = if (state.isRecipeMode) "EXECUTE MASTER RECIPE & EXPORT" else "CREATE & OPEN STUDIO",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (state.isReadyToCreate) TextPrimary else TextTertiary
                            )
                        }
                    }
                }

                if (!state.isReadyToCreate && !state.isLoading) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = if (state.isRecipeMode) {
                            "Select a local video and upload your Master Recipe JSON to start instant production."
                        } else {
                            "Add both local video and source context URL to start automated production."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextTertiary,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
package com.vineyard.aivideostudio.ui.screens.processing

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.StepStatus
import com.vineyard.aivideostudio.core.util.TimeUtils
import com.vineyard.aivideostudio.ui.components.AppTopBar
import com.vineyard.aivideostudio.ui.components.PipelineStepRow
import com.vineyard.aivideostudio.ui.components.StatusChip
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
import com.vineyard.aivideostudio.ui.theme.VioletAccent
import com.vineyard.aivideostudio.ui.theme.VioletPrimary

@Composable
fun ProcessingScreen(
    viewModel: ProcessingViewModel,
    onNavigateBack: () -> Unit,
    onOpenEditor: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val project = state.project
    val isCompleted = project?.status == PipelineStatus.COMPLETED || state.currentStage == PipelineStatus.COMPLETED

    Scaffold(
        topBar = {
            AppTopBar(
                title = project?.name ?: "Video Studio Processing",
                subtitle = if (isCompleted) "Production Complete — Ready to Export" else "State: ${state.currentStage.name}",
                onNavigateBack = onNavigateBack
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
            // Video Preview Player
            item {
                Spacer(modifier = Modifier.height(4.dp))
                VideoPreviewPlayer(
                    videoUriString = project?.finalVideoUri ?: project?.currentVideoUri,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }

            // Paired Source Summary Card (Local Video + Source Context URL)
            if (project != null) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = StudioSurface),
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.Movie,
                                        contentDescription = null,
                                        tint = VioletAccent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Local Video: ${project.metadata.width}x${project.metadata.height} • ${TimeUtils.formatDuration(project.metadata.durationSeconds)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                                Text(
                                    text = project.targetAspectRatio,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = VioletAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (!project.sourceYoutubeUrl.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(6.dp))
                                HorizontalDivider(color = BorderSubtle, thickness = 1.dp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Filled.SmartDisplay,
                                        contentDescription = null,
                                        tint = EmeraldSuccess,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Source Context URL: ${project.sourceYoutubeUrl}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // AI Status & Guidance Card (Unmistakable Completion Banner)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = if (isCompleted) StudioSurfaceElevated else StudioSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (isCompleted) EmeraldSuccess.copy(alpha = 0.8f) else VioletPrimary.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = if (isCompleted) Icons.Filled.CheckCircle else Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = if (isCompleted) EmeraldSuccess else AmberAccent,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isCompleted) "PRODUCTION COMPLETED & VERIFIED" else "GEMINI REASONING ENGINE",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isCompleted) EmeraldSuccess else AmberAccent,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            if (project != null) {
                                StatusChip(status = project.status)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = if (isCompleted) {
                                "Final video is fully transformed, original audio purged, live commentary attached, and subtitles burned in. Ready for export!"
                            } else {
                                state.aiMessage
                            },
                            style = MaterialTheme.typography.bodyLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )

                        if (state.latestQa != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(StudioCardBg)
                                    .padding(10.dp)
                            ) {
                                Text(
                                    text = "QA (${state.latestQa!!.stage.name}): ${state.latestQa!!.verdict.name} — ${state.latestQa!!.feedback}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (state.latestQa!!.verdict.name == "PASS") EmeraldSuccess else AmberAccent
                                )
                            }
                        }
                    }
                }
            }

            // Controls (Export / Start / Cancel)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.isRunning) {
                        Button(
                            onClick = { viewModel.cancelProcessing() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("processing_cancel_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = RoseError),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = null, tint = TextPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Cancel", color = TextPrimary)
                        }
                    } else if (isCompleted && project != null) {
                        // Prominent Primary Action when completed: Open Editor & Save
                        Button(
                            onClick = { onOpenEditor(project.id) },
                            modifier = Modifier
                                .weight(1.3f)
                                .height(48.dp)
                                .testTag("processing_open_editor_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldSuccess),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.Download, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Editor & Export", color = Color.White, fontWeight = FontWeight.Bold)
                        }

                        // Secondary Action: Re-Run only if needed
                        OutlinedButton(
                            onClick = { viewModel.startProcessing() },
                            modifier = Modifier
                                .weight(0.8f)
                                .height(48.dp)
                                .testTag("processing_start_button"),
                            shape = RoundedCornerShape(10.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Re-Run", color = TextSecondary, fontSize = 12.sp)
                        }
                    } else {
                        Button(
                            onClick = { viewModel.startProcessing() },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("processing_start_button"),
                            colors = ButtonDefaults.buttonColors(containerColor = VioletPrimary),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = TextPrimary)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Pipeline", color = TextPrimary, fontWeight = FontWeight.Bold)
                        }

                        if (project != null) {
                            OutlinedButton(
                                onClick = { onOpenEditor(project.id) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                                    .testTag("processing_open_editor_button"),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Edit, contentDescription = null, tint = TextPrimary)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Inspect Editor", color = TextPrimary)
                            }
                        }
                    }
                }
            }

            // Pipeline Stage Checklist
            item {
                Text(
                    text = "SEQUENTIAL PRODUCTION WORKFLOW",
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp)
                )

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = StudioSurface),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderSubtle)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        val stages = listOf(
                            Triple("Source Analysis", PipelineStatus.SOURCE_ANALYSIS, "Scene semantic reasoning"),
                            Triple("Audio Extraction", PipelineStatus.AUDIO_EXTRACTION, "Extract local m4a audio"),
                            Triple("Transcription", PipelineStatus.TRANSCRIPTION, "Timestamped dialogue"),
                            Triple("Trim Analysis & Cut", PipelineStatus.TRIM_EXECUTION, "Pacing & dead-air cuts"),
                            Triple("Trim QA", PipelineStatus.TRIM_QA, "Verify critical scene retention"),
                            Triple("Crop & Framing", PipelineStatus.CROP_EXECUTION, "Target aspect ratio reframe"),
                            Triple("Crop QA", PipelineStatus.CROP_QA, "Verify composition headroom"),
                            Triple("Dynamic Zoom", PipelineStatus.ZOOM_EXECUTION, "Time-dependent punch-in"),
                            Triple("Captions & Subtitles", PipelineStatus.CAPTION_ANALYSIS, "High retention subtitles"),
                            Triple("Commentary & TTS", PipelineStatus.TTS_GENERATION, "Voiceover audio synthesis"),
                            Triple("Final QA & Export", PipelineStatus.FINAL_QA, "Executive signoff & MP4 render")
                        )

                        stages.forEach { (name, stage, detail) ->
                            val isStageCompleted = isStagePassed(stage, project?.status ?: PipelineStatus.IDLE)
                            val isCurrent = state.currentStage == stage
                            val stepStatus = when {
                                isStageCompleted -> StepStatus.COMPLETED
                                isCurrent -> StepStatus.IN_PROGRESS
                                else -> StepStatus.PENDING
                            }

                            PipelineStepRow(
                                stepName = name,
                                status = stepStatus,
                                detail = detail
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

private fun isStagePassed(stage: PipelineStatus, current: PipelineStatus): Boolean {
    if (current == PipelineStatus.COMPLETED) return true
    return stage.ordinal < current.ordinal
}
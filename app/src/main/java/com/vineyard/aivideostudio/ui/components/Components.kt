package com.vineyard.aivideostudio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vineyard.aivideostudio.ai.model.ModelInfo
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.PipelineStep
import com.vineyard.aivideostudio.core.model.StepStatus
import com.vineyard.aivideostudio.ui.theme.AmberAccent
import com.vineyard.aivideostudio.ui.theme.BorderSubtle
import com.vineyard.aivideostudio.ui.theme.CyanInfo
import com.vineyard.aivideostudio.ui.theme.EmeraldSuccess
import com.vineyard.aivideostudio.ui.theme.RoseError
import com.vineyard.aivideostudio.ui.theme.StudioCardBg
import com.vineyard.aivideostudio.ui.theme.StudioSurface
import com.vineyard.aivideostudio.ui.theme.StudioSurfaceElevated
import com.vineyard.aivideostudio.ui.theme.TextPrimary
import com.vineyard.aivideostudio.ui.theme.TextSecondary
import com.vineyard.aivideostudio.ui.theme.TextTertiary
import com.vineyard.aivideostudio.ui.theme.VioletAccent
import com.vineyard.aivideostudio.ui.theme.VioletPrimary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    subtitle: String? = null,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {}
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(
                    onClick = onNavigateBack,
                    modifier = Modifier.testTag("top_bar_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Navigate Back",
                        tint = TextPrimary
                    )
                }
            }
        },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = StudioSurface
        )
    )
}

@Composable
fun StatusChip(
    status: PipelineStatus,
    modifier: Modifier = Modifier
) {
    val (bgColor, textColor, label) = when (status) {
        PipelineStatus.COMPLETED -> Triple(EmeraldSuccess.copy(alpha = 0.2f), EmeraldSuccess, "COMPLETED")
        PipelineStatus.FAILED -> Triple(RoseError.copy(alpha = 0.2f), RoseError, "FAILED")
        PipelineStatus.NEEDS_REVIEW -> Triple(AmberAccent.copy(alpha = 0.2f), AmberAccent, "NEEDS REVIEW")
        PipelineStatus.CANCELLED -> Triple(TextTertiary.copy(alpha = 0.2f), TextSecondary, "CANCELLED")
        PipelineStatus.IDLE -> Triple(StudioSurfaceElevated, TextSecondary, "IDLE")
        else -> Triple(VioletPrimary.copy(alpha = 0.2f), VioletAccent, "PROCESSING")
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .border(1.dp, textColor.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = textColor,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp
        )
    }
}

@Composable
fun PipelineStepRow(
    stepName: String,
    status: StepStatus,
    detail: String? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        when (status) {
            StepStatus.COMPLETED -> {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = "Completed",
                    tint = EmeraldSuccess,
                    modifier = Modifier.size(20.dp)
                )
            }
            StepStatus.IN_PROGRESS -> {
                CircularProgressIndicator(
                    strokeWidth = 2.dp,
                    color = VioletPrimary,
                    modifier = Modifier.size(18.dp)
                )
            }
            StepStatus.SKIPPED -> {
                Icon(
                    imageVector = Icons.Filled.SkipNext,
                    contentDescription = "Skipped",
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
            }
            StepStatus.FAILED -> {
                Icon(
                    imageVector = Icons.Filled.Error,
                    contentDescription = "Failed",
                    tint = RoseError,
                    modifier = Modifier.size(20.dp)
                )
            }
            else -> {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .border(2.dp, BorderSubtle, CircleShape)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stepName,
                style = MaterialTheme.typography.titleMedium,
                color = if (status == StepStatus.SKIPPED) TextTertiary else TextPrimary
            )
            if (detail != null) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
fun ModelDropdown(
    label: String,
    selectedModelId: String,
    availableModels: List<ModelInfo>,
    onModelSelected: (ModelInfo) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val currentModel = availableModels.firstOrNull { it.id == selectedModelId || it.baseId == selectedModelId }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            color = TextSecondary,
            modifier = Modifier.padding(bottom = 6.dp)
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(StudioCardBg)
                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                .clickable { expanded = true }
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("model_dropdown_${label.lowercase().replace(" ", "_")}")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = currentModel?.displayName ?: selectedModelId,
                        style = MaterialTheme.typography.bodyLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = currentModel?.baseId ?: selectedModelId,
                        style = MaterialTheme.typography.labelSmall,
                        color = VioletAccent
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowDropDown,
                    contentDescription = "Expand dropdown",
                    tint = TextSecondary
                )
            }

            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.background(StudioSurfaceElevated)
            ) {
                if (availableModels.isEmpty()) {
                    DropdownMenuItem(
                        text = { Text("No models fetched yet", color = TextSecondary) },
                        onClick = { expanded = false }
                    )
                } else {
                    availableModels.forEach { model ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(
                                        text = model.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = model.baseId,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                            },
                            onClick = {
                                onModelSelected(model)
                                expanded = false
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    message: String,
    confirmText: String = "Confirm",
    dismissText: String = "Cancel",
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, color = TextPrimary) },
        text = { Text(message, style = MaterialTheme.typography.bodyMedium, color = TextSecondary) },
        containerColor = StudioSurfaceElevated,
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag("dialog_confirm_button")
            ) {
                Text(confirmText, color = RoseError, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button")
            ) {
                Text(dismissText, color = TextSecondary)
            }
        }
    )
}

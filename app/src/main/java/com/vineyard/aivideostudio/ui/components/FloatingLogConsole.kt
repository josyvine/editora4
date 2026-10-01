package com.vineyard.aivideostudio.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vineyard.aivideostudio.core.util.TimeUtils
import com.vineyard.aivideostudio.processing.logger.LogEntry
import com.vineyard.aivideostudio.processing.logger.LogSeverity
import com.vineyard.aivideostudio.processing.logger.ProcessingLogger
import com.vineyard.aivideostudio.ui.theme.AmberAccent
import com.vineyard.aivideostudio.ui.theme.BorderSubtle
import com.vineyard.aivideostudio.ui.theme.CyanInfo
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

enum class ConsoleDisplayMode {
    MINIMIZED_BUBBLE,
    FULL_SCREEN
}

@Composable
fun FloatingLogConsole(
    logger: ProcessingLogger,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logs by logger.logs.collectAsStateWithLifecycle()
    val errorCount by logger.errorCount.collectAsStateWithLifecycle()

    var displayMode by remember { mutableStateOf(ConsoleDisplayMode.MINIMIZED_BUBBLE) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var isCopying by remember { mutableStateOf(false) }

    // Position of the draggable bubble
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    // Filter & Search states
    var selectedFilter by remember { mutableStateOf("ALL") }
    var searchQuery by remember { mutableStateOf("") }

    val filteredLogs = remember(logs, selectedFilter, searchQuery) {
        logs.filter { entry ->
            val matchesFilter = when (selectedFilter) {
                "ERRORS" -> entry.severity == LogSeverity.ERROR || entry.severity == LogSeverity.CRASH
                "CRASHES" -> entry.severity == LogSeverity.CRASH
                "NETWORK" -> entry.projectId == "NETWORK"
                "PROCESS" -> entry.projectId != "NETWORK" && entry.severity != LogSeverity.CRASH
                else -> true
            }
            val matchesSearch = if (searchQuery.isBlank()) true else {
                entry.message.contains(searchQuery, ignoreCase = true) ||
                        entry.stage.name.contains(searchQuery, ignoreCase = true) ||
                        (entry.technicalDetails?.contains(searchQuery, ignoreCase = true) == true)
            }
            matchesFilter && matchesSearch
        }
    }

    // Helper to safely copy massive logs (> 1MB) without crash
    fun copyAllLogsToClipboard() {
        if (isCopying) return
        isCopying = true
        scope.launch {
            try {
                val fullText = logger.buildExportableLogText()
                withContext(Dispatchers.Main) {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Editora Full Console Log", fullText)
                    clipboard.setPrimaryClip(clip)
                    val sizeKb = (fullText.toByteArray(Charsets.UTF_8).size) / 1024
                    Toast.makeText(
                        context,
                        "Copied ${logs.size} log entries ($sizeKb KB) to clipboard!",
                        Toast.LENGTH_LONG
                    ).show()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Error copying logs: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            } finally {
                isCopying = false
            }
        }
    }

    if (showClearConfirm) {
        ConfirmationDialog(
            title = "Clear Persistent Log History",
            message = "Are you sure you want to permanently clear all persistent logs? This cannot be undone.",
            confirmText = "Clear All",
            onConfirm = {
                logger.clear()
                showClearConfirm = false
                Toast.makeText(context, "Logs cleared", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showClearConfirm = false }
        )
    }

    // 1. Minimized Draggable Floating Circle Bubble
    if (displayMode == ConsoleDisplayMode.MINIMIZED_BUBBLE) {
        Box(
            modifier = modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 80.dp)
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .shadow(12.dp, CircleShape)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                VioletPrimary,
                                StudioSurfaceElevated
                            )
                        )
                    )
                    .border(
                        2.dp,
                        if (errorCount > 0) RoseError else VioletAccent,
                        CircleShape
                    )
                    .clickable {
                        displayMode = ConsoleDisplayMode.FULL_SCREEN
                    }
                    .padding(14.dp)
                    .testTag("floating_log_bubble"),
                contentAlignment = Alignment.Center
            ) {
                BadgedBox(
                    badge = {
                        if (errorCount > 0) {
                            Badge(
                                containerColor = RoseError,
                                contentColor = Color.White
                            ) {
                                Text("$errorCount")
                            }
                        } else if (logs.isNotEmpty()) {
                            Badge(
                                containerColor = VioletPrimary,
                                contentColor = Color.White
                            ) {
                                Text("${logs.size}")
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = if (errorCount > 0) Icons.Filled.Warning else Icons.Filled.Terminal,
                        contentDescription = "Open Floating Log Console",
                        tint = if (errorCount > 0) AmberAccent else TextPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
    }

    // 2. Full-Screen Floating Log Console Dialog
    if (displayMode == ConsoleDisplayMode.FULL_SCREEN) {
        Dialog(
            onDismissRequest = { displayMode = ConsoleDisplayMode.MINIMIZED_BUBBLE },
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                dismissOnBackPress = true
            )
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onLongPress = {
                                copyAllLogsToClipboard()
                            }
                        )
                    }
                    .testTag("full_screen_log_console"),
                color = StudioDarkBg
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 12.dp, vertical = 10.dp)
                ) {
                    // Top Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(VioletPrimary.copy(alpha = 0.25f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Terminal,
                                    contentDescription = null,
                                    tint = VioletAccent,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "System Log Console",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Persistent across restarts • Long-press to copy all",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Copy button
                            IconButton(
                                onClick = { copyAllLogsToClipboard() },
                                modifier = Modifier.testTag("console_copy_button")
                            ) {
                                if (isCopying) {
                                    CircularProgressIndicator(color = VioletAccent, modifier = Modifier.size(18.dp))
                                } else {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy all logs", tint = TextPrimary)
                                }
                            }

                            // Clear button
                            IconButton(
                                onClick = { showClearConfirm = true },
                                modifier = Modifier.testTag("console_clear_button")
                            ) {
                                Icon(Icons.Filled.DeleteSweep, contentDescription = "Clear logs", tint = RoseError)
                            }

                            // Minimize to bubble button
                            IconButton(
                                onClick = { displayMode = ConsoleDisplayMode.MINIMIZED_BUBBLE },
                                modifier = Modifier.testTag("console_minimize_button")
                            ) {
                                Icon(Icons.Filled.FullscreenExit, contentDescription = "Minimize to bubble", tint = AmberAccent)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Search field
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Filter logs, errors, stacktraces...") },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear search", tint = TextSecondary, modifier = Modifier.size(16.dp))
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("console_search_input"),
                        shape = RoundedCornerShape(8.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = VioletPrimary,
                            unfocusedBorderColor = BorderSubtle,
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary,
                            cursorColor = VioletAccent
                        )
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Filter chips row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val filters = listOf("ALL", "ERRORS", "CRASHES", "PROCESS", "NETWORK")
                        filters.forEach { filter ->
                            val selected = selectedFilter == filter
                            FilterChip(
                                selected = selected,
                                onClick = { selectedFilter = filter },
                                label = {
                                    Text(
                                        filter,
                                        fontSize = 11.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = if (filter == "ERRORS" || filter == "CRASHES") RoseError else VioletPrimary,
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

                    Spacer(modifier = Modifier.height(8.dp))

                    // Status count banner
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(StudioSurface, RoundedCornerShape(6.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Showing ${filteredLogs.size} of ${logs.size} total logs",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                        if (errorCount > 0) {
                            Text(
                                text = "$errorCount failures detected",
                                style = MaterialTheme.typography.labelSmall,
                                color = RoseError,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Logs List
                    val listState = rememberLazyListState()
                    if (filteredLogs.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(StudioSurfaceElevated, RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (logs.isEmpty()) "Log buffer is clean" else "No matching log entries",
                                color = TextSecondary,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(StudioSurface, RoundedCornerShape(8.dp))
                                .border(1.dp, BorderSubtle, RoundedCornerShape(8.dp))
                                .padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(filteredLogs, key = { it.id }) { entry ->
                                LogEntryItem(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LogEntryItem(entry: LogEntry) {
    var expanded by remember { mutableStateOf(false) }

    val (badgeBg, textColor, tagLabel) = when (entry.severity) {
        LogSeverity.CRASH -> Triple(Color(0xFF881337), Color(0xFFFDA4AF), "CRASH")
        LogSeverity.ERROR -> Triple(RoseError.copy(alpha = 0.2f), RoseError, "ERROR")
        LogSeverity.WARNING -> Triple(AmberAccent.copy(alpha = 0.2f), AmberAccent, "WARN")
        LogSeverity.SUCCESS -> Triple(EmeraldSuccess.copy(alpha = 0.2f), EmeraldSuccess, "OK")
        LogSeverity.INFO -> Triple(StudioSurfaceElevated, CyanInfo, "INFO")
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded },
        colors = CardDefaults.cardColors(
            containerColor = if (entry.severity == LogSeverity.CRASH) Color(0xFF280A12) else StudioCardBg
        ),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (entry.severity == LogSeverity.CRASH) RoseError else BorderSubtle.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(badgeBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tagLabel,
                            color = textColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = entry.stage.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                Text(
                    text = TimeUtils.formatTimeOnly(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = TextTertiary,
                    fontSize = 10.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = entry.message,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 3,
                overflow = TextOverflow.Ellipsis
            )

            if (!entry.technicalDetails.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                AnimatedVisibility(visible = expanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(4.dp))
                            .background(StudioDarkBg)
                            .border(1.dp, BorderSubtle, RoundedCornerShape(4.dp))
                            .padding(8.dp)
                    ) {
                        Text(
                            text = entry.technicalDetails,
                            style = MaterialTheme.typography.labelSmall,
                            fontFamily = FontFamily.Monospace,
                            color = AmberAccent,
                            fontSize = 10.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
                if (!expanded) {
                    Text(
                        text = "▶ Tap to expand stacktrace / details",
                        color = VioletAccent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

package com.vineyard.aivideostudio.ui.components

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vineyard.aivideostudio.media.video.FastFilmstripExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * High-performance horizontal filmstrip slider component.
 * Renders low-res micro-thumbnails smoothly and copies full-resolution Ground-Truth JSON on long-press.
 */
@Composable
fun VideoFilmstripSlider(
    modifier: Modifier = Modifier,
    extractor: FastFilmstripExtractor,
    currentPositionMs: Long,
    onSeekTo: (Long) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val durationSec = extractor.getVideoDurationSeconds()
    val (nativeWidth, nativeHeight) = extractor.getVideoResolution()

    // Generate timeline thumbnail slots (e.g. 1 thumbnail every 2 seconds)
    val intervalMs = 2000L
    val totalSlots = (durationSec * 1000.0 / intervalMs).toInt().coerceAtLeast(1)
    val timeStamps = remember(durationSec) {
        List(totalSlots) { index -> (index * intervalMs) }
    }

    val thumbnailMap = remember { mutableStateMapOf<Long, Bitmap?>() }

    // Load micro-thumbnails asynchronously with zero memory strain
    LaunchedEffect(extractor) {
        withContext(Dispatchers.IO) {
            for (timeMs in timeStamps) {
                if (!thumbnailMap.containsKey(timeMs)) {
                    val bmp = extractor.getThumbnailAtTime(timeMs)
                    withContext(Dispatchers.Main) {
                        thumbnailMap[timeMs] = bmp
                    }
                }
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(90.dp)
            .background(Color.Black.copy(alpha = 0.8f))
            .padding(vertical = 8.dp)
    ) {
        LazyRow(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(horizontal = 8.dp)
        ) {
            itemsIndexed(timeStamps) { _, timeMs ->
                val bitmap = thumbnailMap[timeMs]
                val isSelected = abs(currentPositionMs - timeMs) < 1000L

                Box(
                    modifier = Modifier
                        .width(120.dp)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isSelected) Color.Cyan else Color.DarkGray)
                        .pointerInput(timeMs) {
                            detectTapGestures(
                                onTap = { onSeekTo(timeMs) },
                                onLongPress = {
                                    // LONG PRESS: Extract FULL resolution frame at this timestamp and copy Ground-Truth JSON
                                    coroutineScope.launch {
                                        val fullBmp = extractor.getFullResolutionFrameAppSafe(timeMs)
                                        val w = fullBmp?.width ?: nativeWidth
                                        val h = fullBmp?.height ?: nativeHeight
                                        
                                        // Standard full-resolution ground-truth target JSON payload
                                        val jsonPayload = """
                                            {
                                              "timestamp_ms": $timeMs,
                                              "frame_width": $w,
                                              "frame_height": $h,
                                              "target": {
                                                "left": 0.10,
                                                "top": 0.20,
                                                "right": 0.90,
                                                "bottom": 0.30
                                              }
                                            }
                                        """.trimIndent()

                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("Ground-Truth Frame JSON", jsonPayload))
                                        Toast.makeText(context, "Copied Ground-Truth JSON for ${timeMs / 1000}s", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            )
                        },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (bitmap != null && !bitmap.isRecycled) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Filmstrip Frame",
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Timestamp overlay badge
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.6f))
                            .fillMaxWidth()
                            .padding(2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "${timeMs / 1000}s",
                            color = Color.White,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }
    }
}

private fun abs(a: Long): Long = if (a < 0) -a else a

private suspend fun FastFilmstripExtractor.getFullResolutionFrameAppSafe(timeMs: Long): Bitmap? {
    return this.getFullResolutionFrameAtTime(timeMs)
}
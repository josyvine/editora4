package com.vineyard.aivideostudio.core.model

import com.squareup.moshi.JsonClass
import com.vineyard.aivideostudio.core.model.effects.SpeedRampSpec

@JsonClass(generateAdapter = true)
data class TimeRange(
    val start: Double,
    val end: Double
) {
    val duration: Double get() = Math.max(0.0, end - start)
}

@JsonClass(generateAdapter = true)
data class TimelineSegment(
    val id: String,
    val projectId: String,
    val originalStart: Double,
    val originalEnd: Double,
    val currentStart: Double,
    val currentEnd: Double,
    val isRetained: Boolean = true,
    val stageApplied: PipelineStatus = PipelineStatus.IDLE,
    val speedMultiplier: Double = 1.0 // 1.0 = normal, 2.0 = 2x speed (half duration)
) {
    val originalDuration: Double get() = Math.max(0.0, originalEnd - originalStart)
    val currentDuration: Double get() = Math.max(0.0, currentEnd - currentStart)
}

@JsonClass(generateAdapter = true)
data class TimelineMap(
    val projectId: String,
    val originalDuration: Double,
    val currentDuration: Double,
    val segments: List<TimelineSegment> = emptyList(),
    val removedRanges: List<TimeRange> = emptyList()
) {
    /**
     * Maps an original raw video timestamp (in seconds) to the accelerated / edited timeline.
     * Takes into account trims, cuts, and speed ramping multipliers (e.g. 1.75x, 2.0x).
     * If time falls into a gap or outside bounds, it gracefully clamps to the current compressed duration.
     */
    fun mapOriginalToCurrent(originalTime: Double): Double {
        if (segments.isEmpty()) return originalTime.coerceIn(0.0, currentDuration)

        val retained = segments.filter { it.isRetained }.sortedBy { it.originalStart }
        if (retained.isEmpty()) return 0.0

        // Handle points before first retained segment
        if (originalTime <= retained.first().originalStart) {
            return 0.0
        }

        // Match within active segment
        for (seg in retained) {
            if (originalTime in seg.originalStart..seg.originalEnd) {
                val origOffset = originalTime - seg.originalStart
                val speed = if (seg.speedMultiplier > 0.0) seg.speedMultiplier else 1.0
                val scaledOffset = origOffset / speed
                return (seg.currentStart + scaledOffset).coerceIn(0.0, currentDuration)
            }
        }

        // If falls between cut segments, map to the start of the next segment or end of previous
        for (i in 0 until retained.size - 1) {
            val currSeg = retained[i]
            val nextSeg = retained[i + 1]
            if (originalTime > currSeg.originalEnd && originalTime < nextSeg.originalStart) {
                return nextSeg.currentStart.coerceIn(0.0, currentDuration)
            }
        }

        // Past the last segment
        return currentDuration
    }

    /**
     * Millisecond helper for timeline mapping.
     */
    fun mapOriginalToCurrentMs(originalTimeMs: Long): Long {
        val mappedSec = mapOriginalToCurrent(originalTimeMs / 1000.0)
        return (mappedSec * 1000.0).toLong()
    }

    /**
     * Maps a timestamp on the current edited video back to original source video.
     */
    fun mapCurrentToOriginal(currentTime: Double): Double {
        for (seg in segments.filter { it.isRetained }) {
            if (currentTime in seg.currentStart..seg.currentEnd) {
                val currOffset = currentTime - seg.currentStart
                val speed = if (seg.speedMultiplier > 0.0) seg.speedMultiplier else 1.0
                val origOffset = currOffset * speed
                return (seg.originalStart + origOffset).coerceIn(0.0, originalDuration)
            }
        }
        return currentTime.coerceIn(0.0, originalDuration)
    }

    /**
     * Reshapes the timeline with speed-ramping specifications.
     * Slices segments cleanly at ramp boundaries so non-ramped sections stay at 1.0x
     * and ramped sections accelerate at the exact multiplier.
     */
    fun withSpeedRamps(speedRamps: List<SpeedRampSpec>): TimelineMap {
        if (speedRamps.isEmpty()) return this

        val sortedRamps = speedRamps.sortedBy { it.startTimeMs }
        val newSegments = mutableListOf<TimelineSegment>()
        var runningCurrentTime = 0.0

        for (baseSeg in segments.filter { it.isRetained }) {
            var cursor = baseSeg.originalStart

            // Slice base segment across any speed ramp boundaries that intersect it
            val intersectingRamps = sortedRamps.filter { ramp ->
                val rStart = ramp.startTimeMs / 1000.0
                val rEnd = ramp.endTimeMs / 1000.0
                rStart < baseSeg.originalEnd && rEnd > baseSeg.originalStart
            }

            if (intersectingRamps.isEmpty()) {
                val segDuration = (baseSeg.originalEnd - baseSeg.originalStart) / baseSeg.speedMultiplier
                newSegments.add(
                    baseSeg.copy(
                        id = "${baseSeg.id}_norm_${newSegments.size}",
                        currentStart = runningCurrentTime,
                        currentEnd = runningCurrentTime + segDuration
                    )
                )
                runningCurrentTime += segDuration
            } else {
                for (ramp in intersectingRamps) {
                    val rStart = (ramp.startTimeMs / 1000.0).coerceIn(baseSeg.originalStart, baseSeg.originalEnd)
                    val rEnd = (ramp.endTimeMs / 1000.0).coerceIn(rStart, baseSeg.originalEnd)

                    // 1. Un-accelerated slice before the ramp
                    if (rStart > cursor) {
                        val duration = (rStart - cursor) / 1.0
                        newSegments.add(
                            TimelineSegment(
                                id = "${baseSeg.id}_pre_${newSegments.size}",
                                projectId = projectId,
                                originalStart = cursor,
                                originalEnd = rStart,
                                currentStart = runningCurrentTime,
                                currentEnd = runningCurrentTime + duration,
                                isRetained = true,
                                speedMultiplier = 1.0
                            )
                        )
                        runningCurrentTime += duration
                    }

                    // 2. Accelerated slice
                    if (rEnd > rStart) {
                        val speed = ramp.speedMultiplier.toDouble().coerceAtLeast(0.25)
                        val duration = (rEnd - rStart) / speed
                        newSegments.add(
                            TimelineSegment(
                                id = "${baseSeg.id}_ramp_${newSegments.size}",
                                projectId = projectId,
                                originalStart = rStart,
                                originalEnd = rEnd,
                                currentStart = runningCurrentTime,
                                currentEnd = runningCurrentTime + duration,
                                isRetained = true,
                                speedMultiplier = speed
                            )
                        )
                        runningCurrentTime += duration
                    }
                    cursor = rEnd
                }

                // 3. Un-accelerated slice after the last ramp
                if (cursor < baseSeg.originalEnd) {
                    val duration = (baseSeg.originalEnd - cursor) / 1.0
                    newSegments.add(
                        TimelineSegment(
                            id = "${baseSeg.id}_post_${newSegments.size}",
                            projectId = projectId,
                            originalStart = cursor,
                            originalEnd = baseSeg.originalEnd,
                            currentStart = runningCurrentTime,
                            currentEnd = runningCurrentTime + duration,
                            isRetained = true,
                            speedMultiplier = 1.0
                        )
                    )
                    runningCurrentTime += duration
                }
            }
        }

        return copy(
            currentDuration = runningCurrentTime,
            segments = newSegments
        )
    }

    companion object {
        fun identity(projectId: String, duration: Double): TimelineMap {
            val fullSegment = TimelineSegment(
                id = "seg_init_0",
                projectId = projectId,
                originalStart = 0.0,
                originalEnd = duration,
                currentStart = 0.0,
                currentEnd = duration,
                isRetained = true,
                stageApplied = PipelineStatus.IDLE,
                speedMultiplier = 1.0
            )
            return TimelineMap(
                projectId = projectId,
                originalDuration = duration,
                currentDuration = duration,
                segments = listOf(fullSegment),
                removedRanges = emptyList()
            )
        }
    }
}
package com.vineyard.aivideostudio.media.timeline

import com.vineyard.aivideostudio.ai.model.CommentarySegmentDto
import com.vineyard.aivideostudio.ai.model.HighlightSegment
import com.vineyard.aivideostudio.ai.model.TrimSegment
import com.vineyard.aivideostudio.core.model.Caption
import com.vineyard.aivideostudio.core.model.PipelineStatus
import com.vineyard.aivideostudio.core.model.TimeRange
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.TimelineSegment
import com.vineyard.aivideostudio.core.model.effects.BlurSpec
import com.vineyard.aivideostudio.core.model.effects.ReplacementOverlaySpec
import com.vineyard.aivideostudio.core.model.effects.TextCardSpec
import com.vineyard.aivideostudio.core.model.effects.TrackingIndicatorSpec

object TimelineMapper {

    /**
     * Updates timeline coordinates when multiple highlight segments are spliced into a montage.
     */
    fun applyHighlightSplice(
        currentMap: TimelineMap,
        segmentsToKeep: List<HighlightSegment>,
        stage: PipelineStatus = PipelineStatus.TRIM_EXECUTION
    ): TimelineMap {
        if (segmentsToKeep.isEmpty()) return currentMap

        val newRetainedSegments = mutableListOf<TimelineSegment>()
        val newRemovedRanges = mutableListOf<TimeRange>()
        var currentCursor = 0.0
        var segmentCounter = 0

        val sortedKeeps = segmentsToKeep.sortedBy { it.start }
        var lastEnd = 0.0

        for (keep in sortedKeeps) {
            if (keep.start > lastEnd) {
                newRemovedRanges.add(TimeRange(lastEnd, keep.start))
            }
            val duration = (keep.end - keep.start).coerceAtLeast(0.1)
            newRetainedSegments.add(
                TimelineSegment(
                    id = "seg_${stage.name.lowercase()}_${segmentCounter++}",
                    projectId = currentMap.projectId,
                    originalStart = keep.start,
                    originalEnd = keep.end,
                    currentStart = currentCursor,
                    currentEnd = currentCursor + duration,
                    isRetained = true,
                    stageApplied = stage,
                    speedMultiplier = 1.0
                )
            )
            currentCursor += duration
            lastEnd = keep.end
        }

        if (lastEnd < currentMap.originalDuration) {
            newRemovedRanges.add(TimeRange(lastEnd, currentMap.originalDuration))
        }

        return TimelineMap(
            projectId = currentMap.projectId,
            originalDuration = currentMap.originalDuration,
            currentDuration = currentCursor,
            segments = newRetainedSegments,
            removedRanges = newRemovedRanges
        )
    }

    /**
     * Updates an existing timeline map by cutting out the specified trim segments.
     */
    fun applyTrim(
        currentMap: TimelineMap,
        segmentsToRemove: List<TrimSegment>,
        stage: PipelineStatus = PipelineStatus.TRIM_EXECUTION
    ): TimelineMap {
        if (segmentsToRemove.isEmpty()) return currentMap

        val sortedCuts = segmentsToRemove.sortedBy { it.start }
        val newRemovedRanges = currentMap.removedRanges.toMutableList()
        val newRetainedSegments = mutableListOf<TimelineSegment>()

        var currentCursor = 0.0
        var segmentCounter = 0

        for (seg in currentMap.segments.filter { it.isRetained }) {
            val segStartInCurrent = seg.currentStart
            val segEndInCurrent = seg.currentEnd

            val overlappingCuts = sortedCuts.filter { cut ->
                cut.end > segStartInCurrent && cut.start < segEndInCurrent
            }

            var subCursor = segStartInCurrent
            for (cut in overlappingCuts) {
                val cutStartClamped = cut.start.coerceIn(segStartInCurrent, segEndInCurrent)
                val cutEndClamped = cut.end.coerceIn(segStartInCurrent, segEndInCurrent)

                if (cutStartClamped > subCursor) {
                    val pieceDuration = cutStartClamped - subCursor
                    val origStart = seg.originalStart + (subCursor - segStartInCurrent)
                    val origEnd = origStart + pieceDuration

                    newRetainedSegments.add(
                        TimelineSegment(
                            id = "seg_${stage.name.lowercase()}_${segmentCounter++}",
                            projectId = currentMap.projectId,
                            originalStart = origStart,
                            originalEnd = origEnd,
                            currentStart = currentCursor,
                            currentEnd = currentCursor + pieceDuration,
                            isRetained = true,
                            stageApplied = stage,
                            speedMultiplier = seg.speedMultiplier
                        )
                    )
                    currentCursor += pieceDuration
                }

                newRemovedRanges.add(TimeRange(cutStartClamped, cutEndClamped))
                subCursor = cutEndClamped
            }

            if (subCursor < segEndInCurrent) {
                val pieceDuration = segEndInCurrent - subCursor
                val origStart = seg.originalStart + (subCursor - segStartInCurrent)
                val origEnd = origStart + pieceDuration

                newRetainedSegments.add(
                    TimelineSegment(
                        id = "seg_${stage.name.lowercase()}_${segmentCounter++}",
                        projectId = currentMap.projectId,
                        originalStart = origStart,
                        originalEnd = origEnd,
                        currentStart = currentCursor,
                        currentEnd = currentCursor + pieceDuration,
                        isRetained = true,
                        stageApplied = stage,
                        speedMultiplier = seg.speedMultiplier
                    )
                )
                currentCursor += pieceDuration
            }
        }

        return TimelineMap(
            projectId = currentMap.projectId,
            originalDuration = currentMap.originalDuration,
            currentDuration = currentCursor,
            segments = newRetainedSegments,
            removedRanges = newRemovedRanges
        )
    }

    /**
     * Remaps instruction and conclusion text cards to match accelerated video timing.
     */
    fun remapTextCards(
        timelineMap: TimelineMap,
        cards: List<TextCardSpec>
    ): List<TextCardSpec> {
        return cards.map { card ->
            val newStartMs = timelineMap.mapOriginalToCurrentMs(card.startTimeMs)
            val newEndMs = timelineMap.mapOriginalToCurrentMs(card.endTimeMs).coerceAtLeast(newStartMs + 500L)

            card.copy(
                startTimeMs = newStartMs,
                endTimeMs = newEndMs
            )
        }
    }

    /**
     * Remaps all commentary cue start and end timestamps from the raw video timeline
     * to the compressed timeline (taking speed play and highlight cuts into account).
     */
    fun remapCommentarySegments(
        timelineMap: TimelineMap,
        segments: List<CommentarySegmentDto>
    ): List<CommentarySegmentDto> {
        return segments.map { seg ->
            val origStart = seg.start ?: 0.0
            val origEnd = seg.end ?: (origStart + 3.0)
            val newStart = timelineMap.mapOriginalToCurrent(origStart)
            val newEnd = timelineMap.mapOriginalToCurrent(origEnd).coerceAtLeast(newStart + 0.5)

            seg.copy(
                start = newStart,
                end = newEnd
            )
        }
    }

    /**
     * Remaps burned-in captions to match the accelerated video timeline.
     */
    fun remapCaptions(
        timelineMap: TimelineMap,
        captions: List<Caption>
    ): List<Caption> {
        return captions.map { cap ->
            val newStart = timelineMap.mapOriginalToCurrent(cap.start)
            val newEnd = timelineMap.mapOriginalToCurrent(cap.end).coerceAtLeast(newStart + 0.5)

            cap.copy(
                start = newStart,
                end = newEnd
            )
        }
    }

    /**
     * Remaps selective Gaussian and Mosaic privacy blur time windows.
     */
    fun remapBlurSpecs(
        timelineMap: TimelineMap,
        blurSpecs: List<BlurSpec>
    ): List<BlurSpec> {
        return blurSpecs.map { spec ->
            val newStartMs = timelineMap.mapOriginalToCurrentMs(spec.startTimeMs)
            val newEndMs = timelineMap.mapOriginalToCurrentMs(spec.endTimeMs).coerceAtLeast(newStartMs + 200L)

            spec.copy(
                startTimeMs = newStartMs,
                endTimeMs = newEndMs
            )
        }
    }

    /**
     * Remaps 1:1 brand logo / watermark / emoji replacements to the compressed timeline.
     */
    fun remapReplacementOverlays(
        timelineMap: TimelineMap,
        overlays: List<ReplacementOverlaySpec>
    ): List<ReplacementOverlaySpec> {
        return overlays.map { overlay ->
            val newStartMs = timelineMap.mapOriginalToCurrentMs(overlay.startTimeMs)
            val newEndMs = timelineMap.mapOriginalToCurrentMs(overlay.endTimeMs).coerceAtLeast(newStartMs + 200L)

            overlay.copy(
                startTimeMs = newStartMs,
                endTimeMs = newEndMs
            )
        }
    }

    /**
     * Remaps motion tracking keyframes, stationary button locks, and spotlight callouts.
     */
    fun remapTrackingIndicators(
        timelineMap: TimelineMap,
        indicators: List<TrackingIndicatorSpec>
    ): List<TrackingIndicatorSpec> {
        return indicators.map { indicator ->
            val newStartMs = if (indicator.startTimeMs > 0L) timelineMap.mapOriginalToCurrentMs(indicator.startTimeMs) else 0L
            val newEndMs = if (indicator.endTimeMs < Long.MAX_VALUE) {
                timelineMap.mapOriginalToCurrentMs(indicator.endTimeMs).coerceAtLeast(newStartMs + 300L)
            } else {
                Long.MAX_VALUE
            }

            val remappedKeyframes = indicator.keyframes
                .map { kf -> kf.copy(timeMs = timelineMap.mapOriginalToCurrentMs(kf.timeMs)) }
                .sortedBy { it.timeMs }

            indicator.copy(
                startTimeMs = newStartMs,
                endTimeMs = newEndMs,
                keyframes = remappedKeyframes
            )
        }
    }

    fun mapOriginalToCurrent(timelineMap: TimelineMap, originalSec: Double): Double {
        return timelineMap.mapOriginalToCurrent(originalSec)
    }

    fun mapCurrentToOriginal(timelineMap: TimelineMap, currentSec: Double): Double {
        return timelineMap.mapCurrentToOriginal(currentSec)
    }
}
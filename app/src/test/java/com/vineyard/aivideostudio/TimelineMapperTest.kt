package com.vineyard.aivideostudio

import com.vineyard.aivideostudio.ai.model.TrimSegment
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.media.timeline.TimelineMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TimelineMapperTest {

    @Test
    fun testIdentityTimelineMap() {
        val map = TimelineMap.identity("proj_1", 30.0)
        assertEquals(30.0, map.originalDuration, 0.001)
        assertEquals(30.0, map.currentDuration, 0.001)
        assertEquals(15.0, map.mapOriginalToCurrent(15.0)!!, 0.001)
        assertEquals(15.0, map.mapCurrentToOriginal(15.0), 0.001)
    }

    @Test
    fun testSingleTrimMapping() {
        // Original 0..30. Remove 10..15.
        // Current timeline should be 0..25s
        val initialMap = TimelineMap.identity("proj_1", 30.0)
        val cuts = listOf(TrimSegment(start = 10.0, end = 15.0, reason = "Dead air"))

        val trimmedMap = TimelineMapper.applyTrim(initialMap, cuts)

        assertEquals(25.0, trimmedMap.currentDuration, 0.001)
        assertEquals(5.0, trimmedMap.mapOriginalToCurrent(5.0)!!, 0.001)
        // 12.0 was removed, so mapping should return null
        assertNull(trimmedMap.mapOriginalToCurrent(12.0))
        // 20.0 in original should now be at 15.0 in current
        assertEquals(15.0, trimmedMap.mapOriginalToCurrent(20.0)!!, 0.001)

        // Reverse mapping: 15.0 in current should map back to 20.0 in original
        assertEquals(20.0, trimmedMap.mapCurrentToOriginal(15.0), 0.001)
    }

    @Test
    fun testMultipleCutsMapping() {
        val initialMap = TimelineMap.identity("proj_2", 60.0)
        val cuts = listOf(
            TrimSegment(start = 10.0, end = 15.0, reason = "Cut 1"),
            TrimSegment(start = 25.0, end = 30.0, reason = "Cut 2")
        )

        val trimmedMap = TimelineMapper.applyTrim(initialMap, cuts)
        // 60 - 5 - 5 = 50s
        assertEquals(50.0, trimmedMap.currentDuration, 0.001)
        assertNull(trimmedMap.mapOriginalToCurrent(12.0))
        assertNull(trimmedMap.mapOriginalToCurrent(28.0))
    }
}

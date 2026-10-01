package com.vineyard.aivideostudio

import com.vineyard.aivideostudio.ai.model.TrimDecision
import com.vineyard.aivideostudio.core.util.JsonUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiResponseParserTest {

    @Test
    fun testJsonMarkdownExtraction() {
        val markdownWrapped = """
            ```json
            {
              "operation": "trim",
              "isNecessary": true,
              "segmentsToRemove": [
                {
                  "start": 5.0,
                  "end": 8.0,
                  "reason": "Silence"
                }
              ],
              "explanation": "Removed awkward pause"
            }
            ```
        """.trimIndent()

        val parsed = JsonUtils.fromJson<TrimDecision>(markdownWrapped)
        assertNotNull(parsed)
        assertTrue(parsed!!.isNecessary)
        assertEquals(1, parsed.segmentsToRemove.size)
        assertEquals(5.0, parsed.segmentsToRemove[0].start, 0.001)
    }

    @Test
    fun testRawJsonExtractionWithSurroundingText() {
        val mixedText = """
            Here is your editing plan:
            {
              "operation": "trim",
              "isNecessary": false,
              "segmentsToRemove": [],
              "explanation": "No edits required"
            }
            Hope this helps!
        """.trimIndent()

        val parsed = JsonUtils.fromJson<TrimDecision>(mixedText)
        assertNotNull(parsed)
        assertEquals(false, parsed!!.isNecessary)
    }
}

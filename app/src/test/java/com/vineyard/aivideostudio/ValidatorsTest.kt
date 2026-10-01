package com.vineyard.aivideostudio

import com.vineyard.aivideostudio.ai.model.CaptionDecision
import com.vineyard.aivideostudio.ai.model.CaptionItem
import com.vineyard.aivideostudio.ai.model.CropDecision
import com.vineyard.aivideostudio.ai.model.TrimDecision
import com.vineyard.aivideostudio.ai.model.TrimSegment
import com.vineyard.aivideostudio.ai.model.ZoomDecision
import com.vineyard.aivideostudio.ai.validator.AiResponseValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {

    @Test
    fun testValidTrim() {
        val decision = TrimDecision(
            isNecessary = true,
            segmentsToRemove = listOf(
                TrimSegment(start = 2.0, end = 5.0, reason = "Pause"),
                TrimSegment(start = 8.0, end = 10.0, reason = "Dead air")
            )
        )
        val result = AiResponseValidator.validateTrim(decision, 20.0)
        assertTrue(result.isValid)
    }

    @Test
    fun testOverlappingTrimIsRejected() {
        val decision = TrimDecision(
            isNecessary = true,
            segmentsToRemove = listOf(
                TrimSegment(start = 2.0, end = 6.0, reason = "Cut 1"),
                TrimSegment(start = 4.0, end = 8.0, reason = "Overlapping cut")
            )
        )
        val result = AiResponseValidator.validateTrim(decision, 20.0)
        assertFalse(result.isValid)
    }

    @Test
    fun testTrimExceedingDurationIsRejected() {
        val decision = TrimDecision(
            isNecessary = true,
            segmentsToRemove = listOf(TrimSegment(start = 15.0, end = 25.0, reason = "Too long"))
        )
        val result = AiResponseValidator.validateTrim(decision, 20.0)
        assertFalse(result.isValid)
    }

    @Test
    fun testCropBounds() {
        val validCrop = CropDecision(isNecessary = true, x = 0.1f, y = 0.05f, width = 0.8f, height = 0.9f)
        assertTrue(AiResponseValidator.validateCrop(validCrop).isValid)

        val invalidCropX = CropDecision(isNecessary = true, x = 0.6f, y = 0.1f, width = 0.5f, height = 0.8f)
        assertFalse(AiResponseValidator.validateCrop(invalidCropX).isValid)

        val negativeCrop = CropDecision(isNecessary = true, x = -0.1f, y = 0.0f, width = 0.5f, height = 0.5f)
        assertFalse(AiResponseValidator.validateCrop(negativeCrop).isValid)
    }

    @Test
    fun testZoomValidation() {
        val validZoom = ZoomDecision(isNecessary = true, start = 1.0, end = 3.0, fromScale = 1.0f, toScale = 1.2f)
        assertTrue(AiResponseValidator.validateZoom(validZoom, 10.0).isValid)

        val negativeScale = ZoomDecision(isNecessary = true, start = 1.0, end = 3.0, fromScale = -1.0f, toScale = 1.0f)
        assertFalse(AiResponseValidator.validateZoom(negativeScale, 10.0).isValid)
    }

    @Test
    fun testCaptionValidation() {
        val validCaptions = CaptionDecision(
            isNecessary = true,
            captions = listOf(
                CaptionItem(text = "Awesome play!", start = 1.0, end = 3.0, x = 0.5f, y = 0.8f)
            )
        )
        assertTrue(AiResponseValidator.validateCaptions(validCaptions, 10.0).isValid)

        val blankCaption = CaptionDecision(
            isNecessary = true,
            captions = listOf(
                CaptionItem(text = "   ", start = 1.0, end = 3.0)
            )
        )
        assertFalse(AiResponseValidator.validateCaptions(blankCaption, 10.0).isValid)
    }

    @Test
    fun testYoutubeUrlValidation() {
        val validStandard = "https://www.youtube.com/watch?v=dQw4w9WgXcQ"
        val validShortUrl = "https://youtu.be/dQw4w9WgXcQ"
        val validShorts = "https://www.youtube.com/shorts/dQw4w9WgXcQ"
        val validMobile = "https://m.youtube.com/watch?v=dQw4w9WgXcQ"

        assertTrue(com.vineyard.aivideostudio.ui.screens.create.CreateUiState.isValidYoutubeUrl(validStandard))
        assertTrue(com.vineyard.aivideostudio.ui.screens.create.CreateUiState.isValidYoutubeUrl(validShortUrl))
        assertTrue(com.vineyard.aivideostudio.ui.screens.create.CreateUiState.isValidYoutubeUrl(validShorts))
        assertTrue(com.vineyard.aivideostudio.ui.screens.create.CreateUiState.isValidYoutubeUrl(validMobile))

        val invalidGeneric = "https://example.com/video.mp4"
        val invalidEmpty = ""
        val invalidNoId = "https://www.youtube.com/"

        assertFalse(com.vineyard.aivideostudio.ui.screens.create.CreateUiState.isValidYoutubeUrl(invalidGeneric))
        assertFalse(com.vineyard.aivideostudio.ui.screens.create.CreateUiState.isValidYoutubeUrl(invalidEmpty))
        assertFalse(com.vineyard.aivideostudio.ui.screens.create.CreateUiState.isValidYoutubeUrl(invalidNoId))
    }
}

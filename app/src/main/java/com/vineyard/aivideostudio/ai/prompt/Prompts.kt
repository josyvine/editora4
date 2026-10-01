package com.vineyard.aivideostudio.ai.prompt

import com.vineyard.aivideostudio.ai.model.SourceAnalysis
import com.vineyard.aivideostudio.core.model.TimelineMap
import com.vineyard.aivideostudio.core.model.VideoMetadata

object Prompts {

    fun buildSourceAnalysisPrompt(metadata: VideoMetadata, sourceYoutubeUrl: String? = null): String = """
        You are an elite video director performing a complete, comprehensive multimodal source analysis.
        ${if (!sourceYoutubeUrl.isNullOrBlank()) "SOURCE REFERENCE: $sourceYoutubeUrl\n" else ""}
        TECHNICAL METADATA:
        - Total Duration: ${metadata.durationSeconds} seconds
        - Resolution: ${metadata.width}x${metadata.height}
        - Orientation: ${if (metadata.isPortrait) "PORTRAIT" else "LANDSCAPE"}
        - FPS: ${metadata.frameRate}
        
        CRUCIAL MANDATE:
        Watch the ENTIRE video from timestamp 0.0 to ${metadata.durationSeconds}.
        Do not stop at the beginning. Analyze the complete timeline: the intro, full progression of events, peak climax/turning points, and the final conclusion/winner.
        Ground your analysis 100% in the actual subjects, dialogue, and actions present on screen regardless of category (Sports, Science, Technology, Gaming, News, Comedy, Education, Wrestling, etc.).
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "duration": ${metadata.durationSeconds},
          "resolution": "${metadata.width}x${metadata.height}",
          "orientation": "${if (metadata.isPortrait) "PORTRAIT" else "LANDSCAPE"}",
          "category": "SPORTS / SCIENCE / TECH / GAMING / NEWS / COMEDY / DOCUMENTARY / ENTERTAINMENT",
          "summary": "Accurate, grounded summary covering the full narrative arc from start to end",
          "scenes": [
            {
              "start": 0.0,
              "end": ${metadata.durationSeconds},
              "description": "Scene overview with specific details of on-screen action",
              "importance": "CRITICAL / HIGH / MEDIUM / LOW / REMOVABLE",
              "keySubjects": ["names of visible subjects, items, or topics"]
            }
          ],
          "dialogueSegments": [
            {
              "start": 0.0,
              "end": ${metadata.durationSeconds.coerceAtMost(5.0)},
              "speaker": "Speaker",
              "text": "Actual transcribed dialogue"
            }
          ],
          "criticalContent": ["Key highlights, breakthroughs, punchlines, or final outcome"],
          "highlights": [
            {
              "start": 0.0,
              "end": ${metadata.durationSeconds.coerceAtMost(10.0)},
              "title": "Highlight Title",
              "description": "Why this specific moment is important",
              "importance": "CRITICAL"
            }
          ],
          "editingCandidates": [
            {
              "start": 0.0,
              "end": 0.8,
              "recommendation": "TRIM",
              "reason": "Eliminate dead air to tighten pacing"
            }
          ],
          "suggestedEditingStrategy": "High-retention pacing cut with continuous dynamic voiceover"
        }
    """.trimIndent()

    fun buildTrimDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double,
        timelineMap: TimelineMap,
        targetMode: String = "HIGHLIGHTS"
    ): String = """
        You are an elite video editing director creating a transformative derivative edit.
        
        EDITING MODE: $targetMode
        SOURCE CONTEXT:
        - Source Duration: $currentDuration seconds
        - Category: ${sourceAnalysis.category}
        - Full Story: ${sourceAnalysis.summary}
        - Climax / Outcome: ${sourceAnalysis.criticalContent.joinToString()}
        
        RULES BASED ON EDITING MODE:
        1. IF targetMode == "SHORT_60S":
           - Select 4 to 6 key highlight clips from across the entire video (intro hook, middle build-up, turning point, climax/ending).
           - Total combined duration of "segmentsToKeep" MUST equal approximately 55.0 to 60.0 seconds.
        2. IF targetMode == "RECAP_EXTENDED":
           - Select key narrative clips across the whole video totaling approximately 55% to 65% of the original duration (e.g. 4 to 5 minutes for an 8-minute video).
        3. IF targetMode == "TRIM_REMOVE":
           - Identify dead-air or redundant sections in "segmentsToRemove".
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "highlight_compile",
          "isNecessary": true,
          "targetMode": "$targetMode",
          "segmentsToKeep": [
            {
              "start": 0.0,
              "end": 10.0,
              "title": "Opening Hook",
              "description": "Establish the scene and stakes",
              "importance": "CRITICAL"
            }
          ],
          "segmentsToRemove": [],
          "explanation": "Extracted key narrative highlights across the full timeline for $targetMode"
        }
    """.trimIndent()

    fun buildTrimQaPrompt(
        sourceAnalysis: SourceAnalysis,
        expectedCutsCount: Int,
        newDuration: Double
    ): String = """
        Inspect the TRIM/HIGHLIGHT compilation operation.
        - Original Duration: ${sourceAnalysis.duration}s
        - New Duration: ${newDuration}s
        - Segments Processed: $expectedCutsCount
        
        Verify: Narrative arc and final outcome (${sourceAnalysis.criticalContent.joinToString()}) are preserved.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.98,
          "feedback": "Pacing successfully tightened with core narrative intact.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildCropDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        targetAspectRatio: String,
        currentWidth: Int,
        currentHeight: Int
    ): String = """
        Evaluate framing and aspect ratio reframing.
        - Dimensions: ${currentWidth}x${currentHeight}
        - Target Aspect Ratio: $targetAspectRatio
        - Summary: ${sourceAnalysis.summary}
        
        Use normalized coordinates (0.0 to 1.0). Keep main action/speaker centered.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "crop",
          "isNecessary": true,
          "x": 0.0,
          "y": 0.0,
          "width": 1.0,
          "height": 1.0,
          "targetAspectRatio": "$targetAspectRatio",
          "explanation": "Framing reframed for $targetAspectRatio presentation"
        }
    """.trimIndent()

    fun buildCropQaPrompt(
        targetAspectRatio: String,
        appliedCrop: String
    ): String = """
        Inspect CROP operation: Aspect Ratio: $targetAspectRatio, Crop: $appliedCrop.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.95,
          "feedback": "Framing properly aligned.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildZoomDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        Decide on dynamic punch-in zoom for visual engagement.
        - Duration: $currentDuration seconds
        - Context: ${sourceAnalysis.summary}
        - Climax: ${sourceAnalysis.criticalContent.joinToString()}
        
        Apply a scale between 1.15x and 1.30x on high-impact moments.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "zoom",
          "isNecessary": true,
          "start": 0.0,
          "end": $currentDuration,
          "fromScale": 1.0,
          "toScale": 1.25,
          "centerX": 0.5,
          "centerY": 0.5,
          "explanation": "Dynamic zoom applied for visual transformation"
        }
    """.trimIndent()

    fun buildZoomQaPrompt(zoomDetails: String): String = """
        Inspect ZOOM: $zoomDetails.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.95,
          "feedback": "Zoom delivers visual transformation.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildCaptionDecisionPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        You are an elite subtitle director generating transformative captions that cover burned-in subtitles.
        
        ORIGINAL DIALOGUE:
        ${sourceAnalysis.dialogueSegments.mapIndexed { i, d -> "Segment ${i + 1} [${d.start}s - ${d.end}s]: \"${d.text}\"" }.joinToString("\n")}
        
        RULES:
        1. 1:1 Paraphrase every original line with fresh, copyright-safe wording retaining the exact meaning.
        2. Set "x": 0.5 and "y": 0.90 to anchor the background mask over bottom subtitle areas.
        3. Constrain all timestamps between 0.0 and $currentDuration.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "caption",
          "isNecessary": true,
          "captions": [
            {
              "text": "Rewritten sentence conveying identical meaning.",
              "start": 0.0,
              "end": ${currentDuration.coerceAtMost(3.5)},
              "x": 0.5,
              "y": 0.90,
              "style": "BOLD",
              "colorHex": "#FFFFFF"
            }
          ],
          "explanation": "1:1 transformative paraphrasing covering original subtitle area"
        }
    """.trimIndent()

    fun buildCaptionQaPrompt(captionCount: Int): String = """
        Inspect rendered captions ($captionCount items).
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.98,
          "feedback": "Captions accurately cover bottom subtitle space.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildCommentaryPrompt(
        sourceAnalysis: SourceAnalysis,
        currentDuration: Double
    ): String = """
        You are a world-class AI voiceover commentator providing 100% original, continuous audio narration.
        
        CRUCIAL MANDATE — COMPLETE AUDIO COVERAGE (ZERO DEAD AIR):
        The original audio is completely purged. Your commentary will be the sole soundtrack.
        You MUST write enough words to comfortably span the entire $currentDuration seconds at a standard speaking rate (~2.5 words per second / 150 words per minute).
        - For a 60-second video: Write ~140 to 160 words across synchronized segments.
        - For a 4-minute video: Write ~550 to 650 words across continuous narrative segments.
        Do NOT write a 20-word summary for a long video!
        
        VIDEO CONTEXT:
        - Category: ${sourceAnalysis.category}
        - Total Duration to Cover: $currentDuration seconds
        - True Subject & Narrative: ${sourceAnalysis.summary}
        - Highlights & Climax: ${sourceAnalysis.criticalContent.joinToString()}
        
        GENRE & VOCAL ACTING ADAPTATIONS:
        1. SPORTS / WRESTLING / ACTION:
           - Electrifying, fast-paced play-by-play commentary.
           - Cues: [SCREAMING], [LOUD SHOUT], [MOANING IN DISBELIEF], [LOUD ROAR], [EXPLOSIVE EXCITEMENT].
        2. SCIENCE / TECH / EDUCATION:
           - Engaging, clear, fascinating narration explaining the visual phenomena or tech.
           - Cues: [FASCINATED], [ENTHUSIASTIC], [CLEAR EXPLANATION], [THOUGHTFUL].
        3. NEWS / CRIME / DOCUMENTARY:
           - Authoritative, dramatic investigative reporting.
           - Cues: [SERIOUS], [URGENT], [DRAMATIC PAUSE], [STERN].
        4. COMEDY / GAMING / REACTION:
           - Hilarious, energetic, relatable commentary.
           - Cues: [LAUGHING], [WHEEZING], [SHOCKED GASP], [HYPE].
        
        RULES:
        1. Break your commentary into multiple sequential segments spanning from 0.0s to $currentDuration seconds with NO unaddressed gaps longer than 2 seconds.
        2. Ground every line in what happens on screen. No introductory greetings ("Hello viewers").
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "operation": "commentary",
          "isNecessary": true,
          "tone": "Genre-adapted dynamic commentary",
          "commentarySegments": [
            {
              "start": 0.0,
              "end": ${currentDuration.coerceAtMost(15.0)},
              "text": "[ENTHUSIASTIC]: We kick off with immediate high-stakes action right from the opening moments!"
            },
            {
              "start": ${currentDuration.coerceAtMost(15.0)},
              "end": $currentDuration,
              "text": "[EXPLOSIVE EXCITEMENT]: And here comes the deciding moment as the outcome is sealed!"
            }
          ],
          "explanation": "Continuous beat-by-beat voiceover script covering full $currentDuration seconds"
        }
    """.trimIndent()

    fun buildAudioQaPrompt(details: String): String = """
        Inspect audio replacement: $details.
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.98,
          "feedback": "Original audio purged. AI commentary soundtrack active.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()

    fun buildFinalQaPrompt(
        sourceAnalysis: SourceAnalysis,
        finalDuration: Double,
        pipelineHistorySummary: String
    ): String = """
        Executive QA Director final production audit:
        - Source Duration: ${sourceAnalysis.duration}s
        - Final Output Duration: ${finalDuration}s
        - Applied Transformations: $pipelineHistorySummary
        
        OUTPUT FORMAT (STRICT JSON ONLY):
        {
          "verdict": "PASS",
          "confidence": 0.99,
          "feedback": "Production ready. Derivative transformation complete, audio purged, captions burned in.",
          "corrections": [],
          "criticalContentPreserved": true
        }
    """.trimIndent()
}
# Editora — AI Video Studio for Android

Professional AI-Assisted Video Production Studio powered by Gemini reasoning and Android Media3.

## Architecture

Editora maintains a strict separation of concerns between reasoning and execution:
- **Gemini is the Director**: Understands semantic narrative, identifies scenes, recommends precise cuts, reframings, punch-in zooms, subtitles, and commentary.
- **Android Media3 is the Editor**: Deterministically performs exact media transformations (frame-accurate clipping, cropping, scaling, overlays, audio extraction, mixing, and MP4 encoding).
- **Sequential State Machine**: Major operations run independently with dedicated verification:
  1. Source Analysis
  2. Audio Extraction
  3. Dialogue Transcription
  4. Trim Analysis & Execution (Skipped if unnecessary)
  5. Trim QA Verification
  6. Crop & Reframe Analysis & Execution (9:16, 16:9, 1:1)
  7. Crop QA Verification
  8. Dynamic Zoom Punch-in & QA
  9. Caption Generation & Positioning QA
  10. Voiceover Commentary Composition
  11. Gemini TTS Speech Synthesis
  12. Audio Track Mixing & Balance QA
  13. Executive Final QA
  14. Final Production Export

## Key Capabilities

1. **Dynamic Model Discovery & Classification**
   - No hardcoded model lists. Fetches available models directly from `https://generativelanguage.googleapis.com/v1beta/models`.
   - Classifies models based on actual capabilities into Video Analysis, Transcription, Director, Commentary, TTS, and Live Voice.
   - Independent model assignment per purpose.

2. **Timeline Mapping System**
   - Automatically maintains bi-directional mappings between original source timestamps and current edited video timestamps across successive cuts.

3. **Storage Access Framework & Security**
   - Uses SAF and MediaStore for user-authorized media files.
   - API keys are securely managed and masked in UI. Full keys and sensitive payloads are never written to logs.

4. **Background Resilience**
   - Employs a foreground media processing service to ensure exports and transformations finish even when switching apps.

## Requirements & Setup

- Modern Android device or emulator running API 24+
- Gemini API Key configured in the AI Studio Secrets panel or Settings tab.

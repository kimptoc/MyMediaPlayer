package com.example.mymediaplayer.shared

import org.json.JSONArray
import org.json.JSONObject

/**
 * Selects which cloud text-to-speech engine synthesises track announcements.
 *
 * The on-device Android [android.speech.tts.TextToSpeech] engine remains the ultimate
 * fallback for every backend when cloud generation is unavailable.
 */
enum class TtsBackend(val storageValue: String, val displayName: String) {
    /** "As now": Kilo text + Google Cloud TTS standard (Neural2) voice. */
    STANDARD("standard", "Google Cloud TTS (standard)"),

    /** Option 1: Google Cloud TTS using a Gemini-TTS voice. Reuses the Cloud TTS key. */
    CLOUD_GEMINI("cloud_gemini", "Google Cloud TTS (Gemini voice)"),

    /** Option 2: Gemini API `generateContent` TTS. Needs a separate Gemini API key. */
    GEMINI_API("gemini_api", "Gemini API TTS");

    companion object {
        /** Falls back to [STANDARD] for null/unknown values so existing installs keep working. */
        fun fromStorage(value: String?): TtsBackend =
            entries.firstOrNull { it.storageValue == value } ?: STANDARD
    }
}

/** Model/voice identifiers shared by the synthesis paths and key validation. */
object TtsModels {
    const val GEMINI_API_TTS_MODEL = "gemini-2.5-flash-preview-tts"
    const val CLOUD_TTS_GEMINI_MODEL = "gemini-2.5-flash-tts"
    const val GEMINI_VOICE = "Kore"

    const val GEMINI_API_TTS_URL =
        "https://generativelanguage.googleapis.com/v1beta/models/$GEMINI_API_TTS_MODEL:generateContent"

    /**
     * Builds the JSON body for a single-speaker Gemini TTS `generateContent` request. Shared by
     * real synthesis and key validation so the two can never drift apart.
     */
    fun geminiTtsRequestBody(text: String): String = JSONObject().apply {
        put("contents", JSONArray().put(JSONObject().apply {
            put("parts", JSONArray().put(JSONObject().put("text", text)))
        }))
        put("generationConfig", JSONObject().apply {
            put("responseModalities", JSONArray().put("AUDIO"))
            put("speechConfig", JSONObject().apply {
                put("voiceConfig", JSONObject().apply {
                    put("prebuiltVoiceConfig", JSONObject().apply {
                        put("voiceName", GEMINI_VOICE)
                    })
                })
            })
        })
    }.toString()
}

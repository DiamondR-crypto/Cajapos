package com.example.utils

import android.content.Context
import android.speech.tts.TextToSpeech
import java.util.Locale

class TextToSpeechManager(context: Context) {
    private var tts: TextToSpeech? = null
    private var isInitialized = false

    init {
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale("es", "ES")
                isInitialized = true
            }
        }
    }

    fun speak(text: String) {
        if (isInitialized) {
            // Clean text for speech: remove markdown symbols, emojis and other characters not needed for audio
            val cleanText = text
                .replace("*", "")
                .replace("_", "")
                .replace("#", "")
                .replace("`", "")
                .replace("~", "")
                .replace(">", "")
                // Remove most emojis/special symbols but keep basic punctuation
                .replace(Regex("[\\uD83C-\\uDBFF\\uDC00-\\uDFFF]+"), "") 
                .replace(Regex("\\[(.*?)\\]\\(.*?\\)"), "$1") // Keep link text, remove URL
                .trim()
            
            tts?.speak(cleanText, TextToSpeech.QUEUE_FLUSH, null, null)
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun shutdown() {
        tts?.stop()
        tts?.shutdown()
    }
}

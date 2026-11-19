package com.example.data

import android.content.Context

object AppSettings {
    private const val PREFS_NAME = "wspolna_lista_prefs"
    private const val KEY_SPEECH_RATE = "tts_speech_rate"
    private const val KEY_PERMISSIONS_COMPLETED = "permissions_completed"
    private const val KEY_LANGUAGE = "app_language" // "pl" or "en"
    const val DEFAULT_SPEECH_RATE = 0.85f

    fun getSpeechRate(context: Context): Float {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getFloat(KEY_SPEECH_RATE, DEFAULT_SPEECH_RATE).coerceIn(0.5f, 1.5f)
    }

    fun setSpeechRate(context: Context, rate: Float) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putFloat(KEY_SPEECH_RATE, rate.coerceIn(0.5f, 1.5f)).apply()
    }

    fun arePermissionsCompleted(context: Context): Boolean {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(KEY_PERMISSIONS_COMPLETED, false)
    }

    fun setPermissionsCompleted(context: Context, completed: Boolean) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putBoolean(KEY_PERMISSIONS_COMPLETED, completed).apply()
    }

    fun getLanguage(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getString(KEY_LANGUAGE, "pl") ?: "pl"
    }

    fun setLanguage(context: Context, lang: String) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_LANGUAGE, if (lang == "en") "en" else "pl").apply()
    }
}

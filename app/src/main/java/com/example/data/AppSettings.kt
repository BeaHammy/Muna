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

    private const val KEY_CACHED_UID = "cached_uid"
    private const val KEY_CACHED_NAME = "cached_name"
    private const val KEY_CACHED_LIST_CODE = "cached_list_code"
    private const val KEY_CACHED_ROLE = "cached_role"

    fun saveCachedProfile(context: Context, profile: UserProfile) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(KEY_CACHED_UID, profile.uid)
            .putString(KEY_CACHED_NAME, profile.name)
            .putString(KEY_CACHED_LIST_CODE, profile.listCode)
            .putString(KEY_CACHED_ROLE, profile.role)
            .apply()
    }

    fun getCachedProfile(context: Context, currentUid: String): UserProfile? {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val uid = prefs.getString(KEY_CACHED_UID, null)
        if (uid.isNullOrBlank() || uid != currentUid) return null
        val name = prefs.getString(KEY_CACHED_NAME, "") ?: ""
        val listCode = prefs.getString(KEY_CACHED_LIST_CODE, "") ?: ""
        val role = prefs.getString(KEY_CACHED_ROLE, "opiekun") ?: "opiekun"
        if (name.isBlank() || listCode.isBlank()) return null
        return UserProfile(uid = uid, name = name, listCode = listCode, role = role)
    }

    fun clearCachedProfile(context: Context) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit()
            .remove(KEY_CACHED_UID)
            .remove(KEY_CACHED_NAME)
            .remove(KEY_CACHED_LIST_CODE)
            .remove(KEY_CACHED_ROLE)
            .apply()
    }
}

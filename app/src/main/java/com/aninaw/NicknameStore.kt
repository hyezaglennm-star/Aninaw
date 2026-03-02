package com.aninaw

import android.content.Context

object NicknameStore {
    private const val PREFS = "aninaw_prefs"
    private const val KEY_NICKNAME = "nickname"
    private const val FALLBACK = "You"

    fun get(context: Context): String {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_NICKNAME, null)?.trim().orEmpty()
        return if (raw.isBlank()) FALLBACK else raw
    }

    fun set(context: Context, nickname: String?) {
        val cleaned = nickname?.trim().orEmpty()
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_NICKNAME, if (cleaned.isBlank()) FALLBACK else cleaned).apply()
    }
}
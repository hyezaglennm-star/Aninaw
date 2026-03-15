package com.aninaw

sealed class JournalHistoryItem {

    data class Header(
        val title: String
    ) : JournalHistoryItem()

    data class Entry(
        val id: Long,
        val title: String,
        val content: String,
        val type: String,
        val timestamp: Long,
        val mood: String? = null
    ) : JournalHistoryItem()
}
package com.aninaw

sealed class CalmToolHistoryItem {
    data class Header(
        val title: String
    ) : CalmToolHistoryItem()

    data class Entry(
        val id: Long,
        val toolType: String,
        val toolTitle: String,
        val completedAt: Long,
        val durationSeconds: Int?,
        val completionState: String
    ) : CalmToolHistoryItem()
}
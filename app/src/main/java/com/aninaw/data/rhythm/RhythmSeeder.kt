package com.aninaw.data.rhythm

object RhythmSeeder {

    const val PHASE_W61_64 = "W61_64_MINIMAL_STRENGTH"

    fun textsFor(domain: String): List<String> = when (domain) {
        "BODY" -> body100()
        "MIND" -> mind100()
        "SOUL" -> soul100()
        "ENV"  -> env100()
        else -> emptyList()
    }

    private fun body100(): List<String> = listOf(
        // PASTE 100 BODY texts here
        "Walk outdoors for 10 minutes"
    ).take(100)

    private fun mind100(): List<String> = listOf(
        // PASTE 100 MIND texts here
        "Read 5 pages of a meaningful book"
    ).take(100)

    private fun soul100(): List<String> = listOf(
        // PASTE 100 SOUL texts here
        "Sit in silence for 5 minutes"
    ).take(100)

    private fun env100(): List<String> = listOf(
        // PASTE 100 ENV texts here
        "Clean one small area"
    ).take(100)
}
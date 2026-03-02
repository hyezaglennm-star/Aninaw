package com.aninaw.rhythm

import com.aninaw.data.rhythm.RhythmDao
import kotlin.random.Random

class RhythmCycleManager(private val dao: RhythmDao) {

    // tracks used IDs per phase+domain, only while screen/app is alive
    private val usedIds: MutableMap<String, MutableSet<Long>> = mutableMapOf()

    private fun key(phaseKey: String, domain: String) = "$phaseKey::$domain"

    suspend fun nextRhythmText(phaseKey: String, domain: String): String {
        val pool = dao.getByPhaseAndDomain(phaseKey, domain)
        if (pool.isEmpty()) return "No rhythm available."

        val k = key(phaseKey, domain)
        val used = usedIds.getOrPut(k) { mutableSetOf() }

        var available = pool.filter { it.id !in used }

        // ✅ soft reset when exhausted
        if (available.isEmpty()) {
            used.clear()
            available = pool
        }

        val chosen = available[Random.nextInt(available.size)]
        used.add(chosen.id)
        return chosen.text
    }
}

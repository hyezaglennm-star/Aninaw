//UntangleDraft.kt
package com.aninaw

/**
 * Temporary in-memory model for the Untangle flow.
 * This holds user inputs across steps before saving.
 */
data class UntangleDraft(

    // STEP 1 — Moment
    var situation: String = "",
    var category: String = "",

    // STEP 2 — Automatic Thought
    var automaticThought: String = "",

    // STEP 3 — Feelings Before
    var overallEmotion: String = "",
    var intensityBefore: Int = 60,
    var feelingsBefore: String = "",
    var bodyBefore: String = "",

    // STEP 4 — Alternative Perspective
    var alternativeThought: String = "",

    // STEP 5 — Balanced Thought
    var balancedThought: String = "",

    // STEP 6 — Re-rate / After
    var intensityAfter: Int = 50,
    var feelingsAfter: String = "",
    var nextStep: String = ""

)

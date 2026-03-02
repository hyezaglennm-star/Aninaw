//CheckInAnchorActivity.kt
package com.aninaw
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.aninaw.checkin.Emotion
import com.aninaw.checkin.EmotionRepository
import com.aninaw.data.AninawDb
import com.aninaw.data.treering.TreeRingMemoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

class CheckInAnchorActivity : AppCompatActivity() {

    // Only keep these if you actually bind them in this screen layout
    private lateinit var tvSeasonTitle: TextView
    private lateinit var tvDayTitle: TextView
    private lateinit var tvDayEmotions: TextView
    private lateinit var tvDayHint: TextView

    private lateinit var repo: EmotionRepository

    private fun markTreeBonusForTomorrow() {
        val sp = getSharedPreferences("AninawPrefs", Context.MODE_PRIVATE)

        // epoch day without needing java.time or Build imports
        val todayEpochDay = System.currentTimeMillis() / 86_400_000L
        val nextEpochDay = todayEpochDay + 1L

        // Must match TreeVisualEngine.BONUS_PREFIX = "tree_bonus_day_"
        sp.edit()
            .putBoolean("tree_bonus_day_$nextEpochDay", true)
            .apply()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_check_in_anchor)

        setHeader(
            title = "Daily Check-in",
            subtitle = "Pause and record how you feel today."
        )

        // Handles back button for included view_back_button
        com.aninaw.util.BackButton.bind(this)

        // ---- Option cards (they are cards now, not Buttons) ----
        val optionQuick = findViewById<View>(R.id.optionQuickCheckIn)
        val optionStart = findViewById<View>(R.id.optionStartCheckIn)
        val optionBreathe = findViewById<View>(R.id.optionBreathe)

        // Quick Check-in opens bottom sheet
        val openQuickCheckIn = View.OnClickListener {
            QuickCheckInBottomSheet { entry ->

                // 1) Convert 1/2/3 intensity -> 0..1 float (same mapping you had)
                val intensityFloat = when (entry.intensity) {
                    1 -> 0.35f
                    3 -> 0.90f
                    else -> 0.60f
                }

                // 2) Save to Tree Ring DB
                val db = AninawDb.getDatabase(this)
                val ringRepo = TreeRingMemoryRepository(db)

                lifecycleScope.launch {
                    withContext(Dispatchers.IO) {
                        ringRepo.logQuickCheckIn(
                            date = LocalDate.now(),
                            emotion = entry.emotion,
                            intensity = intensityFloat,
                            capacity = entry.capacity,
                            note = entry.note
                        )
                    }

                    // 3) (Optional but recommended) also save to EmotionRepository timeline
                    runCatching {
                        val emoEnum = com.aninaw.checkin.Emotion.fromLabel(entry.emotion)
                        repo.addTodayEntry(emoEnum)
                    }

                    // 4) Growth + bonus (do ONCE, here)
                    val gm = TreeGrowthManager(this@CheckInAnchorActivity)
                    val before = gm.getGrowth()

                    gm.onQuickCheckInCompleted()
                    markTreeBonusForTomorrow()

                    val after = gm.getGrowth()

                    android.widget.Toast.makeText(
                        this@CheckInAnchorActivity,
                        "Quick check-in saved\nBefore: %.4f  After: %.4f".format(before, after),
                        android.widget.Toast.LENGTH_LONG
                    ).show()

                    // 5) (Optional) go home and refresh tree immediately
                    val home = Intent(this@CheckInAnchorActivity, MainActivity::class.java).apply {
                        putExtra(MainActivity.EXTRA_CHECKIN_COMPLETED, true)
                        addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                    }
                    startActivity(home)
                    finish()
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                }

            }.show(supportFragmentManager, "quick_checkin")
        }

        optionQuick.setOnClickListener(openQuickCheckIn)

        optionStart.setOnClickListener {
            goToCheckIn()
        }

        optionBreathe.setOnClickListener {
            startActivity(
                Intent(this, MomentActivity::class.java).apply {
                    putExtra("STATE_TYPE", "PROTECTED")
                    putExtra("STATE_STEADY", false)
                }
            )
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        }

        repo = EmotionRepository(this)

        // Keep these commented unless the IDs exist in THIS screen.
        // tvSeasonTitle = findViewById(R.id.tvSeasonTitle)
        // tvDayTitle = findViewById(R.id.tvDayTitle)
        // tvDayEmotions = findViewById(R.id.tvDayEmotions)
        // tvDayHint = findViewById(R.id.tvDayHint)
    }

    private fun setHeader(title: String, subtitle: String) {
        findViewById<TextView>(R.id.tvScreenTitle).text = title
        findViewById<TextView>(R.id.tvScreenSubtitle).text = subtitle
    }

    private fun goToCheckIn() {
        startActivity(
            Intent(this, UntangleActivity::class.java).apply {
                putExtra("CAPACITY_MODE", "STEADY")
            }
        )
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
    }

}

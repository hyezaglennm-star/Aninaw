package com.aninaw

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.aninaw.data.AninawDb
import com.aninaw.data.systems.BlockEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.view.View
import android.view.WindowManager
import com.aninaw.reminders.ReminderScheduler

class SystemDetailActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_SYSTEM_ID = "extra_system_id"
    }

    private val db by lazy { AninawDb.getDatabase(this) }
    private lateinit var adapter: BlocksAdapter
    private var systemId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_system_detail)

        window.setDimAmount(0.55f)

        window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.attributes = window.attributes.apply { dimAmount = 0.55f }

        findViewById<View>(R.id.scrimTouchBlocker).setOnClickListener {
            finish()
        }

        systemId = intent.getStringExtra(EXTRA_SYSTEM_ID).orEmpty()
        if (systemId.isBlank()) {
            finish()
            return
        }

        val toolbar = findViewById<com.google.android.material.appbar.MaterialToolbar>(R.id.toolbar)
        val tvSchedule = findViewById<android.widget.TextView>(R.id.tvSchedule)
        toolbar.setNavigationOnClickListener { finish() }

        adapter = BlocksAdapter(
            onToggleDone = { block, checked ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.blocksDao().upsertBlock(block.copy(isChecked = checked, updatedAt = System.currentTimeMillis()))
                }
            },
            onDelete = { block ->
                lifecycleScope.launch(Dispatchers.IO) { db.blocksDao().deleteBlock(block) }
            }
        )

        val rv = findViewById<RecyclerView>(R.id.rvBlocks)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        // Drag & drop reorder
        attachReorderHelper(rv)

        // Load header info
        lifecycleScope.launch {
            val sys = withContext(Dispatchers.IO) { db.systemsDao().getSystem(systemId) }
            toolbar.title = "${sys?.icon.orEmpty()} ${sys?.name ?: "System"}"
            tvSchedule.text = when (sys?.scheduleType) {
                "DAILY" -> "Daily"
                "SPECIFIC_DAYS" -> "Specific days"
                else -> sys?.scheduleType ?: "null"
            }
        }

        // Observe blocks
        lifecycleScope.launch {
            db.blocksDao().observeBlocks(systemId).collect { list ->
                adapter.submitList(list)
            }
        }

        // Add block
        findViewById<View>(R.id.fabCreateSystem).setOnClickListener {
            lifecycleScope.launch {
                val next = withContext(Dispatchers.IO) {
                    (db.blocksDao().getMaxSortIndex(systemId) ?: -1) + 1
                }

                AddBlockBottomSheet(
                    systemId = systemId,
                    nextSortIndex = next,
                    onAdd = { block ->
                        lifecycleScope.launch(Dispatchers.IO) {
                            db.blocksDao().upsertBlock(block)

                            // schedule reminder AFTER saving
                            if (block.type == "REMINDER" && block.remindAt != null) {
                                withContext(Dispatchers.Main) {
                                    ReminderScheduler.schedule(
                                        context = this@SystemDetailActivity,
                                        blockId = block.id,
                                        atMillis = block.remindAt,
                                        title = block.title,
                                        note = block.note
                                    )
                                }
                            }
                        }
                    }
                ).show(supportFragmentManager, "AddBlockBottomSheet")
            }
        }
    }

    private fun attachReorderHelper(rv: RecyclerView) {
        val callback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

                val current = adapter.currentList.toMutableList()
                val moved = current.removeAt(from)
                current.add(to, moved)

                // immediate UI update
                adapter.submitList(current)

                // persist new sortIndex (in background)
                persistReorder(current)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) { /* no-op */ }
            override fun isLongPressDragEnabled(): Boolean = true
        }

        ItemTouchHelper(callback).attachToRecyclerView(rv)
    }

    private fun persistReorder(list: List<BlockEntity>) {
        lifecycleScope.launch(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            list.forEachIndexed { index, block ->
                if (block.sortIndex != index) {
                    db.blocksDao().updateSortIndex(block.id, index, now)
                }
            }
        }
    }
    override fun finish() {
        super.finish()
        overridePendingTransition(R.animator.stay, R.animator.slide_down)
    }
}
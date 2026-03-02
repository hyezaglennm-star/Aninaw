package com.aninaw

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.aninaw.data.AninawDb
import com.aninaw.data.systems.SystemEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

class SystemsActivity : AppCompatActivity() {

    private lateinit var adapter: SystemsAdapter
    private val db by lazy { AninawDb.getDatabase(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_systems)

        findViewById<View>(R.id.btnBack).setOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }

        setHeader(
            title = "My Systems",
            subtitle = "Build routines that support your balance."
        )


        supportActionBar?.apply {
            setDisplayShowTitleEnabled(true)
            title = "My Systems"
        }

        adapter = SystemsAdapter(
            onOpen = { system ->
                startActivity(Intent(this, SystemDetailActivity::class.java).apply {
                    putExtra(SystemDetailActivity.EXTRA_SYSTEM_ID, system.id)
                })
                overridePendingTransition(R.animator.slide_up, R.animator.stay)
            },
            onDelete = { system ->
                lifecycleScope.launch(Dispatchers.IO) {
                    db.systemsDao().deleteSystem(system)
                }
            }
        )

        val rv = findViewById<androidx.recyclerview.widget.RecyclerView>(R.id.rvSystems)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = adapter

        val touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0 // no swipe
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false

                // Make a NEW list instance (ListAdapter needs this)
                val newList = adapter.currentList.toMutableList()
                Collections.swap(newList, from, to)
                adapter.submitList(newList)

                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                // no-op (swipe disabled)
            }

            override fun isLongPressDragEnabled(): Boolean = true
        })

        touchHelper.attachToRecyclerView(rv)

        val emptyContainer = findViewById<View>(R.id.emptyContainer)

        findViewById<View>(R.id.fabCreateSystem).setOnClickListener {
            showCreateSystemDialog()
        }
        lifecycleScope.launch {
            db.systemsDao().observeSystems().collect { list ->
                adapter.submitList(list)

                val isEmpty = list.isNullOrEmpty()

                emptyContainer.visibility = if (isEmpty) View.VISIBLE else View.GONE
                rv.visibility = if (isEmpty) View.GONE else View.VISIBLE
            }
        }
    }


    private fun setHeader(title: String, subtitle: String) {
        findViewById<TextView>(R.id.tvScreenTitle).text = title
        findViewById<TextView>(R.id.tvScreenSubtitle).text = subtitle
    }

    private fun showCreateSystemDialog() {
        CreateSystemDialog.show(
            activity = this,
            onCreate = { name, icon, scheduleType, daysMask ->
                lifecycleScope.launch(Dispatchers.IO) {
                    val now = System.currentTimeMillis()
                    val entity = SystemEntity(
                        id = UUID.randomUUID().toString(),
                        name = name.trim(),
                        icon = icon,
                        scheduleType = scheduleType,
                        daysMask = daysMask,
                        createdAt = now,
                        updatedAt = now
                    )
                    db.systemsDao().upsertSystem(entity)
                }
            }
        )
    }
}
package com.melodyflow.app.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.melodyflow.app.R
import com.melodyflow.app.databinding.ActivityCalendarSettingsBinding
import com.melodyflow.app.db.CalendarEvent
import com.melodyflow.app.db.CalendarEventDao
import com.melodyflow.app.db.MusicDatabase
import com.melodyflow.app.repository.WidgetRepository
import kotlinx.coroutines.launch

class MusicCalendarSettingsActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCalendarSettingsBinding
    private lateinit var repository: WidgetRepository
    private lateinit var eventDao: CalendarEventDao
    private var events = listOf<CalendarEvent>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityCalendarSettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val db = MusicDatabase.getInstance(this)
        eventDao = db.calendarEventDao()
        repository = WidgetRepository(this)

        setupRecyclerView()
        setupFab()

        lifecycleScope.launch {
            loadEvents()
        }
    }

    private fun setupRecyclerView() {
        binding.eventsRecyclerView.layoutManager = LinearLayoutManager(this)
    }

    private fun setupFab() {
        binding.fabAddEvent.setOnClickListener {
            showEventEditorDialog(null)
        }
    }

    private suspend fun loadEvents() {
        events = eventDao.getEventsInRange(0, Long.MAX_VALUE)
        // TODO: Update adapter
    }

    private fun showEventEditorDialog(event: CalendarEvent?) {
        val dialog = ScheduleEditorDialog(this, event) { savedEvent ->
            lifecycleScope.launch {
                if (event == null) {
                    eventDao.insertEvent(savedEvent)
                } else {
                    eventDao.updateEvent(savedEvent)
                }
                loadEvents()
                triggerWidgetUpdate()
            }
        }
        dialog.show()
    }

    private fun triggerWidgetUpdate() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, MusicCalendarWidget::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        appWidgetManager.notifyAppWidgetViewDataChanged(widgetIds, R.id.widget_date_grid)
    }
}
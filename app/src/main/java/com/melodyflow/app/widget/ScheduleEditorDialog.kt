package com.melodyflow.app.widget

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import com.melodyflow.app.R
import com.melodyflow.app.db.CalendarEvent
import java.util.Calendar

class ScheduleEditorDialog(
    private val context: Context,
    private val event: CalendarEvent?,
    private val onSave: (CalendarEvent) -> Unit
) {
    private var selectedDate: Long = event?.date ?: System.currentTimeMillis()
    private var selectedStartTime: Long? = event?.startTime
    private var selectedEndTime: Long? = event?.endTime
    private var selectedMood: String? = event?.moodTag
    private var eventType: String = event?.eventType ?: "custom"

    fun show() {
        val builder = AlertDialog.Builder(context)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_schedule_editor, null)
        builder.setView(view)
            .setTitle(if (event == null) "添加日程" else "编辑日程")
            .setPositiveButton("保存") { _, _ ->
                val title = view.findViewById<EditText>(R.id.et_event_title).text.toString()
                if (title.isBlank()) {
                    Toast.makeText(context, "请输入日程标题", Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val savedEvent = CalendarEvent(
                    id = event?.id ?: 0,
                    date = selectedDate,
                    title = title,
                    description = view.findViewById<EditText>(R.id.et_event_desc).text.toString(),
                    eventType = eventType,
                    startTime = selectedStartTime,
                    endTime = selectedEndTime,
                    moodTag = selectedMood,
                    hasAlarm = view.findViewById<CheckBox>(R.id.cb_has_alarm).isChecked,
                    alarmTime = if (view.findViewById<CheckBox>(R.id.cb_has_alarm).isChecked) selectedStartTime else null
                )
                onSave(savedEvent)
            }
            .setNegativeButton("取消", null)
            .show()

        view.findViewById<EditText>(R.id.et_event_title).setText(event?.title ?: "")
        view.findViewById<EditText>(R.id.et_event_desc).setText(event?.description ?: "")

        view.findViewById<Button>(R.id.btn_select_date).setOnClickListener {
            showDatePicker()
        }

        view.findViewById<Button>(R.id.btn_select_time).setOnClickListener {
            showTimePicker()
        }

        view.findViewById<Spinner>(R.id.spinner_event_type).apply {
            adapter = ArrayAdapter(context, android.R.layout.simple_spinner_dropdown_item,
                listOf("工作", "休闲", "运动", "学习", "睡眠", "自定义"))
            setOnItemSelectedListener(object : AdapterView.OnItemSelectedListener {
                override fun onItemSelected(parent: AdapterView<*>, v: View, position: Int, id: Long) {
                    eventType = when (position) {
                        0 -> "work"; 1 -> "leisure"; 2 -> "sport"
                        3 -> "study"; 4 -> "sleep"; else -> "custom"
                    }
                }
                override fun onNothingSelected(parent: AdapterView<*>) {}
            })
        }
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance()
        cal.timeInMillis = selectedDate
        DatePickerDialog(context, { _, year, month, day ->
            cal.set(year, month, day, 0, 0, 0)
            selectedDate = cal.timeInMillis
        }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showTimePicker() {
        TimePickerDialog(context, { _, hour, minute ->
            selectedStartTime = hour * 60L + minute
        }, 9, 0, true).show()
    }
}
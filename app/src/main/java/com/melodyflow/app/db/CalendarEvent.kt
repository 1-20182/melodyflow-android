package com.melodyflow.app.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "calendar_events")
data class CalendarEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: Long, // epoch millis, normalized to start of day
    val title: String,
    val description: String? = null,
    val eventType: String, // work/leisure/sport/study/sleep/custom
    val startTime: Long? = null, // 当日时间段起始(minutes from midnight)
    val endTime: Long? = null,   // 当日时间段结束(minutes from midnight)
    val moodTag: String? = null,
    val customMood: String? = null,
    val hasAlarm: Boolean = false,
    val alarmTime: Long? = null,   // 闹钟时间(minutes from midnight)
    val createdAt: Long = System.currentTimeMillis()
)
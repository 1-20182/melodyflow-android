package com.melodyflow.app.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_songs",
    foreignKeys = [ForeignKey(
        entity = CalendarEvent::class,
        parentColumns = ["id"],
        childColumns = ["eventId"],
        onDelete = ForeignKey.CASCADE
    )]
)
data class EventSong(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val eventId: Long,
    val songId: String,
    val songName: String,
    val artist: String,
    val coverUrl: String? = null,
    val playType: String = "song", // song/playlist/genre
    val playData: String? = null,
    val orderIndex: Int = 0
)
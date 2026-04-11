package com.example.myapplication.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

object DateFormatter {
    fun formatDateTime(epochMillis: Long): String {
        val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        val weekdays = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") // 1-based Kotlinx Enum (iso)
        val months = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
        
        val dayName = weekdays.getOrElse(dt.dayOfWeek.value) { "" }
        val day = dt.dayOfMonth.toString().padStart(2, '0')
        val monthName = months.getOrElse(dt.monthNumber) { "" }
        val hour = dt.hour.toString().padStart(2, '0')
        val min = dt.minute.toString().padStart(2, '0')
        
        // EEE, dd MMM  HH:mm
        return "$dayName, $day $monthName  $hour:$min"
    }
}


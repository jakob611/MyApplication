package com.example.myapplication.domain
import kotlinx.datetime.*
object DateFormatter {
    private val weekdays = listOf("", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun") // 1-based ISO
    private val months = listOf("", "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec")
    private val fullMonths = listOf("", "January", "February", "March", "April", "May", "June", "July", "August", "September", "October", "November", "December")
    fun formatDateTime(epochMillis: Long): String {
        return formatEpoch(epochMillis, "EEE, dd MMM  HH:mm")
    }
    fun formatEpoch(epochMillis: Long, pattern: String): String {
        val dt = Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(TimeZone.currentSystemDefault())
        return formatLocalDateTime(dt, pattern)
    }
    fun format(date: LocalDate, pattern: String): String {
        return formatLocalDateTime(LocalDateTime(date.year, date.monthNumber, date.dayOfMonth, 0, 0), pattern)
    }
    private fun formatLocalDateTime(dt: LocalDateTime, pattern: String): String {
        val dayName = weekdays.getOrElse(dt.dayOfWeek.value) { "" }
        val day = dt.dayOfMonth.toString().padStart(2, '0')
        val monthName = months.getOrElse(dt.monthNumber) { "" }
        val fullMonthName = fullMonths.getOrElse(dt.monthNumber) { "" }
        val hour = dt.hour.toString().padStart(2, '0')
        val min = dt.minute.toString().padStart(2, '0')
        return when (pattern) {
            "EEE" -> dayName
            "MMM" -> monthName
            "dd MMM" ->  "$day $monthName"
            "EEE, dd MMM" -> "$dayName, $day $monthName"
            "EEE, dd MMM yyyy" -> "$dayName, $day $monthName $dt.year"
            "EEE, dd MMM yyyy HH:mm" -> "$dayName, $day $monthName $dt.year $hour:$min"
            "EEE, dd MMM yyyy  HH:mm" -> "$dayName, $day $monthName $dt.year  $hour:$min"
            "EEE, dd MMM  HH:mm" -> "$dayName, $day $monthName  $hour:$min"
            "MMMM d, yyyy" -> "$fullMonthName $dt.dayOfMonth, $dt.year"
            "yyyy-MM-dd" -> "$dt.year-$dt.monthNumber.toString().padStart(2, '0')-$day"
            "HH:mm" -> "$hour:$min"
            else -> dt.toString()
        }
    }
}

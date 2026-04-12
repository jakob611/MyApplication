package com.example.myapplication.domain
import kotlinx.datetime.*
fun LocalDate.Companion.now(): LocalDate {
    return Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
}
fun LocalDate.minusDays(days: Int): LocalDate {
    return this.minus(DatePeriod(days = days))
}
fun LocalDate.plusDays(days: Int): LocalDate {
    return this.plus(DatePeriod(days = days))
}
fun LocalDate.minusMonths(months: Int): LocalDate {
    return this.minus(DatePeriod(months = months))
}
fun LocalDate.plusMonths(months: Int): LocalDate {
    return this.plus(DatePeriod(months = months))
}
fun LocalDate.minusWeeks(weeks: Int): LocalDate {
    // kotlinx datetime DatePeriod doesn't have weeks constructor directly
    return this.minus(DatePeriod(days = weeks * 7))
}
fun LocalDate.plusWeeks(weeks: Int): LocalDate {
    return this.plus(DatePeriod(days = weeks * 7))
}
fun LocalDate.isAfter(other: LocalDate): Boolean {
    return this > other
}
fun LocalDate.isBefore(other: LocalDate): Boolean {
    return this < other
}
fun daysBetween(start: LocalDate, end: LocalDate): Int {
    return start.daysUntil(end)
}
fun LocalDate.minusYears(years: Int): LocalDate {
    return this.minus(DatePeriod(years = years))
}
fun LocalDate.plusYears(years: Int): LocalDate {
    return this.plus(DatePeriod(years = years))
}
fun LocalDate.withDayOfMonth(day: Int): LocalDate {
    return LocalDate(this.year, this.month, day)
}

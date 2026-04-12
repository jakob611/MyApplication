import kotlinx.datetime.Clock
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlinx.datetime.LocalDate
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.plus
import kotlinx.datetime.minus
import kotlinx.datetime.daysUntil
fun main() {
    val today = Clock.System.todayIn(TimeZone.currentSystemDefault())
    val past = today.minus(DatePeriod(days = 6))
    val diff = past.daysUntil(today)
    println(past)
    println(diff)
}

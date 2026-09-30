package com.curiositytechmatters.photomanagerlite

import com.curiositytechmatters.photomanagerlite.model.AppSettings
import com.curiositytechmatters.photomanagerlite.model.NotificationFrequency
import com.curiositytechmatters.photomanagerlite.model.PreferredNotificationStartTime
import com.curiositytechmatters.photomanagerlite.model.WeeklyReviewDay
import java.util.Calendar
import kotlin.test.Test
import kotlin.test.assertEquals

class PhotoReviewNotificationScheduleTest {
    @Test
    fun twelveHourScheduleUsesPreferredStartTimeAndThenTwelveHourInterval() {
        val settings = AppSettings(
            notificationFrequency = NotificationFrequency.TwelveHours,
            preferredNotificationStartTime = PreferredNotificationStartTime(hour = 9, minute = 30),
        )

        val nextTrigger = calendarFields(
            nextTriggerAtMillis(
                nowMillis = millis(
                    year = 2026,
                    month = Calendar.JULY,
                    day = 31,
                    hour = 10,
                    minute = 0,
                ),
                settings = settings,
            )
        )

        assertEquals(2026, nextTrigger.year)
        assertEquals(Calendar.JULY, nextTrigger.month)
        assertEquals(31, nextTrigger.day)
        assertEquals(21, nextTrigger.hour)
        assertEquals(30, nextTrigger.minute)
    }

    @Test
    fun dailyScheduleUsesPreferredStartTimeOnTheNextDayWhenTodayHasPassed() {
        val settings = AppSettings(
            notificationFrequency = NotificationFrequency.Daily,
            preferredNotificationStartTime = PreferredNotificationStartTime(hour = 8, minute = 15),
        )

        val nextTrigger = calendarFields(
            nextTriggerAtMillis(
                nowMillis = millis(
                    year = 2026,
                    month = Calendar.JULY,
                    day = 31,
                    hour = 9,
                    minute = 0,
                ),
                settings = settings,
            )
        )

        assertEquals(Calendar.AUGUST, nextTrigger.month)
        assertEquals(1, nextTrigger.day)
        assertEquals(8, nextTrigger.hour)
        assertEquals(15, nextTrigger.minute)
    }

    @Test
    fun weeklyScheduleUsesThePreferredDayAndTime() {
        val settings = AppSettings(
            notificationFrequency = NotificationFrequency.Weekly,
            preferredNotificationStartTime = PreferredNotificationStartTime(hour = 18, minute = 45),
            weeklyReviewDay = WeeklyReviewDay.Wednesday,
        )

        val nextTrigger = calendarFields(
            nextTriggerAtMillis(
                nowMillis = millis(
                    year = 2026,
                    month = Calendar.JULY,
                    day = 31,
                    hour = 9,
                    minute = 0,
                ),
                settings = settings,
            )
        )

        assertEquals(Calendar.WEDNESDAY, nextTrigger.dayOfWeek)
        assertEquals(18, nextTrigger.hour)
        assertEquals(45, nextTrigger.minute)
    }

    private fun millis(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
    ): Long {
        return Calendar.getInstance().apply {
            clear()
            set(year, month, day, hour, minute)
        }.timeInMillis
    }

    private fun calendarFields(timeMillis: Long): CalendarFields {
        val calendar = Calendar.getInstance().apply {
            timeInMillis = timeMillis
        }
        return CalendarFields(
            year = calendar.get(Calendar.YEAR),
            month = calendar.get(Calendar.MONTH),
            day = calendar.get(Calendar.DAY_OF_MONTH),
            dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK),
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE),
        )
    }
}

private data class CalendarFields(
    val year: Int,
    val month: Int,
    val day: Int,
    val dayOfWeek: Int,
    val hour: Int,
    val minute: Int,
)

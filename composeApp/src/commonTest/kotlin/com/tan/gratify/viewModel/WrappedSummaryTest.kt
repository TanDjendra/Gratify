package com.tan.gratify.viewModel

import com.tan.domain.data.entities.analytics.PlaybackEventEntity
import kotlinx.datetime.LocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class WrappedSummaryTest {
    private fun event(date: String, id: String, seconds: Long) = PlaybackEventEntity(
        timestamp = LocalDateTime.parse(date), videoId = id, listenedSecond = seconds,
    )

    @Test
    fun filtersYearAndZeroListeningAndUsesActualTime() {
        val result = summarizeWrapped(listOf(
            event("2025-12-31T23:59:59", "old", 900),
            event("2026-01-01T00:00:00", "a", 30),
            event("2026-01-01T12:00:00", "a", 45),
            event("2026-01-02T12:00:00", "b", 120),
            event("2026-01-03T12:00:00", "skipped", 0),
            event("2027-01-01T00:00:00", "future", 900),
        ), 2026)
        assertEquals(3L, result.minutes)
        assertEquals(195L, result.totalSeconds)
        assertEquals(3, result.plays)
        assertEquals(2, result.uniqueTracks)
        assertEquals(2, result.activeDays)
        assertEquals("2026-01-02", result.biggestDay)
        assertEquals(2, result.longestStreak)
    }

    @Test
    fun emptyYearHasNoBiggestDay() {
        assertEquals(WrappedSummary(), summarizeWrapped(emptyList(), 2026))
    }

    @Test fun countsThirtySecondPlaysButKeepsShortRecordedTime() {
        val result = summarizeWrapped(listOf(event("2026-01-01T12:00", "a", 29), event("2026-01-01T12:00", "b", 30)), 2026)
        assertEquals(1, result.plays); assertEquals(59L, result.totalSeconds); assertEquals(0L, result.minutes); assertEquals(2, result.uniqueTracks)
    }

    @Test fun rejectsFutureCorruptAndDuplicateRecordsWithoutMergingDifferentSessions() {
        val rows = listOf(event("2026-01-01T12:00", "a", 60).copy(eventId = 4),
            event("2026-01-01T12:00", "a", 60).copy(eventId = 4), event("2026-01-01T12:00", "a", 60).copy(eventId = 5),
            event("2026-11-01T12:00", "future", 900), event("2026-01-01T12:00", "", 90),
            event("2026-01-01T12:00", "bad", -30), event("2026-01-01T12:00", "bad", 86_401))
        val result = summarizeWrapped(rows, 2026, LocalDateTime.parse("2026-10-08T12:00"))
        assertEquals(120L, result.totalSeconds); assertEquals(2, result.plays); assertEquals(1, result.uniqueTracks)
    }

    @Test fun biggestDayTieAndClockBandAreDeterministic() {
        val result = summarizeWrapped(listOf(event("2026-01-02T18:00", "b", 60), event("2026-01-01T06:00", "a", 60)), 2026)
        assertEquals("2026-01-01", result.biggestDay); assertEquals(1, result.clockBand)
    }

    @Test fun streakCrossesMonthBoundaryAndResetsAfterGap() {
        val dates = listOf("2026-01-30", "2026-01-31", "2026-02-01", "2026-02-03", "2026-02-04")
        val result = summarizeWrapped(dates.map { event("${it}T12:00", "a", 60) }, 2026)
        assertEquals(3, result.longestStreak); assertEquals(120L, result.monthlySeconds[0]); assertEquals(180L, result.monthlySeconds[1])
        assertEquals(12, result.monthlySeconds.size); assertEquals("2026-01-30", result.firstDay); assertEquals("2026-02-04", result.lastDay)
    }

    @Test fun countsBoundaryAtStartOfYearAndAtAsOfTime() {
        val time = LocalDateTime.parse("2026-01-01T00:00")
        assertEquals(60L, summarizeWrapped(listOf(event("2026-01-01T00:00", "a", 60), event("2025-12-31T23:59", "b", 60)), 2026, time).totalSeconds)
    }
}

package com.juice.data

import com.juice.core.routine.Cadence
import java.time.DayOfWeek
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

class CadenceCodecTest {
    @Test fun intervalRoundTrips() {
        val cadence = Cadence(setOf(DayOfWeek.FRIDAY), LocalTime.of(9, 0), 2)
        assertEquals("2W:FRI;09:00", cadence.encode())
        assertEquals(cadence, "2W:FRI;09:00".decodeCadence())
    }

    @Test fun weeklyEncodesWithoutPrefix() {
        assertEquals("TUE,THU", Cadence(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)).encode())
    }

    @Test fun oldFormatDecodesAsEveryWeek() {
        assertEquals(Cadence(setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY)), "TUE,THU".decodeCadence())
        assertEquals(Cadence(setOf(DayOfWeek.MONDAY), LocalTime.of(8, 30)), "MON;08:30".decodeCadence())
    }
}

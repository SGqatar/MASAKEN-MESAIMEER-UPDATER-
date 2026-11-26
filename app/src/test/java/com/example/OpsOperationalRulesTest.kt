package com.example

import com.example.data.db.KeyRegisterEntity
import com.example.data.db.KeyStatus
import com.example.data.db.SeedDataProvider
import com.example.data.sync.TestRecordFilter
import com.example.data.sync.XlsxEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream

class OpsOperationalRulesTest {

    @Test
    fun sportsBaselineMatchesExactNov1To25Totals() {
        val sports = SeedDataProvider.buildSportsBookingsSeed()
        val football = sports.filter { it.sport == "FOOTBALL" }
        val padel = sports.filter { it.sport == "PADEL" }

        assertEquals(135, sports.size)
        assertEquals(79, football.size)
        assertEquals(56, padel.size)
        assertEquals(106.5, football.sumOf { it.durationPlayed }, 0.001)
        assertEquals(59.5, padel.sumOf { it.durationPlayed }, 0.001)
        assertEquals(166.0, sports.sumOf { it.durationPlayed }, 0.001)
        assertTrue(sports.all { it.discount == "FREE" })
        assertFalse(sports.any { it.date.startsWith("26/11") })
    }

    @Test
    fun keyRegisterStrictRedAlertsAndTestExclusionWork() {
        val keys = SeedDataProvider.buildKeyRegisterSeed()
        val alertSis = keys.filter { it.isUnreturnedAlert }.map { it.si }.toSet()

        assertEquals(setOf(1634, 1638, 1639, 1643, 1644, 1645), alertSis)
        assertTrue(keys.any { it.si == 1634 && it.flatUnitLocation == "M-A04-16" && it.isUnreturnedAlert })

        val testCharle = KeyRegisterEntity(
            si = 9991, dateOut = "20/11/2026", timeOut = "10:00", flatUnitLocation = "M-A01-01",
            keyCardType = "Key", purpose = "Test", issuedTo = "charle to mansu",
            qidOrNote = "", dateIn = "", timeIn = "", returnedBy = "", remarks = "", status = KeyStatus.ISSUED
        )
        val testMushtaq = KeyRegisterEntity(
            si = 9992, dateOut = "20/11/2026", timeOut = "10:00", flatUnitLocation = "B10-10",
            keyCardType = "Key", purpose = "mushtaq for B10-10", issuedTo = "mushtaq",
            qidOrNote = "", dateIn = "", timeIn = "", returnedBy = "", remarks = "", status = KeyStatus.ISSUED
        )
        assertTrue(TestRecordFilter.isDeletedTestKeyRecord(testCharle))
        assertTrue(TestRecordFilter.isDeletedTestKeyRecord(testMushtaq))
    }

    @Test
    fun visitorLateNovemberFiguresAreSeededAccurately() {
        val visitors = SeedDataProvider.buildVisitorDaysSeed().associateBy { it.dateIso }
        assertEquals(1, visitors["2026-11-24"]?.visitorCount)
        assertEquals(0, visitors["2026-11-24"]?.viewingCount)

        assertEquals(3, visitors["2026-11-25"]?.visitorCount)
        assertEquals(1, visitors["2026-11-25"]?.viewingCount)
        assertTrue(visitors["2026-11-25"]?.flatDetails?.contains("M-D18-41") == true)

        assertEquals(8, visitors["2026-11-26"]?.visitorCount)
        assertEquals(2, visitors["2026-11-26"]?.viewingCount)
        assertTrue(visitors["2026-11-26"]?.flatDetails?.contains("M-C13-16") == true)
        assertTrue(visitors["2026-11-26"]?.flatDetails?.contains("M-C15-21") == true)
    }

    @Test
    fun xlsxExportCreatesValidOoxmlPackage() {
        val keys = SeedDataProvider.buildKeyRegisterSeed()
        val visitors = SeedDataProvider.buildVisitorDaysSeed()
        val maintenance = SeedDataProvider.buildMaintenanceSeed()
        val sports = SeedDataProvider.buildSportsBookingsSeed()

        val baos = ByteArrayOutputStream()
        XlsxEngine.writeAllWorkbooksXlsx(baos, keys, visitors, maintenance, sports)
        val bytes = baos.toByteArray()
        assertTrue(bytes.size > 4000)
        // ZIP magic header PK
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])
    }
}

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

        val bySn = sports.associateBy { it.sn }
        assertEquals("MR. AYAAN", bySn[1]?.name)
        assertEquals("MM-F28-44", bySn[1]?.unitDetails)
        assertEquals("", bySn[89]?.referenceNo)
        assertEquals("CALL CENTER APPROVAL", bySn[133]?.remarks)
        assertEquals("MR. MUNICH", bySn[139]?.name)
        assertEquals("MM-A06-46", bySn[139]?.unitDetails)
        assertEquals(1.5, XlsxEngine.parseDurationPlayed("1 HOUR & 30 MINUTES"), 0.001)
        assertEquals(3.5, XlsxEngine.parseDurationPlayed("3 HOURS & 30 MINUTES"), 0.001)
    }

    @Test
    fun keyRegisterStrictRedAlertsAndTestExclusionWork() {
        val keys = SeedDataProvider.buildKeyRegisterSeed()
        val alertSis = keys.filter { it.isUnreturnedAlert }.map { it.si }.toSet()

        assertEquals(setOf(1283, 1634, 1638, 1639, 1643, 1644, 1645), alertSis)
        assertTrue(keys.any { it.si == 1634 && it.flatUnitLocation == "M-A04-16" && it.isUnreturnedAlert })
        assertTrue(keys.any { it.si == 1283 && it.flatUnitLocation == "M-B09-12" && it.isUnreturnedAlert })

        // Settled Move-in / Transfer rows with confirmed T-codes must not be highlighted red
        val settledMoveIn = keys.first { it.si == 90 }
        assertEquals(KeyStatus.ISSUED_TO_TENANT, settledMoveIn.status)
        assertFalse(settledMoveIn.isUnreturnedAlert)

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
    fun visitorsAndMaintenanceBacklogMatchesFull2026Totals() {
        val visitors = SeedDataProvider.buildVisitorDaysSeed()
        assertEquals(365, visitors.size)

        val byMonth = visitors.groupBy { it.monthKey }
        val expectedMonthly = mapOf(
            "2026-01" to (149 to 7),
            "2026-02" to (114 to 10),
            "2026-03" to (75 to 5),
            "2026-04" to (103 to 3),
            "2026-05" to (80 to 7),
            "2026-06" to (84 to 19),
            "2026-07" to (56 to 8),
            "2026-08" to (85 to 15),
            "2026-09" to (89 to 29),
            "2026-10" to (113 to 16),
            "2026-11" to (74 to 18),
            "2026-12" to (0 to 0)
        )
        expectedMonthly.forEach { (month, pair) ->
            val rows = byMonth[month].orEmpty()
            assertEquals("Month $month visitors", pair.first, rows.sumOf { it.visitorCount })
            assertEquals("Month $month viewings", pair.second, rows.sumOf { it.viewingCount })
        }

        val byDate = visitors.associateBy { it.dateIso }
        assertEquals(1, byDate["2026-11-24"]?.visitorCount)
        assertEquals(0, byDate["2026-11-24"]?.viewingCount)
        assertEquals(3, byDate["2026-11-25"]?.visitorCount)
        assertEquals(1, byDate["2026-11-25"]?.viewingCount)
        assertEquals(8, byDate["2026-11-26"]?.visitorCount)
        assertEquals(2, byDate["2026-11-26"]?.viewingCount)
        assertTrue(byDate["2026-11-26"]?.flatDetails?.contains("M-C13-16") == true)
        assertTrue(byDate["2026-11-26"]?.flatDetails?.contains("M-C15-21") == true)

        val maintenance = SeedDataProvider.buildMaintenanceSeed()
        assertEquals(161, maintenance.size)
        assertEquals(54, maintenance.count { it.sn <= 1000 })
        assertEquals(107, maintenance.count { it.sn > 1000 })
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

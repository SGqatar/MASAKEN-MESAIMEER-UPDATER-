package com.example

import com.example.data.db.KeyRegisterEntity
import com.example.data.db.KeyStatus
import com.example.data.db.SeedDataProvider
import com.example.data.sync.InspectionExportItem
import com.example.data.sync.TestRecordFilter
import com.example.data.sync.XlsxEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class OpsOperationalRulesTest {

    @Test
    fun sportsBaselineMatchesNov1To26EntriesAndCounts() {
        val sports = SeedDataProvider.buildSportsBookingsSeed()
        val football = sports.filter { it.sport == "FOOTBALL" }
        val padel = sports.filter { it.sport == "PADEL" }

        assertEquals(140, sports.size)
        assertEquals(82, football.size)
        assertEquals(58, padel.size)
        assertEquals(112.5, football.sumOf { it.durationPlayed }, 0.001)
        assertEquals(62.0, padel.sumOf { it.durationPlayed }, 0.001)
        assertEquals(174.5, sports.sumOf { it.durationPlayed }, 0.001)
        assertTrue(sports.all { it.discount == "FREE" })
        assertTrue(sports.any { it.date == "26/11/2026" })

        val bySn = sports.associateBy { it.sn }
        assertEquals("MR. AYAAN", bySn[1]?.name)
        assertEquals("MM-F28-44", bySn[1]?.unitDetails)
        assertEquals("", bySn[89]?.referenceNo)
        assertEquals("CALL CENTER APPROVAL", bySn[133]?.remarks)
        assertEquals("MR. MUNICH", bySn[139]?.name)
        assertEquals("MM-A06-46", bySn[139]?.unitDetails)

        // Nov 26, 2026 entries SN 140-144
        assertEquals("MR. SANTOSH", bySn[140]?.name)
        assertEquals("MM-A01-22", bySn[140]?.unitDetails)
        assertEquals("MR. HASEEB", bySn[141]?.name)
        assertEquals("PADEL", bySn[141]?.sport)
        assertEquals("MR. YOUSSEF MANI", bySn[142]?.name)
        assertEquals(3.0, bySn[142]?.durationPlayed ?: 0.0, 0.001)
        assertEquals("MR. SHADY", bySn[143]?.name)
        assertEquals("MM-D17-33", bySn[143]?.unitDetails)
        assertEquals("MR. WARID", bySn[144]?.name)
        assertEquals("MM-F27-37", bySn[144]?.unitDetails)

        assertEquals(1.5, XlsxEngine.parseDurationPlayed("1 HOUR & 30 MINUTES"), 0.001)
        assertEquals(3.5, XlsxEngine.parseDurationPlayed("3 HOURS & 30 MINUTES"), 0.001)
    }

    @Test
    fun keyRegisterStrictRedAlertsMoveInExclusionAndIssuedByExportRuleWork() {
        val keys = SeedDataProvider.buildKeyRegisterSeed()
        val alertSis = keys.filter { it.isUnreturnedAlert }.map { it.si }.toSet()

        // Strictly exclude Move-In keys from red key-out alerts: only SI 1283 (Jul backlog), 1634, 1643
        assertEquals(setOf(1283, 1634, 1643), alertSis)
        val currentMonthAlertSis = keys.filter { it.isCurrentMonthUnreturnedAlert }.map { it.si }.toSet()
        assertEquals(setOf(1634, 1643), currentMonthAlertSis)
        assertTrue(keys.any { it.si == 1634 && it.flatUnitLocation == "M-A04-16" && it.isCurrentMonthUnreturnedAlert })
        assertTrue(keys.any { it.si == 1643 && it.flatUnitLocation == "M-F30-18" && it.isCurrentMonthUnreturnedAlert })
        assertTrue(keys.any { it.si == 1283 && it.flatUnitLocation == "M-B09-12" && it.isUnreturnedAlert && !it.isCurrentMonthUnreturnedAlert })

        // Nov 26 Move-In keys (SI 1638, 1639, 1644, 1645) are amber Move-In Pending T-Code, NOT red alerts
        val moveInPendingSis = setOf(1638, 1639, 1644, 1645)
        assertEquals(4, keys.count { it.isMoveInPendingTCode })
        moveInPendingSis.forEach { si ->
            val row = keys.first { it.si == si }
            assertEquals(KeyStatus.MOVE_IN_PENDING_TCODE, row.status)
            assertTrue("SI $si should be Move-In Pending T-Code", row.isMoveInPendingTCode)
            assertFalse("SI $si must NOT be counted as red alert", row.isUnreturnedAlert)
        }

        // Physical issuers retained in-app (SI 1634 -> Mr. Israel Agbofa, SI 1643 -> Mr. Aqib Rehman)
        assertEquals("Mr. Israel Agbofa", keys.first { it.si == 1634 }.issuedBy)
        assertEquals("Mr. Aqib Rehman", keys.first { it.si == 1643 }.issuedBy)

        // Settled Move-in / Transfer rows with confirmed T-codes must not be highlighted red or amber
        val settledMoveIn = keys.first { it.si == 90 }
        assertEquals(KeyStatus.ISSUED_TO_TENANT, settledMoveIn.status)
        assertFalse(settledMoveIn.isUnreturnedAlert)
        assertFalse(settledMoveIn.isMoveInPendingTCode)

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
    fun xlsxExportCreatesValidOoxmlPackageAndInspectionEmbedsPhotos() {
        val keys = SeedDataProvider.buildKeyRegisterSeed()
        val visitors = SeedDataProvider.buildVisitorDaysSeed()
        val maintenance = SeedDataProvider.buildMaintenanceSeed()
        val sports = SeedDataProvider.buildSportsBookingsSeed()

        val baos = ByteArrayOutputStream()
        XlsxEngine.writeAllWorkbooksXlsx(baos, keys, visitors, maintenance, sports)
        val bytes = baos.toByteArray()
        assertTrue(bytes.size > 4000)
        assertEquals('P'.code.toByte(), bytes[0])
        assertEquals('K'.code.toByte(), bytes[1])

        // Verify Key Register standalone XLSX normalizes Issued By to Mr. Rodel Cabrales
        val keysBaos = ByteArrayOutputStream()
        XlsxEngine.writeKeyRegisterXlsx(keysBaos, keys)
        val parsed = XlsxEngine.readXlsx(ByteArrayInputStream(keysBaos.toByteArray()))
        val row1634 = parsed.keys.first { it.si == 1634 }
        assertEquals(XlsxEngine.EXPORT_ISSUED_BY_DEFAULT, row1634.issuedBy)

        // Verify Building Inspection XLSX embeds JPEGs in OOXML drawing relationships
        val fakeJpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0xE0.toByte(), 0x00, 0x10)
        val inspBaos = ByteArrayOutputStream()
        XlsxEngine.writeInspectionReportXlsx(
            output = inspBaos,
            buildingName = "Building A04",
            inspectionDate = "27/11/2026",
            inspectorName = "Emmanuel Ssali",
            items = listOf(
                InspectionExportItem(
                    id = "1",
                    area = "Corridor",
                    finding = "Emergency light tested OK",
                    severity = "Pass",
                    actionRequired = "None",
                    jpegBytes = fakeJpeg
                )
            )
        )
        val zipEntries = mutableSetOf<String>()
        var drawingXml = ""
        ZipInputStream(ByteArrayInputStream(inspBaos.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                zipEntries.add(entry.name)
                if (entry.name == "xl/drawings/drawing1.xml") {
                    drawingXml = zis.readBytes().toString(Charsets.UTF_8)
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        assertTrue(zipEntries.contains("xl/media/image1.jpg"))
        assertTrue(zipEntries.contains("xl/drawings/drawing1.xml"))
        assertTrue(zipEntries.contains("xl/drawings/_rels/drawing1.xml.rels"))
        assertTrue(zipEntries.contains("xl/worksheets/_rels/sheet1.xml.rels"))
        assertTrue(drawingXml.contains("twoCellAnchor"))

        // Verify combined Visitors + Maintenance 2-sheet XLSX
        val vmBaos = ByteArrayOutputStream()
        XlsxEngine.writeVisitorsAndMaintenanceXlsx(vmBaos, visitors, maintenance)
        val vmZipEntries = mutableSetOf<String>()
        ZipInputStream(ByteArrayInputStream(vmBaos.toByteArray())).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                vmZipEntries.add(entry.name)
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        assertTrue(vmZipEntries.contains("xl/worksheets/sheet1.xml"))
        assertTrue(vmZipEntries.contains("xl/worksheets/sheet2.xml"))
    }

    @Test
    fun weeklyAndMonthlyRemindersTargetSaturdayAndFirstDayOfMonth() {
        val keys = SeedDataProvider.buildKeyRegisterSeed()
        val reminders = SeedDataProvider.buildInitialRemindersSeed(keys)
        val tz = java.util.TimeZone.getTimeZone("Asia/Qatar")

        val weekly = reminders.first { it.id == "weekly_report_fri" }
        val weeklyCal = java.util.Calendar.getInstance(tz).apply { timeInMillis = weekly.scheduledAtMillis }
        assertEquals(java.util.Calendar.SATURDAY, weeklyCal.get(java.util.Calendar.DAY_OF_WEEK))

        val monthly = reminders.first { it.id == "monthly_report_eom" }
        val monthlyCal = java.util.Calendar.getInstance(tz).apply { timeInMillis = monthly.scheduledAtMillis }
        assertEquals(1, monthlyCal.get(java.util.Calendar.DAY_OF_MONTH))
    }
}

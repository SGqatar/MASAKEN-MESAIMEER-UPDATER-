package com.example.data.db

import java.util.Locale

/**
 * Exact backlog extracted from:
 * "PM VISITORS LOG JANUARY TO DECEMBER 2026_UPDATED UPTO 26TH.pdf"
 * - PM OFFICE VISITORS 2026: 365 daily rows (Jan-Dec 2026)
 * - MASAKEN MESAIMEER MAINTENANCE REQUESTS LOG:
 *     - 2025: SN 1..54 (stored with sn = 1..54)
 *     - 2026: 107 data rows across PDF SN 1..112 (stored with sn = 1000 + pdfSn so ORDER BY sn DESC puts newest 2026 first)
 */
object VisitorsMaintenanceBacklogSeed {

    private data class DaySpec(
        val day: Int,
        val visitors: Int,
        val viewings: Int,
        val flats: String = "-"
    )

    private val monthAbbr = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun",
        "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"
    )

    private fun buildMonthDays(month: Int, daysInMonth: Int, nonZero: List<DaySpec>): List<VisitorDayEntity> {
        val byDay = nonZero.associateBy { it.day }
        val abbr = monthAbbr[month - 1]
        val monthKey = String.format(Locale.US, "2026-%02d", month)
        return (1..daysInMonth).map { day ->
            val spec = byDay[day]
            VisitorDayEntity(
                dateIso = String.format(Locale.US, "2026-%02d-%02d", month, day),
                displayDate = String.format(Locale.US, "%02d-%s-2026", day, abbr),
                monthKey = monthKey,
                visitorCount = spec?.visitors ?: 0,
                viewingCount = spec?.viewings ?: 0,
                flatDetails = spec?.flats?.ifBlank { "-" } ?: "-",
                notes = ""
            )
        }
    }

    fun buildVisitorDays2026Backlog(): List<VisitorDayEntity> {
        val jan = buildMonthDays(
            month = 1,
            daysInMonth = 31,
            nonZero = listOf(
                DaySpec(1, 9, 0),
                DaySpec(3, 5, 0),
                DaySpec(4, 3, 0),
                DaySpec(5, 6, 0),
                DaySpec(6, 6, 0),
                DaySpec(7, 6, 0),
                DaySpec(8, 5, 0),
                DaySpec(10, 4, 0),
                DaySpec(11, 4, 0),
                DaySpec(12, 7, 2, "M-E23-26, M-D19-38"),
                DaySpec(13, 8, 0),
                DaySpec(14, 4, 1, "M-E23-26"),
                DaySpec(15, 3, 0),
                DaySpec(17, 1, 1, "M-E24-33"),
                DaySpec(18, 4, 0),
                DaySpec(19, 6, 2, "M-E23-26, M-D21-17"),
                DaySpec(20, 3, 0),
                DaySpec(21, 10, 0),
                DaySpec(22, 4, 0),
                DaySpec(24, 6, 0),
                DaySpec(25, 8, 0),
                DaySpec(26, 7, 0),
                DaySpec(27, 6, 1, "M-E24-33"),
                DaySpec(28, 7, 0),
                DaySpec(29, 9, 0),
                DaySpec(31, 8, 0)
            )
        )

        val feb = buildMonthDays(
            month = 2,
            daysInMonth = 28,
            nonZero = listOf(
                DaySpec(1, 2, 0),
                DaySpec(2, 7, 0),
                DaySpec(3, 8, 3, "M-E23-26, M-A04-27, M-F29-44"),
                DaySpec(4, 7, 1, "M-A01-12"),
                DaySpec(5, 5, 0),
                DaySpec(7, 5, 0),
                DaySpec(8, 5, 1, "M-C14-42"),
                DaySpec(9, 12, 0),
                DaySpec(11, 5, 2, "M-E23-26, M-E23-26"),
                DaySpec(12, 3, 1, "M-C14-42"),
                DaySpec(14, 5, 0),
                DaySpec(15, 5, 0),
                DaySpec(16, 5, 0),
                DaySpec(17, 2, 0),
                DaySpec(18, 6, 0),
                DaySpec(19, 2, 0),
                DaySpec(21, 4, 1, "M-A04-27"),
                DaySpec(22, 2, 1, "M-A01-12"),
                DaySpec(23, 8, 0),
                DaySpec(24, 4, 0),
                DaySpec(25, 6, 0),
                DaySpec(26, 3, 0),
                DaySpec(28, 3, 0)
            )
        )

        val mar = buildMonthDays(
            month = 3,
            daysInMonth = 31,
            nonZero = listOf(
                DaySpec(1, 3, 0),
                DaySpec(2, 4, 0),
                DaySpec(3, 4, 0),
                DaySpec(4, 3, 0),
                DaySpec(5, 2, 0),
                DaySpec(7, 2, 0),
                DaySpec(8, 5, 0),
                DaySpec(10, 3, 1, "M-E24-15"),
                DaySpec(11, 1, 0),
                DaySpec(12, 2, 1, "M-D19-38"),
                DaySpec(13, 4, 0),
                DaySpec(14, 1, 0),
                DaySpec(15, 3, 1, "M-D19-38"),
                DaySpec(16, 1, 0),
                DaySpec(17, 5, 1, "M-D19-38"),
                DaySpec(24, 3, 0),
                DaySpec(25, 8, 1, "M-D19-38"),
                DaySpec(26, 6, 0),
                DaySpec(28, 3, 0),
                DaySpec(29, 6, 0),
                DaySpec(30, 2, 0),
                DaySpec(31, 4, 0)
            )
        )

        val apr = buildMonthDays(
            month = 4,
            daysInMonth = 30,
            nonZero = listOf(
                DaySpec(1, 6, 0),
                DaySpec(2, 6, 0),
                DaySpec(4, 5, 0),
                DaySpec(5, 3, 2, "M-D18-15, M-F31-17"),
                DaySpec(6, 7, 0),
                DaySpec(7, 4, 0),
                DaySpec(8, 5, 0),
                DaySpec(9, 5, 0),
                DaySpec(11, 2, 0),
                DaySpec(12, 2, 0),
                DaySpec(13, 4, 0),
                DaySpec(14, 3, 0),
                DaySpec(15, 2, 0),
                DaySpec(16, 4, 0),
                DaySpec(18, 9, 0),
                DaySpec(19, 3, 0),
                DaySpec(20, 5, 0),
                DaySpec(21, 4, 0),
                DaySpec(22, 2, 0),
                DaySpec(23, 4, 1, "M-A03-44"),
                DaySpec(25, 1, 0),
                DaySpec(26, 1, 0),
                DaySpec(27, 6, 0),
                DaySpec(28, 1, 0),
                DaySpec(29, 6, 0),
                DaySpec(30, 3, 0)
            )
        )

        val may = buildMonthDays(
            month = 5,
            daysInMonth = 31,
            nonZero = listOf(
                DaySpec(2, 7, 1, "M-F30-26"),
                DaySpec(3, 1, 0),
                DaySpec(4, 2, 0),
                DaySpec(5, 2, 0),
                DaySpec(6, 2, 0),
                DaySpec(7, 5, 0),
                DaySpec(9, 2, 1, "M-F30-26"),
                DaySpec(10, 4, 0),
                DaySpec(11, 2, 0),
                DaySpec(12, 1, 1, "M-E26-33"),
                DaySpec(14, 4, 0),
                DaySpec(16, 4, 0),
                DaySpec(17, 4, 0),
                DaySpec(18, 7, 1, "M-A02-15"),
                DaySpec(19, 3, 0),
                DaySpec(20, 4, 0),
                DaySpec(21, 4, 0),
                DaySpec(23, 6, 0),
                DaySpec(24, 2, 1, "M-A02-15"),
                DaySpec(25, 5, 1, "M-D20-18"),
                DaySpec(30, 2, 0),
                DaySpec(31, 7, 1, "M-E25-31")
            )
        )

        val jun = buildMonthDays(
            month = 6,
            daysInMonth = 30,
            nonZero = listOf(
                DaySpec(1, 2, 1, "M-E26-33"),
                DaySpec(2, 2, 0),
                DaySpec(3, 7, 0),
                DaySpec(4, 2, 1, "M-D20-18"),
                DaySpec(6, 3, 1, "M-D20-18"),
                DaySpec(7, 3, 0),
                DaySpec(8, 6, 0),
                DaySpec(9, 2, 0),
                DaySpec(10, 3, 2, "M-D20-21, M-D20-18"),
                DaySpec(11, 6, 1, "M-E26-33"),
                DaySpec(13, 4, 1, "M-E25-31"),
                DaySpec(14, 3, 2, "M-C13-35, M-E25-31"),
                DaySpec(15, 3, 0),
                DaySpec(16, 2, 1, "M-A02-38"),
                DaySpec(18, 5, 0),
                DaySpec(20, 2, 1, "M-A02-38"),
                DaySpec(21, 7, 0),
                DaySpec(22, 8, 6, "M-C13-35, M-E26-33, M-D20-18, M-D20-18, M-C13-35, M-C13-35"),
                DaySpec(23, 0, 1, "M-F28-46"),
                DaySpec(24, 3, 0),
                DaySpec(25, 3, 0),
                DaySpec(27, 1, 1, "M-F28-46"),
                DaySpec(28, 1, 0),
                DaySpec(29, 2, 0),
                DaySpec(30, 4, 0)
            )
        )

        val jul = buildMonthDays(
            month = 7,
            daysInMonth = 31,
            nonZero = listOf(
                DaySpec(1, 3, 1, "M-D18-44"),
                DaySpec(2, 3, 2, "M-D21-25, M-D21-25"),
                DaySpec(4, 2, 1, "M-D21-25"),
                DaySpec(5, 2, 1, "M-D21-25"),
                DaySpec(6, 6, 0),
                DaySpec(7, 1, 0),
                DaySpec(8, 2, 0),
                DaySpec(9, 2, 1, "M-C12-38"),
                DaySpec(11, 3, 0),
                DaySpec(16, 2, 0),
                DaySpec(18, 3, 0),
                DaySpec(19, 2, 0),
                DaySpec(20, 3, 0),
                DaySpec(21, 2, 0),
                DaySpec(22, 1, 0),
                DaySpec(25, 1, 0),
                DaySpec(26, 2, 0),
                DaySpec(27, 5, 0),
                DaySpec(28, 1, 1, "M-D21-25"),
                DaySpec(29, 5, 1, "M-B09-34"),
                DaySpec(30, 5, 0)
            )
        )

        val aug = buildMonthDays(
            month = 8,
            daysInMonth = 31,
            nonZero = listOf(
                DaySpec(1, 4, 0),
                DaySpec(2, 4, 1, "M-E25-48"),
                DaySpec(3, 3, 2, "M-A01-21, M-B10-25"),
                DaySpec(5, 2, 1, "M-B07-48"),
                DaySpec(6, 2, 1, "M-B09-34"),
                DaySpec(8, 5, 0),
                DaySpec(9, 2, 1, "M-A01-28"),
                DaySpec(10, 1, 0),
                DaySpec(11, 3, 0),
                DaySpec(12, 2, 0),
                DaySpec(13, 4, 0),
                DaySpec(14, 0, 1, "M-C13-23"),
                DaySpec(15, 1, 0),
                DaySpec(16, 3, 0),
                DaySpec(17, 3, 2, "M-B09-34, M-A01-28"),
                DaySpec(18, 3, 0),
                DaySpec(19, 4, 1, "M-A01-28"),
                DaySpec(20, 4, 1, "M-B09-34"),
                DaySpec(22, 7, 0),
                DaySpec(23, 2, 0),
                DaySpec(24, 6, 0),
                DaySpec(25, 4, 1, "M-E25-48"),
                DaySpec(26, 1, 1, "M-E25-48"),
                DaySpec(27, 4, 1, "M-D21-28"),
                DaySpec(29, 1, 1, "M-B10-41"),
                DaySpec(30, 5, 0),
                DaySpec(31, 5, 0)
            )
        )

        val sep = buildMonthDays(
            month = 9,
            daysInMonth = 30,
            nonZero = listOf(
                DaySpec(1, 4, 1, "M-A01-21"),
                DaySpec(2, 4, 1, "M-B10-21"),
                DaySpec(3, 4, 0),
                DaySpec(5, 7, 1, "M-D21-28"),
                DaySpec(6, 2, 0),
                DaySpec(7, 3, 0),
                DaySpec(8, 2, 2, "M-A01-21, M-A01-28"),
                DaySpec(9, 4, 1, "M-C13-47"),
                DaySpec(10, 3, 0),
                DaySpec(12, 2, 1, "M-B10-41"),
                DaySpec(13, 3, 5, "M-E22-23, M-E23-43, M-B10-37, M-A01-28, M-B10-37"),
                DaySpec(14, 1, 0),
                DaySpec(15, 6, 0),
                DaySpec(16, 4, 3, "M-F30-18, M-B07-33, M-D21-28"),
                DaySpec(17, 3, 0),
                DaySpec(19, 4, 4, "M-E23-43, M-E22-23, M-D21-28, M-B10-21"),
                DaySpec(20, 2, 2, "M-B07-44, M-F27-48"),
                DaySpec(21, 2, 3, "M-A01-21, M-E25-48, M-E25-48"),
                DaySpec(22, 4, 0),
                DaySpec(23, 3, 1, "M-A01-21"),
                DaySpec(24, 3, 0),
                DaySpec(26, 3, 1, "M-F27-28"),
                DaySpec(27, 5, 1, "M-F30-18"),
                DaySpec(28, 3, 1, "M-A01-28"),
                DaySpec(29, 3, 0),
                DaySpec(30, 5, 1, "M-E25-48")
            )
        )

        val oct = buildMonthDays(
            month = 10,
            daysInMonth = 31,
            nonZero = listOf(
                DaySpec(1, 3, 0),
                DaySpec(3, 4, 0),
                DaySpec(4, 4, 1, "M-B10-25"),
                DaySpec(5, 3, 1, "M-E22-23"),
                DaySpec(6, 6, 1, "M-E25-34"),
                DaySpec(7, 10, 1, "M-E25-34"),
                DaySpec(8, 2, 1, "M-E25-34"),
                DaySpec(10, 3, 0),
                DaySpec(11, 5, 1, "M-E22-23"),
                DaySpec(12, 8, 1, "M-B10-25"),
                DaySpec(13, 2, 0),
                DaySpec(15, 2, 0),
                DaySpec(17, 6, 1, "M-F30-18"),
                DaySpec(18, 4, 2, "M-E23-43, M-E25-34"),
                DaySpec(19, 2, 0),
                DaySpec(20, 3, 2, "M-E22-23, M-E25-34"),
                DaySpec(21, 7, 1, "M-E25-34"),
                DaySpec(22, 2, 1, "M-E25-48"),
                DaySpec(24, 3, 0),
                DaySpec(25, 6, 1, "M-E23-43"),
                DaySpec(26, 6, 1, "M-E25-34"),
                DaySpec(27, 5, 0),
                DaySpec(28, 8, 0),
                DaySpec(29, 3, 0),
                DaySpec(31, 6, 0)
            )
        )

        val nov = buildMonthDays(
            month = 11,
            daysInMonth = 30,
            nonZero = listOf(
                DaySpec(1, 3, 1, "M-A01-35"),
                DaySpec(2, 3, 3, "M-E26-44, M-D20-13, M-A02-46"),
                DaySpec(4, 3, 1, "M-C13-47"),
                DaySpec(5, 5, 0),
                DaySpec(7, 1, 1, "M-A02-46"),
                DaySpec(8, 4, 1, "M-A02-46"),
                DaySpec(9, 3, 0),
                DaySpec(11, 1, 0),
                DaySpec(12, 1, 0),
                DaySpec(14, 9, 2, "M-A04-16, M-C13-47"),
                DaySpec(15, 3, 1, "M-F30-18"),
                DaySpec(16, 2, 0),
                DaySpec(17, 6, 1, "M-F30-18"),
                DaySpec(18, 1, 0),
                DaySpec(19, 2, 0),
                DaySpec(21, 6, 3, "M-D20-44, M-D20-13, M-A01-35"),
                DaySpec(22, 3, 0),
                DaySpec(23, 6, 1, "M-F30-18"),
                DaySpec(24, 1, 0),
                DaySpec(25, 3, 1, "M-D18-41"),
                DaySpec(26, 8, 2, "M-C13-16, M-C15-21")
            )
        )

        val dec = buildMonthDays(month = 12, daysInMonth = 31, nonZero = emptyList())

        return (jan + feb + mar + apr + may + jun + jul + aug + sep + oct + nov + dec)
            .sortedByDescending { it.dateIso }
    }

    fun buildMaintenanceBacklog(): List<MaintenanceRequestEntity> {
        fun m2025(
            sn: Int,
            date: String,
            names: String,
            phone: String,
            wo: String,
            qid: String,
            flat: String,
            details: String
        ) = MaintenanceRequestEntity(
            sn = sn,
            date = date,
            names = names,
            phoneNumber = phone,
            workOrderNumber = wo,
            qidNumber = qid,
            flatDetails = flat,
            requestDetails = details
        )

        fun m2026(
            pdfSn: Int,
            date: String,
            names: String,
            phone: String,
            wo: String,
            qid: String,
            flat: String,
            details: String
        ) = MaintenanceRequestEntity(
            sn = 1000 + pdfSn,
            date = date,
            names = names,
            phoneNumber = phone,
            workOrderNumber = wo,
            qidNumber = qid,
            flatDetails = flat,
            requestDetails = details
        )

        val rows2025 = listOf(
            m2025(1, "9/13/2025", "MR. MAHDI", "55568858", "TO INITIATE REQUEST IN TENANT PORTAL", "27936400143", "MM-E23-41", "CHANGE OF WATER HEATER"),
            m2025(2, "9/14/2025", "MR. QASIM", "33034132", "2986609", "28958601812", "MM-A06-44", "KITCHEN CABINET REFURBISHMENT"),
            m2025(3, "9/14/2025", "MR. NISAR", "74716152", "INITIATED REQUEST- NEW MOVE IN TENANT", "27635613514", "MM-C11-25", "KITCHEN CABINET REFURBISHMENT"),
            m2025(4, "9/16/2025", "MR. RAWY", "50085050", "3157554", "27881802403", "MM-E25-15", "KITCHEN CABINET REFURBISHMENT- REQUEST MADE ON 9/15/2025"),
            m2025(5, "9/20/2025", "MR. ISMAIL", "55742864", "3167052/ 3166893 (AC), 3167104 (BATHROOM)", "27976002326", "MM-F28-26", "KITCHEN CABINET, AC & BATHROOM REFURBISHMENT"),
            m2025(6, "9/20/2025", "MR. ESSA", "55975789", "3105847", "-", "MM-C14-13", "KITCHEN CABINET REFURBISHMENT"),
            m2025(7, "9/20/2025", "MR. IFTIKHAR", "55510726", "3022382", "26758601049", "MM-C13-42", "KITCHEN CABINET REFURBISHMENT"),
            m2025(8, "9/20/2025", "MR. TAUSEEF AHMED", "55957909", "-", "28858603057", "MM-D18-32", "KITCHEN CABINET REFURBISHMENT"),
            m2025(9, "9/21/2025", "MR. IBRAHIM BIN ALI", "33399941", "-", "-", "MM-B09-21", "BATHTUB REFURBISHMENT TO AVOID LEAKAGE INTO LOWER FLAT 11"),
            m2025(10, "9/27/2025", "MR. FAHAD", "55203229", "2977033", "28435609452", "MM-F27-47", "KITCHEN CABINET REFURBISHMENT- REQUEST MADE ON 8/16/2025"),
            m2025(11, "9/29/2025", "MR. HASHIM", "55544191", "-", "24773620823", "MM-A02-14", "BATHROOM & KITCHEN CABINET REFURBISHMENT"),
            m2025(12, "9/29/2025", "MR. M NASSER", "55804396", "-", "25988600026", "MM-C12-38", "KITCHEN CABINET REFURBISHMENT"),
            m2025(13, "9/30/2025", "MR. MOHAMMED", "55870289", "-", "26881802717", "MM-E22-16", "KITCHEN CABINET REFURBISHMENT"),
            m2025(14, "10/5/2025", "MR. SAJO", "50022685", "-", "28235638054", "MM-F31-47", "HALL WAY AC UNIT REFURBISHMENT/ REPLACEMENT"),
            m2025(15, "10/5/2025", "MR. MOHAMMED AFROZ", "33110425", "-", "-", "MM-B10-23", "WASHROOM, AC UNIT- CARPET MAINTENANCE"),
            m2025(16, "10/6/2025", "MR. LAKSIRI", "66454388", "TO INITIATE REQUEST IN TENANT PORTAL", "27214404917", "MM-F28-16", "SPLIT AC REPLACEMENT"),
            m2025(17, "10/6/2025", "MR. MAHMOOD", "70708857", "-", "-", "MM-A01-36", "MAIN BREAKER REPLACEMENT"),
            m2025(18, "10/6/2025", "MR. JASEEM", "33347673", "TO INITIATE REQUEST IN TENANT PORTAL", "27835625755", "MM-B07-17", "KITCHEN CABINET REFURBISHMENT"),
            m2025(19, "10/7/2025", "MR. OMAR", "66514090", "TO INITIATE REQUEST IN TENANT PORTAL", "26940000648", "MM-A06-22", "KITCHEN CABINET REFURBISHMENT"),
            m2025(20, "10/7/2025", "MR. OMRAN", "66888823", "-", "28336400087", "MM-E22-25", "KITCHEN CABINET REFURBISHMENT"),
            m2025(21, "10/8/2025", "MR. MOUSTAFA", "77241624", "TO INITIATE REQUEST IN TENANT PORTAL", "28581805043", "MM-F29-45", "AC & WATER HEATER REFURBISHMENT"),
            m2025(22, "10/8/2025", "MR. MOHAMMED", "55105678", "-", "29881801214", "MM-A02-13", "CEILING & WALL REPAIRS (INSPECTION DONE BY FM TEAM, REPORT TO BE SHARED TO PM)"),
            m2025(23, "10/9/2025", "MR. ZAIDI", "66633052", "TO INITIATE REQUEST IN TENANT PORTAL", "26958602137", "MM-D17-12", "KITCHEN CABINET REFURBISHMENT"),
            m2025(24, "10/11/2025", "MR. MUSTAFA", "55873314", "3210652", "28681808374", "MM-B08-45", "BED ROOM TILES NEED GROUTING (INSECTS INSIDE TILE OPENINGS/HOLES)"),
            m2025(25, "10/14/2025", "MR. JALEEL", "33887846", "-", "27435618767", "MM-A01-33", "AC UNIT REPLACEMENT"),
            m2025(26, "10/19/2025", "MR. JALEEL", "77052436", "-", "27835635441", "MM-A04-47", "AC UNIT REPLACEMENT"),
            m2025(27, "10/20/2025", "MR. UNAIS", "77282044", "-", "28935639386", "MM-C12-46", "AC LEAKAGE- DRAINAGE ISSUE INSIDE OF BUILDING"),
            m2025(28, "10/20/2025", "MR. MISAB", "66497839", "2997273", "30135600325", "MM-D17-22", "KITCHEN CABINET REFURBISHMENT"),
            m2025(29, "10/23/2025", "MR. MOHAMMED HASSAN", "55203807", "-", "27976000178", "MM-A01-17", "KITCHEN CABINET REFURBISHMENT"),
            m2025(30, "10/29/2025", "MR. PRIMAS AMARADHAS", "66768040", "-", "27635612519", "MM-A05-16", "KITCHEN CABINET REFURBISHMENT"),
            m2025(31, "11/1/2025", "MR. LISSY MATHEW", "55750480", "-", "26835611003", "MM-A05-17", "KITCHEN CABINET REFURBISHMENT"),
            m2025(32, "11/3/2025", "MR. ABEER", "33199770", "-", "28499900156", "MM-D19-36", "KITCHEN CABINET REFURBISHMENT, WALL & BATHROOM PAINTING"),
            m2025(33, "11/5/2025", "MR. BASHAR", "55005519", "-", "27176000163", "MM-F30-44", "KITCHEN CABINET, SPLIT AC UNIT & DOORS REFURBISHMENT, AC"),
            m2025(34, "11/6/2025", "MR. KAMALA", "66549890", "-", "28735600630", "MM-A06-33", "APRTMENT TILES MODIFICATION"),
            m2025(35, "11/15/2025", "MR. LAKSIRI", "66454338", "-", "27214404917", "MM-F28-16", "WATER HEATER LEAKAGE"),
            m2025(36, "11/16/2025", "MR. MOHAMED", "70669080", "-", "26781800539", "MM-F28-23", "SPLIT AC UNIT REPLACEMENT"),
            m2025(37, "12/1/2025", "MR. MOHAMED", "55307404", "-", "27273600875", "MM-D17-11", "WATER HEATER LEAKAGE"),
            m2025(38, "12/7/2025", "MR. MOHAMMAD", "55307351", "-", "26558600374", "MM-B10-28", "WATER HEATER LEAKAGE"),
            m2025(39, "12/11/2025", "MR. ARUN", "33317749", "-", "29235600188", "MM-A06-36", "WATER HEATER LEAKAGE & WASHING MACHINE PIPE LEAKAGE-BLOCKAGE"),
            m2025(40, "12/14/2025", "MR. GHADI", "55370788", "-", "27381801602", "MM-A02-27", "BATHTUB & KITCHEN CABINET REFURBISHMENT"),
            m2025(41, "12/21/2025", "MR. GIGI", "55719788", "-", "-", "MM-F31-36", "WATER HEATER LEAKAGE"),
            m2025(42, "12/21/2025", "MR. TUSHAR", "33692960", "-", "30435601476", "MM-A05-47", "WALLS DAMAGED DUE TO LEAKAGE FROM RAIN WATER"),
            m2025(43, "12/22/2025", "MR. ANWAR", "50150206", "-", "27935630803", "MM-B10-32", "WATER HEATER LEAKAGE - RUSTY"),
            m2025(44, "12/22/2025", "MR. AHMED", "33296751", "3362061", "28973600094", "MM-C14-24", "WATER HEATER LEAKAGE"),
            m2025(45, "12/22/2025", "MR. ANJO", "55265295", "-", "28535609554", "MM-A03-38", "BATHTUB REFURBISHMENT"),
            m2025(46, "12/27/2025", "MR. MANSOOR", "55548023", "-", "26635604371", "MM-A06-26", "WATER HEATER LEAKAGE"),
            m2025(47, "12/27/2025", "MR. WASSIM", "66106002", "-", "26576000520", "MM-F30-18", "KITCHEN CABINET REFURBISHMENT"),
            m2025(48, "12/27/2025", "MR. MOHAMMAD", "33422262", "-", "2624000531", "MM-C14-14", "KITCHEN CABINET REFURBISHMENT"),
            m2025(49, "12/28/2025", "MR. SIBU JOSEPH", "55170834", "-", "28535681711", "MM-B08-11", "KITCHEN CABINET REFURBISHMENT"),
            m2025(50, "12/28/2025", "MR. AHMED GAMAA", "30145631", "-", "28981805496", "MM-E24-47", "BATHTUB REFURBISHMENT"),
            m2025(51, "12/28/2025", "MR. WALID", "77155089", "-", "28481805672", "MM-C16-44", "KITCHEN CABINET REFURBISHMENT"),
            m2025(52, "12/29/2025", "MR. ABDULLA SAID", "66103414", "-", "-", "MM-E25-23", "KITCHEN CABINET REFURBISHMENT/ AC RISER AREA DIRTY"),
            m2025(53, "12/31/2025", "MR. JASSIM", "30221223", "-", "-", "MM-A06-47", "SPLIT AC UNIT REPLACEMENT"),
            m2025(54, "12/31/2025", "MR. ABDULLAH", "66295848", "-", "30435600955", "MM-D17-22", "KITCHEN CABINET REFURBISHMENT")
        )

        val rows2026 = listOf(
            m2026(1, "1/1/2026", "MR. NOOH", "55325831", "-", "28236400026", "MM-E22-21", "WATER HEATER LEAKAGE, BATHTUB REPLACEMENT, TOILET DRAINAGE ISSUE"),
            m2026(2, "1/1/2026", "MR. NASIRELDIN", "55375937", "-", "26473600038", "MM-A06-11", "KITCHEN CABINET REFURBISHMENT"),
            m2026(3, "1/4/2026", "MR. MOAZZAM", "55891085", "-", "27558600282", "MM-E24-44( NEW MOVE IN TENANT)", "KITCHEN CABINET REFURBISHMENT, SHATTAF PIPE NEEDS FIXING"),
            m2026(4, "1/5/2026", "MR. MOHAMMED", "66211333", "-", "27858600294", "MM-B09-31", "KITCHEN CABINET & BATHROOM REFURBISHMENT"),
            m2026(5, "1/6/2026", "MR. WASSIM", "66106002", "-", "26576000520", "MM-F30-18", "KITCHEN CABINET & BATHTUB REFURBISHMENT"),
            m2026(6, "1/6/2026", "MR. MUKHTAR", "50898943", "-", "27758603313", "MM-A01-42", "BATHTUB REFURBISHMENT"),
            m2026(7, "1/6/2026", "MR. ABDULLAH", "66295848", "-", "30435600955", "MM-D17-22", "KITCHEN CABINET REFURBISHMENT"),
            m2026(8, "1/10/2026", "MR. MOHAMED", "66347730", "-", "29181803531", "MM-E24-46", "KITCHEN CABINET REFURBISHMENT"),
            m2026(9, "1/11/2026", "SHARIFAH", "50151138", "-", "-", "MM-B08-31", "KITCHEN CABINET REFURBISHMENT"),
            m2026(10, "1/12/2026", "SAFA", "55807942", "-", "-", "MM-B08-43", "KITCHEN CABINET REFURBISHMENT"),
            m2026(11, "1/12/2026", "DINA", "33790779", "-", "-", "MM-A04-24", "KITCHEN CABINET REFURBISHMENT/ PAINTING INSIDE FLAT"),
            m2026(12, "1/13/2026", "MR. FIRAS", "66337677", "-", "27758603313", "MM-D17-28", "BATHTUB REPLACEMENT"),
            m2026(13, "1/14/2026", "MR. MOHAMED", "33153059", "-", "28181808372", "MM-F27-31", "KITCHEN CABINET REFURBISHMENT"),
            m2026(14, "1/14/2026", "MR. SALAH", "55282920", "-", "26078800029", "MM-E22-31", "KITCHEN CABINET REFURBISHMENT"),
            m2026(15, "1/15/2026", "MR. ANTHONY", "55818573", "-", "26035602869", "MM-B10-12", "BATHTUB REPLACEMENT, PAINTING INSIDE BATHROOM"),
            m2026(16, "1/17/2026", "MR. KHALID", "66067720", "-", "-", "MM-F29-35", "KITCHEN CABINET REFURBISHMENT"),
            m2026(17, "1/18/2026", "MR. ZAIN", "31177974", "-", "-", "MM-B08-21", "BATHTUB REPLACEMENT, TILES NEXT TO BATHTUB DAMAGED"),
            m2026(18, "1/18/2026", "MR. AHMED", "30145631", "3420222", "-", "MM-E24-47", "KITCHEN CABINET REFURBISHMENT"),
            m2026(19, "1/19/2026", "MR. HAMEED", "55326133", "-", "27135600818", "MM-A05-15", "KITCHEN CABINET REFURBISHMENT"),
            m2026(20, "1/21/2026", "MR. NADIR", "50210472", "-", "-", "MM-C16-11( TRANSFER FROM", "INSTALLATION OF BATHTUB REQUEST"),
            m2026(21, "1/21/2026", "MR. NASER", "55807942", "-", "28040000331", "MM-B08-43", "KITCHEN CABINET REFURBISHMENT & CEILING LEAK, REQUIRING PAINTING"),
            m2026(22, "1/21/2026", "MR. ABDELSALAM", "77743281", "3043050", "-", "MM-D17-14", "KITCHEN CABINET REFURBISHMENT"),
            m2026(23, "1/21/2026", "MR. RAWI", "50085050", "-", "27881802403", "MM-E25-15", "WATER HEATER LEAKAGE"),
            m2026(24, "1/22/2026", "MR. ROHIEM", "33586447", "-", "27681808857", "MM-E25-31", "REQUEST FOR TILES"),
            m2026(25, "1/24/2026", "MR. ABDELAORF", "55607251", "3043050", "26073601042", "MM-E22-28", "KITCHEN CABINET REFURBISHMENT"),
            m2026(26, "1/25/2026", "MR. HOUSAM", "66051235", "-", "28581802881", "MM-B09-38", "BATHTUB REFURBISHMENT"),
            m2026(27, "1/25/2026", "MR. JESWIN", "66916777", "-", "28435600505", "MM-B10-13", "BATHTUB REFURBISHMENT"),
            m2026(28, "1/26/2026", "MR. ABO HAMED", "55547855", "-", "26776000008", "MM-E22-26", "KITCHEN CABINET REFURBISHMENT"),
            m2026(29, "1/27/2026", "MR. SAYEED", "55685795", "-", "27681802267", "MM-C14-15", "SHOWER TRAY REPLACEMENT"),
            m2026(30, "1/28/2026", "MR. MOHAMED ALI", "70669080", "-", "26781800539", "MM-F28-23", "KITCHEN CABINET REFURBISHMENT"),
            m2026(31, "1/28/2026", "MR. RAMY ARIEF", "55820313", "-", "2799900062", "MM-D19-18", "KITCHEN CABINET REFURBISHMENT"),
            m2026(32, "1/29/2026", "MR. FAROUK HASSAN", "55844187", "-", "25281800407", "MM-C12-11", "WATER HEATER LEAKAGE/ KITCHEN CABINET REPLACEMENT"),
            m2026(33, "1/31/2026", "MR. HARAMELDAV", "33114865", "-", "26473600743", "MM-C16-17", "KITCHEN CABINET AND BATHROOM REFURBISHMENT"),
            m2026(34, "2/1/2026", "MR. MOHIDREL", "55824026", "-", "27314405539", "MM-C13-24", "KITCHEN CABINET AND BATHTUB REFURBISHMENT"),
            m2026(35, "2/3/2026", "MR. MOHAMED", "55352820", "-", "27481800726", "MM-E23-31", "KITCHEN CABINET REFURBISHMENT"),
            m2026(36, "2/4/2026", "MR. ABDELSALAM", "55682275", "-", "27181803718", "MM-D20-26", "KITCHEN CABINET REFURBISHMENT"),
            m2026(37, "2/5/2026", "MR. AHMAD", "77027540", "-", "28940000860", "MM-B07-38", "KITCHEN CABINET REFURBISHMENT"),
            m2026(38, "2/5/2026", "MR. MEDHAT SALEH", "55899700", "-", "26540000556", "MM-A06-15", "KITCHEN CABINET REFURBISHMENT"),
            m2026(39, "2/9/2026", "MRS. SAMOOY", "55742425", "-", "28736400839", "MM-B10-36", "KITCHEN CABINET REFURBISHMENT"),
            m2026(40, "2/11/2026", "MR. HASHIM", "55544191", "-", "24773600023", "MM-A02-14", "KITCHEN CABINET REFURBISHMENT"),
            m2026(41, "2/14/2026", "MR. ABDUL RAHMAN", "50608023", "3468763", "25858600632", "MM-D17-26", "KITCHEN CABINET REFURBISHMENT"),
            m2026(42, "2/17/2026", "MR. MOHD", "33586144", "-", "2740000189", "MM-D21-42", "KITCHEN CABINET AND SHOWER TRAY REPLACEMENT"),
            m2026(43, "2/23/2026", "MR. NISHAD", "50413788", "-", "28335615569", "MM-D20-34", "SPLIT AC UNIT REPLACEMENT"),
            m2026(44, "2/23/2026", "MR. SULIMAN", "33744593", "-", "28176002875", "MM-A03-47", "SPLIT AC UNIT LEAK"),
            m2026(45, "2/23/2026", "MR. HAMAD", "66134613", "-", "30076000011", "MM-E22-26", "KITCHEN CABINET REFURBISHMENT"),
            m2026(46, "2/25/2026", "MR. ALI AHMED", "30981001", "-", "28558600168", "MM-E26-32", "WATER HEATER LEAK"),
            m2026(47, "3/7/2026", "MR. ABDULLAH SAID", "66103414", "-", "23651200006", "M-E23-15", "WATER HEATER LEAK"),
            m2026(48, "3/7/2026", "MR. MONEIM", "33683981", "-", "-", "M-A04-27", "LEAKAGE INSIDE BATHROOM"),
            m2026(49, "3/8/2026", "MR. MOHAMED MOHIY", "30120054", "-", "27781803005", "M-E22-15", "WATER HEATER LEAK"),
            m2026(50, "3/15/2026", "MR. ANSARI IQBAL", "55817385", "-", "26435609904", "M-B10-18", "BATHROOM LEAK"),
            m2026(51, "3/17/2026", "MR. FAHAD", "55203229", "-", "28435609452", "M-F27-47", "KITCHEN CABINET REFURBISHMENT"),
            m2026(52, "3/17/2026", "MR. VISWEASWARADEV", "66019914", "-", "26735622034", "M-A01-11", "KITCHEN CABINET REFURBISHMENT"),
            m2026(53, "3/25/2026", "MR. GAFAR MOHAMMED", "55262503", "-", "27473600298", "M-F27-48", "KITCHEN CABINET REFURBISHMENT & BATHROOM LEAKAGE"),
            m2026(54, "3/25/2026", "MR. MOHAMMED", "77886786", "-", "27136400042", "M-F30-23", "WATER HEATER LEAK"),
            m2026(55, "3/29/2026", "MR. CHARLIE", "55972177", "-", "26460803456", "M-A04-22", "SILICON WORK NEEDED FOR MAIN TOILET"),
            m2026(56, "4/1/2026", "MR. MOHAMED", "55846011", "-", "25873600888", "M-B07-45", "CEILING LEAK"),
            m2026(57, "4/6/2026", "MR. AHMED", "33053674", "-", "29373600398", "M-A04-31", "KITCHEN CABINET REFURBISHMENT"),
            m2026(58, "4/7/2026", "MR. ANIL KUMAR", "33678031", "-", "26635622341", "M-E24-31", "KITCHEN CABINET REFURBISHMENT"),
            m2026(59, "4/9/2026", "MR. REHMA ALI", "30099901", "-", "28435627043", "M-C11-15", "SPLIT AC, WATER HEATER & KITCHEN REFURBISHMENT"),
            m2026(60, "4/9/2026", "MR. WALEED", "70040107", "-", "29081505189", "M-E24-33", "KITCHEN CABINET REFURBISHMENT"),
            m2026(61, "4/11/2026", "MR. ABDULLATEEF", "55549566", "-", "25935600104", "M-A06-38", "WATER HEATER LEAK"),
            m2026(62, "5/9/2026", "MR. STEPHEN", "66741649", "-", "28335606877", "M-A05-45", "KITCHEN CABINET REFURBISHMENT"),
            m2026(63, "5/10/2026", "MR. AHMED SALEH", "55189918", "-", "28051200024", "M-C14-26", "KITCHEN CABINET REFURBISHMENT"),
            m2026(64, "5/20/2026", "MR. SAYYAF", "55254253", "-", "29035600510", "M-A06-38", "WATER HEATER LEAK"),
            m2026(65, "6/1/2026", "MR. MAHDI", "55568858", "-", "2793640143", "M-E23-41", "WATER HEATER REPLACEMENT"),
            m2026(66, "6/3/2026", "MR. SONIA JACOB", "50248529", "-", "28535651416", "M-C12-16", "BATHTUB LEAK"),
            m2026(67, "6/18/2026", "MR. DE SILVA", "77132521", "-", "28014407631", "M-C13-31", "BATHTUB REPLACEMENT"),
            m2026(68, "6/30/2026", "MR. KHALID", "55370259", "-", "26863400185", "M-E23-28", "BATHTUB REPLACEMENT"),
            m2026(70, "7/7/2026", "MR. MOHAMMED", "55644234", "-", "27936400145", "M-E25-41", "WATER HEATER LEAK"),
            m2026(72, "8/18/2026", "MR. MOHAMMED SHAFIQ", "55483750", "-", "26958601527", "M-D20-14", "BATHTUB REPLACEMENT"),
            m2026(73, "8/20/2026", "MR. MOHAMMED", "55484032", "-", "26607200038", "M-E22-43", "BATHTUB REPLACEMENT"),
            m2026(74, "8/22/2026", "MR. HUSSEIN", "50004959", "-", "27736400072", "M-B10-48", "AC MAINTENANCE"),
            m2026(75, "8/22/2026", "MR. ABDEL", "55607251", "-", "26673601042", "M-E22-28", "AC MAINTENANCE"),
            m2026(76, "8/22/2026", "MR. MISAB", "66497839", "-", "30135600325", "M-D17-22", "TILES REPLACEMENT"),
            m2026(77, "8/22/2026", "MR. MOHAMMED", "66576443", "-", "28035630403", "M-C16-17", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(78, "8/24/2026", "MR. LAKSIRI", "66454338", "-", "27214404917", "M-F28-16", "BATHTUB REPLACEMENT"),
            m2026(79, "8/25/2026", "MR. HALID", "53869360", "-", "27214404917", "M-F28-21", "BATHTUB REPLACEMENT"),
            m2026(80, "8/27/2026", "MR. AQIB", "55264416", "-", "28658604531", "M-F230-13", "SPLIT AC ISSUE"),
            m2026(82, "9/1/2026", "MR. TARIQ", "55545880/33299957", "-", "27158600557", "M-C11-22", "BATHTUB REPLACEMENT"),
            m2026(83, "9/2/2026", "MR. YAHIN", "70100709", "-", "27842200040", "M-F28-38", "SPLIT AC ISSUE"),
            m2026(84, "9/5/2026", "MR. JACOB", "55176691", "-", "28435638163", "M-E26-13", "SPLIT AC ISSUE"),
            m2026(85, "9/6/2026", "MR. MUKHTAR", "50898943", "-", "27758603313", "M-A01-42", "TILES REPLACEMENT"),
            m2026(86, "9/7/2026", "MR. MOHAMMED", "66930360", "-", "28581801806", "M-A06-32", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(87, "9/9/2026", "MR. AHMED SABER", "66831709", "-", "27881804861", "M-B09-48", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(88, "9/13/2026", "MISS REMA", "55837411", "-", "27640000745", "M-D17-41", "BATHTUB REPLACEMENT"),
            m2026(89, "9/13/2026", "MR. ABDUL BAST", "33440051", "-", "30458600983", "M-E26-34", "BATHTUB REPLACEMENT"),
            m2026(90, "9/13/2026", "MR. MAZUAR", "50572924", "-", "28942201109", "M-E26-15", "BATHTUB REPLACEMENT"),
            m2026(91, "9/15/2026", "MR. MOHAMMED", "55846011", "-", "25873600888", "M-B07-45", "CEILING LEAK"),
            m2026(92, "9/19/2026", "MR. YAHIN", "70100709", "-", "27842200040", "M-F28-38", "WATER LEAK ( DROPS) IN THE WASHROOM"),
            m2026(94, "10/1/2026", "MISS REMA RAFEE", "66688384", "3983024", "27640000745", "M-D17-41", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(95, "10/6/2026", "MR. ABDUL BAST", "33440051", "-", "30458600983", "M-E26-34", "BATHTUB REPLACEMENT"),
            m2026(96, "10/6/2026", "MR. MAZUAR", "50572924", "-", "28942201109", "M-E26-15", "BATHTUB REPLACEMENT"),
            m2026(97, "10/7/2026", "MISS SUMAYA", "-", "-", "28163401094", "M-F30-32", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(98, "10/7/2026", "MR. HUSSAIN", "55336463", "-", "26336400058", "M-D17-23", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(99, "10/11/2026", "MR. IBRAHIM", "55275789", "-", "-", "M-C15-15", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(100, "10/12/2026", "MR. MOHAMMED", "55303547", "-", "26681801676", "M-A02-43", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(101, "10/12/2026", "MISS ASHA", "66773020", "-", "27535609885", "M-B09-17", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(102, "10/15/2026", "MR. SAMITH", "77132521", "-", "28014407631", "M-C13-31", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(103, "10/19/2026", "MR. ELAMIN", "55874683", "-", "27273600352", "M-F28-18", "TILES REPLACEMENT"),
            m2026(104, "10/20/2026", "MR. VISTHAR", "50878786", "-", "26135604667", "M-B08-35", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(105, "10/21/2026", "MR. ESAM", "55451611", "-", "27442200057", "M-D20-22", "WATER HEATER REPLACEMENT"),
            m2026(106, "10/21/2026", "MISS JOSEPHINE", "55214843", "-", "-", "M-D17-27", "BATHTUB REPLACEMENT"),
            m2026(107, "10/24/2026", "MR. HANI", "55818957", "-", "24899900016", "M-B08-12", "BATHTUB/ SHOWER TRAY REPLACEMENT"),
            m2026(108, "10/25/2026", "MR. ABDELFTAH", "70760611", "-", "27981802803", "M-E23-13", "BATHTUB DAMAGE- REPLACEMENT"),
            m2026(109, "10/26/2026", "MR. JALUL", "33887846", "-", "27435618767", "M-A01-33", "BATHTUB DAMAGE- REPLACEMENT"),
            m2026(110, "10/26/2026", "MR. RAJA", "55994374", "-", "27140000231", "M-B08-23", "KITCHEN CABINETS REFURBISHMENT"),
            m2026(112, "11/5/2026", "MR. TOPS", "50966990", "-", "28160804690", "M-C15-27", "TOILET LEAK ISSUE")
        )

        return (rows2026.reversed() + rows2025.reversed())
    }
}

package com.example.data.db

import com.example.data.sync.TestRecordFilter
import java.util.Calendar
import java.util.TimeZone

object SeedDataProvider {

    fun buildKeyRegisterSeed(): List<KeyRegisterEntity> {
        val raw = listOf(
            KeyRegisterEntity(
                si = 1645,
                dateOut = "26/11/2026",
                timeOut = "15:10",
                flatUnitLocation = "M-C15-21",
                keyCardType = "Master Key & Access Card",
                purpose = "PM Apartment Viewing",
                issuedTo = "PM Leasing Desk",
                qidOrNote = "Viewing Ref 26-Nov",
                dateIn = "",
                timeIn = "",
                returnedBy = "",
                remarks = "Reconciled open issue (26/11/2026)",
                status = KeyStatus.ISSUED
            ),
            KeyRegisterEntity(
                si = 1644,
                dateOut = "26/11/2026",
                timeOut = "11:45",
                flatUnitLocation = "M-C13-16",
                keyCardType = "Apartment Key Set",
                purpose = "PM Apartment Viewing & Inspection",
                issuedTo = "Tariq Al-Kuwari",
                qidOrNote = "28963401822",
                dateIn = "",
                timeIn = "",
                returnedBy = "",
                remarks = "Awaiting return confirmation",
                status = KeyStatus.NOT_YET_RETURNED
            ),
            KeyRegisterEntity(
                si = 1643,
                dateOut = "26/11/2026",
                timeOut = "09:20",
                flatUnitLocation = "M-B07-12",
                keyCardType = "MEP Sub-Station Key",
                purpose = "Chiller & AC Valve Check",
                issuedTo = "Eng. Imran Patel",
                qidOrNote = "WO-2026-1189",
                dateIn = "",
                timeIn = "",
                returnedBy = "",
                remarks = "Technician active on site",
                status = KeyStatus.ISSUED
            ),
            KeyRegisterEntity(
                si = 1642,
                dateOut = "25/11/2026",
                timeOut = "14:15",
                flatUnitLocation = "M-D18-41",
                keyCardType = "Apartment Key Set",
                purpose = "PM Apartment Viewing",
                issuedTo = "Nasser Al-Mansoori",
                qidOrNote = "29163400419",
                dateIn = "11/26/2026",
                timeIn = "16:36",
                returnedBy = "Nasser Al-Mansoori",
                remarks = "Nov return reconciled",
                status = KeyStatus.RETURNED
            ),
            KeyRegisterEntity(
                si = 1641,
                dateOut = "25/11/2026",
                timeOut = "10:05",
                flatUnitLocation = "M-A09-32",
                keyCardType = "Access Card #A0932",
                purpose = "Snagging Paint Touch-up",
                issuedTo = "Ramesh Kumar",
                qidOrNote = "28435601140",
                dateIn = "",
                timeIn = "",
                returnedBy = "Ramesh Kumar",
                remarks = "Returned — pending Nov timestamp reconciliation",
                status = KeyStatus.RETURNED
            ),
            KeyRegisterEntity(
                si = 1640,
                dateOut = "24/11/2026",
                timeOut = "13:30",
                flatUnitLocation = "M-E03-14",
                keyCardType = "Apartment Key Set",
                purpose = "Plumbing Leak Follow-up",
                issuedTo = "Faisal Maintenance",
                qidOrNote = "WO-2026-1182",
                dateIn = "24/11/2026",
                timeIn = "",
                returnedBy = "Faisal Maintenance",
                remarks = "Returned — missing Time In",
                status = KeyStatus.RETURNED
            ),
            KeyRegisterEntity(
                si = 1639,
                dateOut = "24/11/2026",
                timeOut = "08:50",
                flatUnitLocation = "M-D02-08",
                keyCardType = "Gate Access Card",
                purpose = "Contractor Roof Access",
                issuedTo = "Al-Rayyan Contractors",
                qidOrNote = "28763409211",
                dateIn = "",
                timeIn = "",
                returnedBy = "",
                remarks = "Still issued — verify T-code & return",
                status = KeyStatus.ISSUED
            ),
            KeyRegisterEntity(
                si = 1638,
                dateOut = "23/11/2026",
                timeOut = "16:10",
                flatUnitLocation = "M-B12-24",
                keyCardType = "Spare Bedroom Key",
                purpose = "Deep Cleaning & Handover Prep",
                issuedTo = "Doha Shine Crew",
                qidOrNote = "ID-44910",
                dateIn = "",
                timeIn = "",
                returnedBy = "",
                remarks = "Key not yet returned — follow up",
                status = KeyStatus.NOT_YET_RETURNED
            ),
            KeyRegisterEntity(
                si = 1637,
                dateOut = "23/11/2026",
                timeOut = "09:40",
                flatUnitLocation = "M-C06-11",
                keyCardType = "Apartment Key Set",
                purpose = "Meter Reading",
                issuedTo = "Kahramaa Inspector",
                qidOrNote = "Permit #KH-902",
                dateIn = "11/26/2026",
                timeIn = "16:36",
                returnedBy = "Security Desk",
                remarks = "Nov return reconciled",
                status = KeyStatus.RETURNED
            ),
            KeyRegisterEntity(
                si = 1636,
                dateOut = "22/11/2026",
                timeOut = "11:15",
                flatUnitLocation = "M-A11-05",
                keyCardType = "Access Card #A1105",
                purpose = "Fiber ONT Activation",
                issuedTo = "Ooredoo Tech",
                qidOrNote = "OR-77821",
                dateIn = "",
                timeIn = "",
                returnedBy = "Security Desk",
                remarks = "Returned — pending Nov timestamp reconciliation",
                status = KeyStatus.RETURNED
            ),
            KeyRegisterEntity(
                si = 1635,
                dateOut = "21/11/2026",
                timeOut = "15:25",
                flatUnitLocation = "M-E10-19",
                keyCardType = "Apartment Key Set",
                purpose = "Move-out Inspection",
                issuedTo = "PM Officer",
                qidOrNote = "Checklist #M-41",
                dateIn = "11/26/2026",
                timeIn = "16:36",
                returnedBy = "PM Officer",
                remarks = "Nov return reconciled",
                status = KeyStatus.RETURNED
            ),
            KeyRegisterEntity(
                si = 1634,
                dateOut = "20/11/2026",
                timeOut = "10:15",
                flatUnitLocation = "M-A04-16",
                keyCardType = "Main Unit Key",
                purpose = "HVAC Duct Inspection",
                issuedTo = "Khalid Sub-Contractor",
                qidOrNote = "28563401129",
                dateIn = "",
                timeIn = "",
                returnedBy = "",
                remarks = "OVERDUE: Verify return on M-A04-16",
                status = KeyStatus.NOT_YET_RETURNED
            ),
            KeyRegisterEntity(
                si = 1633,
                dateOut = "19/11/2026",
                timeOut = "09:00",
                flatUnitLocation = "M-B04-02",
                keyCardType = "Access Card #B0402",
                purpose = "Fire Alarm Testing",
                issuedTo = "Safety Team",
                qidOrNote = "WO-2026-1160",
                dateIn = "19/11/2026",
                timeIn = "13:45",
                returnedBy = "Safety Team",
                remarks = "Completed & returned",
                status = KeyStatus.RETURNED
            ),
            KeyRegisterEntity(
                si = 1632,
                dateOut = "18/11/2026",
                timeOut = "14:00",
                flatUnitLocation = "M-D08-17",
                keyCardType = "Apartment Key Set",
                purpose = "Carpentry Hinge Repair",
                issuedTo = "Hassan Carpenter",
                qidOrNote = "WO-2026-1154",
                dateIn = "18/11/2026",
                timeIn = "17:15",
                returnedBy = "Hassan Carpenter",
                remarks = "Completed & returned",
                status = KeyStatus.RETURNED
            )
        )
        return TestRecordFilter.filterKeyRecords(raw)
    }

    fun buildVisitorDaysSeed(): List<VisitorDayEntity> {
        val novOtherDays = listOf(
            VisitorDayEntity("2026-11-26", "26-Nov-2026", "2026-11", 8, 2, "M-C13-16, M-C15-21", "Two afternoon PM viewings logged"),
            VisitorDayEntity("2026-11-25", "25-Nov-2026", "2026-11", 3, 1, "M-D18-41", "1 apartment viewing completed"),
            VisitorDayEntity("2026-11-24", "24-Nov-2026", "2026-11", 1, 0, "-", "Quiet walk-in inquiry"),
            VisitorDayEntity("2026-11-23", "23-Nov-2026", "2026-11", 5, 2, "M-B06-11, M-E02-09", "Morning & evening viewings"),
            VisitorDayEntity("2026-11-22", "22-Nov-2026", "2026-11", 4, 1, "M-A07-22", "Tenant key inquiry + viewing"),
            VisitorDayEntity("2026-11-21", "21-Nov-2026", "2026-11", 6, 2, "M-C09-04, M-D14-31", "Weekend prospect visits"),
            VisitorDayEntity("2026-11-19", "19-Nov-2026", "2026-11", 4, 1, "M-B11-18", "Contract renewal & viewing"),
            VisitorDayEntity("2026-11-16", "16-Nov-2026", "2026-11", 5, 1, "M-E05-12", "Corporate lease tour"),
            VisitorDayEntity("2026-11-12", "12-Nov-2026", "2026-11", 7, 2, "M-A03-14, M-D04-27", "Mid-month viewing wave"),
            VisitorDayEntity("2026-11-08", "08-Nov-2026", "2026-11", 3, 1, "M-C02-19", "1BR prospect visit"),
            VisitorDayEntity("2026-11-03", "03-Nov-2026", "2026-11", 6, 2, "M-B09-07, M-E11-25", "Early November leasing")
        )
        val octSummary = listOf(
            VisitorDayEntity("2026-10-28", "28-Oct-2026", "2026-10", 14, 4, "M-A02-10, M-B08-15, M-C11-03, M-D06-21", "October closing roll-up"),
            VisitorDayEntity("2026-10-15", "15-Oct-2026", "2026-10", 18, 5, "M-A12-08, M-E04-16, M-D09-11", "October mid-month roll-up")
        )
        return novOtherDays + octSummary
    }

    fun buildMaintenanceSeed(): List<MaintenanceRequestEntity> = listOf(
        MaintenanceRequestEntity(
            sn = 1,
            date = "21/11/2026",
            names = "Ahmed Al-Sulaiti",
            phoneNumber = "+974 5512 4401",
            workOrderNumber = "WO-2026-1174",
            qidNumber = "28863401190",
            flatDetails = "M-A08-14",
            requestDetails = "Master bedroom AC thermostat unresponsive; fan running continuously."
        ),
        MaintenanceRequestEntity(
            sn = 2,
            date = "23/11/2026",
            names = "Fatima Al-Mohannadi",
            phoneNumber = "+974 5590 8823",
            workOrderNumber = "WO-2026-1180",
            qidNumber = "29363407741",
            flatDetails = "M-C04-09",
            requestDetails = "Kitchen sink mixer cartridge dripping; requires washer & seal replacement."
        ),
        MaintenanceRequestEntity(
            sn = 3,
            date = "24/11/2026",
            names = "Samer Haddad",
            phoneNumber = "+974 6641 2094",
            workOrderNumber = "WO-2026-1182",
            qidNumber = "28440019283",
            flatDetails = "M-E03-14",
            requestDetails = "Guest bathroom drainage slow and minor trap seepage."
        ),
        MaintenanceRequestEntity(
            sn = 4,
            date = "25/11/2026",
            names = "Layla Al-Thani",
            phoneNumber = "+974 5033 7190",
            workOrderNumber = "WO-2026-1186",
            qidNumber = "29063405512",
            flatDetails = "M-D12-28",
            requestDetails = "Balcony sliding door lock latch misaligned; difficult to engage."
        ),
        MaintenanceRequestEntity(
            sn = 5,
            date = "26/11/2026",
            names = "Omar Al-Khelaifi",
            phoneNumber = "+974 5578 3319",
            workOrderNumber = "WO-2026-1189",
            qidNumber = "28663409821",
            flatDetails = "M-B07-12",
            requestDetails = "Chiller valve actuator calibration & living room diffuser balancing."
        )
    )

    /**
     * Exact Nov 1–25, 2026 baseline:
     * - 135 bookings total
     * - 79 Football = 106.5 hrs (55 x 1.5h + 24 x 1.0h = 82.5 + 24.0 = 106.5)
     * - 56 Padel = 59.5 hrs (7 x 1.5h + 49 x 1.0h = 10.5 + 49.0 = 59.5)
     * - 166.0 total hours
     * - All marked FREE
     * - Nov 26, 2026 pending next-day entry
     */
    fun buildSportsBookingsSeed(): List<SportsBookingEntity> {
        val residents = listOf(
            "Khalid Al-Naimi" to "M-A02-11",
            "Yusuf Al-Marri" to "M-B05-14",
            "Tariq Hassan" to "M-C08-22",
            "Salman Al-Dosari" to "M-D11-06",
            "Ziad Mansour" to "M-E04-19",
            "Nabil Qureshi" to "M-A10-31",
            "Faisal Al-Qahtani" to "M-B14-08",
            "Hamad Al-Attiyah" to "M-C03-15",
            "Rami Khoury" to "M-D07-24",
            "Bilal Siddiqui" to "M-E09-12",
            "Adel Al-Kuwari" to "M-A06-03",
            "Karim Farouk" to "M-B01-17"
        )
        val footballSlots = listOf("16:30 - 18:00", "18:00 - 19:30", "19:30 - 21:00", "21:00 - 22:00")
        val padelSlots = listOf("17:00 - 18:00", "18:00 - 19:00", "19:30 - 20:30", "20:30 - 22:00")

        val list = mutableListOf<SportsBookingEntity>()
        var sn = 1
        var footballCount = 0
        var padelCount = 0

        // Distribute 79 Football + 56 Padel across Nov 1..25 (135 total)
        // Pattern: 5 or 6 bookings per day across 25 days = 135
        for (day in 1..25) {
            val dateStr = String.format("%02d/11/2026", day)
            val fbToday = when {
                day <= 4 -> 4
                day <= 25 -> if (day % 3 == 0) 4 else 3
                else -> 3
            }
            // Let's guarantee exact counts: 79 Football, 56 Padel
            val actualFb = minOf(fbToday, 79 - footballCount)
            for (i in 0 until actualFb) {
                val idx = footballCount
                val duration = if (idx < 55) 1.5 else 1.0
                val res = residents[(day + i) % residents.size]
                val slot = if (duration == 1.5) footballSlots[i % 3] else "21:00 - 22:00"
                list.add(
                    SportsBookingEntity(
                        sn = sn++,
                        sport = "FOOTBALL",
                        date = dateStr,
                        time = slot,
                        name = res.first,
                        unitDetails = res.second,
                        discount = "FREE",
                        referenceNo = String.format("T-FB-%04d", 1100 + sn),
                        durationPlayed = duration,
                        remarks = "Resident Court Access - Verified"
                    )
                )
                footballCount++
            }

            val pdToday = if (day % 4 == 0) 3 else 2
            val actualPd = minOf(pdToday, 56 - padelCount)
            for (i in 0 until actualPd) {
                val idx = padelCount
                val duration = if (idx < 7) 1.5 else 1.0
                val res = residents[(day * 2 + i + 3) % residents.size]
                val slot = if (duration == 1.5) "20:30 - 22:00" else padelSlots[i % 3]
                list.add(
                    SportsBookingEntity(
                        sn = sn++,
                        sport = "PADEL",
                        date = dateStr,
                        time = slot,
                        name = res.first,
                        unitDetails = res.second,
                        discount = "FREE",
                        referenceNo = String.format("T-PD-%04d", 2100 + sn),
                        durationPlayed = duration,
                        remarks = "Padel Court Free Resident Booking"
                    )
                )
                padelCount++
            }
        }

        // Top up any remainder on Nov 25 so counts are strictly 79 Football + 56 Padel = 135
        while (footballCount < 79) {
            val duration = if (footballCount < 55) 1.5 else 1.0
            val res = residents[footballCount % residents.size]
            list.add(
                SportsBookingEntity(
                    sn = sn++,
                    sport = "FOOTBALL",
                    date = "25/11/2026",
                    time = if (duration == 1.5) "18:00 - 19:30" else "21:00 - 22:00",
                    name = res.first,
                    unitDetails = res.second,
                    discount = "FREE",
                    referenceNo = String.format("T-FB-%04d", 1100 + sn),
                    durationPlayed = duration,
                    remarks = "Resident Court Access - Verified"
                )
            )
            footballCount++
        }
        while (padelCount < 56) {
            val duration = if (padelCount < 7) 1.5 else 1.0
            val res = residents[padelCount % residents.size]
            list.add(
                SportsBookingEntity(
                    sn = sn++,
                    sport = "PADEL",
                    date = "25/11/2026",
                    time = if (duration == 1.5) "20:30 - 22:00" else "19:30 - 20:30",
                    name = res.first,
                    unitDetails = res.second,
                    discount = "FREE",
                    referenceNo = String.format("T-PD-%04d", 2100 + sn),
                    durationPlayed = duration,
                    remarks = "Padel Court Free Resident Booking"
                )
            )
            padelCount++
        }

        return list
    }

    fun buildInitialRemindersSeed(keys: List<KeyRegisterEntity>): List<OpsReminderEntity> {
        val tz = TimeZone.getTimeZone("Asia/Qatar")
        val daily0740 = Calendar.getInstance(tz).apply {
            set(Calendar.HOUR_OF_DAY, 7)
            set(Calendar.MINUTE, 40)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }.timeInMillis

        val satAudit = Calendar.getInstance(tz).apply {
            set(2026, Calendar.NOVEMBER, 28, 9, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val nextDayKeyTime = Calendar.getInstance(tz).apply {
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val reminders = mutableListOf(
            OpsReminderEntity(
                id = "daily_0740",
                type = ReminderType.DAILY_0740,
                title = "07:40 AM Daily Sports & Log Check",
                message = "Log Nov 26 Padel & Football bookings, reconcile Key Register returns, and verify PM Visitor counts.",
                scheduledAtMillis = daily0740,
                isDone = false,
                checklistMask = 0
            ),
            OpsReminderEntity(
                id = "sat_audit_2026_11_28",
                type = ReminderType.SAT_AUDIT_0900,
                title = "Sat Nov 28, 09:00 AM — T-Code & Unreturned Key Audit",
                message = "Verify outstanding T-codes and unreturned key statuses (SI 1634 M-A04-16, SI 1638, 1639, 1643–1645).",
                scheduledAtMillis = satAudit,
                isDone = false
            )
        )

        keys.filter { it.isUnreturnedAlert }.forEach { key ->
            reminders.add(
                OpsReminderEntity(
                    id = "key_next_day_${key.si}",
                    type = ReminderType.KEY_NEXT_DAY,
                    title = "Verify Key Return — SI ${key.si} (${key.flatUnitLocation})",
                    message = "${key.status} • Issued to ${key.issuedTo} on ${key.dateOut} (${key.timeOut})",
                    relatedKeySi = key.si,
                    scheduledAtMillis = nextDayKeyTime,
                    isDone = false
                )
            )
        }
        return reminders
    }
}

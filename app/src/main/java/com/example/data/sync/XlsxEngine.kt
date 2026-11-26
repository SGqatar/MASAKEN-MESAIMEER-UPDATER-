package com.example.data.sync

import android.util.Xml
import com.example.data.db.KeyRegisterEntity
import com.example.data.db.MaintenanceRequestEntity
import com.example.data.db.SportsBookingEntity
import com.example.data.db.VisitorDayEntity
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportedWorkbookBundle(
    val keys: List<KeyRegisterEntity> = emptyList(),
    val visitors: List<VisitorDayEntity> = emptyList(),
    val maintenance: List<MaintenanceRequestEntity> = emptyList(),
    val sports: List<SportsBookingEntity> = emptyList(),
    val skippedTestRecords: Int = 0
)

/**
 * Pure-Kotlin OOXML (.xlsx) reader & writer for Masaken Mesaimeer Ops workbooks.
 * Preserves red cell fill styling strictly for unreturned/active issued keys on export.
 */
object XlsxEngine {

    private const val STYLE_DEFAULT = 0
    private const val STYLE_HEADER = 1
    private const val STYLE_RED_ALERT = 2
    private const val STYLE_RETURNED = 3

    fun writeAllWorkbooksXlsx(
        output: OutputStream,
        keys: List<KeyRegisterEntity>,
        visitors: List<VisitorDayEntity>,
        maintenance: List<MaintenanceRequestEntity>,
        sports: List<SportsBookingEntity>
    ) {
        val sheets = listOf(
            "Key Register" to buildKeyRows(keys),
            "PM Visitors 2026" to buildVisitorRows(visitors),
            "Maintenance Log" to buildMaintenanceRows(maintenance),
            "November 2026" to buildSportsRows(sports)
        )
        writeXlsxPackage(output, sheets)
    }

    private data class StyledCell(val value: String, val isNumber: Boolean = false, val styleId: Int = STYLE_DEFAULT)

    private fun buildKeyRows(keys: List<KeyRegisterEntity>): List<List<StyledCell>> {
        val headers = listOf(
            "SI", "Date Out", "Time Out", "Flat / Unit / Location", "Key / Card Type",
            "Purpose", "Issued To (Q-Out)", "QID / ID or Note", "Date In", "Time In",
            "Returned By (Q-In)", "Remarks", "Status"
        ).map { StyledCell(it, styleId = STYLE_HEADER) }

        val rows = keys.sortedBy { it.si }.map { k ->
            val rowStyle = if (k.isUnreturnedAlert) STYLE_RED_ALERT else STYLE_RETURNED
            listOf(
                StyledCell(k.si.toString(), isNumber = true, styleId = rowStyle),
                StyledCell(k.dateOut, styleId = rowStyle),
                StyledCell(k.timeOut, styleId = rowStyle),
                StyledCell(k.flatUnitLocation, styleId = rowStyle),
                StyledCell(k.keyCardType, styleId = rowStyle),
                StyledCell(k.purpose, styleId = rowStyle),
                StyledCell(k.issuedTo, styleId = rowStyle),
                StyledCell(k.qidOrNote, styleId = rowStyle),
                StyledCell(k.dateIn, styleId = rowStyle),
                StyledCell(k.timeIn, styleId = rowStyle),
                StyledCell(k.returnedBy, styleId = rowStyle),
                StyledCell(k.remarks, styleId = rowStyle),
                StyledCell(k.status, styleId = rowStyle)
            )
        }
        return listOf(headers) + rows
    }

    private fun buildVisitorRows(visitors: List<VisitorDayEntity>): List<List<StyledCell>> {
        val headers = listOf("Date", "Number of Visitors", "Apartment Viewing", "Flat Details", "Notes")
            .map { StyledCell(it, styleId = STYLE_HEADER) }
        val rows = visitors.sortedBy { it.dateIso }.map { v ->
            listOf(
                StyledCell(v.displayDate),
                StyledCell(v.visitorCount.toString(), isNumber = true),
                StyledCell(v.viewingCount.toString(), isNumber = true),
                StyledCell(v.flatDetails),
                StyledCell(v.notes)
            )
        }
        val totalVisitors = visitors.sumOf { it.visitorCount }
        val totalViewings = visitors.sumOf { it.viewingCount }
        val summaryRow = listOf(
            StyledCell("SUM (All Months)", styleId = STYLE_HEADER),
            StyledCell(totalVisitors.toString(), isNumber = true, styleId = STYLE_HEADER),
            StyledCell(totalViewings.toString(), isNumber = true, styleId = STYLE_HEADER),
            StyledCell("", styleId = STYLE_HEADER),
            StyledCell("", styleId = STYLE_HEADER)
        )
        return listOf(headers) + rows + listOf(summaryRow)
    }

    private fun buildMaintenanceRows(items: List<MaintenanceRequestEntity>): List<List<StyledCell>> {
        val headers = listOf(
            "SN", "Date", "Names", "Phone Number", "Work Order Number",
            "Q.I.D Number", "Flat Details", "Maintenance Request Details"
        ).map { StyledCell(it, styleId = STYLE_HEADER) }

        val rows = items.sortedBy { it.sn }.map { m ->
            listOf(
                StyledCell(m.sn.toString(), isNumber = true),
                StyledCell(m.date),
                StyledCell(m.names),
                StyledCell(m.phoneNumber),
                StyledCell(m.workOrderNumber),
                StyledCell(m.qidNumber),
                StyledCell(m.flatDetails),
                StyledCell(m.requestDetails)
            )
        }
        return listOf(headers) + rows
    }

    private fun buildSportsRows(bookings: List<SportsBookingEntity>): List<List<StyledCell>> {
        val headers = listOf(
            "SN", "SPORT", "DATE", "TIME", "NAME", "UNIT DETAILS",
            "DISCOUNT", "REFERENCE NO", "DURATION PLAYED", "REMARKS"
        ).map { StyledCell(it, styleId = STYLE_HEADER) }

        val rows = bookings.sortedBy { it.sn }.map { b ->
            listOf(
                StyledCell(b.sn.toString(), isNumber = true),
                StyledCell(b.sport),
                StyledCell(b.date),
                StyledCell(b.time),
                StyledCell(b.name),
                StyledCell(b.unitDetails),
                StyledCell(b.discount),
                StyledCell(b.referenceNo),
                StyledCell(b.durationPlayed.toString(), isNumber = true),
                StyledCell(b.remarks)
            )
        }
        return listOf(headers) + rows
    }

    private fun writeXlsxPackage(output: OutputStream, sheets: List<Pair<String, List<List<StyledCell>>>>) {
        ZipOutputStream(output).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", buildContentTypesXml(sheets.size))
            writeZipEntry(zip, "_rels/.rels", rootRelsXml())
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sheets.size))
            writeZipEntry(zip, "xl/workbook.xml", buildWorkbookXml(sheets.map { it.first }))
            writeZipEntry(zip, "xl/styles.xml", buildStylesXml())
            sheets.forEachIndexed { idx, (_, rows) ->
                writeZipEntry(zip, "xl/worksheets/sheet${idx + 1}.xml", buildWorksheetXml(rows))
            }
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun buildContentTypesXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        append("""</Types>""")
    }

    private fun rootRelsXml(): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            """</Relationships>"""

    private fun buildWorkbookRelsXml(sheetCount: Int): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        for (i in 1..sheetCount) {
            append("""<Relationship Id="rId$i" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet$i.xml"/>""")
        }
        append("""<Relationship Id="rId${sheetCount + 1}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>""")
        append("""</Relationships>""")
    }

    private fun buildWorkbookXml(sheetNames: List<String>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        append("""<sheets>""")
        sheetNames.forEachIndexed { idx, name ->
            append("""<sheet name="${escapeXml(name)}" sheetId="${idx + 1}" r:id="rId${idx + 1}"/>""")
        }
        append("""</sheets></workbook>""")
    }

    private fun buildStylesXml(): String = """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<styleSheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
  <fonts count="3">
    <font><sz val="11"/><color rgb="FF111827"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF991B1B"/><name val="Calibri"/></font>
  </fonts>
  <fills count="5">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0F172A"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFCDD2"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFECFDF5"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="4">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="0" fillId="4" borderId="0" xfId="0" applyFill="1"/>
  </cellXfs>
</styleSheet>"""

    private fun buildWorksheetXml(rows: List<List<StyledCell>>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main"><sheetData>""")
        rows.forEachIndexed { rowIdx, cells ->
            val r = rowIdx + 1
            append("""<row r="$r">""")
            cells.forEachIndexed { colIdx, cell ->
                val ref = "${colLetter(colIdx)}$r"
                val numVal = cell.value.toDoubleOrNull()
                if (cell.isNumber && numVal != null) {
                    append("""<c r="$ref" s="${cell.styleId}"><v>${cell.value}</v></c>""")
                } else {
                    append("""<c r="$ref" s="${cell.styleId}" t="inlineStr"><is><t>${escapeXml(cell.value)}</t></is></c>""")
                }
            }
            append("""</row>""")
        }
        append("""</sheetData></worksheet>""")
    }

    private fun colLetter(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n >= 0) {
            sb.insert(0, ('A' + (n % 26)))
            n = n / 26 - 1
        }
        return sb.toString()
    }

    private fun escapeXml(s: String): String = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
        .replace("'", "&apos;")

    fun readXlsx(input: InputStream): ImportedWorkbookBundle {
        val entries = mutableMapOf<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    entries[entry.name] = zip.readBytes()
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }

        val sharedStrings = entries["xl/sharedStrings.xml"]?.let(::parseSharedStrings) ?: emptyList()
        val sheetPaths = entries.keys.filter { it.startsWith("xl/worksheets/sheet") && it.endsWith(".xml") }.sorted()

        val importedKeys = mutableListOf<KeyRegisterEntity>()
        val importedVisitors = mutableListOf<VisitorDayEntity>()
        val importedMaintenance = mutableListOf<MaintenanceRequestEntity>()
        val importedSports = mutableListOf<SportsBookingEntity>()
        var skippedTest = 0

        sheetPaths.forEach { path ->
            val bytes = entries[path] ?: return@forEach
            val rows = parseWorksheet(bytes, sharedStrings)
            if (rows.isEmpty()) return@forEach

            val headerIdx = rows.indexOfFirst { row ->
                val joined = row.joinToString("|").lowercase(Locale.ROOT)
                (joined.contains("si") && joined.contains("date out")) ||
                    (joined.contains("visitor") && joined.contains("viewing")) ||
                    (joined.contains("work order") && joined.contains("maintenance")) ||
                    (joined.contains("sport") && joined.contains("duration"))
            }.takeIf { it >= 0 } ?: 0

            val headers = rows[headerIdx].map { it.trim().lowercase(Locale.ROOT) }
            val dataRows = rows.drop(headerIdx + 1)

            when {
                headers.any { it.contains("date out") } || headers.any { it.contains("key / card") || it.contains("key/card") } -> {
                    dataRows.forEach { r ->
                        val si = colValue(r, headers, "si").toDoubleOrNull()?.toInt() ?: return@forEach
                        val entry = KeyRegisterEntity(
                            si = si,
                            dateOut = normalizeExcelDate(colValue(r, headers, "date out")),
                            timeOut = colValue(r, headers, "time out"),
                            flatUnitLocation = colValue(r, headers, "flat", "unit", "location"),
                            keyCardType = colValue(r, headers, "key / card", "key/card", "card type"),
                            purpose = colValue(r, headers, "purpose"),
                            issuedTo = colValue(r, headers, "issued to"),
                            qidOrNote = colValue(r, headers, "qid", "id or note"),
                            dateIn = normalizeExcelDate(colValue(r, headers, "date in")),
                            timeIn = colValue(r, headers, "time in"),
                            returnedBy = colValue(r, headers, "returned by"),
                            remarks = colValue(r, headers, "remarks"),
                            status = colValue(r, headers, "status").ifBlank { "Key issued" }
                        )
                        if (TestRecordFilter.isDeletedTestKeyRecord(entry)) {
                            skippedTest++
                        } else {
                            importedKeys.add(entry)
                        }
                    }
                }
                headers.any { it.contains("work order") } -> {
                    dataRows.forEachIndexed { idx, r ->
                        val details = colValue(r, headers, "maintenance request", "details")
                        val names = colValue(r, headers, "names", "name")
                        if (names.isBlank() && details.isBlank()) return@forEachIndexed
                        val sn = colValue(r, headers, "sn").toDoubleOrNull()?.toInt() ?: (idx + 1)
                        importedMaintenance.add(
                            MaintenanceRequestEntity(
                                sn = sn,
                                date = normalizeExcelDate(colValue(r, headers, "date")),
                                names = names,
                                phoneNumber = colValue(r, headers, "phone"),
                                workOrderNumber = colValue(r, headers, "work order"),
                                qidNumber = colValue(r, headers, "q.i.d", "qid"),
                                flatDetails = colValue(r, headers, "flat"),
                                requestDetails = details
                            )
                        )
                    }
                }
                headers.any { it.contains("visitor") } -> {
                    dataRows.forEach { r ->
                        val dateStr = colValue(r, headers, "date")
                        if (dateStr.isBlank() || dateStr.startsWith("SUM", ignoreCase = true) || dateStr.startsWith("TOTAL", ignoreCase = true)) {
                            return@forEach
                        }
                        val normalized = normalizeVisitorDate(dateStr) ?: return@forEach
                        importedVisitors.add(
                            VisitorDayEntity(
                                dateIso = normalized.first,
                                displayDate = normalized.second,
                                monthKey = normalized.first.take(7),
                                visitorCount = colValue(r, headers, "visitor").toDoubleOrNull()?.toInt() ?: 0,
                                viewingCount = colValue(r, headers, "viewing").toDoubleOrNull()?.toInt() ?: 0,
                                flatDetails = colValue(r, headers, "flat").ifBlank { "-" },
                                notes = colValue(r, headers, "note")
                            )
                        )
                    }
                }
                headers.any { it.contains("sport") } -> {
                    dataRows.forEachIndexed { idx, r ->
                        val sport = colValue(r, headers, "sport").uppercase(Locale.ROOT)
                        if (sport != "FOOTBALL" && sport != "PADEL") return@forEachIndexed
                        val sn = colValue(r, headers, "sn").toDoubleOrNull()?.toInt() ?: (idx + 1)
                        importedSports.add(
                            SportsBookingEntity(
                                sn = sn,
                                sport = sport,
                                date = normalizeExcelDate(colValue(r, headers, "date")),
                                time = colValue(r, headers, "time"),
                                name = colValue(r, headers, "name"),
                                unitDetails = colValue(r, headers, "unit"),
                                discount = colValue(r, headers, "discount").ifBlank { "FREE" },
                                referenceNo = colValue(r, headers, "reference"),
                                durationPlayed = colValue(r, headers, "duration").toDoubleOrNull() ?: 1.0,
                                remarks = colValue(r, headers, "remarks")
                            )
                        )
                    }
                }
            }
        }

        return ImportedWorkbookBundle(
            keys = importedKeys,
            visitors = importedVisitors,
            maintenance = importedMaintenance,
            sports = importedSports,
            skippedTestRecords = skippedTest
        )
    }

    private fun colValue(row: List<String>, headers: List<String>, vararg hints: String): String {
        val idx = headers.indexOfFirst { h -> hints.any { hint -> h.contains(hint) } }
        return if (idx in row.indices) row[idx].trim() else ""
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val list = mutableListOf<String>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")
        var insideSi = false
        val current = StringBuilder()
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    if (parser.name == "si") {
                        insideSi = true
                        current.clear()
                    } else if (insideSi && parser.name == "t") {
                        current.append(parser.nextText())
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (parser.name == "si") {
                        list.add(current.toString())
                        insideSi = false
                    }
                }
            }
            event = parser.next()
        }
        return list
    }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val parser = Xml.newPullParser()
        parser.setInput(ByteArrayInputStream(bytes), "UTF-8")

        var currentRow = mutableListOf<String>()
        var currentColIdx = 0
        var cellType = ""
        var inCell = false
        var cellValue = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "row" -> currentRow = mutableListOf()
                        "c" -> {
                            inCell = true
                            cellValue = ""
                            cellType = parser.getAttributeValue(null, "t") ?: ""
                            val ref = parser.getAttributeValue(null, "r") ?: ""
                            currentColIdx = refToColIndex(ref, currentRow.size)
                            while (currentRow.size < currentColIdx) {
                                currentRow.add("")
                            }
                        }
                        "v" -> if (inCell) {
                            val raw = parser.nextText()
                            cellValue = if (cellType == "s") {
                                raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                            } else {
                                raw
                            }
                        }
                        "t" -> if (inCell && cellType == "inlineStr") {
                            cellValue = parser.nextText()
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    when (parser.name) {
                        "c" -> {
                            if (currentColIdx < currentRow.size) {
                                currentRow[currentColIdx] = cellValue
                            } else {
                                currentRow.add(cellValue)
                            }
                            inCell = false
                        }
                        "row" -> if (currentRow.any { it.isNotBlank() }) rows.add(currentRow)
                    }
                }
            }
            event = parser.next()
        }
        return rows
    }

    private fun refToColIndex(ref: String, fallback: Int): Int {
        val letters = ref.takeWhile { it.isLetter() }
        if (letters.isEmpty()) return fallback
        var col = 0
        for (ch in letters.uppercase(Locale.ROOT)) {
            col = col * 26 + (ch - 'A' + 1)
        }
        return (col - 1).coerceAtLeast(0)
    }

    private fun normalizeExcelDate(raw: String): String {
        val trimmed = raw.trim()
        val serial = trimmed.toDoubleOrNull()
        if (serial != null && serial in 30000.0..60000.0) {
            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                set(1899, Calendar.DECEMBER, 30, 0, 0, 0)
                add(Calendar.DAY_OF_YEAR, serial.toInt())
            }
            return SimpleDateFormat("dd/MM/yyyy", Locale.US).format(cal.time)
        }
        return trimmed
    }

    private fun normalizeVisitorDate(raw: String): Pair<String, String>? {
        val clean = normalizeExcelDate(raw).trim()
        val formats = listOf("dd-MMM-yyyy", "dd/MM/yyyy", "yyyy-MM-dd", "MM/dd/yyyy")
        for (fmt in formats) {
            try {
                val parser = SimpleDateFormat(fmt, Locale.US).apply { isLenient = false }
                val date = parser.parse(clean) ?: continue
                val iso = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(date)
                val display = SimpleDateFormat("dd-MMM-yyyy", Locale.US).format(date)
                return iso to display
            } catch (_: Exception) {
            }
        }
        return null
    }
}

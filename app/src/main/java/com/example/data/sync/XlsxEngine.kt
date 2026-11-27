package com.example.data.sync

import com.example.data.db.KeyRegisterEntity
import com.example.data.db.MaintenanceRequestEntity
import com.example.data.db.SportsBookingEntity
import com.example.data.db.VisitorDayEntity
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import javax.xml.parsers.SAXParserFactory
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

data class InspectionExportItem(
    val id: String,
    val area: String,
    val finding: String,
    val severity: String,
    val actionRequired: String,
    val jpegBytes: ByteArray? = null
)

/**
 * Pure-Kotlin OOXML (.xlsx) reader & writer for Masaken Mesaimeer Ops workbooks.
 * - Preserves red cell fill styling strictly for genuinely unreturned non-move-in keys.
 * - Uses amber highlight for Move-In keys awaiting Mr. Shajith T-code follow-up.
 * - Always populates "Issued By" as "Mr. Rodel Cabrales" on exported Key Register workbooks.
 * - Embeds JPEG inspection photos directly inside Excel rows via DrawingML twoCellAnchor.
 */
object XlsxEngine {

    const val EXPORT_ISSUED_BY_DEFAULT = "Mr. Rodel Cabrales"

    private const val STYLE_DEFAULT = 0
    private const val STYLE_HEADER = 1
    private const val STYLE_RED_ALERT = 2
    private const val STYLE_RETURNED = 3
    private const val STYLE_MOVE_IN_AMBER = 4

    private data class StyledCell(
        val value: String,
        val isNumber: Boolean = false,
        val styleId: Int = STYLE_DEFAULT
    )

    private data class RowData(
        val cells: List<StyledCell>,
        val heightPt: Double? = null
    )

    private data class ColumnWidthSpec(
        val minCol: Int,
        val maxCol: Int,
        val width: Double
    )

    private data class DrawingImagePlacement(
        val rowZeroBased: Int,
        val colZeroBased: Int,
        val relId: String,
        val zipMediaPath: String,
        val relTargetPath: String,
        val name: String,
        val jpegBytes: ByteArray
    )

    private data class SheetSpec(
        val name: String,
        val rows: List<RowData>,
        val colWidths: List<ColumnWidthSpec> = emptyList(),
        val images: List<DrawingImagePlacement> = emptyList()
    )

    fun writeAllWorkbooksXlsx(
        output: OutputStream,
        keys: List<KeyRegisterEntity>,
        visitors: List<VisitorDayEntity>,
        maintenance: List<MaintenanceRequestEntity>,
        sports: List<SportsBookingEntity>
    ) {
        val sheets = listOf(
            SheetSpec(
                name = "Key Register",
                rows = buildKeyRows(keys).map { RowData(it) },
                colWidths = keyColumnWidths()
            ),
            SheetSpec(
                name = "PM Visitors 2026",
                rows = buildVisitorRows(visitors).map { RowData(it) },
                colWidths = visitorColumnWidths()
            ),
            SheetSpec(
                name = "Maintenance Log",
                rows = buildMaintenanceRows(maintenance).map { RowData(it) }
            ),
            SheetSpec(
                name = "November 2026",
                rows = buildSportsRows(sports).map { RowData(it) },
                colWidths = sportsColumnWidths()
            )
        )
        writeXlsxPackage(output, sheets)
    }

    fun writeKeyRegisterXlsx(
        output: OutputStream,
        keys: List<KeyRegisterEntity>,
        sheetTitle: String = "Key Register"
    ) {
        writeXlsxPackage(
            output,
            listOf(
                SheetSpec(
                    name = sanitizeSheetName(sheetTitle),
                    rows = buildKeyRows(keys).map { RowData(it) },
                    colWidths = keyColumnWidths()
                )
            )
        )
    }

    fun writeVisitorsXlsx(
        output: OutputStream,
        visitors: List<VisitorDayEntity>,
        sheetTitle: String = "PM Visitors 2026"
    ) {
        writeXlsxPackage(
            output,
            listOf(
                SheetSpec(
                    name = sanitizeSheetName(sheetTitle),
                    rows = buildVisitorRows(visitors).map { RowData(it) },
                    colWidths = visitorColumnWidths()
                )
            )
        )
    }

    fun writeSportsXlsx(
        output: OutputStream,
        sports: List<SportsBookingEntity>,
        sheetTitle: String = "November 2026"
    ) {
        writeXlsxPackage(
            output,
            listOf(
                SheetSpec(
                    name = sanitizeSheetName(sheetTitle),
                    rows = buildSportsRows(sports).map { RowData(it) },
                    colWidths = sportsColumnWidths()
                )
            )
        )
    }

    fun writeVisitorsAndMaintenanceXlsx(
        output: OutputStream,
        visitors: List<VisitorDayEntity>,
        maintenance: List<MaintenanceRequestEntity>
    ) {
        writeXlsxPackage(
            output,
            listOf(
                SheetSpec(
                    name = "PM Visitors 2026",
                    rows = buildVisitorRows(visitors).map { RowData(it) },
                    colWidths = visitorColumnWidths()
                ),
                SheetSpec(
                    name = "Maintenance Log",
                    rows = buildMaintenanceRows(maintenance).map { RowData(it) },
                    colWidths = listOf(
                        ColumnWidthSpec(1, 1, 8.0),
                        ColumnWidthSpec(2, 2, 14.0),
                        ColumnWidthSpec(3, 4, 20.0),
                        ColumnWidthSpec(5, 7, 18.0),
                        ColumnWidthSpec(8, 8, 38.0)
                    )
                )
            )
        )
    }

    /**
     * Generates a structured Building Inspection .xlsx workbook grouped by inspection Area,
     * embedding each photo directly inside Column F of its corresponding Excel row via DrawingML.
     */
    fun writeInspectionReportXlsx(
        output: OutputStream,
        buildingName: String,
        inspectionDate: String,
        inspectorName: String,
        items: List<InspectionExportItem>
    ) {
        val grouped = items.groupBy { it.area.trim().ifBlank { "General Area" } }
        val rows = mutableListOf<RowData>()
        val placements = mutableListOf<DrawingImagePlacement>()

        rows.add(
            RowData(
                cells = listOf(
                    StyledCell("Masaken Mesaimeer Inspection", styleId = STYLE_HEADER),
                    StyledCell(buildingName.ifBlank { "Property Building" }, styleId = STYLE_HEADER),
                    StyledCell("Date: $inspectionDate", styleId = STYLE_HEADER),
                    StyledCell("Inspector: $inspectorName", styleId = STYLE_HEADER),
                    StyledCell("Items: ${items.size}", styleId = STYLE_HEADER),
                    StyledCell("Photos Embedded in Column F", styleId = STYLE_HEADER)
                ),
                heightPt = 24.0
            )
        )
        rows.add(
            RowData(
                cells = listOf(
                    "Item #",
                    "Area / Zone",
                    "Inspection Finding",
                    "Severity",
                    "Action Required",
                    "Photo"
                ).map { StyledCell(it, styleId = STYLE_HEADER) },
                heightPt = 22.0
            )
        )

        var itemNumber = 1
        var mediaIndex = 1

        grouped.forEach { (areaName, areaItems) ->
            areaItems.forEach { item ->
                val rowIdxOneBased = rows.size + 1
                val severityLower = item.severity.trim().lowercase(Locale.ROOT)
                val sevStyle = when {
                    severityLower.contains("urgent") || severityLower.contains("critical") || severityLower.contains("fail") -> STYLE_RED_ALERT
                    severityLower.contains("minor") || severityLower.contains("warn") || severityLower.contains("follow") -> STYLE_MOVE_IN_AMBER
                    else -> STYLE_RETURNED
                }
                val bytes = item.jpegBytes?.takeIf { it.isNotEmpty() }
                val hasPhoto = bytes != null
                if (bytes != null) {
                    placements.add(
                        DrawingImagePlacement(
                            rowZeroBased = rowIdxOneBased - 1,
                            colZeroBased = 5, // Column F (0-indexed 5)
                            relId = "rId$mediaIndex",
                            zipMediaPath = "xl/media/image$mediaIndex.jpg",
                            relTargetPath = "../media/image$mediaIndex.jpg",
                            name = "Inspection_${itemNumber}_${areaName.replace(" ", "_")}",
                            jpegBytes = bytes
                        )
                    )
                    mediaIndex++
                }
                rows.add(
                    RowData(
                        cells = listOf(
                            StyledCell(itemNumber.toString(), isNumber = true),
                            StyledCell(areaName),
                            StyledCell(item.finding.ifBlank { "-" }),
                            StyledCell(item.severity.ifBlank { "Pass" }, styleId = sevStyle),
                            StyledCell(item.actionRequired.ifBlank { "None" }),
                            StyledCell(if (hasPhoto) "" else "No photo attached")
                        ),
                        heightPt = if (hasPhoto) 108.0 else 22.0
                    )
                )
                itemNumber++
            }
        }

        val colWidths = listOf(
            ColumnWidthSpec(1, 1, 9.0),
            ColumnWidthSpec(2, 2, 22.0),
            ColumnWidthSpec(3, 3, 38.0),
            ColumnWidthSpec(4, 4, 14.0),
            ColumnWidthSpec(5, 5, 34.0),
            ColumnWidthSpec(6, 6, 30.0)
        )

        writeXlsxPackage(
            output,
            listOf(
                SheetSpec(
                    name = "Inspection Report",
                    rows = rows,
                    colWidths = colWidths,
                    images = placements
                )
            )
        )
    }

    private fun buildKeyRows(keys: List<KeyRegisterEntity>): List<List<StyledCell>> {
        val headers = listOf(
            "SI", "Date Out", "Time Out", "Flat / Unit / Location", "Key / Card Type",
            "Purpose", "Issued By", "Issued To (Q-Out)", "QID / ID or Note",
            "Date In", "Time In", "Returned By (Q-In)", "Remarks", "Status"
        ).map { StyledCell(it, styleId = STYLE_HEADER) }

        val rows = keys.sortedBy { it.si }.map { k ->
            val rowStyle = when {
                k.isUnreturnedAlert -> STYLE_RED_ALERT
                k.isMoveInPendingTCode -> STYLE_MOVE_IN_AMBER
                else -> STYLE_RETURNED
            }
            listOf(
                StyledCell(k.si.toString(), isNumber = true, styleId = rowStyle),
                StyledCell(k.dateOut, styleId = rowStyle),
                StyledCell(k.timeOut, styleId = rowStyle),
                StyledCell(k.flatUnitLocation, styleId = rowStyle),
                StyledCell(k.keyCardType, styleId = rowStyle),
                StyledCell(k.purpose, styleId = rowStyle),
                // Operational export requirement: Excel workbook Issued By column normalizes to Mr. Rodel Cabrales
                StyledCell(EXPORT_ISSUED_BY_DEFAULT, styleId = rowStyle),
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

    private fun keyColumnWidths(): List<ColumnWidthSpec> = listOf(
        ColumnWidthSpec(1, 1, 8.0),
        ColumnWidthSpec(2, 3, 13.0),
        ColumnWidthSpec(4, 4, 18.0),
        ColumnWidthSpec(5, 6, 20.0),
        ColumnWidthSpec(7, 8, 22.0),
        ColumnWidthSpec(9, 13, 16.0),
        ColumnWidthSpec(14, 14, 24.0)
    )

    private fun visitorColumnWidths(): List<ColumnWidthSpec> = listOf(
        ColumnWidthSpec(1, 1, 15.0),
        ColumnWidthSpec(2, 3, 18.0),
        ColumnWidthSpec(4, 4, 28.0),
        ColumnWidthSpec(5, 5, 24.0)
    )

    private fun sportsColumnWidths(): List<ColumnWidthSpec> = listOf(
        ColumnWidthSpec(1, 1, 7.0),
        ColumnWidthSpec(2, 4, 14.0),
        ColumnWidthSpec(5, 5, 22.0),
        ColumnWidthSpec(6, 10, 16.0)
    )

    private fun sanitizeSheetName(name: String): String {
        val cleaned = name.replace(Regex("[\\\\/*?:\\[\\]]"), "-").trim()
        return cleaned.take(31).ifBlank { "Sheet1" }
    }

    private fun writeXlsxPackage(output: OutputStream, sheets: List<SheetSpec>) {
        val drawingSheetIndices = mutableListOf<Int>()
        sheets.forEachIndexed { idx, spec ->
            if (spec.images.isNotEmpty()) {
                drawingSheetIndices.add(idx + 1)
            }
        }

        ZipOutputStream(output).use { zip ->
            writeZipEntry(zip, "[Content_Types].xml", buildContentTypesXml(sheets.size, drawingSheetIndices))
            writeZipEntry(zip, "_rels/.rels", rootRelsXml())
            writeZipEntry(zip, "xl/_rels/workbook.xml.rels", buildWorkbookRelsXml(sheets.size))
            writeZipEntry(zip, "xl/workbook.xml", buildWorkbookXml(sheets.map { it.name }))
            writeZipEntry(zip, "xl/styles.xml", buildStylesXml())

            sheets.forEachIndexed { idx, spec ->
                val sheetNum = idx + 1
                val hasDrawings = spec.images.isNotEmpty()
                writeZipEntry(
                    zip,
                    "xl/worksheets/sheet$sheetNum.xml",
                    buildWorksheetXml(spec.rows, spec.colWidths, hasDrawings)
                )
                if (hasDrawings) {
                    writeZipEntry(
                        zip,
                        "xl/worksheets/_rels/sheet$sheetNum.xml.rels",
                        buildSheetDrawingRelsXml(sheetNum)
                    )
                    writeZipEntry(
                        zip,
                        "xl/drawings/drawing$sheetNum.xml",
                        buildDrawingXml(spec.images)
                    )
                    writeZipEntry(
                        zip,
                        "xl/drawings/_rels/drawing$sheetNum.xml.rels",
                        buildDrawingRelsXml(spec.images)
                    )
                    spec.images.forEach { placement ->
                        writeZipBinaryEntry(zip, placement.zipMediaPath, placement.jpegBytes)
                    }
                }
            }
        }
    }

    private fun writeZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun writeZipBinaryEntry(zip: ZipOutputStream, name: String, bytes: ByteArray) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(bytes)
        zip.closeEntry()
    }

    private fun buildContentTypesXml(sheetCount: Int, drawingSheetIndices: List<Int>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""")
        append("""<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""")
        append("""<Default Extension="xml" ContentType="application/xml"/>""")
        if (drawingSheetIndices.isNotEmpty()) {
            append("""<Default Extension="jpg" ContentType="image/jpeg"/>""")
        }
        append("""<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""")
        append("""<Override PartName="/xl/styles.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.styles+xml"/>""")
        for (i in 1..sheetCount) {
            append("""<Override PartName="/xl/worksheets/sheet$i.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""")
        }
        for (d in drawingSheetIndices) {
            append("""<Override PartName="/xl/drawings/drawing$d.xml" ContentType="application/vnd.openxmlformats-officedocument.drawing+xml"/>""")
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
  <fonts count="4">
    <font><sz val="11"/><color rgb="FF111827"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FFFFFFFF"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF991B1B"/><name val="Calibri"/></font>
    <font><b/><sz val="11"/><color rgb="FF92400E"/><name val="Calibri"/></font>
  </fonts>
  <fills count="6">
    <fill><patternFill patternType="none"/></fill>
    <fill><patternFill patternType="gray125"/></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FF0F172A"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFFCDD2"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFECFDF5"/></patternFill></fill>
    <fill><patternFill patternType="solid"><fgColor rgb="FFFEF3C7"/></patternFill></fill>
  </fills>
  <borders count="1">
    <border><left/><right/><top/><bottom/><diagonal/></border>
  </borders>
  <cellStyleXfs count="1">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0"/>
  </cellStyleXfs>
  <cellXfs count="5">
    <xf numFmtId="0" fontId="0" fillId="0" borderId="0" xfId="0"/>
    <xf numFmtId="0" fontId="1" fillId="2" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="2" fillId="3" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
    <xf numFmtId="0" fontId="0" fillId="4" borderId="0" xfId="0" applyFill="1"/>
    <xf numFmtId="0" fontId="3" fillId="5" borderId="0" xfId="0" applyFont="1" applyFill="1"/>
  </cellXfs>
</styleSheet>"""

    private fun buildWorksheetXml(
        rows: List<RowData>,
        colWidths: List<ColumnWidthSpec>,
        hasDrawings: Boolean
    ): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        if (hasDrawings) {
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        } else {
            append("""<worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">""")
        }
        if (colWidths.isNotEmpty()) {
            append("""<cols>""")
            colWidths.forEach { w ->
                append("""<col min="${w.minCol}" max="${w.maxCol}" width="${w.width}" customWidth="1"/>""")
            }
            append("""</cols>""")
        }
        append("""<sheetData>""")
        rows.forEachIndexed { rowIdx, rowData ->
            val r = rowIdx + 1
            if (rowData.heightPt != null) {
                append("""<row r="$r" ht="${rowData.heightPt}" customHeight="1">""")
            } else {
                append("""<row r="$r">""")
            }
            rowData.cells.forEachIndexed { colIdx, cell ->
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
        append("""</sheetData>""")
        if (hasDrawings) {
            append("""<drawing r:id="rId1"/>""")
        }
        append("""</worksheet>""")
    }

    private fun buildSheetDrawingRelsXml(sheetNum: Int): String =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/drawing" Target="../drawings/drawing$sheetNum.xml"/>""" +
            """</Relationships>"""

    private fun buildDrawingRelsXml(images: List<DrawingImagePlacement>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""")
        images.forEach { p ->
            append("""<Relationship Id="${p.relId}" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/image" Target="${p.relTargetPath}"/>""")
        }
        append("""</Relationships>""")
    }

    private fun buildDrawingXml(images: List<DrawingImagePlacement>): String = buildString {
        append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""")
        append("""<xdr:wsDr xmlns:xdr="http://schemas.openxmlformats.org/drawingml/2006/spreadsheetDrawing" xmlns:a="http://schemas.openxmlformats.org/drawingml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""")
        images.forEachIndexed { idx, p ->
            val picId = idx + 1
            // Two-cell anchor keeping photo cleanly padded inside target Excel row & column
            append("""<xdr:twoCellAnchor editAs="oneCell">""")
            append("""<xdr:from><xdr:col>${p.colZeroBased}</xdr:col><xdr:colOff>38100</xdr:colOff><xdr:row>${p.rowZeroBased}</xdr:row><xdr:rowOff>38100</xdr:rowOff></xdr:from>""")
            append("""<xdr:to><xdr:col>${p.colZeroBased + 1}</xdr:col><xdr:colOff>-38100</xdr:colOff><xdr:row>${p.rowZeroBased + 1}</xdr:row><xdr:rowOff>-38100</xdr:rowOff></xdr:to>""")
            append("""<xdr:pic>""")
            append("""<xdr:nvPicPr><xdr:cNvPr id="$picId" name="${escapeXml(p.name)}"/><xdr:cNvPicPr><a:picLocks noChangeAspect="1"/></xdr:cNvPicPr></xdr:nvPicPr>""")
            append("""<xdr:blipFill><a:blip r:embed="${p.relId}"/><a:stretch><a:fillRect/></a:stretch></xdr:blipFill>""")
            append("""<xdr:spPr><a:prstGeom prst="rect"><a:avLst/></a:prstGeom></xdr:spPr>""")
            append("""</xdr:pic>""")
            append("""<xdr:clientData/>""")
            append("""</xdr:twoCellAnchor>""")
        }
        append("""</xdr:wsDr>""")
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
                            status = colValue(r, headers, "status").ifBlank { "Key issued" },
                            issuedBy = colValue(r, headers, "issued by")
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
                                durationPlayed = parseDurationPlayed(colValue(r, headers, "duration")),
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
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newSAXParser()
        parser.parse(ByteArrayInputStream(bytes), object : DefaultHandler() {
            private var insideSi = false
            private var insideT = false
            private val current = StringBuilder()

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                val tag = localName?.ifBlank { null } ?: qName.orEmpty()
                when (tag) {
                    "si" -> {
                        insideSi = true
                        current.clear()
                    }
                    "t" -> if (insideSi) insideT = true
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (insideSi && insideT) {
                    current.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val tag = localName?.ifBlank { null } ?: qName.orEmpty()
                when (tag) {
                    "t" -> insideT = false
                    "si" -> {
                        list.add(current.toString())
                        insideSi = false
                    }
                }
            }
        })
        return list
    }

    private fun parseWorksheet(bytes: ByteArray, sharedStrings: List<String>): List<List<String>> {
        val rows = mutableListOf<List<String>>()
        val factory = SAXParserFactory.newInstance().apply { isNamespaceAware = true }
        val parser = factory.newSAXParser()
        parser.parse(ByteArrayInputStream(bytes), object : DefaultHandler() {
            private var currentRow = mutableListOf<String>()
            private var currentColIdx = 0
            private var cellType = ""
            private var inCell = false
            private var captureText = false
            private val textBuf = StringBuilder()
            private var cellValue = ""

            override fun startElement(uri: String?, localName: String?, qName: String?, attributes: Attributes?) {
                val tag = localName?.ifBlank { null } ?: qName.orEmpty()
                when (tag) {
                    "row" -> currentRow = mutableListOf()
                    "c" -> {
                        inCell = true
                        cellValue = ""
                        cellType = attributes?.getValue("t").orEmpty()
                        val ref = attributes?.getValue("r").orEmpty()
                        currentColIdx = refToColIndex(ref, currentRow.size)
                        while (currentRow.size < currentColIdx) {
                            currentRow.add("")
                        }
                    }
                    "v" -> if (inCell) {
                        captureText = true
                        textBuf.clear()
                    }
                    "t" -> if (inCell && cellType == "inlineStr") {
                        captureText = true
                        textBuf.clear()
                    }
                }
            }

            override fun characters(ch: CharArray, start: Int, length: Int) {
                if (captureText) {
                    textBuf.append(ch, start, length)
                }
            }

            override fun endElement(uri: String?, localName: String?, qName: String?) {
                val tag = localName?.ifBlank { null } ?: qName.orEmpty()
                when (tag) {
                    "v" -> if (inCell && captureText) {
                        captureText = false
                        val raw = textBuf.toString()
                        cellValue = if (cellType == "s") {
                            raw.toIntOrNull()?.let { sharedStrings.getOrNull(it) } ?: ""
                        } else {
                            raw
                        }
                    }
                    "t" -> if (inCell && cellType == "inlineStr" && captureText) {
                        captureText = false
                        cellValue = textBuf.toString()
                    }
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
        })
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

    fun parseDurationPlayed(raw: String): Double {
        val clean = raw.trim().uppercase(Locale.ROOT)
        clean.toDoubleOrNull()?.let { return it }
        val hours = Regex("""(\d+(?:\.\d+)?)\s*HOURS?""").find(clean)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val mins = Regex("""(\d+(?:\.\d+)?)\s*MINUTES?""").find(clean)?.groupValues?.getOrNull(1)?.toDoubleOrNull() ?: 0.0
        val total = hours + (mins / 60.0)
        return if (total > 0.0) total else 1.0
    }
}

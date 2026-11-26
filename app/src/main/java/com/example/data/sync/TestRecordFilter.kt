package com.example.data.sync

import com.example.data.db.KeyRegisterEntity

/**
 * Operational sanitization rule:
 * Exclude known deleted test entries such as "charle to mansu" or "mushtaq for B10-10"
 * during initial seeding and external workbook imports.
 */
object TestRecordFilter {
    private val blockedSubstrings = listOf(
        "charle to mansu",
        "charles to mansu",
        "mushtaq for b10-10",
        "b10-10 test",
        "test entry"
    )

    fun isDeletedTestKeyRecord(entry: KeyRegisterEntity): Boolean {
        val haystack = listOf(
            entry.flatUnitLocation,
            entry.purpose,
            entry.issuedTo,
            entry.qidOrNote,
            entry.returnedBy,
            entry.remarks
        ).joinToString(" ").lowercase()

        if (blockedSubstrings.any { haystack.contains(it) }) {
            return true
        }
        val hasMushtaq = haystack.contains("mushtaq")
        val hasB10 = haystack.contains("b10-10")
        if (hasMushtaq && hasB10) return true

        val hasCharle = haystack.contains("charle")
        val hasMansu = haystack.contains("mansu")
        if (hasCharle && hasMansu) return true

        return false
    }

    fun filterKeyRecords(entries: List<KeyRegisterEntity>): List<KeyRegisterEntity> =
        entries.filterNot(::isDeletedTestKeyRecord)
}

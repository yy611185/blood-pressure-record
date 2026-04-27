package com.example.bloodpressurerecord.data.repository.transfer

import com.example.bloodpressurerecord.data.repository.SaveSessionInput
import com.example.bloodpressurerecord.data.repository.SessionReadingInput
import com.example.bloodpressurerecord.util.DateTimeInputFormatter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class ParsedImportRow(
    val input: SaveSessionInput
)

object ImportRowParser {
    private val supportedColumns = setOf(
        "datetime",
        "scene",
        "reading1_sbp",
        "reading1_dbp",
        "reading1_pulse",
        "reading2_sbp",
        "reading2_dbp",
        "reading2_pulse",
        "reading3_sbp",
        "reading3_dbp",
        "reading3_pulse",
        "note",
        "symptoms"
    )

    fun validateHeader(header: List<String>): String? {
        if (header.isEmpty()) return "Missing header"
        val normalized = header.map { it.trim().lowercase() }
        val required = listOf(
            "datetime",
            "scene",
            "reading1_sbp",
            "reading1_dbp",
            "reading2_sbp",
            "reading2_dbp"
        )
        val missing = required.filterNot { normalized.contains(it) }
        if (missing.isNotEmpty()) return "Missing required columns: ${missing.joinToString(",")}"
        val unknown = normalized.filterNot { supportedColumns.contains(it) }
        if (unknown.isNotEmpty()) return "Contains unknown columns: ${unknown.joinToString(",")}"
        return null
    }

    fun parse(row: Map<String, String>): Result<ParsedImportRow> = runCatching {
        val measuredAt = parseDatetime(requireText(row, "datetime"))
            ?: error("Invalid datetime format")
        val scene = requireText(row, "scene")
        val readings = mutableListOf<SessionReadingInput>()
        readings += parseReading(row, 1, required = true)
            ?: error("reading1 is required")
        readings += parseReading(row, 2, required = true)
            ?: error("reading2 is required")
        parseReading(row, 3, required = false)?.let { readings += it }
        val note = row["note"]?.trim().orEmpty().ifBlank { null }
        val symptoms = parseSymptoms(row["symptoms"].orEmpty())
        ParsedImportRow(
            input = SaveSessionInput(
                measuredAt = measuredAt,
                scene = scene,
                note = note,
                symptoms = symptoms,
                readings = readings
            )
        )
    }

    private fun parseReading(
        row: Map<String, String>,
        index: Int,
        required: Boolean
    ): SessionReadingInput? {
        val sbpText = row["reading${index}_sbp"]?.trim().orEmpty()
        val dbpText = row["reading${index}_dbp"]?.trim().orEmpty()
        val pulseText = row["reading${index}_pulse"]?.trim().orEmpty()

        if (!required && sbpText.isBlank() && dbpText.isBlank() && pulseText.isBlank()) {
            return null
        }
        if (sbpText.isBlank() || dbpText.isBlank()) {
            error("reading${index}_sbp and reading${index}_dbp are required together")
        }
        val sbp = sbpText.toIntOrNull()?.takeIf { it > 0 }
            ?: error("reading${index}_sbp must be a positive integer")
        val dbp = dbpText.toIntOrNull()?.takeIf { it > 0 }
            ?: error("reading${index}_dbp must be a positive integer")
        if (dbp > sbp) {
            error("reading$index diastolic cannot be greater than systolic")
        }
        val pulse = if (pulseText.isBlank()) {
            null
        } else {
            pulseText.toIntOrNull()?.takeIf { it > 0 }
                ?: error("reading${index}_pulse must be a positive integer")
        }
        return SessionReadingInput(
            systolic = sbp,
            diastolic = dbp,
            pulse = pulse
        )
    }

    private fun parseSymptoms(raw: String): List<String> {
        return raw.split("|", ",", ";", "/")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun requireText(row: Map<String, String>, key: String): String {
        return row[key]?.trim().orEmpty().takeIf { it.isNotBlank() } ?: error("$key cannot be blank")
    }

    private fun parseDatetime(text: String): Long? {
        text.toLongOrNull()?.let { return it }
        DateTimeInputFormatter.parse(text)?.let { return it }
        runCatching {
            LocalDateTime.parse(text, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()?.let { return it }
        runCatching {
            LocalDate.parse(text, DateTimeFormatter.ISO_LOCAL_DATE)
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        }.getOrNull()?.let { return it }
        return null
    }
}

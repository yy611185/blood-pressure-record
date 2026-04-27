package com.example.bloodpressurerecord.data.repository.transfer

object CsvCodec {
    fun encodeRow(values: List<String>): String {
        return values.joinToString(",") { escapeCell(it) }
    }

    fun decodeRow(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val ch = line[index]
            if (ch == '"') {
                if (inQuotes && index + 1 < line.length && line[index + 1] == '"') {
                    current.append('"')
                    index += 2
                    continue
                }
                inQuotes = !inQuotes
                index++
                continue
            }
            if (ch == ',' && !inQuotes) {
                result += current.toString()
                current.clear()
            } else {
                current.append(ch)
            }
            index++
        }
        result += current.toString()
        return result
    }

    private fun escapeCell(value: String): String {
        if (value.none { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            return value
        }
        val escaped = value.replace("\"", "\"\"")
        return "\"$escaped\""
    }
}

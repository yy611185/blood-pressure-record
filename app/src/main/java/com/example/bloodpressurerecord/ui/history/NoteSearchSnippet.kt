package com.example.bloodpressurerecord.ui.history

private const val SnippetLength = 104

internal data class NoteSearchSnippet(
    val text: String,
    val matches: List<IntRange>
)

internal fun buildNoteSearchSnippet(
    keyword: String,
    note: String?,
    symptomNote: String?,
    factorNote: String?
): NoteSearchSnippet? {
    val needle = keyword.trim().sqliteLowercase()
    if (needle.isEmpty()) return null

    return listOfNotNull(note, symptomNote, factorNote)
        .mapNotNull { text -> buildSnippet(text, needle) }
        .maxByOrNull { it.matches.size }
}

private fun buildSnippet(text: String, needle: String): NoteSearchSnippet? {
    val searchable = text.sqliteLowercase()
    val firstMatch = searchable.indexOf(needle)
    if (firstMatch < 0) return null

    val length = maxOf(SnippetLength, needle.length)
    val context = (length - needle.length) / 2
    var start = (firstMatch - context).coerceIn(0, (text.length - length).coerceAtLeast(0))
    var end = start + minOf(length, text.length - start)
    if (start > 0 && Character.isSurrogatePair(text[start - 1], text[start])) start--
    if (end < text.length && Character.isSurrogatePair(text[end - 1], text[end])) end++

    val prefix = if (start > 0) "…" else ""
    val suffix = if (end < text.length) "…" else ""
    val snippet = text.substring(start, end).replace('\r', ' ').replace('\n', ' ').replace('\t', ' ')
    val matches = buildList {
        var match = firstMatch
        while (match >= 0 && match <= end - needle.length) {
            val offset = prefix.length + match - start
            add(offset until offset + needle.length)
            match = searchable.indexOf(needle, match + 1)
        }
    }
    return NoteSearchSnippet(prefix + snippet + suffix, matches)
}

// SQLite LIKE folds ASCII letters only by default.
private fun String.sqliteLowercase(): String = buildString(length) {
    for (character in this@sqliteLowercase) {
        append(if (character in 'A'..'Z') character.lowercaseChar() else character)
    }
}

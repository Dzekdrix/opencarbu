package fr.opencarbu.data

import java.io.BufferedReader

/**
 * A small CSV reader (RFC 4180): fields may be wrapped in double quotes, which may then
 * contain the delimiter, line breaks and doubled quotes (`""`). Streams the text, so even a
 * large file is never held in memory as a whole.
 *
 * Written here rather than imported: the need is tiny and the app avoids extra dependencies.
 */
internal object CsvReader {

    /**
     * Calls [onRecord] with the fields of each non-blank line, in order.
     *
     * @param keep whether the column at a given index is wanted; the others come out as empty
     *   strings without being copied, which makes reading a very wide file much faster.
     */
    fun read(
        reader: BufferedReader,
        delimiter: Char,
        keep: (column: Int) -> Boolean = { true },
        onRecord: (List<String>) -> Unit,
    ) {
        val field = StringBuilder()
        var fields = ArrayList<String>()
        var inQuotes = false
        var atFieldStart = true
        var skipping = !keep(0)

        fun endRecord() {
            fields.add(field.toString())
            field.setLength(0)
            // A blank line comes out as one empty field: ignore it.
            if (!(fields.size == 1 && fields[0].isEmpty())) onRecord(fields)
            fields = ArrayList(fields.size)
            atFieldStart = true
            skipping = !keep(0)
        }

        while (true) {
            val code = reader.read()
            if (code < 0) break
            val c = code.toChar()
            if (inQuotes) {
                if (c == '"') {
                    reader.mark(1)
                    val next = reader.read()
                    if (next == '"'.code) {
                        if (!skipping) field.append('"') // a doubled quote is a literal quote
                    } else {
                        inQuotes = false
                        if (next >= 0) reader.reset()
                    }
                } else if (!skipping) {
                    field.append(c)
                }
            } else {
                when {
                    c == '"' && atFieldStart -> {
                        inQuotes = true
                        atFieldStart = false
                    }
                    c == delimiter -> {
                        fields.add(field.toString())
                        field.setLength(0)
                        atFieldStart = true
                        skipping = !keep(fields.size)
                    }
                    c == '\n' -> endRecord()
                    c == '\r' -> Unit // the \n of a Windows line break ends the record
                    else -> {
                        atFieldStart = false
                        if (!skipping) field.append(c)
                    }
                }
            }
        }
        if (field.isNotEmpty() || fields.isNotEmpty() || !atFieldStart) endRecord()
    }
}

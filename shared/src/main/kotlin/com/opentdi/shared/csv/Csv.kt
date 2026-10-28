package com.opentdi.shared.csv

import java.io.Closeable
import java.io.Flushable
import java.io.Reader
import java.io.Writer

/**
 * Writes the §12.1 dialect: LF, comma, one header row, null = empty, booleans 0/1, lists joined with ';', floats by
 * Float/Double.toString() (shortest round-trip form), RFC 4180 quoting. [out] should be a UTF-8 writer.
 */
class CsvWriter(private val out: Writer, columns: List<String>) : Closeable, Flushable {
    private val width = columns.size

    init {
        write(columns)
    }

    /** One row; the field count must match the header. */
    fun row(vararg fields: Any?) {
        require(fields.size == width) { "expected $width fields, got ${fields.size}" }
        write(fields.asList())
    }

    override fun flush() = out.flush()

    override fun close() = out.close()

    private fun write(fields: List<Any?>) = out.write(csvLine(fields))
}

/** One record in the [CsvWriter] dialect, LF included: for appending a row to a file written earlier. */
fun csvLine(fields: List<Any?>): String = fields.joinToString(",", postfix = "\n") { quote(csvValue(it)) }

/** Reads text written by [CsvWriter] (header included) as records; an empty unquoted field is null. */
fun readCsv(input: Reader): List<List<String?>> {
    val text = input.readText()
    val records = mutableListOf<List<String?>>()
    var record = mutableListOf<String?>()
    val field = StringBuilder()
    var quoted = false
    fun endField() {
        record.add(if (field.isEmpty() && !quoted) null else field.toString())
        field.clear()
        quoted = false
    }
    var i = 0
    while (i < text.length) {
        val c = text[i++]
        when {
            c == '"' && field.isEmpty() && !quoted -> {
                quoted = true
                while (true) {
                    require(i < text.length) { "unterminated quoted field" }
                    val q = text[i++]
                    if (q != '"') {
                        field.append(q)
                    } else if (i < text.length && text[i] == '"') {
                        field.append('"')
                        i++
                    } else {
                        break
                    }
                }
            }
            c == ',' -> endField()
            c == '\n' -> {
                endField()
                records += record
                record = mutableListOf()
            }
            c == '\r' && i < text.length && text[i] == '\n' -> Unit // tolerate CRLF
            else -> field.append(c)
        }
    }
    if (field.isNotEmpty() || quoted || record.isNotEmpty()) {
        endField()
        records += record
    }
    return records
}

private fun csvValue(value: Any?): String = when (value) {
    null -> ""
    is Boolean -> if (value) "1" else "0"
    is Iterable<*> -> value.joinToString(";") { csvValue(it) }
    is Enum<*> -> value.name
    else -> value.toString()
}

private fun quote(s: String): String =
    if (s.any { it == ',' || it == '"' || it == '\r' || it == '\n' }) "\"" + s.replace("\"", "\"\"") + "\"" else s

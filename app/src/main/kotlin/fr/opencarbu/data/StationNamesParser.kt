package fr.opencarbu.data

import fr.opencarbu.domain.model.GeoPoint
import fr.opencarbu.domain.model.StationIdentity
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.CharBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.Charset
import java.util.zip.GZIPInputStream
import java.nio.charset.CodingErrorAction

/**
 * Reads the "stations names and brands" reference file (a CSV derived from OpenStreetMap,
 * published on data.gouv.fr) into a map from the official station id to its [StationIdentity].
 *
 * The file has hundreds of columns; only a few are read, found by their header name:
 *  - `ref:FR:prix-carburants`: the official station id, the same as the price feed's `id`;
 *  - `brand` and `name`;
 *  - `lon` and `lat`: the position of the station's OSM object, more precise than the price feed's;
 *  - the postal address (`addr:housenumber`, `addr:street`, `addr:postcode`, `addr:city`), which
 *    is often more accurate than the price feed's, so the station sheet prefers it.
 * (`osm_id` is NOT the key: it is the OpenStreetMap object number.)
 */
object StationNamesParser {
    private const val COLUMN_REF = "ref:FR:prix-carburants"
    private const val COLUMN_BRAND = "brand"
    private const val COLUMN_NAME = "name"
    private const val COLUMN_HOUSE_NUMBER = "addr:housenumber"
    private const val COLUMN_STREET = "addr:street"
    private const val COLUMN_POSTCODE = "addr:postcode"
    private const val COLUMN_CITY = "addr:city"
    private const val COLUMN_LONGITUDE = "lon"
    private const val COLUMN_LATITUDE = "lat"
    private const val COLUMN_OSM_ID = "osm_id"
    private const val COLUMN_OSM_TYPE = "osm_type"

    /** The header line is read ahead to guess the delimiter; it is far shorter than this. */
    private const val MAGIC_BYTES = 3
    private const val HEADER_PEEK_CHARS = 1 shl 20

    /** How much of an unexpected header is quoted in the error message, to help diagnose it. */
    private const val DIAGNOSTIC_COLUMNS = 6
    private const val DIAGNOSTIC_CHARS = 60

    /** @throws IOException if the file lacks the expected columns (it is not the right file). */
    fun parse(file: File): Map<Long, StationIdentity> = openText(file).use { parse(it) }

    /**
     * Opens [file] as text, whatever its packaging: the reference file is published compressed
     * with bzip2 (`.csv.bz2`), but a plain or gzip file or a UTF-16 one is read too. The kind is
     * recognised from the first bytes, not from the file name.
     */
    internal fun openText(file: File): BufferedReader {
        val raw = BufferedInputStream(file.inputStream())
        try {
            raw.mark(MAGIC_BYTES)
            val start = ByteArray(MAGIC_BYTES)
            val read = raw.read(start)
            raw.reset()
            val b0 = start[0].toInt() and 0xFF
            val b1 = start[1].toInt() and 0xFF
            val b2 = start[2].toInt() and 0xFF
            return when {
                read >= 3 && b0 == 'B'.code && b1 == 'Z'.code && b2 == 'h'.code ->
                    Bzip2InputStream(raw).bufferedReader(Charsets.UTF_8)
                read >= 2 && b0 == 0x1F && b1 == 0x8B ->
                    GZIPInputStream(raw).bufferedReader(Charsets.UTF_8)
                read >= 2 && ((b0 == 0xFF && b1 == 0xFE) || (b0 == 0xFE && b1 == 0xFF)) ->
                    raw.bufferedReader(Charsets.UTF_16)
                else -> raw.bufferedReader(Charsets.UTF_8)
            }
        } catch (e: Exception) {
            raw.close()
            throw e
        }
    }

    fun parse(reader: BufferedReader): Map<Long, StationIdentity> {
        reader.mark(HEADER_PEEK_CHARS)
        val headerLine = reader.readLine() ?: throw IOException("Empty names file")
        reader.reset()
        // Comma is the norm; semicolon- or tab-separated exports are tolerated: the most frequent wins.
        val delimiter = listOf(',', ';', '\t').maxBy { d -> headerLine.count { it == d } }

        val result = HashMap<Long, StationIdentity>()
        var refColumn = -1
        var brandColumn = -1
        var nameColumn = -1
        var houseColumn = -1
        var streetColumn = -1
        var postcodeColumn = -1
        var cityColumn = -1
        var lonColumn = -1
        var latColumn = -1
        var osmIdColumn = -1
        var osmTypeColumn = -1
        var headerSeen = false

        // Only a few of the hundreds of columns are needed: the others are not even copied.
        // Until the header has been read, every column is kept so that it can be searched.
        val keep = { column: Int ->
            !headerSeen || column == refColumn || column == brandColumn || column == nameColumn ||
                column == houseColumn || column == streetColumn || column == postcodeColumn || column == cityColumn || column == lonColumn || column == latColumn ||
                column == osmIdColumn || column == osmTypeColumn
        }
        CsvReader.read(reader, delimiter, keep) { fields ->
            if (!headerSeen) {
                headerSeen = true
                val header = fields.map { it.removePrefix("\uFEFF").trim() }
                fun columnOf(wanted: String) = header.indexOfFirst { it.equals(wanted, ignoreCase = true) }
                refColumn = columnOf(COLUMN_REF)
                brandColumn = columnOf(COLUMN_BRAND)
                nameColumn = columnOf(COLUMN_NAME)
                houseColumn = columnOf(COLUMN_HOUSE_NUMBER)
                streetColumn = columnOf(COLUMN_STREET)
                postcodeColumn = columnOf(COLUMN_POSTCODE)
                cityColumn = columnOf(COLUMN_CITY)
                lonColumn = columnOf(COLUMN_LONGITUDE)
                latColumn = columnOf(COLUMN_LATITUDE)
                osmIdColumn = columnOf(COLUMN_OSM_ID)
                osmTypeColumn = columnOf(COLUMN_OSM_TYPE)
                if (refColumn < 0 || (brandColumn < 0 && nameColumn < 0 && streetColumn < 0 && lonColumn < 0)) {
                    val shown = delimiter.toString().replace("\t", "TAB")
                    val start = header.take(DIAGNOSTIC_COLUMNS).joinToString(" | ") { it.take(DIAGNOSTIC_CHARS) }
                    throw IOException(
                        "Unexpected names file: columns $COLUMN_REF and brand/name not found. " +
                            "Received ${header.size} column(s) separated by '$shown', starting with: $start",
                    )
                }
                return@read
            }
            val brand = clean(fields.getOrNull(brandColumn))
            val name = clean(fields.getOrNull(nameColumn))
            // A street alone is usable; a house number without a street is not.
            val street = clean(fields.getOrNull(streetColumn))?.let { street ->
                listOfNotNull(clean(fields.getOrNull(houseColumn)), street).joinToString(" ")
            }
            val postalCode = clean(fields.getOrNull(postcodeColumn))
            val city = clean(fields.getOrNull(cityColumn))
            val position = readPosition(fields.getOrNull(latColumn), fields.getOrNull(lonColumn))
            val osmObject = if (position == null) null else {
                val type = fields.getOrNull(osmTypeColumn)?.trim().orEmpty()
                val osmId = fields.getOrNull(osmIdColumn)?.trim().orEmpty()
                if (osmId.isEmpty()) null else type + osmId
            }
            val found = StationIdentity(brand, name, street, postalCode, city, position, osmObject)
            if (found.isEmpty) return@read
            // A cell may list several ids ("123;456") when one OSM object stands for several stations.
            for (part in (fields.getOrNull(refColumn) ?: "").split(';')) {
                val id = part.trim().toLongOrNull()?.takeIf { it > 0 } ?: continue
                val previous = result[id]
                // If an id appears twice, keep the first value found for each field.
                result[id] = if (previous == null) found else previous.mergedWith(found)
            }
        }
        if (!headerSeen) throw IOException("Empty names file")
        return result
    }

    /** The OSM position, or null if a coordinate is missing, unreadable or impossible (0/0 included). */
    private fun readPosition(latitude: String?, longitude: String?): GeoPoint? {
        val lat = latitude?.trim()?.toDoubleOrNull() ?: return null
        val lon = longitude?.trim()?.toDoubleOrNull() ?: return null
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0 || (lat == 0.0 && lon == 0.0)) return null
        return GeoPoint(lat, lon)
    }

    private fun clean(raw: String?): String? = raw?.let(::repairMojibake)?.trim()?.takeIf { it.isNotEmpty() }

    /**
     * Some copies of the file have text encoded twice ("Ã©" instead of "é"). If the text looks
     * like that and converts back cleanly, the repaired text is returned; otherwise it is left
     * as is, so correct text (even one containing a real "Ã") is never damaged.
     */
    internal fun repairMojibake(text: String): String {
        if (text.none { it == 'Ã' || it == 'Â' }) return text
        return try {
            val bytes = Charset.forName("windows-1252").newEncoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .encode(CharBuffer.wrap(text))
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes.array(), 0, bytes.limit()))
                .toString()
        } catch (_: CharacterCodingException) {
            text
        }
    }
}

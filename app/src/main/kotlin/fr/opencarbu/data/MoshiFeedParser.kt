package fr.opencarbu.data

import com.squareup.moshi.JsonReader
import fr.opencarbu.domain.model.Station
import okio.buffer
import okio.source
import java.io.File

/**
 * Reads the JSON export (a top-level array of station records) as a stream: only one record
 * is in memory at a time, which keeps memory use low and the parsing fast.
 *
 * Moshi's [JsonReader] is used for its low-level streaming API only (no reflection, no
 * code generation). `readJsonValue()` turns one JSON object into a plain
 * `Map<String, Any?>` (numbers as Double, nested objects as Map, arrays as List),
 * which is exactly what [StationMapper] consumes.
 */
object MoshiFeedParser : FeedParser {

    override fun parse(file: File): ParsedFeed {
        val stations = ArrayList<Station>(EXPECTED_STATION_COUNT)
        var totalRecords = 0

        file.source().buffer().use { source ->
            val reader = JsonReader.of(source)
            reader.beginArray()
            while (reader.hasNext()) {
                totalRecords++
                @Suppress("UNCHECKED_CAST")
                val record = reader.readJsonValue() as? Map<String, Any?>
                if (record != null) StationMapper.map(record)?.let(stations::add)
            }
            reader.endArray()
        }
        return ParsedFeed(stations, totalRecords)
    }

    /** Only used to pre-size the list and avoid repeated growth. */
    private const val EXPECTED_STATION_COUNT = 10_500
}

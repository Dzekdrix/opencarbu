package fr.opencarbu.data

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class Bzip2InputStreamTest {
    private fun decode(bytes: ByteArray) = Bzip2InputStream(ByteArrayInputStream(bytes)).readBytes()

    @Test
    fun runsOfEqualBytesAndConcatenatedStreamsAreDecoded() {
        assertContentEquals(Bz2Fixtures.RUNS_TEXT + "second stream".toByteArray(), decode(Bz2Fixtures.TWO_STREAMS_BZ2))
    }

    @Test
    fun severalBlocksAreDecoded() {
        assertContentEquals(Bz2Fixtures.MULTI_BLOCK_TEXT, decode(Bz2Fixtures.MULTI_BLOCK_BZ2))
    }

    @Test
    fun smallReadsGiveTheSameResultAsOneBigRead() {
        val stream = Bzip2InputStream(ByteArrayInputStream(Bz2Fixtures.TWO_STREAMS_BZ2))
        val out = java.io.ByteArrayOutputStream()
        while (true) {
            val b = stream.read()
            if (b < 0) break
            out.write(b)
        }
        assertEquals(Bz2Fixtures.RUNS_TEXT.size + "second stream".length, out.size())
    }

    @Test
    fun corruptionIsDetected() {
        val damaged = Bz2Fixtures.MULTI_BLOCK_BZ2.copyOf()
        damaged[damaged.size / 2] = (damaged[damaged.size / 2].toInt() xor 0x55).toByte()
        assertFailsWith<IOException> { decode(damaged) }
    }

    @Test
    fun truncatedAndNonBzip2DataIsRejected() {
        assertFailsWith<EOFException> { decode(Bz2Fixtures.MULTI_BLOCK_BZ2.copyOf(40)) }
        assertFailsWith<IOException> { decode("<html>not bzip2</html>".toByteArray()) }
        assertFailsWith<IOException> { decode(ByteArray(0)) }
    }
}

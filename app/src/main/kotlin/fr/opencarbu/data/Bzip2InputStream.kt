package fr.opencarbu.data

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * Reads a bzip2-compressed stream (the `.bz2` format).
 *
 * Android and the Java standard library cannot read bzip2, and a library for one small file
 * would be a heavy dependency, so this is a compact decoder of the public format. It is tested
 * against files made by the reference `bzip2` compressor (see `Bzip2InputStreamTest`).
 *
 * The format, briefly: the data is cut into blocks of up to 900 000 bytes; each block is
 * (1) run-length coded, (2) shuffled by the Burrows-Wheeler transform, (3) move-to-front coded
 * and (4) Huffman coded. Decoding undoes these steps in reverse order. Several streams
 * glued one after the other are read as one. Every block carries a CRC that is checked.
 */
class Bzip2InputStream(private val input: InputStream) : InputStream() {

    // ---- bit reader -------------------------------------------------------------------
    private var bitBuffer = 0L
    private var bitCount = 0

    private fun readBits(n: Int): Int {
        while (bitCount < n) {
            val b = input.read()
            if (b < 0) throw EOFException("Unexpected end of bzip2 data")
            bitBuffer = (bitBuffer shl 8) or b.toLong()
            bitCount += 8
        }
        bitCount -= n
        return ((bitBuffer ushr bitCount) and ((1L shl n) - 1)).toInt()
    }

    private fun readBit(): Int = readBits(1)

    // ---- stream state -----------------------------------------------------------------
    private var streamOpen = false
    private var finished = false
    private var maxBlockSize = 0
    private var combinedCrc = 0

    // ---- current block ----------------------------------------------------------------
    private var tt = IntArray(0) // low 8 bits: block byte; high bits: BWT link
    private var blockLength = 0
    private var blockPos = 0
    private var tPos = 0
    private var expectedBlockCrc = 0
    private var crc = 0

    // run-length (stage 1) decoding state
    private var runByte = -1
    private var runLength = 0
    private var repeatLeft = 0
    private var repeatByte = 0
    private var countNext = false

    override fun read(): Int {
        val one = ByteArray(1)
        return if (read(one, 0, 1) < 0) -1 else one[0].toInt() and 0xFF
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        var written = 0
        while (written < length) {
            if (repeatLeft > 0) {
                repeatLeft--
                buffer[offset + written++] = emit(repeatByte)
                continue
            }
            if (blockPos >= blockLength) {
                if (blockLength > 0) endBlock()
                if (!startNextBlock()) break
                continue
            }
            tPos = tt[tPos]
            val value = tPos and 0xFF
            tPos = tPos ushr 8
            blockPos++
            if (countNext) {
                // After four equal bytes, this byte says how many more copies follow.
                countNext = false
                repeatLeft = value
                repeatByte = runByte
                runByte = -1 // a new run starts after the count, even with the same byte
                runLength = 0
                continue
            }
            buffer[offset + written++] = emit(value)
            if (value == runByte) {
                runLength++
            } else {
                runByte = value
                runLength = 1
            }
            if (runLength == RUN_THRESHOLD) countNext = true
        }
        return if (written == 0) -1 else written
    }

    override fun close() = input.close()

    private fun emit(value: Int): Byte {
        crc = (crc shl 8) xor CRC_TABLE[((crc ushr 24) xor value) and 0xFF]
        return value.toByte()
    }

    private fun endBlock() {
        val actual = crc.inv()
        if (actual != expectedBlockCrc) throw IOException("bzip2 data is corrupt (block checksum)")
        combinedCrc = ((combinedCrc shl 1) or (combinedCrc ushr 31)) xor actual
        blockLength = 0
    }

    /** Reads the next block, or the end of the stream. Returns false when all data has been read. */
    private fun startNextBlock(): Boolean {
        while (true) {
            if (finished) return false
            if (!streamOpen && !openStream()) {
                finished = true
                return false
            }
            val magicHigh = readBits(24)
            val magicLow = readBits(24)
            if (magicHigh == BLOCK_MAGIC_HIGH && magicLow == BLOCK_MAGIC_LOW) {
                readBlock()
                return true
            }
            if (magicHigh == END_MAGIC_HIGH && magicLow == END_MAGIC_LOW) {
                val storedCombined = (readBits(16) shl 16) or readBits(16)
                if (storedCombined != combinedCrc) throw IOException("bzip2 data is corrupt (stream checksum)")
                bitCount -= bitCount % 8 // the next stream, if any, starts on a byte boundary
                streamOpen = false
                continue // another stream may follow
            }
            throw IOException("bzip2 data is corrupt (bad block header)")
        }
    }

    /** Reads "BZh" + level. Returns false if the input is cleanly over. */
    private fun openStream(): Boolean {
        val first = input.read()
        if (first < 0) {
            if (!everOpened) throw EOFException("Empty bzip2 data")
            return false
        }
        if (first != 'B'.code || input.read() != 'Z'.code || input.read() != 'h'.code) {
            throw IOException("Not a bzip2 stream")
        }
        val level = input.read() - '0'.code
        if (level !in 1..9) throw IOException("Not a bzip2 stream (bad block size)")
        everOpened = true
        maxBlockSize = level * 100_000
        if (tt.size < maxBlockSize) tt = IntArray(maxBlockSize)
        combinedCrc = 0
        bitBuffer = 0
        bitCount = 0
        streamOpen = true
        return true
    }

    private var everOpened = false

    private fun readBlock() {
        expectedBlockCrc = (readBits(16) shl 16) or readBits(16)
        if (readBit() != 0) throw IOException("Randomised bzip2 blocks are not supported")
        val origPointer = readBits(24)

        // Which byte values occur in the block.
        val usedGroups = readBits(16)
        val symbolOf = IntArray(256)
        var inUse = 0
        for (group in 0 until 16) {
            if (usedGroups and (0x8000 ushr group) == 0) continue
            val used = readBits(16)
            for (j in 0 until 16) if (used and (0x8000 ushr j) != 0) symbolOf[inUse++] = group * 16 + j
        }
        if (inUse == 0) throw IOException("bzip2 data is corrupt (no symbols)")
        val alphabetSize = inUse + 2
        val endOfBlock = inUse + 1

        // Which Huffman table codes each group of 50 symbols.
        val tableCount = readBits(3)
        if (tableCount !in 2..6) throw IOException("bzip2 data is corrupt (table count)")
        val selectorCount = readBits(15)
        if (selectorCount < 1) throw IOException("bzip2 data is corrupt (selector count)")
        val selectors = ByteArray(selectorCount.coerceAtMost(MAX_SELECTORS))
        val order = IntArray(tableCount) { it }
        for (i in 0 until selectorCount) {
            var j = 0
            while (readBit() == 1) {
                j++
                if (j >= tableCount) throw IOException("bzip2 data is corrupt (selector)")
            }
            val table = order[j]
            for (k in j downTo 1) order[k] = order[k - 1]
            order[0] = table
            if (i < MAX_SELECTORS) selectors[i] = table.toByte()
        }
        val usableSelectors = selectors.size

        // The Huffman tables themselves, as code lengths stored as differences.
        val tables = Array(tableCount) {
            val lengths = IntArray(alphabetSize)
            var current = readBits(5)
            for (symbol in 0 until alphabetSize) {
                while (true) {
                    if (current !in 1..MAX_CODE_LENGTH) throw IOException("bzip2 data is corrupt (code length)")
                    if (readBit() == 0) break
                    if (readBit() == 0) current++ else current--
                }
                lengths[symbol] = current
            }
            HuffmanTable(lengths, alphabetSize)
        }

        // Huffman + move-to-front + zero-run decoding, into the low byte of tt.
        val mtf = IntArray(256) { it }
        val counts = IntArray(256)
        var length = 0
        var selectorIndex = -1
        var groupLeft = 0
        var table = tables[0]

        fun nextSymbol(): Int {
            if (groupLeft == 0) {
                selectorIndex++
                if (selectorIndex >= usableSelectors) throw IOException("bzip2 data is corrupt (selectors exhausted)")
                table = tables[selectors[selectorIndex].toInt()]
                groupLeft = GROUP_SIZE
            }
            groupLeft--
            var bits = table.minLength
            var code = readBits(bits)
            while (true) {
                if (bits > table.maxLength) throw IOException("bzip2 data is corrupt (Huffman code)")
                if (code <= table.limit[bits]) break
                bits++
                code = (code shl 1) or readBit()
            }
            return table.perm[code - table.base[bits]]
        }

        var symbol = nextSymbol()
        while (symbol != endOfBlock) {
            if (symbol <= RUN_B) {
                // RUNA/RUNB: a run of the byte at the front of the move-to-front list, its length
                // written in bijective base 2 ("1" and "2" digits, least significant first).
                var run = 0
                var weight = 1
                while (symbol <= RUN_B) {
                    run += if (symbol == RUN_A) weight else 2 * weight
                    weight = weight shl 1
                    if (run > maxBlockSize) throw IOException("bzip2 data is corrupt (run too long)")
                    symbol = nextSymbol()
                }
                val value = symbolOf[mtf[0]]
                if (length + run > maxBlockSize) throw IOException("bzip2 data is corrupt (block overflow)")
                counts[value] += run
                java.util.Arrays.fill(tt, length, length + run, value)
                length += run
            } else {
                if (length >= maxBlockSize) throw IOException("bzip2 data is corrupt (block overflow)")
                val index = symbol - 1
                val entry = mtf[index]
                System.arraycopy(mtf, 0, mtf, 1, index)
                mtf[0] = entry
                val value = symbolOf[entry]
                counts[value]++
                tt[length++] = value
                symbol = nextSymbol()
            }
        }
        if (length == 0 || origPointer >= length) throw IOException("bzip2 data is corrupt (block pointer)")

        // Inverse Burrows-Wheeler transform: link each position to the next one in the output.
        val start = IntArray(256)
        var sum = 0
        for (v in 0 until 256) {
            start[v] = sum
            sum += counts[v]
        }
        for (i in 0 until length) {
            val v = tt[i] and 0xFF
            tt[start[v]] = tt[start[v]] or (i shl 8)
            start[v]++
        }
        blockLength = length
        blockPos = 0
        tPos = tt[origPointer] ushr 8
        crc = -1
        runByte = -1
        runLength = 0
        repeatLeft = 0
        countNext = false
    }

    /** Decoding table for one Huffman code (canonical codes, as in the reference implementation). */
    private class HuffmanTable(lengths: IntArray, alphabetSize: Int) {
        val minLength: Int = lengths.min()
        val maxLength: Int = lengths.max()
        val limit = IntArray(MAX_CODE_LENGTH + 2)
        val base = IntArray(MAX_CODE_LENGTH + 2)
        val perm = IntArray(alphabetSize)

        init {
            var next = 0
            for (length in minLength..maxLength) {
                for (symbol in 0 until alphabetSize) if (lengths[symbol] == length) perm[next++] = symbol
            }
            val countAtOrBelow = IntArray(MAX_CODE_LENGTH + 2)
            for (symbol in 0 until alphabetSize) countAtOrBelow[lengths[symbol] + 1]++
            for (i in 1 until countAtOrBelow.size) countAtOrBelow[i] += countAtOrBelow[i - 1]
            var code = 0
            for (length in minLength..maxLength) {
                code += countAtOrBelow[length + 1] - countAtOrBelow[length]
                limit[length] = code - 1
                code = code shl 1
            }
            for (length in minLength + 1..maxLength) {
                base[length] = ((limit[length - 1] + 1) shl 1) - countAtOrBelow[length]
            }
            base[minLength] = countAtOrBelow[minLength]
        }
    }

    private companion object {
        const val BLOCK_MAGIC_HIGH = 0x314159
        const val BLOCK_MAGIC_LOW = 0x265359
        const val END_MAGIC_HIGH = 0x177245
        const val END_MAGIC_LOW = 0x385090
        const val MAX_CODE_LENGTH = 20
        const val MAX_SELECTORS = 18_002
        const val GROUP_SIZE = 50
        const val RUN_A = 0
        const val RUN_B = 1
        const val RUN_THRESHOLD = 4

        /** CRC-32 as bzip2 uses it: polynomial 0x04C11DB7, most significant bit first. */
        val CRC_TABLE = IntArray(256) { index ->
            var c = index shl 24
            repeat(8) { c = if (c < 0) (c shl 1) xor 0x04C11DB7 else c shl 1 }
            c
        }
    }
}

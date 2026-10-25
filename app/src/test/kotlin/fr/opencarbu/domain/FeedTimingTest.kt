package fr.opencarbu.domain

import fr.opencarbu.domain.model.FeedTiming
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FeedTimingTest {
    @Test
    fun savedValuesAreRestored() {
        assertEquals(FeedTiming(350, 1800), FeedTiming.fromStored(350, 1800))
        assertEquals(FeedTiming(0, 0), FeedTiming.fromStored(0, 0))
    }

    @Test
    fun missingOrSenselessValuesGiveNothing() {
        assertNull(FeedTiming.fromStored(null, 1800))
        assertNull(FeedTiming.fromStored(350, null))
        assertNull(FeedTiming.fromStored(-1, 1800))
        assertNull(FeedTiming.fromStored(2000, 1800)) // the answer cannot take longer than the whole download
    }
}

package fr.opencarbu.domain.birds

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BirdFlightTest {
    private fun bird(massKg: Double?) = Bird("Oiseau", massKg = massKg)

    @Test
    fun speedFollowsTheAlerstamLaw() {
        // 1 kg: 15.9 m/s = 57.2 km/h
        assertEquals(57.24, BirdFlight.speedKmh(bird(1.0)), 0.01)
        // 20 g songbird: 15.9 * 0.02^0.13 = 9.5 m/s
        assertEquals(34.3, BirdFlight.speedKmh(bird(0.02)), 0.3)
    }

    @Test
    fun heavierBirdsFlyFasterWithinTheObservedRange() {
        assertTrue(BirdFlight.speedKmh(bird(10.0)) > BirdFlight.speedKmh(bird(0.05)))
        assertEquals(8.0 * 3.6, BirdFlight.speedKmh(bird(0.0000001)), 1e-9) // clamped low
        assertEquals(23.0 * 3.6, BirdFlight.speedKmh(bird(150000.0)), 1e-9) // clamped high
    }

    @Test
    fun unknownMassUsesTheDefaultSpeed() {
        assertEquals(BirdFlight.DEFAULT_SPEED_KMH, BirdFlight.speedKmh(bird(null)), 0.0)
    }

    @Test
    fun minutesAreRoundedAndNeverZero() {
        assertEquals(60, BirdFlight.minutesFor(40.0, bird(null)))
        assertEquals(1, BirdFlight.minutesFor(0.1, bird(null)))
        assertEquals(15, BirdFlight.minutesFor(10.0, bird(null)))
    }
}

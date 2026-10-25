package fr.opencarbu.domain.birds

import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * How fast a bird flies, to tell how long the straight-line distance would take on its back.
 *
 * The speed comes from the bird's body mass with the allometric law measured on 138 species by
 * Alerstam et al. (2007, PLoS Biology 5(8): e197): airspeed = 15.9 × mass^0.13, in m/s with the
 * mass in kg. Observed speeds range from 8 to 23 m/s, so the result is kept within that range.
 * A bird whose mass is unknown flies at [DEFAULT_SPEED_KMH]. This is an estimate of a typical
 * cruising speed, not a measurement for the species.
 */
object BirdFlight {
    private const val COEFFICIENT_MS = 15.9
    private const val EXPONENT = 0.13
    private const val MIN_SPEED_MS = 8.0
    private const val MAX_SPEED_MS = 23.0
    private const val KMH_PER_MS = 3.6

    /** Speed of a bird with no known mass: about that of a 50 g songbird. */
    const val DEFAULT_SPEED_KMH = 40.0

    fun speedKmh(bird: Bird): Double {
        val mass = bird.massKg ?: return DEFAULT_SPEED_KMH
        val metresPerSecond = (COEFFICIENT_MS * mass.pow(EXPONENT)).coerceIn(MIN_SPEED_MS, MAX_SPEED_MS)
        return metresPerSecond * KMH_PER_MS
    }

    /** Whole minutes to fly [distanceKm] at the bird's speed, at least 1. */
    fun minutesFor(distanceKm: Double, bird: Bird): Int =
        (distanceKm / speedKmh(bird) * 60.0).roundToInt().coerceAtLeast(1)
}

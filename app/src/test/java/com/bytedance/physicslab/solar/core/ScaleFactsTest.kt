package com.bytedance.physicslab.solar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every number the "to scale" and "size lineup" views say out loud, re-derived and checked against the
 * NASA planetary fact sheet (nssdc.gsfc.nasa.gov/planetary/factsheet: volume in Earths, mean radius) and
 * the IAU constants (AU = 149 597 870.7 km, nominal solar radius 695 700 km, c = 299 792.458 km/s).
 */
class ScaleFactsTest {
    @Test
    fun sunAsAMetreBall() {
        // Sun = 1 m: Earth 12 742 km / 1 391 400 km = 9.16 mm at 1 AU / 1 391 400 km = 107.5 m
        assertEquals(9.158, ScaleFacts.mmIfSunIsMetre(2), 0.005)
        assertEquals(107.5, ScaleFacts.metresIfSunIsMetre(1.0), 0.1)
        assertEquals(100.5, ScaleFacts.mmIfSunIsMetre(4), 0.1)                 // Jupiter ~ a grapefruit
        assertEquals(559.3, ScaleFacts.metresIfSunIsMetre(Solar.planets[4].a), 0.5)
        assertEquals(35.4, ScaleFacts.mmIfSunIsMetre(7), 0.1)                  // Neptune ~ a walnut
        assertEquals(3233.0, ScaleFacts.metresIfSunIsMetre(Solar.planets[7].a), 3.0)
        // the everyday objects named in the readout really are that size
        for (i in 0..8) {
            val mm = ScaleFacts.mmIfSunIsMetre(i); val t = ScaleFacts.THINGS[i]
            println("SCALE %-8s %.1f mm ~ %s (%.0f-%.0f mm)".format(ScaleFacts.nameEn(i), mm, t.en, t.minMm, t.maxMm))
            assertTrue("${ScaleFacts.nameEn(i)} $mm mm vs ${t.en}", mm in t.minMm..t.maxMm)
        }
        // nearest star, Proxima Centauri 4.2465 ly: 28 900 km away on the same scale
        val proximaKm = 4.2465 * 9.4607e12 / (2 * ScaleFacts.SUN_KM) / 1000
        assertEquals(28870.0, proximaKm, 50.0)
    }

    @Test
    fun volumesMatchTheNasaFactSheet() {
        // NASA fact sheet "volume (Earth = 1)": Jupiter 1321, Saturn 764, Uranus 63.1, Neptune 57.7, Mars 0.151, Sun 1 301 019
        assertEquals(1321.0, ScaleFacts.earthVolumes(4), 2.0)
        assertEquals(764.0, ScaleFacts.earthVolumes(5), 1.5)
        assertEquals(63.1, ScaleFacts.earthVolumes(6), 0.3)
        assertEquals(57.7, ScaleFacts.earthVolumes(7), 0.3)
        assertEquals(0.151, ScaleFacts.earthVolumes(3), 0.002)
        assertEquals(1.30e6, ScaleFacts.earthVolumes(9), 0.01e6)
        // Sun's diameter = 109 Earths = 9.95 Jupiters ("about 10 Jupiters")
        assertEquals(109.2, ScaleFacts.re(9), 0.1)
        assertEquals(9.95, ScaleFacts.re(9) / ScaleFacts.re(4), 0.02)
        assertEquals(0.2727, ScaleFacts.re(8), 0.0005)                         // the Moon ~ a quarter of Earth
    }

    @Test
    fun theRulerOfTheToScaleView() {
        // Neptune's mean distance -> 1.62 m: 1 AU = 5.39 cm, the Sun 0.50 mm across, Earth 4.6 micrometres
        val mPerAu = 1.62 / Solar.planets[7].a
        assertEquals(0.0539, mPerAu, 0.0001)
        assertEquals(0.50, 2 * ScaleFacts.SUN_KM / Sky.AU_KM * mPerAu * 1000, 0.01)
        assertEquals(4.6, 2 * Solar.EARTH_RADIUS_KM / Sky.AU_KM * mPerAu * 1e6, 0.05)
        // light time: 8.32 min to Earth, 4.17 h to Neptune
        assertEquals(8.317, ScaleFacts.lightSeconds(1.0) / 60, 0.005)
        assertEquals(4.17, ScaleFacts.lightSeconds(Solar.planets[7].a) / 3600, 0.01)
        // "planets fill under a trillionth of the space inside Neptune's orbit"
        val planets = (0..7).sumOf { Math.pow(ScaleFacts.radiusKm(it), 3.0) }
        val inside = Math.pow(Solar.planets[7].a * Sky.AU_KM, 3.0)
        println("SCALE planets / sphere of Neptune's orbit = %.2e".format(planets / inside))
        assertTrue(planets / inside < 1e-12)
    }
}

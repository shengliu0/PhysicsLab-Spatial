package com.bytedance.physicslab.solar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.asin

/**
 * The Earth-Moon close-up and its eclipse presets, checked against published numbers:
 *  - the lunar series against Meeus' worked example 47.a (Astronomical Algorithms, 2nd ed.);
 *  - eclipse geometry against NASA's per-eclipse plots (F. Espenak, eclipse.gsfc.nasa.gov: LEplot
 *    2025 Sep 07 / 2026 Mar 03 / 2028 Dec 31, SEplot 2026 Aug 12 / 2027 Aug 02): gamma, umbral
 *    magnitude, shadow radii, the Moon's horizontal parallax, local magnitude at greatest eclipse;
 *  - a census of every new and full moon 2025-2028 against the NASA catalogue (which months HAVE
 *    an eclipse, and of which type) — the "why not every month" claim, tested.
 */
class SolarSkyTest {
    private fun arcmin(d: Int, m: Int, s: Double) = d + m / 60.0 + s / 3600.0

    @Test
    fun lunarSeriesReproducesMeeusExample47a() {
        // Example 47.a: 1992 April 12, 0h TD = JDE 2448724.5 -> lambda 133.162655, beta -3.229126, Delta 368409.7 km
        val p = Lunar.position(2448724.5)
        println("MEEUS 47.a lambda=%.6f beta=%.6f Delta=%.1f".format(p.lonDate, p.lat, p.distKm))
        assertEquals(133.162655, p.lonDate, 2e-5)
        assertEquals(-3.229126, p.lat, 2e-5)
        assertEquals(368409.7, p.distKm, 0.5)
    }

    /** NASA LEplot values: TD of greatest eclipse, gamma, umbral magnitude, U/P radius (deg), Moon H.P. (deg). */
    private class LE(val jd: Double, val gamma: Double, val umag: Double, val ru: Double, val rp: Double, val hp: Double)

    private val nasaLunar = listOf(
        LE(Sky.jd(2025, 9, 7, 18, 12, 57.9), -0.2752, 1.3619, 0.7364, 1.2655, arcmin(0, 59, 19.1)),
        LE(Sky.jd(2026, 3, 3, 11, 34, 52.1), -0.3765, 1.1507, 0.6983, 1.2361, arcmin(0, 57, 18.7)),
        LE(Sky.jd(2028, 12, 31, 16, 53, 15.1), 0.3258, 1.2463, 0.7089, 1.2511, arcmin(0, 58, 4.3)),
    )

    @Test
    fun lunarEclipsesMatchNasa() {
        for (e in nasaLunar) {
            val g = Eclipse.lunar(e.jd)
            println("LUNAR %s: gamma %.4f (NASA %.4f)  umbral mag %.4f (%.4f)  Ru %.4f (%.4f)  Rp %.4f (%.4f)  HP %.4f (%.4f)  Moon in umbra %.0f%%"
                .format(SolarModel.dateString(e.jd), g.gamma, e.gamma, g.umbralMag, e.umag, g.umbraDeg, e.ru, g.penumbraDeg, e.rp, g.moonHpDeg, e.hp, g.umbraFraction * 100))
            assertEquals("Moon parallax (distance)", e.hp, g.moonHpDeg, 0.003)
            assertEquals("umbra radius", e.ru, g.umbraDeg, 0.004)
            assertEquals("penumbra radius", e.rp, g.penumbraDeg, 0.004)
            assertEquals("gamma", e.gamma, g.gamma, 0.02)
            assertEquals("umbral magnitude", e.umag, g.umbralMag, 0.04)
            assertEquals('T', g.type)
            assertEquals("the whole Moon is inside the Earth's umbra", 1.0, g.umbraFraction, 1e-9)
        }
    }

    /** NASA SEplot values: TD of greatest eclipse, gamma, magnitude, greatest-eclipse point. */
    private class SE(val jd: Double, val gamma: Double, val mag: Double, val lat: Double, val lon: Double, val sdMoon: Double, val sdSun: Double)

    private val nasaSolar = listOf(
        SE(Sky.jd(2026, 8, 12, 17, 47, 7.5), 0.8976, 1.0386, 65 + 13.0 / 60, -(25 + 13.6 / 60), arcmin(0, 16, 16.9), arcmin(0, 15, 47.0)),
        SE(Sky.jd(2027, 8, 2, 10, 7, 53.4), 0.1419, 1.0790, 25 + 29.6 / 60, 33 + 13.2 / 60, arcmin(0, 16, 43.1), arcmin(0, 15, 45.5)),
    )

    @Test
    fun solarEclipsesMatchNasa() {
        for (e in nasaSolar) {
            val g = Eclipse.solar(e.jd)
            val loc = Eclipse.localSolar(e.jd, e.lat, e.lon, 84.0)
            println("SOLAR %s: gamma %.4f (NASA %.4f)  geocentric Sun-Moon %.3f deg  penumbra %.3f Re  umbra %.4f Re  central ratio %.4f | at (%.2f, %.2f): mag %.4f (NASA %.4f) sep %.1f\" sdM %.4f (%.4f) sdS %.4f (%.4f)"
                .format(SolarModel.dateString(e.jd), g.gamma, e.gamma, g.sepDeg, g.penumbraRe, g.umbraRe, g.centralMagnitude,
                    e.lat, e.lon, loc.magnitude, e.mag, loc.sepDeg * 3600, loc.moonSdDeg, e.sdMoon, loc.sunSdDeg, e.sdSun))
            assertEquals("gamma", e.gamma, g.gamma, 0.02)
            assertEquals('T', g.type)
            assertTrue("geocentric Sun-Moon angle inside the solar-eclipse limit (1.5 deg)", g.sepDeg < 1.5)
            val sdGeo = Math.toDegrees(asin(Eclipse.R_MOON / Sky.moonGeoKm(e.jd).length))
            assertEquals("geocentric Moon semi-diameter", e.sdMoon, sdGeo, 0.002)
            assertEquals("Moon/Sun diameter ratio seen at greatest eclipse", e.mag, loc.magnitude, 0.003)
            assertEquals("Sun semi-diameter", e.sdSun, loc.sunSdDeg, 0.002)
            assertTrue("totality at NASA's greatest-eclipse point: separation ${loc.sepDeg * 3600}\" must be below sdM - sdS = ${(loc.moonSdDeg - loc.sunSdDeg) * 3600}\"",
                loc.sepDeg < loc.moonSdDeg - loc.sunSdDeg)
        }
    }

    @Test
    fun eclipseCensus2025to2028MatchesNasaCatalogue() {
        // NASA catalogue (LE2001-2100 / SE2001-2100): umbral lunar eclipses and solar eclipses, by date
        val lunar = mapOf("2025-03-14" to 'T', "2025-09-07" to 'T', "2026-03-03" to 'T', "2026-08-28" to 'P',
            "2028-01-12" to 'P', "2028-07-06" to 'P', "2028-12-31" to 'T')
        val penumbralOnly = setOf("2027-02-20", "2027-08-17", "2027-07-18")
        val solar = mapOf("2025-03-29" to 'P', "2025-09-21" to 'P', "2026-02-17" to 'A', "2026-08-12" to 'T',
            "2027-02-06" to 'A', "2027-08-02" to 'T', "2028-01-26" to 'A', "2028-07-22" to 'T')
        var t = Sky.jd(2025, 1, 1)
        val foundL = HashMap<String, Char>(); val foundS = HashMap<String, Char>()
        var newMoons = 0; var noEclipse = 0
        while (t < Sky.jd(2029, 1, 1)) {
            val full = Eclipse.syzygy(t + 14.8, true)
            val gL = Eclipse.lunar(Eclipse.greatest(full, false))
            val dL = SolarModel.dateString(full + 0.0)
            if (gL.umbralMag > 0) foundL[dL] = gL.type
            else if (gL.penumbralMag > 0.02 && dL !in penumbralOnly) foundL[dL] = 'N'
            val nw = Eclipse.syzygy(t + 29.5, false)
            val tg = Eclipse.greatest(nw, true)
            val gS = Eclipse.solar(tg)
            newMoons++
            if (tg >= Sky.jd(2029, 1, 1)) break
            if (gS.anywhere) foundS[SolarModel.dateString(tg)] = gS.type else noEclipse++
            t = nw + 0.5
        }
        println("CENSUS lunar (umbral) $foundL")
        println("CENSUS solar $foundS; $noEclipse of $newMoons new moons had NO solar eclipse")
        assertEquals(lunar, foundL.filterValues { it != 'N' })
        assertTrue("unexpected penumbral-only: $foundL", foundL.filterValues { it == 'N' }.isEmpty())
        assertEquals(solar, foundS)
    }

    @Test
    fun newMoonFarFromTheNodesHasNoEclipse() {
        // 2025-06-25 new moon: ~ 5 days from the node line's alignment? pick by geometry: the new moon in
        // 2025-2026 whose ecliptic latitude is largest, and check the Moon's shadow misses the Earth.
        var t = Sky.jd(2025, 1, 1); var worst = 0.0; var worstJd = 0.0
        while (t < Sky.jd(2027, 1, 1)) {
            val nw = Eclipse.syzygy(t + 29.5, false)
            val lat = abs(Sky.latDeg(Sky.moonGeoKm(nw)))
            if (lat > worst) { worst = lat; worstJd = nw }
            t = nw + 0.5
        }
        val g = Eclipse.solar(worstJd); val l = Eclipse.lunar(Eclipse.syzygy(worstJd + 14.8, true))
        println("FAR-FROM-NODE new moon %s: Moon %.2f deg from the ecliptic, shadow axis misses Earth by %.1f Earth radii (needs < %.2f); next full moon %.2f deg from the shadow axis (umbra %.2f deg)"
            .format(SolarModel.dateString(worstJd), worst, abs(g.gamma), 1 + g.penumbraRe, l.sepDeg, l.umbraDeg))
        assertTrue(worst > 4.5)
        assertTrue(!g.anywhere && abs(g.gamma) > 4.0)
        assertTrue(l.sepDeg > 3 * l.penumbraDeg)
    }

    @Test
    fun phasesAndAgeOnDatedEvents() {
        // USNO/NASA phase times 2025 (UT, +75 s -> TT): full Sep 7 18:09, last quarter Sep 14 10:33,
        // new Sep 21 19:54, first quarter Sep 29 23:54
        fun tt(y: Int, mo: Int, d: Int, h: Int, mi: Int) = Sky.jd(y, mo, d, h, mi, 75.0)
        val full = Sky.elongationDeg(tt(2025, 9, 7, 18, 9)); val lq = Sky.elongationDeg(tt(2025, 9, 14, 10, 33))
        val nw = Sky.elongationDeg(tt(2025, 9, 21, 19, 54)); val fq = Sky.elongationDeg(tt(2025, 9, 29, 23, 54))
        println("PHASES 2025-09: full %.3f, last q %.3f, new %.3f, first q %.3f deg; illuminated at full %.4f; age at full %.2f d"
            .format(full, lq, (nw + 180) % 360 - 180, fq, Sky.illuminated(tt(2025, 9, 7, 18, 9)), Sky.moonAgeDays(tt(2025, 9, 7, 18, 9))))
        assertEquals(180.0, full, 0.1); assertEquals(270.0, lq, 0.1); assertEquals(90.0, fq, 0.1)
        assertEquals(0.0, (nw + 180) % 360 - 180, 0.1)
        assertEquals(4, Sky.phaseIndex(full)); assertEquals(6, Sky.phaseIndex(lq)); assertEquals(2, Sky.phaseIndex(fq))
        assertEquals("满月", Sky.PHASE_ZH[Sky.phaseIndex(full)])
        assertEquals(1.0, Sky.illuminated(tt(2025, 9, 7, 18, 9)), 0.002)
        // first quarter: half lit (phase angle 90 deg, not elongation 90 — differ by ~0.15 deg)
        assertEquals(0.5, Sky.illuminated(tt(2025, 9, 29, 23, 54)), 0.01)
        // age at the full moon = time since the Aug 23 06:06 UT new moon
        assertEquals(tt(2025, 9, 7, 18, 9) - tt(2025, 8, 23, 6, 6), Sky.moonAgeDays(tt(2025, 9, 7, 18, 9)), 0.02)
        // waxing moon: bright limb toward the Sun = west, position angle ~ 240..300 deg
        val chi = Sky.brightLimbDeg(tt(2025, 9, 29, 23, 54))
        println("PHASES first quarter bright limb PA %.1f deg".format(chi))
        assertTrue(chi in 220.0..320.0)
    }

    @Test
    fun synodicMonthAndDatedNewMoons() {
        // 1500 lunations 1900-2021: mean synodic month 29.530589 d (Meeus ch. 49 / USNO)
        val a = Eclipse.syzygy(Sky.jd(1900, 1, 1), false); var b = a
        repeat(1500) { b = Eclipse.syzygy(b + 29.53, false) }
        val mean = (b - a) / 1500
        // single lunations are NOT the mean: 2025-01-29 12:36 UT -> 49th new moon 2029-01-14 17:24 UT (NASA phases)
        var c = Eclipse.syzygy(Sky.jd(2025, 1, 29, 12, 37, 15.0), false); val c0 = c
        repeat(49) { c = Eclipse.syzygy(c + 29.53, false) }
        println("SYNODIC mean over 1500 lunations = %.5f d; 49 lunations from 2025-01-29: %.3f d (NASA 1446.20) -> %s".format(mean, c - c0, SolarModel.dateString(c)))
        assertEquals(29.530589, mean, 0.0005)
        assertEquals(Sky.jd(2029, 1, 14, 17, 25, 30.0), c, 0.02)
    }

    @Test
    fun earthShadowIsLongerThanTheMoonDistance() {
        // umbra length L = R_E d / (R_S - R_E) with d = 1 AU -> 1.38 million km = 3.6 lunar distances
        val l = Eclipse.R_EARTH * Sky.AU_KM / (Eclipse.R_SUN - Eclipse.R_EARTH)
        println("UMBRA length %.0f km = %.2f x 384400 km; Moon's umbra %.0f km".format(l, l / 384400, Eclipse.R_MOON * Sky.AU_KM / (Eclipse.R_SUN - Eclipse.R_MOON)))
        assertEquals(1.385e6, l, 0.01e6)
        val sdS = Math.toDegrees(asin(Eclipse.R_SUN / Sky.AU_KM))
        assertEquals(0.2666, sdS, 0.001)
    }
}

package com.bytedance.physicslab.solar.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.pow
import kotlin.math.sign

/**
 * The physics half of the Unity-vs-SpatialSDK comparison.
 *
 * PARITY: golden_positions.csv was dumped from the ORIGINAL C# PhysicsLab.Core.Solar that ships in
 * the Unity app (compiled and run with Unity's bundled Mono, no editor involved; the generator is
 * golden_positions.README.cs). 8 planets x 401 dates over ~40 years, doubles in round-trip form.
 * If the Kotlin port matches to 1e-9 AU everywhere, the two stacks show the same sky — so any
 * visual difference is presentation, never physics.
 *
 * PHYSICS: the same assertions the Unity app's DemoCoreTests.Solar_Ephemeris passes, so the bar
 * for the port is identical to the bar the original met.
 */
class SolarCoreParityTest {

    private data class Row(val planet: Int, val jd: Double, val x: Double, val y: Double, val z: Double)

    private fun golden(): List<Row> {
        val text = javaClass.classLoader!!.getResource("golden_positions.csv")!!.readText()
        return text.lineSequence().drop(1).filter { it.isNotBlank() }.map { line ->
            val f = line.split(",")
            Row(f[0].toInt(), f[1].toDouble(), f[2].toDouble(), f[3].toDouble(), f[4].toDouble())
        }.toList()
    }

    @Test
    fun matchesCSharpGoldenToOneNanoAU() {
        val rows = golden()
        assertEquals("golden row count (8 planets x 401 dates)", 8 * 401, rows.size)
        var worst = 0.0
        var worstAt = ""
        for (r in rows) {
            val p = Solar.position(Solar.planets[r.planet], Solar.centuriesFromJd(r.jd))
            val d = maxOf(abs(p.x - r.x), abs(p.y - r.y), abs(p.z - r.z))
            if (d > worst) { worst = d; worstAt = "${Solar.planets[r.planet].name} jd=${r.jd}" }
        }
        println("PARITY worst |delta| = $worst AU at $worstAt over ${rows.size} positions")
        assertTrue("Kotlin port deviates from C# by $worst AU at $worstAt", worst < 1e-9)
    }

    @Test
    fun displayScaleMatchesCSharp() {
        // values printed by the C# generator (golden_positions.README.cs -> stderr)
        assertEquals(0.16131376533505937, Solar.orbitDisplayRadius(2, Solar.planets[2].a, 0.4), 1e-15)
        assertEquals(0.006, Solar.sunDisplayRadius(0.0), 1e-15)
    }

    @Test
    fun keplerThirdLawHoldsForAllEight() {
        var worst = 0.0
        for (p in Solar.planets) {
            val years = p.periodDays / 365.25
            worst = maxOf(worst, abs(years * years - p.a.pow(3)) / p.a.pow(3))
        }
        println("Kepler III worst relative error = $worst")
        assertTrue("Kepler III violated: $worst", worst < 0.01)
    }

    @Test
    fun earthPerihelionAndAphelion() {
        val earth = Solar.planets[2]
        var lo = 9.0; var hi = 0.0
        for (d in 0..365) {
            val r = Solar.sunDistance(earth, Solar.centuriesFromJd(Solar.J2000 + d))
            lo = minOf(lo, r); hi = maxOf(hi, r)
        }
        println("Earth distance range = $lo .. $hi AU")
        assertEquals(0.9833, lo, 2e-3)
        assertEquals(1.0167, hi, 2e-3)
    }

    @Test
    fun mercuryApoPeriRatio() {
        val m = Solar.planets[0]
        var lo = 9.0; var hi = 0.0
        for (d in 0..87) {
            val r = Solar.sunDistance(m, Solar.centuriesFromJd(Solar.J2000 + d))
            lo = minOf(lo, r); hi = maxOf(hi, r)
        }
        val expect = (1 + m.e) / (1 - m.e)
        println("Mercury apo/peri = ${hi / lo} (expect $expect)")
        assertEquals(1.0, (hi / lo) / expect, 0.02)
    }

    @Test
    fun marsGoesRetrogradeFromEarth() {
        val earth = Solar.planets[2]; val mars = Solar.planets[3]
        var reversals = 0; var prevLon = 0.0; var prevDelta = 0.0
        for (d in 0 until 800) {
            val t = Solar.centuriesFromJd(Solar.J2000 + d)
            val e = Solar.position(earth, t); val m = Solar.position(mars, t)
            val lon = Math.toDegrees(atan2(m.y - e.y, m.x - e.x))
            if (d > 0) {
                val delta = Solar.wrap180(lon - prevLon)
                if (d > 1 && sign(delta) != sign(prevDelta) && abs(delta) > 1e-6) reversals++
                prevDelta = delta
            }
            prevLon = lon
        }
        println("Mars apparent-longitude reversals in 800 d = $reversals")
        assertTrue("expected retrograde loops, got $reversals reversals", reversals >= 2)
    }

    @Test
    fun noOrbitCrossingsAtAnyScale() {
        var s = 0.0
        while (s <= 1.0001) {
            for (i in 0 until 7) {
                val pin = Solar.planets[i]; val pout = Solar.planets[i + 1]
                val apo = Solar.orbitDisplayRadius(i, pin.a, s) * (1 + pin.e)
                val peri = Solar.orbitDisplayRadius(i + 1, pout.a, s) * (1 - pout.e)
                assertTrue("${pin.name}/${pout.name} cross at spread $s", apo < peri)
            }
            val sun = Solar.sunDisplayRadius(s)
            val mercPeri = Solar.orbitDisplayRadius(0, Solar.planets[0].a, s) * (1 - Solar.planets[0].e)
            assertTrue("sun swallows Mercury at spread $s", sun < mercPeri)
            s += 0.1
        }
    }

    @Test
    fun wrap180MatchesCSharpRemainderSemantics() {
        // C# and Kotlin both use truncated remainder (sign follows the dividend)
        assertEquals(-170.0, Solar.wrap180(190.0), 1e-12)
        assertEquals(170.0, Solar.wrap180(-190.0), 1e-12)
        assertEquals(-100.0, Solar.wrap180(980.0), 1e-12)
        assertEquals(100.0, Solar.wrap180(-980.0), 1e-12)
    }
}

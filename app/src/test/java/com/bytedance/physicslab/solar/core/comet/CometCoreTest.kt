package com.bytedance.physicslab.solar.core.comet

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

class CometCoreTest {
    private val aE = 0.20                      // drawn Earth orbit (display m)
    private val mu = CometThrow.mu(aE)

    @Test
    fun throwMappingHitsTheDesignPoints() {
        val k = { v: Double -> CometThrow.launchFactor(v) }
        println("k(0)=%.3f k(0.5)=%.3f k(1)=%.3f k(2)=%.3f k(3)=%.3f k(10)=%.3f".format(k(0.0), k(0.5), k(1.0), k(2.0), k(3.0), k(10.0)))
        assertEquals(0.20, k(0.05), 1e-9)                       // dead zone
        assertEquals(0.59, k(0.5), 0.02)
        assertEquals(1.00, k(1.0), 0.02)
        assertEquals(1.45, k(2.0), 0.03)
        assertTrue("2 m/s must escape", k(2.0) > sqrt(2.0))
        assertTrue("1 m/s must stay bound", k(1.0) < sqrt(2.0))
        assertTrue("saturates", k(100.0) < 1.65)
        var prev = -1.0
        for (i in 0..300) { val v = k(i * 0.01); assertTrue(v >= prev); prev = v }   // monotonic
    }

    @Test
    fun earthDistanceCircularThrowTakesOneYear() {
        val c = Comet(aE, 0.0, 0.0)
        c.launch(mu, 1.0, 0.0, 0.0, -1.0)                       // tangential (prograde = -z at +x)
        var worst = 0.0
        val steps = 365 * 4
        repeat(steps) { CometSim.step(c, mu, 0.25, 0.01, 2.0); worst = maxOf(worst, abs(c.r / aE - 1)) }
        println("circular orbit: radius drift ${"%.2e".format(worst)} over one year; back at x=${"%.4f".format(c.x)}")
        assertTrue(worst < 1e-3)
        assertEquals(aE, c.x, 1e-3)                             // one display year = one revolution
        assertEquals(0.0, c.z, 2e-3)
    }

    @Test
    fun keplerThirdLawAndEnergyForAnEccentricComet() {
        val c = Comet(0.30, 0.0, 0.0)
        c.launch(mu, 0.55, 0.0, 0.0, -1.0)                       // toss: aphelion here, eccentric
        val (a, e) = c.elements(mu)
        val e0 = c.energy(mu)
        val tExpect = 2 * PI * sqrt(a * a * a / mu)
        // time one revolution by the return to the start point
        var t = 0.0; val h = tExpect / 4000
        var minR = 9.0
        while (t < tExpect * 0.999) { CometSim.step(c, mu, h, 0.001, 5.0); t += h; minR = minOf(minR, c.r) }
        println("comet: a=%.4f e=%.3f perihelion %.4f, T=%.1f d, energy drift %.1e, back at (%.4f, %.4f)".format(
            a, e, minR, tExpect, abs(c.energy(mu) / e0 - 1), c.x, c.z))
        assertTrue(c.alive)
        assertEquals(0.30, c.x, 2e-3); assertEquals(0.0, c.z, 2e-3)
        assertTrue(abs(c.energy(mu) / e0 - 1) < 1e-3)
        assertEquals(a * (1 - e), minR, 2e-3)                   // perihelion = a(1-e)
    }

    @Test
    fun hardThrowEscapesAndPlainDropHitsTheSunNeighbourhood() {
        val hard = Comet(0.25, 0.0, 0.0).also { it.launch(mu, CometThrow.launchFactor(2.2), 0.0, 0.0, -1.0) }
        assertTrue(hard.energy(mu) > 0)
        repeat(5000) { CometSim.step(hard, mu, 1.0, 0.01, 1.2) }
        assertEquals("escaped", hard.fate)
        val drop = Comet(0.25, 0.0, 0.0).also { it.launch(mu, CometThrow.launchFactor(0.0), 0.0, 0.0, -1.0) }
        val (a, e) = drop.elements(mu)
        println("plain drop: perihelion %.4f (sun-grazing), e=%.3f".format(a * (1 - e), e))
        assertTrue(a * (1 - e) < 0.02)                           // k = 0.2 -> perihelion ~ 2 % of r
    }

    @Test
    fun releaseVelocityTakesThePeakNotTheDecelerationTail() {
        // synthetic flick: accelerate to 1.6 m/s along +x over 0.12 s, then decelerate to 0.3 m/s
        val tr = HandTracker()
        var v = 0.0; var t = 0.0; val dt = 1.0 / 90
        while (t < 0.30) {
            v = if (t < 0.12) 1.6 * t / 0.12 else maxOf(0.3, 1.6 - (t - 0.12) * 10)
            tr.add(t, v * dt, 0.0, 0.0); t += dt
        }
        val r = tr.releaseVelocity()
        println("release velocity estimate = %.3f m/s (true peak 1.6, at release %.2f)".format(r[0], v))
        // a 40 ms window averages the sharp triangular peak: best achievable ~1.47 (not 1.6); the
        // saturating k(v) makes the difference irrelevant, the point is NOT reading the 0.3 m/s tail
        assertEquals(1.6, r[0], 0.15)
        assertEquals(0.0, r[1], 1e-9)
    }

    @Test
    fun verticalThrowFallsBackToPrograde() {
        val c = Comet(0.2, 0.0, 0.0)
        c.launch(mu, 1.0, 0.0, 1.0, 0.0)                         // straight up
        assertEquals(0.0, c.vy, 1e-12)
        assertTrue(c.vz < 0)                                      // prograde at +x is -z (counter-clockwise from +Y)
    }
}

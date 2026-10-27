package com.bytedance.physicslab.solar.core.faraday

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin
import kotlin.math.sqrt

class FaradayCoreTest {
    private val magnet = BarMagnet(a = 0.010, len = 0.080, br = 0.40)      // ferrite bar, 2 cm x 8 cm
    private val coil = Coil(r = 0.030, len = 0.080, turns = 400)

    @Test
    fun ellipticIntegralsMatchTables() {
        // A&S table 17.1, m = k^2 = 0.5: K = 1.854074677, E = 1.350643881
        val k = sqrt(0.5)
        assertEquals(1.854074677, Elliptic.K(k), 1e-9)
        assertEquals(1.350643881, Elliptic.E(k), 1e-9)
        assertEquals(PI / 2, Elliptic.K(0.0), 1e-15)
        assertEquals(PI / 2, Elliptic.E(0.0), 1e-15)
        assertEquals(1.0, Elliptic.E(1.0 - 1e-12), 1e-6)
    }

    @Test
    fun mutualInductanceMatchesNeumannIntegral() {
        // M = mu0/4pi * double loop integral of dl1.dl2 / |r1 - r2|
        val a = 0.03; val b = 0.05; val d = 0.02; val n = 2000
        var s = 0.0
        for (i in 0 until n) for (j in 0 until n step 1) {
            if (j % 4 != 0) continue
            val p = 2 * PI * i / n; val q = 2 * PI * j / n
            val dx = a * cos(p) - b * cos(q); val dy = a * sin(p) - b * sin(q)
            s += cos(p - q) / sqrt(dx * dx + dy * dy + d * d)
        }
        val neumann = Em.MU0 / (4 * PI) * a * b * s * (2 * PI / n) * (2 * PI / (n / 4))
        val m = Coax.mutual(a, b, d)
        println("coaxial loops 3 cm / 5 cm, 2 cm apart: M = ${"%.6e".format(m)} H, Neumann ${"%.6e".format(neumann)} H")
        assertEquals(neumann, m, abs(m) * 1e-6)
        // far field: M -> mu0 pi a^2 b^2 / (2 d^3)
        val far = Coax.mutual(a, b, 5.0)
        assertEquals(Em.MU0 * PI * a * a * b * b / (2 * 125.0), far, far * 3e-4)
    }

    @Test
    fun loopFieldOnAndOffAxis() {
        val a = 0.04; val i = 2.0
        for (z in listOf(0.0, 0.01, 0.05, 0.2)) {
            val onAxis = Em.MU0 * i * a * a / (2 * Math.pow(a * a + z * z, 1.5))
            assertEquals(onAxis, Coax.loopField(a, i, 1e-7, z)[1], onAxis * 1e-6)
        }
        // div B = 0 numerically: (1/rho) d(rho Br)/drho + dBz/dz = 0 at an off-axis point
        val r = 0.03; val z = 0.02; val h = 1e-5
        val dr = ((r + h) * Coax.loopField(a, i, r + h, z)[0] - (r - h) * Coax.loopField(a, i, r - h, z)[0]) / (2 * h) / r
        val dz = (Coax.loopField(a, i, r, z + h)[1] - Coax.loopField(a, i, r, z - h)[1]) / (2 * h)
        assertEquals(0.0, dr + dz, abs(dz) * 1e-5)
    }

    @Test
    fun magnetIsADipoleFarAway_andRealisticUpClose() {
        println("magnet: M = ${"%.0f".format(magnet.magnetisation)} A/m, m = ${"%.3f".format(magnet.moment)} A m^2")
        // finite length: the next multipole is ~(L/z)^2 smaller -> 2 % at 0.4 m, 0.3 % at 1 m
        for ((z, tol) in listOf(0.4 to 0.025, 1.0 to 0.004, 2.0 to 0.001)) {
            val exact = magnet.fluxThroughLoop(0.03, z); val dip = magnet.dipoleFlux(0.03, z)
            println("z=$z m: flux ${"%.4e".format(exact)} Wb, point dipole ${"%.4e".format(dip)} Wb")
            assertEquals(dip, exact, dip * tol)
        }
        // in the middle of the magnet the field is ~Br * (L/2)/sqrt((L/2)^2+a^2) (long-solenoid end effect)
        val bc = magnet.field(0.0, 0.0)[1]
        assertEquals(0.40 * 0.04 / sqrt(0.04 * 0.04 + 0.01 * 0.01), bc, 1e-4)
        // flux through the coil turn around the magnet's middle: less than B_inside * pi a^2 (return flux)
        val phi0 = magnet.fluxThroughLoop(0.03, 0.0)
        println("flux through a 3 cm turn at the magnet's centre: ${"%.3e".format(phi0)} Wb (inside alone ${"%.3e".format(bc * PI * 1e-4)})")
        assertTrue(phi0 > 0.5 * bc * PI * 1e-4 && phi0 < bc * PI * 1e-4)
    }

    @Test
    fun emfIsMinusDLambdaDt_netZeroOverAPassAndProportionalToSpeedAndTurns() {
        val ind = Induction(magnet, coil)
        // pass through at constant v from -0.28 to +0.28: integral of emf dt = -(Lambda_end - Lambda_start) = 0
        val v = 0.5; val dt = 1e-4
        var x = -0.28; var integ = 0.0; var peak = 0.0
        while (x < 0.28) { val e = ind.emf(x, v); integ += e * dt; peak = maxOf(peak, abs(e)); x += v * dt }
        println("pass at 0.5 m/s: peak EMF ${"%.1f".format(peak * 1e3)} mV, integral ${"%.2e".format(integ)} V s, Lambda(0) = ${"%.4f".format(ind.linkage(0.0))} Wb")
        assertEquals(0.0, integ, 1e-3 * ind.linkage(0.0))
        // insertion only: integral = -Lambda(0) + Lambda(-0.28)
        x = -0.28; integ = 0.0
        while (x < 0.0) { integ += ind.emf(x, v) * dt; x += v * dt }
        assertEquals(-(ind.linkage(0.0) - ind.linkage(-0.28)), integ, 2e-3 * ind.linkage(0.0))
        // twice the speed -> twice the EMF; twice the turns -> twice the EMF
        assertEquals(2 * ind.emf(-0.07, 0.3), ind.emf(-0.07, 0.6), 1e-12)
        val ind2 = Induction(magnet, Coil(0.03, 0.08, 800))
        assertEquals(2 * ind.emf(-0.07, 0.3), ind2.emf(-0.07, 0.3), 1e-9)
        assertTrue(peak in 0.02..2.0)                                  // tens to hundreds of mV: a real school coil
    }

    @Test
    fun lenzInducedCurrentAlwaysBrakesTheMagnet() {
        val ind = Induction(magnet, coil)
        val rTot = coil.resistance + 100.0                             // + galvanometer
        println("coil: R = ${"%.1f".format(coil.resistance)} ohm, L = ${"%.2f".format(coil.inductance * 1e3)} mH, L/R = ${"%.0f".format(coil.inductance / rTot * 1e6)} us")
        assertTrue(coil.inductance / rTot < 1e-3)                      // inductive lag negligible vs ~0.1 s plunges
        for (pole in listOf(1, -1)) for (x in listOf(-0.15, -0.06, -0.02, 0.03, 0.09)) for (v in listOf(-0.4, 0.4)) {
            val e = ind.emf(x, v, pole); val i = e / rTot
            val f = ind.forceOnMagnet(x, i, pole)
            // mechanical power drained = electrical power dissipated, and the force opposes motion
            assertEquals(-e * e / rTot, f * v, 1e-12 + 1e-9 * e * e / rTot)
            assertTrue(f * v <= 0)
        }
        // N pole (moment +x) approaching from the left: flux along +x grows -> EMF < 0 ->
        // current left-handed about +x -> induced field -x -> the coil's LEFT end is an N pole: repels
        assertTrue(ind.emf(-0.10, +0.4, 1) < 0)
        assertTrue(ind.emf(-0.10, -0.4, 1) > 0)       // withdrawing: S at the left end, attracts
        assertTrue(ind.emf(-0.10, +0.4, -1) > 0)      // flip the magnet: everything reverses
    }

    @Test
    fun galvanometerNeedleIsADampedOscillator() {
        val g = Galvanometer(fullScaleA = 1e-3, fullScaleRad = 0.70, period = 0.9, zeta = 0.55)
        val dt = 1.0 / 90
        var peak = 0.0; var t = 0.0
        while (t < 3.0) { g.step(0.5e-3, dt); peak = maxOf(peak, g.theta); t += dt }
        val overshoot = peak / 0.35 - 1
        val expect = exp(-PI * 0.55 / sqrt(1 - 0.55 * 0.55))
        println("galvanometer step: settles at ${"%.4f".format(g.theta)} rad (0.35), overshoot ${"%.1f".format(overshoot * 100)} % (theory ${"%.1f".format(expect * 100)} %)")
        assertEquals(0.35, g.theta, 1e-4)
        assertEquals(expect, overshoot, 2e-3)
        // the same response at 60 Hz and 120 Hz (exact integration, no frame-rate dependence)
        val a = Galvanometer(1e-3, 0.7); val b = Galvanometer(1e-3, 0.7)
        repeat(60) { a.step(0.3e-3, 1.0 / 60) }; repeat(120) { b.step(0.3e-3, 1.0 / 120) }
        assertEquals(a.theta, b.theta, 1e-9)
        // current off: it returns to zero
        repeat(400) { g.step(0.0, dt) }
        assertEquals(0.0, g.theta, 1e-4)
        // pegged: stops at the end stop
        val p = Galvanometer(1e-3, 0.7)
        repeat(200) { p.step(5e-3, dt) }
        assertTrue(p.theta <= p.stopRad + 1e-12)
    }
}

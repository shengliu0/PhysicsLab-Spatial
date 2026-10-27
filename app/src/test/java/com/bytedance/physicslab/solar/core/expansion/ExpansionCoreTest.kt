package com.bytedance.physicslab.solar.core.expansion

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

class ExpansionCoreTest {

    @Test
    fun tabulatedCoefficientsGiveTextbookExpansions() {
        // CRC values near 20 °C, 1e-6/K: aluminium 23.1, brass 19, copper 16.5, carbon steel ~12
        assertEquals(23.1e-6, Metals.ALUMINIUM.alpha, 1e-9)
        assertEquals(16.5e-6, Metals.COPPER.alpha, 1e-9)
        // a 25 m steel rail between winter (-10 °C) and summer (+30 °C) grows 12 mm — why rails have gaps
        val rail = Expansion.length(25.0, Metals.STEEL.alpha, Expansion.T0 + 30) - Expansion.length(25.0, Metals.STEEL.alpha, Expansion.T0 - 10)
        println("25 m steel rail, 40 K: dL = ${"%.2f".format(rail * 1000)} mm; Al/steel alpha ratio ${"%.3f".format(Metals.ALUMINIUM.alpha / Metals.STEEL.alpha)}")
        assertEquals(0.012, rail, 1e-9)
        // the demo's brass ball at 650 °C: dD = 19e-6 x 25.40 mm x 630 K = 0.304 mm
        val d = Expansion.length(0.0254, Metals.BRASS.alpha, 273.15 + 650) * 1000
        println("brass ball 25.40 mm at 650 °C -> ${"%.4f".format(d)} mm")
        assertEquals(25.7040, d, 1e-4)
    }

    @Test
    fun clearanceIsExactAndTheHoleGrowsWithTheRing() {
        val b = BallRing(Metals.BRASS)
        assertEquals(50e-6, b.clearance, 1e-12)                       // 25.45 - 25.40 mm at 20 °C
        // the ball jams when alpha D dT = 50 µm: dT* = 50e-6 / (19e-6 x 0.0254) = 103.6 K
        val jam = b.jamT - Expansion.T0
        println("brass jams at dT = ${"%.2f".format(jam)} K (ring at 20 °C)")
        assertEquals(50e-6 / (19e-6 * 0.0254), jam, 1e-9)
        b.tBall = b.jamT; assertEquals(0.0, b.clearance, 1e-15)
        // heating the RING makes the hole bigger, not smaller (it dilates like a photo enlargement)
        b.reset(); b.tRing = Expansion.T0 + 300
        println("ring at 320 °C: hole ${"%.4f".format(b.holeD * 1000)} mm, clearance ${"%.1f".format(b.clearance * 1e6)} µm")
        assertEquals(50e-6 + 19e-6 * 0.02545 * 300, b.clearance, 1e-12)
        // heat BOTH by the same dT: everything scales by (1 + alpha dT), clearance included
        b.tBall = b.tRing
        assertEquals(50e-6 * (1 + 19e-6 * 300), b.clearance, 1e-15)
        // so a hot ball passes a hot ring: what decides is the temperature DIFFERENCE
        assertTrue(b.passes)
        for (m in Metals.ALL) println("  ${m.nameEn}: jam dT = ${"%.1f".format(BallRing(m).jamT - Expansion.T0)} K")
    }

    @Test
    fun unityGeometryNeverAllowedTheColdBallThrough() {
        val ballD = 0.14; val ringMajorD = 0.15; val tube = 0.012
        val aperture = ringMajorD - 2 * tube
        println("Unity: ball Ø$ballD m vs clear aperture Ø$aperture m (overlap ${"%.0f".format((ballD - aperture) * 1000)} mm)")
        assertTrue(ballD > aperture)
        // and its x400 exaggeration: aluminium, dT = 500 K -> diameter x 5.6
        val grown = ballD * (1 + 400 * 23e-6 * 500)
        println("Unity aluminium dT=500: drawn Ø$grown m (x${"%.1f".format(grown / ballD)}) for a real +1.15 %")
        assertEquals(0.784, grown, 1e-9)
    }

    @Test
    fun planckianLocusMatchesTheCieTable() {
        // CIE 1931 chromaticity of the Planckian locus (standard tables)
        val table = mapOf(1000.0 to (0.6528 to 0.3444), 1500.0 to (0.5857 to 0.3931), 2000.0 to (0.5267 to 0.4133),
            3000.0 to (0.4369 to 0.4041), 5000.0 to (0.3451 to 0.3516), 6500.0 to (0.3135 to 0.3237), 10000.0 to (0.2807 to 0.2884))
        for ((t, xy) in table) {
            val c = Blackbody.chromaticity(t)
            val tol = if (t < 2000) 0.012 else 0.003
            println("Planck $t K: (${"%.4f".format(c[0])}, ${"%.4f".format(c[1])}) table (${xy.first}, ${xy.second})")
            assertEquals(xy.first, c[0], tol); assertEquals(xy.second, c[1], tol)
        }
        // 6500 K must come out (nearly) white in sRGB (D65 is 6504 K, slightly off the locus)
        val rgb = DoubleArray(3); Blackbody.linearSrgb(6500.0, rgb)
        println("6500 K linear sRGB ${rgb.toList()}")
        for (k in 0 until 3) assertTrue(rgb[k] > 0.85)
        // a red-hot metal is red: green under 5 % of red, no blue
        Blackbody.linearSrgb(950.0, rgb)
        assertTrue(rgb[0] == 1.0 && rgb[1] < 0.05 && rgb[2] == 0.0)
    }

    @Test
    fun wienAndStefanBoltzmannFallOutOfPlanck() {
        for (t in listOf(798.0, 1000.0, 5772.0)) {
            val peak = Blackbody.peakWavelength(t)
            println("T = $t K: peak ${"%.4f".format(peak * 1e6)} µm (Wien ${"%.4f".format(Blackbody.WIEN_B / t * 1e6)} µm)")
            assertEquals(Blackbody.WIEN_B / t, peak, 1e-4 * peak)
            val m = Blackbody.exitance(t); val s = 5.670374419e-8 * t * t * t * t
            assertEquals(1.0, m / s, 1e-4)
        }
        // at the Draper point the peak is at 3.6 µm: almost all of the radiation is infrared
        assertTrue(Blackbody.peakWavelength(798.0) > 3.5e-6)
    }

    @Test
    fun noVisibleGlowBelowTheDraperPoint() {
        assertEquals(0.0, Blackbody.glow(700.0, 1.0), 0.0)
        assertEquals(0.0, Blackbody.glow(Blackbody.DRAPER_K, 1.0), 1e-9)
        assertEquals(0.0, Blackbody.glow(793.0, 1.0), 0.0)            // Unity's hottest setting (520 °C)
        val ratio = Blackbody.luminance(1023.0) / Blackbody.luminance(798.0)
        println("visible luminance 750 °C / 525 °C = ${"%.0f".format(ratio)}x; glow(brass 650 °C) = ${"%.2f".format(Blackbody.glow(923.15, 0.6))}, glow(steel 700 °C) = ${"%.2f".format(Blackbody.glow(973.15, 0.8))}")
        assertTrue(ratio in 300.0..1200.0)
        assertTrue(Blackbody.glow(923.15, 0.6) in 0.3..0.7)
        // Unity painted a 3800-4900 K colour on a 393-793 K ball: luminance 3800 K vs 793 K
        val unity = Blackbody.luminance(3800.0) / Blackbody.luminance(793.0)
        println("Unity's glow colour belongs to a body ${"%.1e".format(unity)}x brighter than the metal it was on")
        assertTrue(unity > 1e8)
        // aluminium at its demo maximum (450 °C) never glows; emissivity also lifts the threshold
        assertEquals(0.0, Blackbody.glow(273.15 + 450, Metals.ALUMINIUM.emissivity), 0.0)
        assertEquals(0.0, Blackbody.glow(805.0, 0.6), 0.0)
    }

    @Test
    fun contactExchangeConservesHeatAndRelaxesExponentially() {
        val b = BallRing(Metals.BRASS); b.losses = false; b.contact = true
        b.reset(923.15, 293.15)
        val e0 = b.cBall * b.tBall + b.cRing * b.tRing
        val rate = BallRing.G_CONTACT * (1 / b.cBall + 1 / b.cRing)
        b.step(30.0)
        val e1 = b.cBall * b.tBall + b.cRing * b.tRing
        val diff = 630.0 * exp(-rate * 30.0)
        println("contact: C_ball ${"%.1f".format(b.cBall)} J/K, C_ring ${"%.1f".format(b.cRing)} J/K; after 30 s dT = ${"%.3f".format(b.tBall - b.tRing)} (analytic ${"%.3f".format(diff)})")
        assertEquals(e0, e1, 1e-9 * e0)
        assertEquals(diff, b.tBall - b.tRing, 1e-4 * diff)
    }

    @Test
    fun classroomTimelineIsRealistic() {
        for (m in Metals.ALL) {
            val b = BallRing(m)
            // heat in the flame: reaches the demo target well below melting
            b.flameOnBall = true
            var t = 0.0
            while (b.tBall < 273.15 + m.heatTargetC && t < 1200) { b.step(0.5); t += 0.5 }
            val heat = t
            // flame equilibrium (left on forever) must stay below the melting point for the metals
            // that glow; aluminium is stopped by the demo (it would get there)
            val eq = BallRing(m).apply { flameOnBall = true; step(5000.0) }.tBall
            // drop it on a 20 °C ring
            b.flameOnBall = false; b.contact = true; t = 0.0
            val stuck = !b.passes
            while (!b.passes && t < 1200) { b.step(0.5); t += 0.5 }
            println("${m.nameEn}: heats to ${m.heatTargetC} °C in ${"%.0f".format(heat)} s (flame equilibrium ${"%.0f".format(eq - 273.15)} °C, melts ${"%.0f".format(m.meltK - 273.15)} °C); stuck=$stuck, drops through after ${"%.0f".format(t)} s with ball ${"%.0f".format(b.tBall - 273.15)} °C / ring ${"%.0f".format(b.tRing - 273.15)} °C")
            assertTrue(heat in 30.0..400.0)
            assertTrue(stuck)
            assertTrue(t in 15.0..300.0)
            if (m !== Metals.ALUMINIUM) assertTrue(eq < m.meltK && eq > 273.15 + m.heatTargetC)
            // quench in water: back under 40 °C quickly
            b.contact = false; b.quench = true; t = 0.0
            while (b.tBall > 313.15 && t < 300) { b.step(0.1); t += 0.1 }
            println("   quench to 40 °C in ${"%.1f".format(t)} s")
            assertTrue(t < 40)
        }
    }

    @Test
    fun relativeGrowthIsAlphaTimesDeltaT() {
        // any length of the ball — diameter, circumference — grows by the same fraction alpha dT
        for (m in Metals.ALL) {
            val dT = 100.0
            val frac = Expansion.length(1.0, m.alpha, Expansion.T0 + dT) - 1
            assertEquals(m.alpha * dT, frac, 1e-15)
            assertTrue(abs(frac) < 0.003)        // fractions of a percent: the gap must be magnified
        }
    }
}

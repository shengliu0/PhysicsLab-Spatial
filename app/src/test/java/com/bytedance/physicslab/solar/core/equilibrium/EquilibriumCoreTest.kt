package com.bytedance.physicslab.solar.core.equilibrium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

class EquilibriumCoreTest {
    private val R = Dimerization.R

    @Test
    fun kpAt298IsTheTabulatedValue() {
        // ΔG° = −57200 + 298.15 × 175.8 = −4785.2 J/mol -> Kp = exp(4785.2 / (8.314462618 × 298.15))
        val k = Dimerization.kp(298.15)
        println("Kp(298.15 K) = %.6f bar⁻¹, ΔG° = %.1f J/mol".format(k, Dimerization.deltaG(298.15)))
        assertEquals(6.891853, k, 2e-6)
        // the constants are the NBS/Atkins formation data: ΔH = ΔfH(N₂O₄) − 2ΔfH(NO₂), ΔS likewise
        assertEquals(9.16e3 - 2 * 33.18e3, Dimerization.DH, 1.0)
        assertEquals(304.29 - 2 * 240.06, Dimerization.DS, 0.05)
        // ... and the ΔfG° route of the same table (97.89 − 2·51.31 kJ/mol) agrees within its rounding
        val kG = exp(4.73e3 / (R * 298.15))
        println("Kp from ΔfG° table = %.3f (table rounding of ΔG° ±0.05 kJ/mol ≈ ±2 %%)".format(kG))
        assertEquals(1.0, k / kG, 0.03)
    }

    @Test
    fun classicDegreeOfDissociationAt1atm() {
        // Textbook: N₂O₄ at 25 °C and 1 atm total is ~18.6 % dissociated (Kd = 1/Kp = 0.145 bar).
        val pTot = 1.01325
        val p0 = Dimerization.p0ForTotal(pTot, 298.15)
        val alpha = Dimerization.alphaEq(p0, 298.15)
        val closed = sqrt(1 / Dimerization.kp(298.15) / (1 / Dimerization.kp(298.15) + 4 * pTot))
        println("alpha(1 atm, 25 °C) = %.4f (closed form α = √(Kd/(Kd+4p)) = %.4f)".format(alpha, closed))
        assertEquals(closed, alpha, 1e-12)
        assertEquals(0.186, alpha, 0.001)
        // the demo fill: 1.000 bar total at 25 °C
        val v = Vessel(units = Dimerization.p0ForTotal(1.0, 298.15) * 1e5 * 1e-4 / (R * 298.15), volume = 1e-4, t = 298.15)
        println("fill: P0 = %.4f bar, p(NO₂) %.4f + p(N₂O₄) %.4f = %.4f bar, x(NO₂) = %.4f".format(v.p0(), v.pNO2(), v.pN2O4(), v.pTotal(), v.moleFractionNO2()))
        assertEquals(1.0, v.pTotal(), 1e-12)
    }

    @Test
    fun vantHoffSlopeIsMinusDeltaHOverR() {
        // d ln K / d(1/T) = −ΔH°/R exactly (ΔH°, ΔS° constant): finite differences over 250–400 K
        val pts = doubleArrayOf(250.0, 273.15, 298.15, 323.15, 353.15, 373.15, 400.0)
        for (i in 0 until pts.size - 1) {
            val s = (ln(Dimerization.kp(pts[i + 1])) - ln(Dimerization.kp(pts[i]))) / (1 / pts[i + 1] - 1 / pts[i])
            assertEquals(-Dimerization.DH / R, s, 1e-6)
        }
        // linear fit through all points
        val xs = pts.map { 1 / it }; val ys = pts.map { ln(Dimerization.kp(it)) }
        val mx = xs.average(); val my = ys.average()
        val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / xs.indices.sumOf { (xs[it] - mx) * (xs[it] - mx) }
        val icpt = my - slope * mx
        println("van 't Hoff: slope %.2f K (−ΔH/R = %.2f K), intercept %.4f (ΔS/R = %.4f)".format(slope, -Dimerization.DH / R, icpt, Dimerization.DS / R))
        assertEquals(-Dimerization.DH / R, slope, 1e-6)
        assertEquals(Dimerization.DS / R, icpt, 1e-9)
    }

    @Test
    fun solverSatisfiesKpExactlyAndConservesNitrogen() {
        var worst = 0.0
        for (tC in doubleArrayOf(-10.0, 0.0, 25.0, 50.0, 80.0, 100.0))
            for (units in doubleArrayOf(1e-4, 3.4e-3, 6.8e-3, 2e-2))
                for (vol in doubleArrayOf(5e-5, 1e-4, 2e-4, 1e-3)) {
                    val t = tC + Dimerization.KELVIN
                    val v = Vessel(units, vol, t)
                    val k = v.pN2O4() / (v.pNO2() * v.pNO2())
                    val rel = abs(k / Dimerization.kp(t) - 1)
                    worst = maxOf(worst, rel)
                    // mass balance: n(NO₂) + 2 n(N₂O₄) = n0  (from the partial pressures, independently)
                    val nNO2 = v.pNO2() * 1e5 * vol / (R * t); val nN2O4 = v.pN2O4() * 1e5 * vol / (R * t)
                    assertEquals(units, nNO2 + 2 * nN2O4, units * 1e-12)
                    assertTrue(v.alpha in 0.0..1.0)
                }
        println("solver: worst |Qp/Kp − 1| over 96 (T, n0, V) cases = %.2e".format(worst))
        assertTrue(worst < 1e-12)
    }

    @Test
    fun leChatelierHeatingShiftsTowardNO2() {
        val units = 6.8e-3; val vol = 1e-4
        var last = -1.0
        val out = StringBuilder()
        for (tC in -10..100 step 5) {
            val v = Vessel(units, vol, tC + Dimerization.KELVIN)
            assertTrue("x(NO₂) must rise with T", v.moleFractionNO2() > last)
            last = v.moleFractionNO2()
            if (tC % 25 == 0 || tC == 80) out.append(" %d°C: x=%.3f c=%.1f mmol/L;".format(tC, v.moleFractionNO2(), v.cNO2()))
        }
        println("heating:$out")
        // the three flasks of the demo (same fill): ice / room / hot
        val p0 = Dimerization.p0ForTotal(1.0, 298.15)
        val n0 = p0 * 1e5 * vol / (R * 298.15)
        val c = doubleArrayOf(0.0, 25.0, 80.0).map { Vessel(n0, vol, it + Dimerization.KELVIN).cNO2() }
        println("flasks 0/25/80 °C: c(NO₂) = %.2f / %.2f / %.2f mmol/L (ratio 1 : %.2f : %.2f)".format(c[0], c[1], c[2], c[1] / c[0], c[2] / c[0]))
        assertTrue(c[0] < c[1] && c[1] < c[2])
        assertEquals(12.72, c[1], 0.01)
    }

    @Test
    fun compressionTwoStepThenShiftToFewerMolecules() {
        val p0 = Dimerization.p0ForTotal(1.0, 298.15)
        val v = Vessel(p0 * 1e5 * 2e-4 / (R * 298.15), 2e-4, 298.15)
        val c0 = v.cNO2(); val p0NO2 = v.pNO2(); val x0 = v.moleFractionNO2()
        v.volume = 1e-4                                       // push the syringe in: V/2, instantly
        val cJump = v.cNO2() / c0
        assertEquals("step 1: same molecules in half the space", 2.0, cJump, 1e-12)
        assertEquals(2.0, v.pNO2() / p0NO2, 1e-12)
        assertEquals(x0, v.moleFractionNO2(), 1e-15)
        // step 2: relax (kF = the demo's display rate constant); p(NO₂) falls monotonically
        var prev = v.pNO2(); val trace = ArrayList<Double>()
        repeat(600) { v.step(0.05, 298.15, 0.25, 4.0); assertTrue(v.pNO2() <= prev + 1e-15); prev = v.pNO2(); if (it % 40 == 0) trace += v.cNO2() / c0 }
        val cEnd = v.cNO2() / c0
        println("compression V/2: c(NO₂) ×%.3f at once -> %s -> ×%.4f; x(NO₂) %.3f -> %.3f".format(cJump,
            trace.joinToString(" ") { "%.3f".format(it) }, cEnd, x0, v.moleFractionNO2()))
        assertEquals(1.4577, cEnd, 2e-4)                       // closed form: 2·P0 → p(NO₂) 0.4595 bar (30 s = 15 τ)
        assertTrue(cEnd in 1.0..2.0)                           // only partly undone: Le Chatelier "opposes", never reverses
        assertTrue(v.moleFractionNO2() < x0)                   // shifted to fewer molecules
        // back at equilibrium: Qp = Kp up to the e⁻¹⁵ ≈ 3e-7 left after 15 relaxation times
        assertEquals(1.0, v.pN2O4() / (v.pNO2() * v.pNO2()) / Dimerization.kp(298.15), 1e-6)
    }

    @Test
    fun relaxationIsTheExactSolutionOfTheRateLaw() {
        // closed form vs RK4 on dp/dt = −2(kF p² − kF/K · (P0 − p)/2), and step-size independence
        val t = 298.15; val p0 = 3.3696; val kF = 0.25; val k = Dimerization.kp(t)
        val a0 = 0.6304                                       // just after a 2x compression
        val aClosed = Dimerization.relax(a0 / p0, p0, t, kF, 3.0) * p0
        var a = a0; val h = 1e-4
        fun f(x: Double) = -2 * (kF * x * x - kF / k * (p0 - x) / 2)
        repeat(30000) { val k1 = f(a); val k2 = f(a + h / 2 * k1); val k3 = f(a + h / 2 * k2); val k4 = f(a + h * k3); a += h / 6 * (k1 + 2 * k2 + 2 * k3 + k4) }
        var aSteps = a0 / p0
        repeat(37) { aSteps = Dimerization.relax(aSteps, p0, t, kF, 3.0 / 37) }
        println("relax 3 s: closed %.12f  RK4 %.12f  37 steps %.12f bar; rate %.3f 1/s".format(aClosed, a, aSteps * p0, Dimerization.relaxRate(p0, t, kF)))
        assertEquals(a, aClosed, 1e-10)
        assertEquals(aClosed, aSteps * p0, 1e-12)
    }

    @Test
    fun beerLambertColourDeepensWithNO2() {
        val path = 0.007
        val tr = doubleArrayOf(0.0, 25.0, 80.0).map { tC ->
            val p0 = Dimerization.p0ForTotal(1.0, 298.15) * (tC + 273.15) / 298.15
            NO2Optics.transmittance(Dimerization.pNO2(p0, tC + 273.15) * 1e5 / (R * (tC + 273.15)), path)
        }
        tr.forEachIndexed { i, t -> println("flask %d: T(R,G,B) = %.3f %.3f %.3f, luminance %.3f".format(i, t[0], t[1], t[2], NO2Optics.luminance(t))) }
        for (t in tr) assertTrue("blue absorbed most, red least (brown)", t[2] < t[1] && t[1] < t[0])
        assertTrue(NO2Optics.luminance(tr[0]) > NO2Optics.luminance(tr[1]) && NO2Optics.luminance(tr[1]) > NO2Optics.luminance(tr[2]))
        // Beer–Lambert: absorbance is linear in c and in path length
        val a1 = -ln(NO2Optics.transmittance(10.0, path)[1]); val a2 = -ln(NO2Optics.transmittance(20.0, path)[1]); val a3 = -ln(NO2Optics.transmittance(10.0, 2 * path)[1])
        assertEquals(2.0, a2 / a1, 1e-12); assertEquals(2.0, a3 / a1, 1e-12)
        assertEquals(1.47e-23 * 10.0 * Dimerization.NA * path, a1, 1e-12)
    }
}

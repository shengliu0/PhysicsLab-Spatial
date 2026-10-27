package com.bytedance.physicslab.solar.core.cyclotron

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.sqrt

class CyclotronCoreTest {
    private val p = Lorentz.PROTON

    /**
     * Gyrate with E = 0 and measure: the period as the time between two successive upward
     * crossings of y = 0 (so the leapfrog half-step start offset cancels), the radius from the
     * y extent of a whole turn, and the z advance over that same turn (the helix pitch).
     */
    private fun gyrate(v: Double, b: Double, vPar: Double = 0.0, stepsPerTurn: Int = 2000): DoubleArray {
        val gamma = 1 / sqrt(1 - (v * v + vPar * vPar) / (Phys.C * Phys.C))
        val r = doubleArrayOf(0.0, 0.0, 0.0); val u = doubleArrayOf(gamma * v, 0.0, gamma * vPar)
        val tGuess = Lorentz.period(p.q, p.m, b, gamma)
        val dt = tGuess / stepsPerTurn
        var t = 0.0; var prevY = 0.0; var prevZ = 0.0
        val tc = ArrayList<Double>(); val zc = ArrayList<Double>()
        var minY = 0.0; var maxY = 0.0
        while (t < 2.6 * tGuess) {
            Lorentz.boris(r, u, p.q / p.m, 0.0, 0.0, 0.0, 0.0, 0.0, b, dt); t += dt
            if (tc.size == 1) { minY = minOf(minY, r[1]); maxY = maxOf(maxY, r[1]) }
            if (t > 0.5 * tGuess && prevY < 0 && r[1] >= 0) {
                val w = r[1] / (r[1] - prevY)
                tc += t - dt * w; zc += r[2] - (r[2] - prevZ) * w
                if (tc.size == 1) { minY = r[1]; maxY = r[1] }
            }
            prevY = r[1]; prevZ = r[2]
        }
        return doubleArrayOf(tc[1] - tc[0], (maxY - minY) / 2, 0.0, zc[1] - zc[0])
    }

    @Test
    fun magneticForceDoesNoWork_speedConservedToRoundOff() {
        val r = doubleArrayOf(0.0, 0.0, 0.0); val u = doubleArrayOf(3e6, 1e6, 5e5)
        val e0 = Lorentz.kinetic(u, p.m)
        repeat(200_000) { Lorentz.boris(r, u, p.q / p.m, 0.0, 0.0, 0.0, 0.2, -0.4, 1.3, 1e-10) }
        val rel = abs(Lorentz.kinetic(u, p.m) / e0 - 1)
        println("Boris, 2e5 steps in B: |dE/E| = $rel")
        assertTrue(rel < 1e-12)
    }

    @Test
    fun periodIndependentOfSpeed_andRadiusIsMvOverQB() {
        val b = 1.3
        val f = Lorentz.cyclotronFreq(p.q, p.m, b)
        println("proton in 1.3 T: f_c = ${"%.4f".format(f / 1e6)} MHz")
        assertEquals(19.8186e6, f, 0.001e6)                       // qB/(2 pi m), CODATA constants
        for (v in listOf(1e5, 1e6, 5e6, 1.5e7)) {
            val res = gyrate(v, b)
            val gamma = 1 / sqrt(1 - v * v / (Phys.C * Phys.C))
            val tExact = Lorentz.period(p.q, p.m, b, gamma)
            val rExact = Lorentz.gyroRadius(gamma * p.m, v, p.q, b)
            println("v=${"%.1e".format(v)}: T=${"%.5f".format(res[0] * 1e9)} ns (exact ${"%.5f".format(tExact * 1e9)}), r=${"%.5f".format(res[1])} m (exact ${"%.5f".format(rExact)})")
            assertEquals(tExact, res[0], tExact * 2e-6)
            assertEquals(rExact, res[1], rExact * 1e-5)
            // non-relativistically the SAME period at every speed (the cyclotron's secret)
            assertEquals(1 / f, res[0], (1 / f) * 1.5e-3)
        }
    }

    @Test
    fun helixPitchIsVparTimesPeriod() {
        val b = 0.5; val v = 2e6; val vPar = 0.6e6
        val res = gyrate(v, b, vPar)
        val gamma = 1 / sqrt(1 - (v * v + vPar * vPar) / (Phys.C * Phys.C))
        val pitch = vPar * Lorentz.period(p.q, p.m, b, gamma)        // = Lorentz.pitch * gamma
        println("helix: pitch v_par*T = ${"%.6f".format(pitch)} m, measured advance per turn = ${"%.6f".format(res[3])} m")
        assertEquals(pitch, res[3], pitch * 1e-6)
        assertEquals(Lorentz.period(p.q, p.m, b, gamma), res[0], res[0] * 1e-6)
    }

    @Test
    fun tunedMachineGainsQUPerCrossingAndExitsAtQBR2over2m() {
        val c = Cyclotron(p, 1.3, 60e3)
        val tr = c.fly()
        val eMeV = tr.ek[tr.extractIdx] / Phys.MEV
        val dE = tr.crossings.drop(2).map { it.dE / (p.q * 60e3) }
        println("tuned: ${tr.crossings.size} crossings, ${"%.2f".format(tr.turns)} turns, E_exit=${"%.4f".format(eMeV)} MeV, (qBR)^2/2m=${"%.4f".format(c.eFinal / Phys.MEV)} MeV")
        println("  gain per crossing / qU0 (crossing 3..): min ${"%.4f".format(dE.min())} max ${"%.4f".format(dE.max())}")
        println("  RF phase at crossings: ${tr.crossings.take(8).joinToString { "%.0f".format(it.phaseDeg) }} ...")
        assertTrue(tr.extracted)
        assertTrue(dE.all { it > 0.99 && it < 1.002 })                // every crossing: +qU0 (gap edge sub-step 0.2 %)
        // the deflector takes it on the first pass after r = p/qB reached R: at most one turn late
        assertTrue(eMeV >= c.eFinal / Phys.MEV && eMeV <= c.eFinal / Phys.MEV + 2 * 0.060)
        assertEquals(1.1656, c.eFinal / Phys.MEV, 0.001)              // 1.3 T, R = 12 cm, proton
        // phases sit on the crests: 0 deg (U>0, crossing +x) and 180 deg (U<0, crossing -x)
        for (k in tr.crossings.drop(1)) {
            val d = minOf(abs(k.phaseDeg), abs(k.phaseDeg - 180), abs(k.phaseDeg - 360))
            assertTrue("phase ${k.phaseDeg}", d < 6)
        }
    }

    @Test
    fun finalEnergyDoesNotDependOnDeeVoltage_onlyTheTurnCount() {
        val lo = Cyclotron(p, 1.3, 20e3).fly(maxTurns = 80)
        val hi = Cyclotron(p, 1.3, 80e3).fly()
        val eLo = lo.ek[lo.extractIdx] / Phys.MEV; val eHi = hi.ek[hi.extractIdx] / Phys.MEV
        println("U=20 kV: ${"%.1f".format(lo.turns)} turns, ${"%.3f".format(eLo)} MeV   U=80 kV: ${"%.1f".format(hi.turns)} turns, ${"%.3f".format(eHi)} MeV")
        val eF = Cyclotron(p, 1.3, 1.0).eFinal / Phys.MEV
        assertTrue(eLo in eF..eF + 0.040 && eHi in eF..eF + 0.160)     // (qBR)^2/2m + at most one turn
        assertEquals(4.0, lo.turns / hi.turns, 0.6)
    }

    @Test
    fun detunedRfSlipsPhaseAndTheBeamIsNeverExtracted() {
        val c = Cyclotron(p, 1.3, 60e3, rfRatio = 1.04)
        val tr = c.fly(maxTurns = 30)
        val peak = tr.ek.indices.maxBy { tr.ek[it] }
        val minAfter = (peak until tr.n).minOf { tr.ek[it] }
        println("detuned +4%: extracted=${tr.extracted}, peak E=${"%.3f".format(tr.maxEk / Phys.MEV)} MeV at turn ${"%.1f".format(peak * tr.dt / c.tc)}, lowest after peak=${"%.3f".format(minAfter / Phys.MEV)} MeV, flight ${"%.1f".format(tr.tEnd / c.tc)} turns")
        assertFalse(tr.extracted)
        assertTrue(tr.maxEk < 0.8 * c.eFinal)
        assertTrue(minAfter < 0.15 * tr.maxEk)                        // it decelerates again
    }

    @Test
    fun relativityIsWhyClassicCyclotronsTopOut() {
        // a 20 MeV proton circles 2.1% slower: gamma = 1 + 20/938.27
        val ek = 20 * Phys.MEV
        val gamma = 1 + ek / (p.m * Phys.C * Phys.C)
        val v = Phys.C * sqrt(1 - 1 / (gamma * gamma))
        val res = gyrate(v, 1.3)
        val slow = res[0] * Lorentz.cyclotronFreq(p.q, p.m, 1.3) - 1
        println("20 MeV proton: gamma=${"%.5f".format(gamma)}, period longer by ${"%.3f".format(slow * 100)} %")
        assertEquals(gamma - 1, slow, 2e-5)
        assertEquals(0.02132, gamma - 1, 2e-5)
    }

    @Test
    fun lorentzForceDirection_positiveChargeCurvesClockwiseInBz() {
        // v = +x, B = +z: F = q v x B = -y, so a proton starts curving DOWN (clockwise seen from +z)
        val r = doubleArrayOf(0.0, 0.0, 0.0); val u = doubleArrayOf(1e6, 0.0, 0.0)
        repeat(50) { Lorentz.boris(r, u, p.q / p.m, 0.0, 0.0, 0.0, 0.0, 0.0, 1.0, 1e-10) }
        assertTrue(r[1] < 0 && u[1] < 0)
        assertEquals(hypot(1e6, 0.0), hypot(u[0], u[1]), 1e-3)
        assertEquals(2 * PI * p.m / (p.q * 1.0), Lorentz.period(p.q, p.m, 1.0), 1e-15)
    }
}

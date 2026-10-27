package com.bytedance.physicslab.solar.core.equilibrium

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class MicroBoxTest {
    private val units = 60                                   // the demo's window
    private val kF = 0.25                                     // the demo's display rate constant (bar⁻¹ s⁻¹)
    private val p0At25 = Dimerization.p0ForTotal(1.0, 298.15)

    /** κ, k_r and P0 for a flask of the demo's fill at [tC] °C compressed [vf]-fold (1 = start volume). */
    private fun rates(tC: Double, vf: Double = 1.0): Triple<Double, Double, Double> {
        val t = tC + Dimerization.KELVIN
        val p0 = p0At25 * t / 298.15 * vf
        val k = Dimerization.kp(t)
        return Triple(k * p0 / units, kF / k, p0)
    }

    @Test
    fun timeAverageConvergesToTheFlaskEquilibrium() {
        // the window magnifies a flask: its time-averaged composition must match the flask's Kp
        // equilibrium (macroscopic) — exactly the canonical value, which differs from it by ≤ 0.002 at N = 60
        for ((tC, vf) in listOf(0.0 to 1.0, 25.0 to 1.0, 80.0 to 1.0, 25.0 to 2.0)) {
            val (kappa, kr, p0) = rates(tC, vf)
            val t = tC + Dimerization.KELVIN
            val macro = Dimerization.alphaEq(p0, t)
            val canon = MicroTheory.canonicalAlpha(units, kappa)
            val box = MicroBox(units, seed = 7L + tC.toLong())
            box.reset(0.5, t)                                    // start far from equilibrium
            box.setTop(box.half - 2 * box.half * (1 - 1 / vf))
            val tau = 1 / Dimerization.relaxRate(p0, t, kF)
            repeat((10 * tau * 60).toInt()) { box.step(1.0 / 60, kappa, kr) }   // burn in 10 relaxation times
            val i0 = box.nTimeIntegral; val s0 = box.simTime; val a0 = box.assocEvents; val d0 = box.dissocEvents
            val runS = maxOf(3000.0, 600 * tau)
            repeat((runS * 60).toInt()) { box.step(1.0 / 60, kappa, kr) }
            val avg = (box.nTimeIntegral - i0) / (box.simTime - s0) / units
            val na = box.assocEvents - a0; val nd = box.dissocEvents - d0
            println("micro %3.0f °C V/%.0f: <α> = %.4f  canonical %.4f  macro (Kp) %.4f  |Δ| = %.4f;  %d dimerisations vs %d splits in %.0f s (τ = %.2f s)".format(
                tC, vf, avg, canon, macro, abs(avg - macro), na, nd, runS, tau))
            assertEquals("canonical (exact for N = $units)", canon, avg, 0.008)
            assertEquals("flask equilibrium", macro, avg, 0.010)
            assertEquals("finite-size term (∝ 1/N)", macro, canon, 0.004)
            // detailed balance: forward and backward events balance (difference = change in m, ≤ 50)
            assertTrue(abs(na - nd) <= units / 2)
            assertTrue(na > 500)
        }
    }

    @Test
    fun atomsAreConservedAndMoleculesStayInTheBox() {
        val (kappa, kr, _) = rates(25.0)
        val box = MicroBox(units, seed = 11L)
        box.reset(0.19, 298.15)
        var flashes = 0
        repeat(60 * 120) { f ->
            // squeeze the lid down and back up halfway through, like the syringe
            if (f == 60 * 40) box.setTop(0.0)
            if (f == 60 * 80) box.setTop(box.half)
            box.step(1.0 / 60, if (f in 2400 until 4800) kappa * 2 else kappa, kr)
            var nA = 0; var nB = 0
            for (i in 0 until box.capA) if (box.aAlive[i]) {
                nA++
                if (!box.isPending(i)) for (k in 0 until 3) {
                    val lim = if (k == 1) (box.top - box.marginA + 1e-9) else (box.half - box.marginA + 1e-9)
                    assertTrue("NO₂ outside the box", box.ax[i * 3 + k] <= lim && box.ax[i * 3 + k] >= -box.half + box.marginA - 1e-9)
                }
            }
            for (i in 0 until box.capB) if (box.bAlive[i]) {
                nB++
                assertTrue(box.bx[i * 3 + 1] <= box.top - box.marginB + 1e-9)
            }
            assertEquals("N atoms: NO₂ + 2 N₂O₄ = units", units, nA + 2 * nB)
            assertEquals(units, box.nChem + 2 * box.mChem)
            for (k in 0 until box.flashCap) if (box.fAge[k] == 0.0) flashes++
        }
        // quaternions stay unit length
        for (i in 0 until box.capA) if (box.aAlive[i]) {
            val q = box.aq; val l = sqrt(q[i * 4] * q[i * 4] + q[i * 4 + 1] * q[i * 4 + 1] + q[i * 4 + 2] * q[i * 4 + 2] + q[i * 4 + 3] * q[i * 4 + 3])
            assertEquals(1.0, l, 1e-9)
        }
        println("box: 120 s, %d reactions drawn as flashes, %d / %d events, final NO₂ %d N₂O₄ %d".format(
            flashes, box.assocEvents, box.dissocEvents, box.nChem, box.mChem))
        assertTrue(flashes > 100)
    }

    @Test
    fun dimerHalvesSitWhereTheMonomersWere() {
        // N₂O₄ geometry is built from the published structure; each half's centre of mass is 1.205 Å out
        val off = MolGeom.HALF_OFFSET
        val a = MolGeom.n2o4Atoms()
        val dNN = a[0] - a[3]
        val dNO = sqrt((a[6] - a[0]) * (a[6] - a[0]) + a[7] * a[7])
        val n = MolGeom.no2Atoms()
        val comNO2 = (MolGeom.M_N * n[0] + MolGeom.M_O * (n[3] + n[6])) / (MolGeom.M_N + 2 * MolGeom.M_O)
        println("N₂O₄: N–N %.3f Å, N–O %.3f Å, half offset %.4f Å; NO₂ COM x = %.1e".format(dNN, dNO, off, comNO2))
        assertEquals(1.782, dNN, 1e-12); assertEquals(1.190, dNO, 1e-12); assertEquals(0.0, comNO2, 1e-12)
        assertEquals(1.205, off, 0.001)
        // quaternion helpers: fromAxes reproduces its axes
        val q = MicroBox.fromAxes(0.0, 0.6, 0.8, 1.0, 0.0, 0.0)
        val ex = MicroBox.rotate(q, 1.0, 0.0, 0.0); val ey = MicroBox.rotate(q, 0.0, 1.0, 0.0)
        assertEquals(0.6, ex[1], 1e-12); assertEquals(0.8, ex[2], 1e-12); assertEquals(1.0, ey[0], 1e-12)
    }
}

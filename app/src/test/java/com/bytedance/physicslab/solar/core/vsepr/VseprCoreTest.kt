package com.bytedance.physicslab.solar.core.vsepr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

/**
 * VSEPR as physics: the shapes must EMERGE from "points on a sphere repel as 1/d^3, lone pairs sit at 0.8 R".
 * Reference angles: NIST CCCBDB experimental geometries (NH3 106.7, H2O 104.5); ClF3 87.5 (Smith 1953),
 * SF4 173.1 / 101.6 (Tolles & Gwinn 1962); ideal polyhedra by symmetry (109.47 = acos(-1/3)).
 */
class VseprCoreTest {
    private fun groundAngles(nB: Int, nL: Int): DoubleArray {
        val g = Vsepr.ground(nB, nL)
        return Vsepr.bondAngles(g.u, Vsepr.types(nB, nL), nB + nL)
    }

    private fun lpAngles(u: DoubleArray, nB: Int, nL: Int): DoubleArray {
        val out = ArrayList<Double>()
        for (i in nB until nB + nL) for (j in i + 1 until nB + nL) out += Vsepr.angleDeg(u, i, j)
        return out.sorted().toDoubleArray()
    }

    private fun fmt(a: DoubleArray) = a.joinToString(" ") { "%.2f".format(it) }

    @Test
    fun regularPolyhedraEmerge() {
        val tet = Math.toDegrees(kotlin.math.acos(-1.0 / 3))
        val cases = mapOf(
            2 to listOf(180.0), 3 to listOf(120.0), 4 to listOf(tet),
            5 to listOf(90.0, 120.0, 180.0), 6 to listOf(90.0, 180.0),
        )
        for ((n, want) in cases) {
            val a = groundAngles(n, 0)
            println("VSEPR AX$n: ${fmt(a)}   E = %.6f".format(Vsepr.ground(n, 0).e1))
            for (x in a) assertTrue("AX$n angle $x not in $want", want.any { abs(it - x) < 0.5 })
            for (w in want) assertTrue("AX$n lacks $w", a.any { abs(it - w) < 0.5 })
        }
        // TBP: 6 x 90, 3 x 120, 1 x 180; octahedron 12 x 90, 3 x 180
        val tbp = groundAngles(5, 0)
        assertEquals(6, tbp.count { abs(it - 90) < 0.5 }); assertEquals(3, tbp.count { abs(it - 120) < 0.5 })
        assertEquals(12, groundAngles(6, 0).count { abs(it - 90) < 0.5 })
    }

    @Test
    fun lonePairsSqueezeTheBondAngles() {
        val nh3 = groundAngles(3, 1); val h2o = groundAngles(2, 2)
        println("VSEPR AX3E (NH3): ${fmt(nh3)}  vs 106.7 (CCCBDB)")
        println("VSEPR AX2E2 (H2O): ${fmt(h2o)}  vs 104.5 (CCCBDB)")
        for (x in nh3) assertEquals(106.7, x, 1.5)
        assertEquals(104.5, h2o[0], 1.5)
        // the order: more lone pairs, smaller angle; AX2E (SO2-like) sits below 120
        val ax2e = groundAngles(2, 1)[0]
        println("VSEPR AX2E: %.2f".format(ax2e))
        assertTrue(ax2e < 120 && ax2e > 105)
        assertTrue(h2o[0] < nh3[0] && nh3[0] < 109.47)
    }

    @Test
    fun trigonalBipyramidFamilyPutsLonePairsEquatorial() {
        // AX4E seesaw (SF4): the lone pair has two bonds near 90 deg and two near 120 deg (equatorial)
        run {
            val g = Vsepr.ground(4, 1); val n = 5
            val lpb = (0 until 4).map { Vsepr.angleDeg(g.u, 4, it) }.sorted()
            val b = Vsepr.bondAngles(g.u, Vsepr.types(4, 1), n)
            println("VSEPR AX4E (SF4): bonds ${fmt(b)}  LP-bond ${lpb.joinToString(" ") { "%.1f".format(it) }}  (exp ax 173.1, eq 101.6)")
            assertEquals(2, lpb.count { it < 100 }); assertEquals(2, lpb.count { it > 110 })
            assertTrue("seesaw axial angle bends below 180", b.last() in 165.0..178.0)
            println("VSEPR AX4E minima: ${Vsepr.ground(4, 1).minima.joinToString { "%.5f".format(it) }}  (2nd = lone pair axial)")
        }
        // AX3E2 T-shape (ClF3): both lone pairs equatorial (LP-LP near 120, not 180)
        run {
            val g = Vsepr.ground(3, 2)
            val b = Vsepr.bondAngles(g.u, Vsepr.types(3, 2), 5); val ll = lpAngles(g.u, 3, 2)
            println("VSEPR AX3E2 (ClF3): bonds ${fmt(b)}  LP-LP ${fmt(ll)}  (exp 87.5)")
            assertTrue(ll[0] in 110.0..130.0)
            assertEquals(87.5, b[0], 1.5); assertEquals(87.5, b[1], 1.5)
            assertTrue(b[2] in 165.0..179.0)
        }
        // AX2E3 linear (XeF2): three lone pairs fill the equator
        run {
            val g = Vsepr.ground(2, 3)
            val b = Vsepr.bondAngles(g.u, Vsepr.types(2, 3), 5); val ll = lpAngles(g.u, 2, 3)
            println("VSEPR AX2E3 (XeF2): bond ${fmt(b)}  LP-LP ${fmt(ll)}")
            assertEquals(180.0, b[0], 0.5); for (x in ll) assertEquals(120.0, x, 0.5)
        }
    }

    @Test
    fun octahedronFamily() {
        run {   // AX4E2 square planar (XeF4): lone pairs TRANS
            val g = Vsepr.ground(4, 2)
            val b = Vsepr.bondAngles(g.u, Vsepr.types(4, 2), 6); val ll = lpAngles(g.u, 4, 2)
            println("VSEPR AX4E2 (XeF4): bonds ${fmt(b)}  LP-LP ${fmt(ll)}")
            assertEquals(180.0, ll[0], 0.5)
            assertEquals(4, b.count { abs(it - 90) < 0.5 }); assertEquals(2, b.count { abs(it - 180) < 0.5 })
            println("VSEPR AX4E2 minima: ${g.minima.joinToString { "%.5f".format(it) }}  (2nd = cis lone pairs)")
        }
        run {   // AX5E square pyramid: the lone pair pushes the four basal bonds below 90
            val g = Vsepr.ground(5, 1)
            val b = Vsepr.bondAngles(g.u, Vsepr.types(5, 1), 6)
            println("VSEPR AX5E: bonds ${fmt(b)}")
            assertTrue(b.first() < 90.0 && b.first() > 80.0)
        }
    }

    @Test
    fun theGroundStateIsTheTrueGlobalMinimum() {
        // 100 random starts per composition, each settled: none may beat the labelling search, and the
        // second minimum (used for the early-accept test) must not hide a lower one
        for (n in 2..6) for (nL in 0..(n - 2)) {
            val nB = n - nL; val g = Vsepr.ground(nB, nL); val lone = Vsepr.types(nB, nL)
            val found = ArrayList<Double>()
            for (s in 0 until 100) {
                val f = Flow(n, lone, Vsepr.randomStart(n, Random(500 + s))); f.relax(); found += f.energy
            }
            val mins = Vsepr.distinctMinima(found)
            println("VSEPR ${Vsepr.formula(nB, nL)}: E1 = %.6f  E2 = %.6f   random-start minima: %s".format(g.e1, g.e2, mins.joinToString { "%.6f".format(it) }))
            assertTrue("${Vsepr.formula(nB, nL)}: random start found lower energy ${mins.first()} < ${g.e1}", mins.first() >= g.e1 - 1e-7)
            for (m in mins) assertTrue("${Vsepr.formula(nB, nL)}: hidden minimum $m between E1 and E2", m <= g.e1 + 1e-7 || m >= g.e2 - 1e-6)
        }
    }

    @Test
    fun settleConvergesFromRandomStartsDeterministically() {
        var total = 0
        for (n in 2..6) for (nL in 0..(n - 2)) {
            val nB = n - nL; val g = Vsepr.ground(nB, nL); val lone = Vsepr.types(nB, nL)
            for (s in 0 until 25) {
                val start = Vsepr.randomStart(n, Random(9000 + s))
                val a = Vsepr.settle(n, lone, start); val b = Vsepr.settle(n, lone, start)
                assertTrue("${Vsepr.formula(nB, nL)} seed $s: ${a.energy} vs ground ${g.e1}", Vsepr.isGround(a.energy, g))
                assertTrue("not deterministic", a.u.contentEquals(b.u))
                total++
            }
        }
        println("VSEPR settle: $total random starts, all reached the ground state, bit-identical on repeat")
    }

    @Test
    fun energyNeverIncreasesAlongTheFlow() {
        var steps = 0
        for (n in 2..6) for (nL in 0..(n - 2)) {
            val lone = Vsepr.types(n - nL, nL)
            for (s in 0 until 8) {
                val f = Flow(n, lone, Vsepr.randomStart(n, Random(70 + s)))
                var e = f.energy; var k = 0
                while (k++ < 20000 && f.step()) { assertTrue("energy rose ${f.energy} > $e", f.energy <= e); e = f.energy; steps++
                    assertTrue(f.lastMove <= Vsepr.CAP_RAD + 1e-12) }
                assertTrue("flow did not converge in 20000 steps", f.converged)
            }
        }
        println("VSEPR monotone: $steps flow steps, energy never increased, no move above 2.5 deg")
    }

    @Test
    fun liveSimAddsAndRemovesIntoTheRightShapes() {
        val sim = VseprSim()
        sim.reset(4, 0); val steps0 = stepsToVisualSettle(sim); sim.finish()
        assertEquals(109.47, Vsepr.bondAngles(sim.u, sim.lone, sim.n).average(), 0.05)
        // CH4 -> +LP: seesaw with the lone pair equatorial
        sim.add(true); val s1 = stepsToVisualSettle(sim); sim.finish()
        val g1 = Vsepr.ground(4, 1); assertTrue(Vsepr.isGround(sim.energy, g1))
        // -> +LP: square planar, lone pairs trans
        sim.add(true); val s2 = stepsToVisualSettle(sim); sim.finish()
        assertEquals(180.0, Vsepr.angleDeg(sim.u, 4, 5), 0.5)
        // remove a bond -> T-shape
        sim.remove(0); val s3 = stepsToVisualSettle(sim); sim.finish()
        assertTrue(Vsepr.isGround(sim.energy, Vsepr.ground(3, 2)))
        println("VSEPR live: steps to visual settle CH4 bloom $steps0, +LP $s1, +LP $s2, -bond $s3 (60 steps = 1 s at x1); hops ${sim.hops}")
        assertEquals(0, sim.hops)
        assertTrue(steps0 < 150 && s1 < 150 && s2 < 150)
    }

    private fun stepsToVisualSettle(sim: VseprSim): Int {
        var k = 0
        while (k < 5000 && !sim.settled && !sim.visuallySettled) { sim.advance(); k++ }
        return k
    }

    @Test
    fun randomUiSessionsAlwaysEndInTheGroundState() {
        val rnd = Random(2026)
        val sim = VseprSim()
        var ops = 0; var maxSteps = 0; var sumSteps = 0
        var tapMs = 0.0; var tapMax = 0.0; var lastOpMs = 0.0
        repeat(3) { VseprSim().apply { reset(4, 0); add(true); add(true); remove(0) } }   // JIT warm-up
        for (p in VseprData.presets) {
            sim.reset(p.nB, p.nL); sim.finish()
            assertTrue("${p.formula}", Vsepr.isGround(sim.energy, sim.ground))
            for (k in 0 until 30) {
                val r = rnd.nextInt(4)
                val t0 = System.nanoTime()
                val did = when {
                    r == 0 && sim.n < 6 -> sim.add(false) >= 0
                    r == 1 && sim.n < 6 -> sim.add(true) >= 0
                    else -> sim.remove(rnd.nextInt(sim.n))
                }
                lastOpMs = (System.nanoTime() - t0) / 1e6
                if (!did) continue
                ops++
                tapMs += lastOpMs; tapMax = maxOf(tapMax, lastOpMs)
                val h0 = sim.hops
                val s = stepsToVisualSettle(sim); maxSteps = maxOf(maxSteps, s); sumSteps += s
                sim.finish()
                if (sim.hops > h0) println("VSEPR hop needed: preset ${p.formula} op $k (${if (r == 0) "+bond" else if (r == 1) "+LP" else "remove"}) -> ${Vsepr.formula(sim.nBonds, sim.nLone)}")
                assertTrue("after op $ops: ${Vsepr.formula(sim.nBonds, sim.nLone)} E=${sim.energy} ground ${sim.ground.e1}", Vsepr.isGround(sim.energy, sim.ground))
                // the prediction the demo draws its arcs from is where the flow really ended
                for (i in 0 until sim.n) assertTrue(Vsepr.angleDeg(doubleArrayOf(sim.u[3 * i], sim.u[3 * i + 1], sim.u[3 * i + 2], sim.target[3 * i], sim.target[3 * i + 1], sim.target[3 * i + 2]), 0, 1) < 0.5)
            }
        }
        println("VSEPR UI fuzz: $ops add/remove ops over 11 presets, all ended in the ground state; pseudorotation hops ${sim.hops}; " +
            "steps to visual settle mean %.0f max %d; compute per tap (start search + prediction) mean %.2f ms max %.2f ms (JVM, Mac)".format(
                sumSteps.toDouble() / ops, maxSteps, tapMs / ops, tapMax))
    }

    @Test
    fun presetsAgainstExperiment() {
        val sim = VseprSim()
        for (p in VseprData.presets) {
            sim.reset(p.nB, p.nL); sim.finish()
            val s = p.angles.joinToString("   ") { a ->
                val v = VseprData.measure(a.stat, sim.u, sim.lone, sim.n)
                "${a.labelEn} model %.1f / exp %.1f".format(v, a.exp)
            }
            println("VSEPR preset ${p.formula} ${Vsepr.formula(p.nB, p.nL)}: $s")
            for (a in p.angles) {
                val v = VseprData.measure(a.stat, sim.u, sim.lone, sim.n)
                val tol = when {
                    p.nL == 0 -> 0.5                          // symmetry
                    p.formula == "SF₄" && a.stat == VseprData.Stat.OTHER -> 25.0   // the documented point-model failure
                    else -> 3.5
                }
                assertEquals("${p.formula} ${a.labelEn}", a.exp, v, tol)
            }
        }
    }

    @Test
    fun canonicalPoseIsARotationWithLonePairsUp() {
        val sim = VseprSim()
        for (p in VseprData.presets) {
            sim.reset(p.nB, p.nL); val steps = stepsToVisualSettle(sim); sim.finish()
            val r = sim.presentRotation
            // orthonormal, det +1
            for (a in 0 until 3) for (b in 0 until 3) {
                val d = r[3 * a] * r[3 * b] + r[3 * a + 1] * r[3 * b + 1] + r[3 * a + 2] * r[3 * b + 2]
                assertEquals(if (a == b) 1.0 else 0.0, d, 1e-9)
            }
            val det = r[0] * (r[4] * r[8] - r[5] * r[7]) - r[1] * (r[3] * r[8] - r[5] * r[6]) + r[2] * (r[3] * r[7] - r[4] * r[6])
            assertEquals(1.0, det, 1e-9)
            // presented = R u; presenting it again changes nothing (the rule is rotation-equivariant)
            val shown = Vsepr.rotateAll(r, sim.u, sim.n)
            val again = Vsepr.canonicalRotation(shown, sim.lone, sim.n)
            for (a in 0 until 3) assertEquals("${p.formula} presentation not idempotent", 1.0, again[4 * a], 1e-6)
            // with the display's current axes as hints (what the demo passes after a change) it stays put too
            val hinted = Vsepr.canonicalRotation(shown, sim.lone, sim.n, doubleArrayOf(0.0, 1.0, 0.0), doubleArrayOf(1.0, 0.0, 0.0))
            for (a in 0 until 3) assertEquals("${p.formula} hinted presentation moved", 1.0, hinted[4 * a], 1e-6)
            if (p.nL in 1..2 && p.formula != "XeF₄") {
                val s = DoubleArray(3); for (i in 0 until sim.n) if (sim.lone[i]) for (a in 0 until 3) s[a] += shown[3 * i + a]
                assertTrue("${p.formula}: lone pairs should point up", s[1] > 0.3)
            }
            println("VSEPR bloom ${p.formula}: %d steps to visual settle (%.2f s at x1)".format(steps, steps / 60.0))
            assertTrue(steps < 180)
        }
    }
}

package com.bytedance.physicslab.solar.core.buoyancy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sqrt

class BuoyancyCoreTest {
    private val g = Buoy.G
    // the demo's rig: 9 cm cube, 0.40 x 0.26 m can, 0.25 m of liquid
    private fun rig(obj: Int, fluid: Int) = OverflowRig(0.09, Buoy.SOLIDS[obj].rho, Buoy.FLUIDS[fluid].rho, 0.40 * 0.26, -0.215, 0.035)

    /** lower the cube the way the demo does, then wait for everything (spring, bobbing, spout) to settle */
    private fun lowerAndSettle(r: OverflowRig, seconds: Double = 40.0): OverflowRig {
        r.hookTarget = r.loweredHook()
        var t = 0.0
        while (t < seconds) { r.step(1.0 / 120); t += 1.0 / 120 }
        return r
    }

    @Test
    fun archimedesLawIsLiteral() {
        assertEquals(1000 * 9.81 * 1e-3, Buoy.buoyancy(1000.0, 9.81, 1e-3), 1e-12)        // 1 L of water: 9.81 N
        val r = rig(3, 1)
        // hold the cube at several depths and check F_b = rho g a^2 d, with d from the RISEN level
        for (bottom in listOf(0.04, 0.0, -0.03, -0.08, -0.15)) {
            r.y = bottom
            val d = (r.level - bottom).coerceIn(0.0, r.a)
            assertEquals(1000 * g * r.a * r.a * d, r.buoyancy, 1e-9)
        }
    }

    @Test
    fun levelRiseIsDisplacedVolumeOverTankArea() {
        val r = rig(3, 1); r.spoutOpen = false
        r.vTank = r.fullVolume() - 0.004   // 4 L below the brim so nothing can overflow
        val l0 = r.levelAt(1.0, r.vTank)  // cube far above
        r.y = -0.20                        // fully under
        assertEquals(r.vol / r.tankArea, r.level - l0, 1e-12)
        // half in: the implicit solve must satisfy level = floor + (V + a^2 (level - y)) / A
        r.y = l0 - 0.03
        val L = r.level
        assertEquals(r.floorY + (r.vTank + r.a * r.a * (L - r.y)) / r.tankArea, L, 1e-12)
    }

    @Test
    fun floatersSettleAtDensityRatioAndOverflowWeighsTheObject() {
        // every floating pair in the demo: wood/ice in alcohol? (ice sinks there), water, brine; nylon in brine
        for ((o, f) in listOf(0 to 0, 0 to 1, 0 to 2, 1 to 1, 1 to 2, 2 to 2)) {
            val r = lowerAndSettle(rig(o, f))
            val frac = r.depth / r.a
            val expect = Buoy.SOLIDS[o].rho / Buoy.FLUIDS[f].rho
            println("${Buoy.SOLIDS[o].en} in ${Buoy.FLUIDS[f].en}: submerged ${"%.4f".format(frac)} (rho ratio ${"%.4f".format(expect)}), " +
                "scale ${"%.3f".format(r.scaleReading)} N, G=${"%.3f".format(r.weight)} N, overflow ${"%.3f".format(r.overflowWeight)} N")
            assertTrue(r.slack)
            assertEquals(0.0, r.scaleReading, 1e-9)
            assertEquals(expect, frac, 2e-3)
            assertEquals(r.weight, r.buoyancy, 0.01 * r.weight)
            assertEquals(r.weight, r.overflowWeight, 0.01 * r.weight)        // G_overflow = G_object
        }
    }

    @Test
    fun sinkersHangWithScaleReadingWeightMinusBuoyancy() {
        for ((o, f) in listOf(3 to 0, 3 to 1, 3 to 2, 2 to 1, 2 to 0, 1 to 0)) {
            val r = lowerAndSettle(rig(o, f))
            val fb = Buoy.FLUIDS[f].rho * g * r.vol
            println("${Buoy.SOLIDS[o].en} in ${Buoy.FLUIDS[f].en}: G ${"%.3f".format(r.weight)}  F_scale ${"%.3f".format(r.scaleReading)}  " +
                "F_b ${"%.3f".format(r.buoyancy)} (rho g V ${"%.3f".format(fb)})  G_overflow ${"%.3f".format(r.overflowWeight)}")
            assertEquals(r.a, r.depth, 1e-9)                                // fully under
            assertEquals(fb, r.buoyancy, 1e-9)
            assertEquals(r.weight - fb, r.scaleReading, 0.01)               // weighing method
            assertEquals(fb, r.overflowWeight, 0.005 * fb)                 // overflow = displaced
        }
    }

    @Test
    fun waterIsConservedAndTheCanStaysFullToTheSpout() {
        val r = rig(1, 1)
        val total = r.vTank + r.vOut
        r.hookTarget = r.loweredHook()
        var worst = 0.0; var t = 0.0
        while (t < 20) { r.step(1.0 / 90); t += 1.0 / 90; worst = maxOf(worst, abs(r.vTank + r.vOut - total)) }
        assertTrue("water created/destroyed: $worst m^3", worst < 1e-12)
        assertEquals(r.spoutY, r.level, 2e-4)      // drained back to the lip
        // lifting the cube out leaves the can SHORT by exactly the overflow (water does not climb back)
        r.hookTarget = r.loweredHook() + 0.25
        t = 0.0; while (t < 20) { r.step(1.0 / 90); t += 1.0 / 90 }
        assertEquals(r.spoutY - r.vOut / r.tankArea, r.level, 1e-9)
    }

    @Test
    fun heaveOscillationMatchesSmallAmplitudeTheory() {
        // free-floating ice in water, string far away, spout closed, damping off: count zero crossings
        val r = rig(1, 1); r.spoutOpen = false
        val frac = Buoy.floatFraction(r.rhoObj, r.rhoF)
        r.vTank = r.fullVolume() - 0.002
        // equilibrium bottom: level - frac a, with the level from the implicit solve
        var yb = r.levelAt(1.0, r.vTank) - frac * r.a
        repeat(50) { yb = r.levelAt(yb, r.vTank) - frac * r.a }
        r.hook = -10.0; r.hookTarget = -10.0
        r.y = yb - 0.002; r.v = 0.0
        // undamped reference: simulate by hand with drag/radiation removed (same model otherwise)
        val m = r.mass; var y = r.y; var v = 0.0; val h = 1e-4; var t = 0.0
        val cross = ArrayList<Double>(); var prev = y - yb
        while (t < 5.0) {
            val d = (r.levelAt(y, r.vTank) - y).coerceIn(0.0, r.a)
            val mEff = m + Buoy.CA_CUBE * r.rhoF * r.a * r.a * d
            v += (-m * g + r.rhoF * g * r.a * r.a * d) / mEff * h; y += v * h; t += h
            val e = y - yb
            if (prev < 0 && e >= 0) cross += t
            prev = e
        }
        val period = (cross.last() - cross.first()) / (cross.size - 1)
        // theory: the level also moves (by a^2/A of the heave), stiffening it by A/(A - a^2)
        val omega = Buoy.heaveOmega(r.a, r.rhoObj, r.rhoF) * sqrt(r.tankArea / (r.tankArea - r.a * r.a))
        println("ice heave: simulated T = $period s, theory ${2 * PI / omega} s")
        assertEquals(2 * PI / omega, period, 0.01 * period)
    }

    @Test
    fun dampingBringsADroppedFloaterToRestAndDecaysAtTheRadiationRate() {
        val r = rig(0, 1); r.spoutOpen = false; r.vTank = r.fullVolume() - 0.002
        r.hook = -10.0; r.hookTarget = -10.0
        r.y = r.level + 0.05; r.v = 0.0           // dropped from 5 cm above the surface
        val peaks = ArrayList<Double>(); var t = 0.0; var vPrev = 0.0
        while (t < 8) {
            r.step(1.0 / 1000); t += 1.0 / 1000
            if (vPrev > 0 && r.v <= 0 && t > 0.3) peaks += r.y
            vPrev = r.v
        }
        val frac = r.depth / r.a
        println("wood dropped from 5 cm: rests at ${"%.4f".format(frac)} submerged after 8 s; peaks ${peaks.take(4).map { "%.4f".format(it) }}")
        assertEquals(0.6, frac, 2e-3)
        assertTrue(abs(r.v) < 1e-4)
        // late, small peaks: quadratic drag is negligible, so successive amplitudes shrink by
        // exp(-2 pi zeta / sqrt(1 - zeta^2)) with zeta = 0.10 -> ratio 0.532
        val yEq = r.y
        val amps = peaks.map { it - yEq }.filter { it > 1e-6 }
        if (amps.size >= 3) {
            val ratio = amps[2] / amps[1]
            println("late amplitude ratio $ratio (zeta 0.10 -> 0.532)")
            assertEquals(0.532, ratio, 0.08)
        }
    }

    @Test
    fun aCutSinkerReachesTerminalSpeedInDeepWater() {
        val r = OverflowRig(0.09, 2700.0, 1000.0, 1.0, -50.0, 0.0)
        r.spoutOpen = false; r.hook = -100.0; r.hookTarget = -100.0
        r.y = -0.5; r.v = 0.0
        var t = 0.0; while (t < 6) { r.step(1.0 / 500); t += 1.0 / 500 }
        // with added mass the approach is slower, but the asymptote is the force balance
        println("aluminium cube sinking in water: v = ${-r.v} m/s, terminal ${Buoy.terminalSpeed(0.09, 2700.0, 1000.0)}")
        assertEquals(Buoy.terminalSpeed(0.09, 2700.0, 1000.0), -r.v, 0.01)
    }

    // ---------------------------------------------------------------- the Unity audit, in numbers

    @Test
    fun auditUnitySpringModel() {
        // Unity: a = (eqCenterY - y) * 30 - vy * 6, block 0.16 x 0.18 x 0.16 m, released at y = 0.35
        val blockH = 0.18; val frac = 0.92                       // ice in water
        val eq = 0.0 + blockH * (0.5 - frac)
        val a0 = (eq - 0.35) * 30
        println("Unity ice release acceleration (in AIR): $a0 m/s^2 vs g = -9.81")
        assertEquals(-12.77, a0, 0.01)
        // sinker: SubmergedFraction = 1 -> centre at -0.09, top exactly AT the surface; floor was -0.40
        val eqSteel = blockH * (0.5 - 1.0)
        assertEquals(0.0, eqSteel + blockH / 2, 1e-12)
        // the same omega^2 = 30 and zeta = 6/(2 sqrt 30) = 0.55 for every pair; real heave of that ice block:
        val zeta = 6 / (2 * sqrt(30.0))
        val w2 = Buoy.heaveOmega(0.16, 917.0, 1000.0).let { it * it }
        val w2Hg = Buoy.heaveOmega(0.16, 7800.0, 13600.0).let { it * it }
        println("Unity: omega^2 = 30, zeta = $zeta for all pairs; real (cube approx) ice/water ${"%.1f".format(w2)}, steel/mercury ${"%.1f".format(w2Hg)}")
        assertEquals(0.548, zeta, 1e-3)
        // the surface never rose: 0.92 x 4.608 L in a 0.80 x 0.48 tank
        val rise = 0.92 * 0.16 * 0.16 * 0.18 / (0.80 * 0.48)
        println("Unity tank level should have risen ${"%.1f".format(rise * 1000)} mm")
        assertEquals(0.011, rise, 5e-4)
    }
}

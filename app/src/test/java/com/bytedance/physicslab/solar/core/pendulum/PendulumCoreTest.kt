package com.bytedance.physicslab.solar.core.pendulum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

class PendulumCoreTest {

    @Test
    fun periodRatioMatchesTabulatedValues() {
        // T/T0 for amplitude 10, 45, 90, 150 deg (standard tables: 1.00191, 1.03997, 1.18034, 1.76220)
        val expect = mapOf(10.0 to 1.00191, 45.0 to 1.03997, 90.0 to 1.18034, 150.0 to 1.76220)
        for ((deg, r) in expect) {
            val ratio = Pendulum.period(1.0, 9.81, Math.toRadians(deg)) / Pendulum.period0(1.0, 9.81)
            println("T/T0 at $deg deg = $ratio (table $r)")
            assertEquals(r, ratio, 2e-5)
        }
    }

    @Test
    fun exactSolutionStartsAtAmplitudeAndReturnsAfterOnePeriod() {
        for (deg in listOf(5.0, 30.0, 80.0, 160.0)) {
            val th0 = Math.toRadians(deg)
            val t = Pendulum.period(0.5, 9.81, th0)
            assertEquals(th0, Pendulum.angle(th0, 0.5, 9.81, 0.0), 1e-10)
            assertEquals(-th0, Pendulum.angle(th0, 0.5, 9.81, t / 2), 1e-9)
            assertEquals(th0, Pendulum.angle(th0, 0.5, 9.81, t), 1e-9)
            assertEquals(0.0, Pendulum.angle(th0, 0.5, 9.81, t / 4), 1e-9)
        }
    }

    @Test
    fun exactSolutionAgreesWithRk4Integration() {
        // integrate theta'' = -(g/L) sin theta with RK4 at a fine step and compare over 20 s
        val g = 9.81; val l = 0.4; val th0 = Math.toRadians(45.0)
        var th = th0; var w = 0.0; val h = 1e-4; var t = 0.0; var worst = 0.0
        fun acc(x: Double) = -(g / l) * kotlin.math.sin(x)
        while (t < 20.0) {
            val k1t = w; val k1w = acc(th)
            val k2t = w + h / 2 * k1w; val k2w = acc(th + h / 2 * k1t)
            val k3t = w + h / 2 * k2w; val k3w = acc(th + h / 2 * k2t)
            val k4t = w + h * k3w; val k4w = acc(th + h * k3t)
            th += h / 6 * (k1t + 2 * k2t + 2 * k3t + k4t); w += h / 6 * (k1w + 2 * k2w + 2 * k3w + k4w); t += h
            worst = maxOf(worst, abs(th - Pendulum.angle(th0, l, g, t)))
        }
        println("exact vs RK4 over 20 s at 45 deg: worst |dtheta| = $worst rad")
        assertTrue(worst < 1e-6)
    }

    @Test
    fun smallAngleApproximationIsWhatDrifts() {
        // the Unity claim, quantified: 20 vs 32 deg (its two "identical" pendulums) after 30 s
        val g = 9.81; val l = 0.5
        val dT = Pendulum.period(l, g, Math.toRadians(32.0)) - Pendulum.period(l, g, Math.toRadians(20.0))
        val swingsToHalfCycle = Pendulum.period(l, g, Math.toRadians(20.0)) / 2 / dT
        println("20 vs 32 deg: period differs ${"%.2f".format(dT * 1000)} ms -> half a cycle apart after ${"%.0f".format(swingsToHalfCycle)} swings")
        assertTrue(swingsToHalfCycle in 30.0..60.0)
        // and the small-angle form IS the limit: at 2 deg the exact period is longer by
        // theta0^2/16 = 7.6e-5; w0 = sqrt(9.81/0.5) = 4.43 rad/s so 5 s is 22.1 rad of phase and
        // the curves separate by ~theta0 * 22.1 * 7.6e-5 = 5.9e-5 rad. Bound it at 8e-5.
        val th0 = Math.toRadians(2.0)
        var worst = 0.0
        for (k in 0..100) {
            val t = k * 0.05
            worst = maxOf(worst, abs(Pendulum.angleSmall(th0, l, g, t) - Pendulum.angle(th0, l, g, t)))
        }
        println("2 deg: small-angle vs exact worst = $worst rad over 5 s")
        assertTrue(worst < 8e-5)
    }

    @Test
    fun pendulumWaveRealignsAfterWindow() {
        val ls = Pendulum.waveLengths(15, 60.0, 51)
        println("wave lengths: ${"%.3f".format(ls.first())} .. ${"%.3f".format(ls.last())} m")
        assertEquals(0.3440, ls.first(), 1e-3)
        for ((n, l) in ls.withIndex()) {
            val cycles = 60.0 / Pendulum.period0(l, 9.81)
            assertEquals((51 + n).toDouble(), cycles, 1e-9)
            // small amplitude: phase at t = window is back at zero (cos = 1)
            assertEquals(1.0, cos(2 * PI * cycles), 1e-9)
        }
    }
}

// PendulumCore — the EXACT simple pendulum, pure Kotlin.
//
// The Unity app used theta(t) = theta0 cos(w t) — the small-angle solution — at amplitudes up to
// 32 deg, and printed "same length, different amplitude -> identical period, stay in phase
// forever". A real pendulum's period GROWS with amplitude: T = 4 K(k) / w0, k = sin(theta0/2).
// At 32 deg it is 2.0% longer than the small-angle value (20 deg: 0.77%), so those two pendulums
// drift half a cycle apart in ~40 swings. Galileo's isochronism is an approximation, and here it
// is shown as one.
//
// The exact motion is closed form (no integration drift, any t, any frame rate):
//     theta(t) = 2 asin( k sn(K - w0 t, k) ),   w0 = sqrt(g / L)
// with sn the Jacobi elliptic sine and K the complete elliptic integral of the first kind, both
// evaluated by the arithmetic-geometric mean (Abramowitz & Stegun 16.4, 17.6).

package com.bytedance.physicslab.solar.core.pendulum

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.sin
import kotlin.math.sqrt

object Pendulum {
    /** Earth standard gravity and friends (m/s^2), same set the Unity app offered. */
    val G = doubleArrayOf(1.62, 3.71, 9.81, 24.79)
    /** Display names: [G_NAME] is the Chinese-UI label (with the English key term), [G_NAME_EN] the English one. */
    val G_NAME = listOf("月球 Moon", "火星 Mars", "地球 Earth", "木星 Jupiter")
    val G_NAME_EN = listOf("Moon", "Mars", "Earth", "Jupiter")

    fun omega0(l: Double, g: Double) = sqrt(g / l)

    /** Small-angle period 2 pi sqrt(L/g). */
    fun period0(l: Double, g: Double) = 2 * PI * sqrt(l / g)

    /** Exact period for amplitude theta0 (rad): 4 K(k) / w0. */
    fun period(l: Double, g: Double, theta0: Double): Double = 4 * ellipK(sin(abs(theta0) / 2)) / omega0(l, g)

    /** Exact angle at time t, released from rest at +theta0 at t = 0. */
    fun angle(theta0: Double, l: Double, g: Double, t: Double): Double {
        if (theta0 == 0.0) return 0.0
        val k = sin(abs(theta0) / 2)
        val kk = ellipK(k)
        val s = sn(kk - omega0(l, g) * t, k)
        return 2 * asin((k * s).coerceIn(-1.0, 1.0)) * if (theta0 < 0) -1 else 1
    }

    /** The approximation the Unity build used (kept for the side-by-side lesson and the tests). */
    fun angleSmall(theta0: Double, l: Double, g: Double, t: Double) = theta0 * kotlin.math.cos(omega0(l, g) * t)

    /** Complete elliptic integral of the first kind K(k) = pi / (2 AGM(1, sqrt(1-k^2))). */
    fun ellipK(k: Double): Double {
        var a = 1.0; var b = sqrt(1 - k * k)
        repeat(40) { if (abs(a - b) < 1e-15) return PI / (2 * a); val an = (a + b) / 2; b = sqrt(a * b); a = an }
        return PI / (2 * a)
    }

    /** Jacobi sn(u, k) by the descending AGM (A&S 16.4.3). */
    fun sn(u: Double, k: Double): Double {
        if (k < 1e-12) return sin(u)
        val a = DoubleArray(32); val c = DoubleArray(32)
        a[0] = 1.0; var b = sqrt(1 - k * k); c[0] = k
        var n = 0
        while (n < 30 && abs(c[n]) > 1e-15) {
            a[n + 1] = (a[n] + b) / 2; c[n + 1] = (a[n] - b) / 2; b = sqrt(a[n] * b); n++
        }
        var phi = (1 shl n).toDouble() * a[n] * u
        for (j in n downTo 1) phi = (phi + asin((c[j] / a[j] * sin(phi)).coerceIn(-1.0, 1.0))) / 2
        return sin(phi)
    }

    // ---------------------------------------------------------------- pendulum wave

    /**
     * The classic "pendulum wave" (Harvard Natural Sciences demo): pendulum n makes exactly
     * (n0 + n) oscillations in [window] seconds, so the row realigns every [window] s and passes
     * through travelling waves, standing patterns and apparent chaos in between.
     * L_n = g (window / (2 pi (n0 + n)))^2   (small-amplitude periods; amplitude is kept small).
     */
    fun waveLengths(count: Int, window: Double, n0: Int, g: Double = 9.81) =
        DoubleArray(count) { n -> val t = window / (n0 + n); g * (t / (2 * PI)) * (t / (2 * PI)) }
}

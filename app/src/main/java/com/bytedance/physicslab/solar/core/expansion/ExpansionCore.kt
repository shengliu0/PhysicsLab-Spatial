// ExpansionCore — 's Gravesande's ball and ring (1720), pure Kotlin: linear expansion with tabulated
// coefficients, the exact ball-through-ring clearance, a lumped thermal model of the apparatus,
// and the visible colour/brightness of hot metal from Planck's law.
//
// WHAT THE UNITY BUILD DID (ThermalExpansionDemo.cs + ThermoCore.cs) AND WHAT WAS WRONG WITH IT
//   * Geometry: ball Ø0.140 m, ring major Ø0.150 m with a 0.012 m tube radius -> clear aperture
//     Ø0.126 m. The COLD ball was already 14 mm wider than the hole and sat interpenetrating the
//     ring; it never moved. "Cold passes, hot sticks" — the experiment itself — never happened.
//   * Exaggeration x400 on the diameter: aluminium at dT = 500 K grew 0.14 -> 0.78 m (x5.6), while
//     the readout said "1.15 % of its size".
//   * Glow: colour from ProcTex.Blackbody(3800..4900 K) (which also clamps everything below 3800 K
//     to the 3800 K colour), driven by a 0..1 "heat" sine — so a ball at dT = 100 K (393 K) glowed
//     as orange as one at dT = 500 K. Real metal shows NO visible glow below the Draper point
//     (~798 K, 525 °C); every Unity setting (393-793 K) was below it, and 3800-4900 K is 5-6x the
//     metal's temperature (aluminium melts at 933 K).
//   * No thermal model: ball and ring followed sin(0.9 t) and never exchanged heat; the ring never
//     warmed, so the classroom ending (the ball sitting on the ring cools, the ring warms, and the
//     ball drops through) could not occur.
// Here: real 25.40 mm ball / 25.45 mm hole, tabulated alpha, exact clearance, lumped heat flow
// (flame, contact conduction, convection, radiation, water quench), and incandescence from the
// metal's real temperature through Planck x CIE 1931 -> sRGB, invisible below the Draper point.

package com.bytedance.physicslab.solar.core.expansion

import kotlin.math.exp
import kotlin.math.expm1
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.PI

/** Room-temperature properties (CRC Handbook / engineering tables), emissivity of an OXIDISED surface. */
data class Metal(
    val nameZh: String, val nameEn: String,
    val alpha: Double,          // linear expansion coefficient near 20 °C, 1/K
    val density: Double,        // kg/m³
    val heatCapacity: Double,   // J/(kg K)
    val emissivity: Double,     // total hemispherical, oxidised (a flame-heated part is never polished)
    val meltK: Double,          // melting (solidus) temperature, K
    val heatTargetC: Double,    // how hot the demo heats it: glowing where the metal allows it
)

object Metals {
    val BRASS = Metal("黄铜", "brass", 19.0e-6, 8470.0, 380.0, 0.60, 1173.0, 700.0)
    val COPPER = Metal("紫铜", "copper", 16.5e-6, 8960.0, 385.0, 0.65, 1357.8, 720.0)
    val STEEL = Metal("钢", "steel", 12.0e-6, 7850.0, 470.0, 0.80, 1700.0, 700.0)
    /** Melts at 660 °C: the demo stops at 450 °C — below the Draper point, so it never glows. */
    val ALUMINIUM = Metal("铝", "aluminium", 23.1e-6, 2700.0, 897.0, 0.20, 933.5, 450.0)
    val ALL = listOf(BRASS, COPPER, STEEL, ALUMINIUM)
}

object Expansion {
    const val T0 = 293.15                // the apparatus is machined at 20 °C

    /** L(T) = L0 (1 + alpha (T - T0)): every length of the part, holes included. */
    fun length(l0: Double, alpha: Double, t: Double) = l0 * (1 + alpha * (t - T0))

    /**
     * Diametral clearance hole - ball (m). > 0: the ball passes; < 0: it rests on the rim.
     * The hole of a heated ring grows exactly like the metal around it (uniform dilation).
     */
    fun clearance(holeD0: Double, ballD0: Double, ring: Metal, ball: Metal, tRing: Double, tBall: Double) =
        length(holeD0, ring.alpha, tRing) - length(ballD0, ball.alpha, tBall)

    /** Ball temperature at which the clearance closes, for a ring at [tRing] (same or different metal). */
    fun jamTemperature(holeD0: Double, ballD0: Double, ring: Metal, ball: Metal, tRing: Double) =
        T0 + (length(holeD0, ring.alpha, tRing) / ballD0 - 1) / ball.alpha
}

/**
 * The classroom apparatus: a Ø25.40 mm ball on a rod, a ring with a Ø25.45 mm hole (50 µm
 * diametral clearance at 20 °C), both of [metal]. Lumped (uniform-temperature) heat flow:
 *   ball:  flame G_f (T_f - T_b), contact G_c (T_r - T_b), water G_q (T_w - T_b),
 *          convection h A (T_a - T_b), radiation eps sigma A (T_a⁴ - T_b⁴)
 *   ring:  the same terms with its own area, plus conduction into its stand G_s (T_a - T_r).
 * Coefficients are order-of-magnitude engineering values (Bunsen flame impingement ~125 W/m²K,
 * natural convection 12 W/m²K, quench in water ~3000 W/m²K, ball-on-rim contact 0.08 W/K: a line
 * contact under 0.7 N plus the thin air film beside it); the lesson rests on the expansion maths,
 * which is exact — these only set how many seconds each step takes.
 */
class BallRing(var metal: Metal) {
    companion object {
        const val BALL_D0 = 0.02540
        const val HOLE_D0 = 0.02545
        const val RING_OD = 0.036
        const val RING_T = 0.005
        const val SIGMA = 5.670374419e-8
        const val T_FLAME = 1500.0
        const val H_CONV = 12.0
        const val G_FLAME_BALL = 0.25
        const val G_FLAME_RING = 0.18
        const val G_CONTACT = 0.08
        const val G_QUENCH = 6.0
        const val G_STAND = 0.03
    }

    var tAmbient = Expansion.T0
    var tBall = Expansion.T0
    var tRing = Expansion.T0
    var flameOnBall = false
    var flameOnRing = false
    var contact = false
    var quench = false
    /** Convection, radiation and the stand (off only in tests that isolate the contact exchange). */
    var losses = true

    val ballVolume get() = PI / 6 * BALL_D0 * BALL_D0 * BALL_D0
    val ballArea get() = PI * BALL_D0 * BALL_D0
    val ringVolume get() = PI / 4 * (RING_OD * RING_OD - HOLE_D0 * HOLE_D0) * RING_T
    val ringArea get() = 2 * PI / 4 * (RING_OD * RING_OD - HOLE_D0 * HOLE_D0) + PI * (RING_OD + HOLE_D0) * RING_T
    val cBall get() = metal.density * ballVolume * metal.heatCapacity   // J/K
    val cRing get() = metal.density * ringVolume * metal.heatCapacity

    val ballD get() = Expansion.length(BALL_D0, metal.alpha, tBall)
    val holeD get() = Expansion.length(HOLE_D0, metal.alpha, tRing)
    val clearance get() = holeD - ballD
    val passes get() = clearance >= 0
    /** Hottest ball that still passes the ring at its present temperature. */
    val jamT get() = Expansion.jamTemperature(HOLE_D0, BALL_D0, metal, metal, tRing)

    fun reset(tb: Double = Expansion.T0, tr: Double = Expansion.T0) { tBall = tb; tRing = tr }

    /** Heat flow into the ball and the ring (W) at the present state. */
    fun flows(out: DoubleArray) {
        val ta = tAmbient
        var qb = 0.0; var qr = 0.0
        if (losses) {
            qb = -H_CONV * ballArea * (tBall - ta) - metal.emissivity * SIGMA * ballArea * (q4(tBall) - q4(ta))
            qr = -H_CONV * ringArea * (tRing - ta) - metal.emissivity * SIGMA * ringArea * (q4(tRing) - q4(ta)) -
                G_STAND * (tRing - ta)
        }
        if (flameOnBall) qb += G_FLAME_BALL * (T_FLAME - tBall)
        if (flameOnRing) qr += G_FLAME_RING * (T_FLAME - tRing)
        if (quench) qb += G_QUENCH * (Expansion.T0 - tBall)
        if (contact) { val q = G_CONTACT * (tBall - tRing); qb -= q; qr += q }
        out[0] = qb; out[1] = qr
    }

    private val k1 = DoubleArray(2); private val k2 = DoubleArray(2)

    /** Advance [dt] seconds of real time (midpoint rule, substeps <= 0.05 s: the quench is stiff). */
    fun step(dt: Double) {
        if (dt <= 0) return
        val n = kotlin.math.ceil(dt / 0.05).toInt().coerceAtLeast(1)
        val h = dt / n
        repeat(n) {
            val b0 = tBall; val r0 = tRing
            flows(k1)
            tBall = b0 + 0.5 * h * k1[0] / cBall; tRing = r0 + 0.5 * h * k1[1] / cRing
            flows(k2)
            tBall = b0 + h * k2[0] / cBall; tRing = r0 + h * k2[1] / cRing
        }
    }

    private fun q4(t: Double) = t * t * t * t
}

/**
 * Incandescence from Planck's law. Spectral radiance B(lambda, T) integrated against the CIE 1931
 * 2° colour-matching functions (Wyman, Sloan & Shirley 2013 multi-lobe fit, chromaticity within
 * 0.003 of the tabulated Planckian locus above 2000 K, ~0.01 at 1000 K where the fit is weakest),
 * XYZ -> linear sRGB. Below ~1500 K the glow lies outside the sRGB gamut on the red side: it is
 * shown as the nearest displayable red (negative channels clipped), which is what it looks like.
 */
object Blackbody {
    const val H = 6.62607015e-34
    const val C = 2.99792458e8
    const val KB = 1.380649e-23
    const val WIEN_B = 2.897771955e-3
    /** Draper (1847): every solid starts to glow a faint red at about this temperature, in the dark. */
    const val DRAPER_K = 798.0
    /** Display scale for the glow: brightness 1 at this many decades above the Draper luminance. */
    const val GLOW_DECADES = 3.0

    /** Spectral radiance, W sr⁻¹ m⁻³. */
    fun planck(lambdaM: Double, t: Double): Double =
        2 * H * C * C / (lambdaM * lambdaM * lambdaM * lambdaM * lambdaM) / expm1(H * C / (lambdaM * KB * t))

    private fun lobe(l: Double, mu: Double, s1: Double, s2: Double): Double {
        val s = if (l < mu) s1 else s2; val d = (l - mu) / s; return exp(-0.5 * d * d)
    }

    /** CIE 1931 x̄ ȳ z̄ at [nm] (multi-lobe Gaussian fit). */
    fun cmf(nm: Double, out: DoubleArray) {
        out[0] = 1.056 * lobe(nm, 599.8, 37.9, 31.0) + 0.362 * lobe(nm, 442.0, 16.0, 26.7) - 0.065 * lobe(nm, 501.1, 20.4, 26.2)
        out[1] = 0.821 * lobe(nm, 568.8, 46.9, 40.5) + 0.286 * lobe(nm, 530.9, 16.3, 31.1)
        out[2] = 1.217 * lobe(nm, 437.0, 11.8, 36.0) + 0.681 * lobe(nm, 459.0, 26.0, 13.8)
    }

    /** Tristimulus X, Y, Z of a blackbody at [t] (1 nm steps, 360-830 nm; arbitrary common unit). */
    fun xyz(t: Double, out: DoubleArray) {
        val c = DoubleArray(3); var x = 0.0; var y = 0.0; var z = 0.0
        for (nm in 360..830) {
            val b = planck(nm * 1e-9, t); cmf(nm.toDouble(), c)
            x += b * c[0]; y += b * c[1]; z += b * c[2]
        }
        out[0] = x; out[1] = y; out[2] = z
    }

    fun luminance(t: Double): Double { val o = DoubleArray(3); xyz(t, o); return o[1] }

    /** CIE 1931 chromaticity (x, y). */
    fun chromaticity(t: Double): DoubleArray { val o = DoubleArray(3); xyz(t, o); val s = o[0] + o[1] + o[2]; return doubleArrayOf(o[0] / s, o[1] / s) }

    /** Linear sRGB of the glow's COLOUR, brightest channel = 1 (negative channels clipped). */
    fun linearSrgb(t: Double, out: DoubleArray) {
        val o = DoubleArray(3); xyz(t, o)
        val r = 3.2406 * o[0] - 1.5372 * o[1] - 0.4986 * o[2]
        val g = -0.9689 * o[0] + 1.8758 * o[1] + 0.0415 * o[2]
        val b = 0.0557 * o[0] - 0.2040 * o[1] + 1.0570 * o[2]
        val m = max(r, max(g, b))
        out[0] = max(0.0, r) / m; out[1] = max(0.0, g) / m; out[2] = max(0.0, b) / m
    }

    /** Wavelength of peak spectral radiance, found numerically (Wien's law says b / T). */
    fun peakWavelength(t: Double): Double {
        var lo = 1e-8; var hi = 1e-3
        repeat(200) { val a = lo + (hi - lo) / 3; val b = hi - (hi - lo) / 3; if (planck(a, t) < planck(b, t)) lo = a else hi = b }
        return (lo + hi) / 2
    }

    /** Total radiant exitance pi * integral of B over wavelength (= sigma T⁴), numerically. */
    fun exitance(t: Double): Double {
        // integrate in ln(lambda) from 50 nm to 1 m
        val a = ln(5e-8); val b = ln(1.0); val n = 20000; val h = (b - a) / n
        var s = 0.0
        for (i in 0..n) { val l = exp(a + i * h); val w = if (i == 0 || i == n) 0.5 else 1.0; s += w * planck(l, t) * l }
        return PI * s * h
    }

    private val yDraper by lazy { luminance(DRAPER_K) }

    /**
     * Display brightness 0..1 of a surface of emissivity [eps] at [t]: 0 until its visible
     * luminance eps Y(T) reaches the Draper threshold Y(798 K), then logarithmic (the eye's
     * response), 1 at [GLOW_DECADES] decades above it. Luminance grows ~600x from 798 K to 1023 K.
     */
    fun glow(t: Double, eps: Double): Double {
        if (t < 600) return 0.0
        val r = eps * luminance(t) / yDraper
        return if (r <= 1) 0.0 else (log10(r) / GLOW_DECADES).coerceIn(0.0, 1.0)
    }
}

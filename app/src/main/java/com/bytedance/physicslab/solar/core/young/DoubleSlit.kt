// DoubleSlit — Young's two-slit interference with real numbers, pure Kotlin.
//
// Two slits of width a, centres d apart, lit coherently by light of wavelength λ; screen at L.
// Far field (Fraunhofer), angle θ from the axis:
//     I(θ) = sinc²(β) · (1 + V cos 2α) / 2,   α = π d sinθ / λ,   β = π a sinθ / λ
// normalised to 1 on the axis. cos² fringes (spacing Δy = λL/d on the screen) under the single-slit
// sinc² envelope (first zero at sinθ = λ/a), so orders with d/a = integer vanish ("missing orders").
// V is the fringe visibility left by a source slit of finite width a0 at distance L0 in front of
// the double slit (van Cittert–Zernike): V = |sinc(π a0 d / (λ L0))| — Young's reason for a pinhole.
// With one slit covered the pattern is just sinc²(β)/4: no zeros, and places that were dark get
// BRIGHTER — adding light from the second slit is what makes them dark.
//
// UNITY AUDIT. The Unity "InterferenceDemo" was two speakers (sound), not Young's slits. Its dots
// were coloured by AMPLITUDE |cos(πΔ/λ)| instead of intensity cos²(πΔ/λ) — bright lanes look 66% of
// the period wide instead of 50% — and it gave both sources equal amplitude everywhere, so it
// promised "loud to silent" in nodal lanes. Real amplitudes fall with distance; near the sources
// a "silent" lane is not silent (see [nodalMinimum]: -9 dB, not -inf, at its own grid point).

package com.bytedance.physicslab.solar.core.young

import com.bytedance.physicslab.solar.core.prism.Spectrum
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object DoubleSlit {
    fun sinc(x: Double) = if (abs(x) < 1e-8) 1 - x * x / 6 else sin(x) / x

    /**
     * Normalised far-field intensity at direction sinθ (= y / sqrt(y² + L²) on a screen).
     * [lambda], [d], [a] in metres; [v] fringe visibility; [open1]/[open2] = slits uncovered.
     * Both open, V = 1, θ = 0  ->  1.
     */
    fun intensity(sinT: Double, lambda: Double, d: Double, a: Double, v: Double = 1.0,
                  open1: Boolean = true, open2: Boolean = true): Double {
        if (!open1 && !open2) return 0.0
        val env = sinc(PI * a * sinT / lambda).let { it * it }
        if (!(open1 && open2)) return env / 4
        return env * (1 + v * cos(2 * PI * d * sinT / lambda)) / 2
    }

    /** Small-angle fringe spacing on the screen: Δy = λ L / d. */
    fun fringeSpacing(lambda: Double, l: Double, d: Double) = lambda * l / d

    /** Exact screen height of bright order m: d sinθ = mλ, y = L tanθ. NaN if |mλ/d| >= 1. */
    fun brightY(m: Int, lambda: Double, l: Double, d: Double): Double {
        val s = m * lambda / d
        return if (abs(s) >= 1) Double.NaN else l * s / sqrt(1 - s * s)
    }

    /** Fringe visibility from a source slit of width [a0] at [l0] before the double slit. */
    fun visibility(a0: Double, d: Double, lambda: Double, l0: Double) = abs(sinc(PI * a0 * d / (lambda * l0)))

    /**
     * Direct Huygens–Fresnel sum (no far-field approximation): cylindrical wavelets e^{ikr}/sqrt(r)
     * with the Kirchhoff obliquity (1 + cosχ)/2 from [samples] points across each open slit, to
     * the screen point (L, y). Returns |ψ|², unnormalised. Used to validate [intensity].
     */
    fun huygens(y: Double, lambda: Double, d: Double, a: Double, l: Double, samples: Int = 200,
                open1: Boolean = true, open2: Boolean = true): Double {
        val k = 2 * PI / lambda
        var re = 0.0; var im = 0.0
        for (slit in 0..1) {
            if ((slit == 0 && !open1) || (slit == 1 && !open2)) continue
            val c = if (slit == 0) d / 2 else -d / 2
            for (j in 0 until samples) {
                val s = c - a / 2 + a * (j + 0.5) / samples
                val dy = y - s
                val r = sqrt(l * l + dy * dy)
                val w = (1 + l / r) / 2 / sqrt(r)
                re += w * cos(k * r); im += w * sin(k * r)
            }
        }
        return re * re + im * im
    }

    /**
     * White light (equal-energy, white-balanced): linear-sRGB intensity of the fringe pattern at
     * sinθ, summed over the visible spectrum with CIE weights. (1,1,1) on the axis with V = 1.
     * [vis] gives the visibility per wavelength (metres -> V), default fully coherent.
     */
    fun whiteRgb(sinT: Double, d: Double, a: Double, open1: Boolean = true, open2: Boolean = true,
                 vis: (Double) -> Double = { 1.0 }): DoubleArray {
        val out = DoubleArray(3)
        for (l in Spectrum.GRID) {
            val lm = l * 1e-9
            val i = intensity(sinT, lm, d, a, vis(lm), open1, open2)
            val w = Spectrum.weights(l)
            for (c in 0..2) out[c] += w[c] * i
        }
        return out
    }

    /**
     * Interference minimum relative to maximum when the two waves arrive with amplitudes A1 >= A2:
     * ((A1 - A2)/(A1 + A2))². Equal amplitudes -> 0 (silent); unequal (near the sources) -> not.
     */
    fun nodalMinimum(a1: Double, a2: Double): Double { val r = (a1 - a2) / (a1 + a2); return r * r }
}

// PhotoCore — the photoelectric effect (Einstein 1905, Millikan 1916), pure Kotlin.
//
// AUDIT OF THE UNITY SOURCE (PhotoelectricDemo.cs + ModernCore.cs):
//   * DEFAULT SHOWED NOTHING: default light RED = 0.46 PHz (652 nm, hf = 1.90 eV) on caesium
//     (W = 2.1 eV): below threshold, so a visitor opening the demo saw no electrons at all.
//   * ZINC COULD NEVER EMIT: zinc W = 4.3 eV needs f > 1.04 PHz (lambda < 288 nm), but the bluest
//     source offered was "UV" = 0.90 PHz (333 nm, 3.72 eV). One of the two metals was a dead
//     option that silently taught "zinc is not photosensitive" — the opposite of Hallwachs' 1888
//     zinc-plate experiment, which used the 254 nm mercury line. Here the source is continuous
//     200-700 nm (and the mercury lines 254/365/405/436/546/578 nm are presets).
//   * No stopping voltage, no anode, no current: the experiment that actually measured
//     E_k = hf - W (Lenard 1902, Millikan 1916: U_c versus f, slope h/e) was missing. Here the tube
//     has a cathode, a mesh anode, a reverse/forward voltage and an I-U curve.
//   * Every electron got the SAME speed sqrt(KE_max)*0.25, straight up. Real photoelectrons leave
//     with energies from 0 to E_k,max in a cosine (Lambert) distribution of directions; that is what
//     makes the I-U curve fall smoothly to zero exactly at -U_c. Here the particles and the curve
//     come from the same model (so the dots you see are the current you read).
//
// Units: energies in eV, wavelengths in nm, voltages in V, currents in A.

package com.bytedance.physicslab.solar.core.photoelectric

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

object Photo {
    const val H = 6.62607015e-34          // J s (exact, SI 2019)
    const val QE = 1.602176634e-19        // C (exact)
    const val C = 299_792_458.0           // m/s
    const val ME = 9.1093837015e-31       // kg
    const val HC_EV_NM = H * C / QE * 1e9 // 1239.84198 eV nm

    data class Metal(val name: String, val nameEn: String, val symbol: String, val workEv: Double)   // name = Chinese, nameEn = English
    /** Work functions (polycrystalline, eV): Cs 2.14 (CRC), Na 2.28 (Halliday's table), Zn 4.33 (CRC). */
    val METALS = listOf(Metal("铯", "Caesium", "Cs", 2.14), Metal("钠", "Sodium", "Na", 2.28), Metal("锌", "Zinc", "Zn", 4.33))
    /** Mercury-lamp lines (nm) — the source Millikan and Hallwachs actually used — plus a red 650. */
    val HG_LINES = doubleArrayOf(254.0, 365.0, 405.0, 436.0, 546.0, 578.0, 650.0)

    fun photonEv(lambdaNm: Double) = HC_EV_NM / lambdaNm
    fun freqHz(lambdaNm: Double) = C / (lambdaNm * 1e-9)
    fun thresholdHz(workEv: Double) = workEv * QE / H
    fun thresholdNm(workEv: Double) = HC_EV_NM / workEv
    /** Einstein: E_k,max = h f - W (eV), 0 below threshold — however bright the light. */
    fun ekMax(lambdaNm: Double, workEv: Double) = max(0.0, photonEv(lambdaNm) - workEv)
    /** Stopping voltage U_c = E_k,max / e (numerically equal in volts). */
    fun stoppingVoltage(lambdaNm: Double, workEv: Double) = ekMax(lambdaNm, workEv)
    fun speedOf(ekEv: Double) = sqrt(2 * ekEv * QE / ME)

    /**
     * Quantum yield near threshold (Fowler 1931: yield ~ (h f - W)^2), capped at 10 %. The
     * prefactor (2 % per eV^2) is a typical clean alkali surface; only the shape matters here.
     */
    fun yield(lambdaNm: Double, workEv: Double): Double { val d = photonEv(lambdaNm) - workEv; return if (d <= 0) 0.0 else minOf(0.10, 0.02 * d * d) }

    /** Saturation photocurrent for [powerW] of light: e * yield * P / (h f). Linear in the intensity. */
    fun saturationCurrent(lambdaNm: Double, workEv: Double, powerW: Double) =
        QE * yield(lambdaNm, workEv) * powerW / (photonEv(lambdaNm) * QE)

    /** Visible-spectrum sRGB for a wavelength (Bruton's piecewise fit), UV/IR as dim violet/red. */
    fun spectralRgb(lambdaNm: Double): DoubleArray {
        val l = lambdaNm
        var r: Double; var g: Double; var b: Double
        when {
            l < 380 -> { r = 0.55; g = 0.35; b = 1.0 }
            l < 440 -> { r = -(l - 440) / 60; g = 0.0; b = 1.0 }
            l < 490 -> { r = 0.0; g = (l - 440) / 50; b = 1.0 }
            l < 510 -> { r = 0.0; g = 1.0; b = -(l - 510) / 20 }
            l < 580 -> { r = (l - 510) / 70; g = 1.0; b = 0.0 }
            l < 645 -> { r = 1.0; g = -(l - 645) / 65; b = 0.0 }
            else -> { r = 1.0; g = 0.0; b = 0.0 }
        }
        val k = when { l < 380 -> 0.75; l < 420 -> 0.35 + 0.65 * (l - 380) / 40; l > 700 -> 0.35 + 0.65 * (780 - l) / 80; else -> 1.0 }
        fun gam(c: Double) = if (c <= 0) 0.0 else (c * k).pow(0.8)
        return doubleArrayOf(gam(r), gam(g), gam(b))
    }
}

/**
 * One photoelectron, dimensionless: energy E = u * E_k,max, direction from the cathode normal
 * with cos^2(theta) = c2 (Lambert's cosine law makes c2 uniform on 0..1), azimuth phi, and a
 * start point (rx, ry) in the lit spot, in units of the cathode-anode gap d.
 */
class Photoelectron(val u: Double, val c2: Double, val phi: Double, val rx: Double, val ry: Double) {
    private val cp = cos(phi); private val sp = sin(phi)
    private val un = u * c2; private val vn = sqrt(un); private val vt = sqrt(u * (1 - c2))

    /**
     * Does it reach a coaxial anode disc of radius [anodeRatio] d, under s = eU / E_k,max?
     * Uniform field between parallel plates: x/d = sqrt(u c2) T + s T^2 / 4 (T in d / v_max units),
     * lateral = r0 + sqrt(u (1 - c2)) T.
     */
    fun collected(s: Double, anodeRatio: Double): Boolean {
        if (un + s < 0) return false                         // turned back by the retarding field
        val disc = vn + sqrt(un + s)
        if (disc <= 1e-12) return false
        val t = 2 / disc                                     // first arrival at x = d (stable root)
        val lx = rx + vt * t * cp; val ly = ry + vt * t * sp
        return lx * lx + ly * ly <= anodeRatio * anodeRatio
    }

    companion object {
        /** Low-discrepancy (Halton) samples, so curves are smooth and deterministic. */
        fun sample(i: Int, spotRatio: Double): Photoelectron {
            fun halton(n: Int, b: Int): Double { var f = 1.0; var r = 0.0; var k = n; while (k > 0) { f /= b; r += f * (k % b); k /= b }; return r }
            val u = halton(i + 1, 2); val c2 = halton(i + 1, 3); val phi = 2 * PI * halton(i + 1, 5)
            val rr = spotRatio * sqrt(halton(i + 1, 7)); val pa = 2 * PI * halton(i + 1, 11)
            return Photoelectron(u, c2, phi, rr * cos(pa), rr * sin(pa))
        }
    }
}

/**
 * A phototube: parallel cathode and mesh anode (radius [anodeRatio] x gap), lit spot of radius
 * [spotRatio] x gap. [fraction](s) = collected / emitted, tabulated once over s = eU/E_k,max
 * (dimensionless, so ONE table serves every metal, colour and intensity).
 */
class PhotoTube(val anodeRatio: Double = 1.2, val spotRatio: Double = 0.12, n: Int = 3000) {
    // fine grid where the curve bends (-U_c .. a few U_c), coarse one out to saturation
    private val s0 = -1.05; private val s1 = 3.0; private val s2 = 40.0; private val m1 = 320; private val m2 = 80
    private val fine: DoubleArray; private val coarse: DoubleArray

    init {
        val es = Array(n) { Photoelectron.sample(it, spotRatio) }
        fun frac(s: Double) = es.count { it.collected(s, anodeRatio) }.toDouble() / n
        fine = DoubleArray(m1 + 1) { frac(s0 + (s1 - s0) * it / m1) }
        coarse = DoubleArray(m2 + 1) { frac(s1 + (s2 - s1) * it / m2) }
    }

    fun fraction(s: Double): Double {
        val (t, a, b, m) = if (s < s1) listOf(fine, s0, s1, m1) else listOf(coarse, s1, s2, m2)
        t as DoubleArray; a as Double; b as Double; m as Int
        val f = ((s - a) / (b - a) * m).coerceIn(0.0, m.toDouble())
        val i = f.toInt().coerceAtMost(m - 1); val w = f - i
        return t[i] + (t[i + 1] - t[i]) * w
    }

    /** Photocurrent at anode voltage [u] (V) for saturation current [iSat] and E_k,max [ek] (eV). */
    fun current(u: Double, iSat: Double, ek: Double): Double = if (ek <= 0) 0.0 else iSat * fraction(u / ek)

    companion object {
        /** Infinite anode (every electron that reaches the anode plane counts): F = (1-s) + s ln s, s = e|U|/E_k,max. */
        fun infinitePlateFraction(sNeg: Double): Double { val s = -sNeg; return if (s >= 1) 0.0 else if (s <= 0) 1.0 else (1 - s) + s * ln(s) }
    }
}

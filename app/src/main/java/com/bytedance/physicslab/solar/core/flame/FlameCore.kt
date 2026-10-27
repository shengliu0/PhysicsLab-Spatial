// FlameCore — flame tests and atomic spectra (Bunsen & Kirchhoff, 1860), pure Kotlin.
//
// THE CHAIN, all from physics, nothing looked up by colour name:
//   salt in the flame -> its emitters (atoms, or hot molecules like CaOH) -> emission features
//   (lines at NIST wavelengths, molecular bands) x relative radiant power -> CIE 1931 XYZ
//   -> linear sRGB -> displayed flame colour; the colour NAME comes from the dominant wavelength.
//   Optional cobalt-blue glass multiplies every feature by its transmission T(λ) first.
//
// COLOUR MATCHING FUNCTIONS. The brief suggested an analytic fit (Wyman, Sloan & Shirley 2013,
// "Simple Analytic Approximations to the CIE XYZ Color Matching Functions", JCGT 2(2)). It is kept
// here ([Wyman2013]) as an independent cross-check of the CIE table — they agree within ~1 % over
// 450-650 nm — but it is NOT used for the colours: its Gaussian lobes fall like exp(-x²) while the
// real CMFs fall exponentially in the far red. At potassium's 766.5 nm line the fit's x̄ is ~1000x
// too small (FlameCoreTest prints it), so potassium's red line would vanish from the colour sum.
// The colours therefore use the CIE 1931 2° table itself (core/prism/Spectrum.cmf, validated in
// PrismCoreTest) — the thing the fit approximates.
//
// SOURCES.
//   * Atomic lines (air wavelengths) and level energies: NIST Atomic Spectra Database (ASD v5,
//     Kramida, Ralchenko, Reader & NIST ASD Team). Line pairs keep their statistical (gA) ratio 2:1.
//   * Molecular bands (CaOH 554/622, SrOH 606/646/682, BaCl/BaOH 487-532, CuCl 435-538):
//     Pearse & Gaydon, "The Identification of Molecular Spectra", 4th ed. (1976); Gaydon,
//     "The Spectroscopy of Flames", 2nd ed. (1974). Band centres rounded to 1 nm, widths 6-14 nm.
//   * The RELATIVE POWERS are representative Bunsen-flame values (strongest feature of each salt = 1),
//     rounded — the hue depends on them, so they are stated per feature below and printed by the test.
//     Potassium's weak continuum is the one Kirchhoff & Bunsen drew in 1860 (Ann. Phys. 110, 161).
//   * Cobalt glass: Co²⁺ in tetrahedral sites, the spin-allowed ⁴A₂ -> ⁴T₁(P) band split into a
//     triplet near 540 / 592 / 643 nm (Bamford, "Colour Generation and Control in Glass", 1977).
//     Strength chosen for a dense flame-test plate: optical density ~5 at the sodium D lines.
//   * hc = 1239.841984 eV·nm (CODATA 2018, exact SI). Air index: Edlén (1966) dispersion formula.

package com.bytedance.physicslab.solar.core.flame

import com.bytedance.physicslab.solar.core.prism.Spectrum
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** E = hc/λ, and the air <-> vacuum wavelength conversion that NIST level energies need. */
object Photon {
    const val HC_EV_NM = 1239.841984

    /** Photon energy (eV) of wavelength [nm] (the school formula: whatever λ you quote). */
    fun energyEv(nm: Double) = HC_EV_NM / nm

    /** Refractive index of standard dry air at vacuum wavelength [nmVac] (Edlén 1966). */
    fun nAir(nmVac: Double): Double {
        val s2 = (1000.0 / nmVac).pow(2)
        return 1.0 + 1e-8 * (8342.54 + 2406147.0 / (130.0 - s2) + 15998.0 / (38.9 - s2))
    }

    /** Air wavelength of a transition of [dEv] eV (λ_vac = hc/ΔE, then / n_air). */
    fun airNmOf(dEv: Double): Double { val v = HC_EV_NM / dEv; return v / nAir(v) }
}

/**
 * One emission feature. [fwhm] = 0: an atomic line (NIST air wavelength); > 0: a molecular band,
 * a Gaussian of that full width. [power] = integrated radiant power, strongest feature of the salt = 1.
 * [upperEv]/[lowerEv]: the transition's levels (NIST for atoms; bands: hc/λ above the ground state).
 */
data class Feature(
    val nm: Double, val power: Double, val emitter: String, val fwhm: Double = 0.0,
    val upperEv: Double = Photon.energyEv(nm), val lowerEv: Double = 0.0, val upperName: String = "", val lowerName: String = "",
) {
    val isLine get() = fwhm == 0.0
    val visible get() = nm >= 380.0 && nm <= 780.0
}

/**
 * A chloride salt for the flame test. [radiantYield] scales its features when salts are MIXED (equal
 * amounts): potassium's resonance level (1.62 eV) lies lower than sodium's (2.10 eV), so at a Bunsen
 * flame's ~2200 K its excited fraction is exp(0.49 eV / kT) ~ 13x larger; ionisation (K 4.34 eV vs
 * Na 5.14 eV) takes back about half: K ≈ 6 x Na in radiant power. Others ~1 (order of magnitude).
 * [diagram]: indices into [features] drawn in the energy-level diagram, strongest (animated) first.
 */
class Salt(
    val formula: String, val symbol: String, val zh: String, val en: String,
    val features: List<Feature>, val radiantYield: Double, val diagram: List<Int>,
) {
    /** The strongest VISIBLE feature (what sets the flame's character). */
    val main: Feature get() = features.filter { it.visible }.maxByOrNull { it.power }!!
}

object Salts {
    // NIST ASD level energies (eV), converted from cm⁻¹ with 8065.543937 cm⁻¹/eV (CODATA 2018)
    private const val NA_3P = 2.104429; private const val NA_3P1 = 2.102297
    private const val K_4P = 1.617111; private const val K_4P1 = 1.609956
    private const val K_5P = 3.064907; private const val K_5P1 = 3.062581
    private const val LI_2P = 1.847844; private const val LI_3D = 3.878608; private const val LI_4D = 4.540720
    private const val CA_4P = 2.932512; private const val SR_5P = 2.690265; private const val BA_6P = 2.239187
    private const val CU_4P = 3.816691; private const val CU_4P1 = 3.785895

    val NA = Salt("NaCl", "Na", "钠", "sodium", listOf(
        Feature(588.995, 1.00, "Na", upperEv = NA_3P, upperName = "3p", lowerName = "3s"),     // D2
        Feature(589.592, 0.50, "Na", upperEv = NA_3P1, upperName = "3p", lowerName = "3s"),    // D1
    ), 1.0, listOf(0))
    val K = Salt("KCl", "K", "钾", "potassium", listOf(
        Feature(766.490, 1.00, "K", upperEv = K_4P, upperName = "4p", lowerName = "4s"),
        Feature(769.896, 0.50, "K", upperEv = K_4P1, upperName = "4p", lowerName = "4s"),
        Feature(404.414, 0.010, "K", upperEv = K_5P, upperName = "5p", lowerName = "4s"),
        Feature(404.721, 0.005, "K", upperEv = K_5P1, upperName = "5p", lowerName = "4s"),
        Feature(500.0, 0.0010, "K", fwhm = 250.0),                                          // weak continuum (1860 plate)
    ), 6.0, listOf(0, 2))
    val LI = Salt("LiCl", "Li", "锂", "lithium", listOf(
        Feature(670.78, 1.00, "Li", upperEv = LI_2P, upperName = "2p", lowerName = "2s"),      // 670.776 + 670.791, unresolved
        Feature(610.354, 0.030, "Li", upperEv = LI_3D, lowerEv = LI_2P, upperName = "3d", lowerName = "2p"),
        Feature(460.286, 0.004, "Li", upperEv = LI_4D, lowerEv = LI_2P, upperName = "4d", lowerName = "2p"),
    ), 1.5, listOf(0, 1))
    val CA = Salt("CaCl₂", "Ca", "钙", "calcium", listOf(
        Feature(622.0, 1.00, "CaOH", fwhm = 14.0),
        Feature(554.0, 0.30, "CaOH", fwhm = 8.0),
        Feature(422.673, 0.12, "Ca", upperEv = CA_4P, upperName = "4s4p", lowerName = "4s²"),
    ), 1.0, listOf(0))
    val SR = Salt("SrCl₂", "Sr", "锶", "strontium", listOf(
        Feature(646.0, 1.00, "SrOH", fwhm = 12.0),
        Feature(682.0, 0.60, "SrOH", fwhm = 12.0),
        Feature(606.0, 0.40, "SrOH", fwhm = 10.0),
        Feature(460.733, 0.05, "Sr", upperEv = SR_5P, upperName = "5s5p", lowerName = "5s²"),
    ), 1.0, listOf(0))
    val BA = Salt("BaCl₂", "Ba", "钡", "barium", listOf(
        Feature(553.548, 1.00, "Ba", upperEv = BA_6P, upperName = "6s6p", lowerName = "6s²"),
        Feature(524.0, 0.45, "BaCl", fwhm = 8.0),
        Feature(532.0, 0.35, "BaCl", fwhm = 6.0),
        Feature(513.0, 0.30, "BaCl", fwhm = 8.0),
        Feature(487.0, 0.15, "BaOH", fwhm = 10.0),
    ), 1.0, listOf(0))
    val CU = Salt("CuCl₂", "Cu", "铜", "copper", listOf(
        Feature(526.0, 1.00, "CuCl", fwhm = 10.0),
        Feature(510.0, 0.75, "CuCl", fwhm = 8.0),
        Feature(538.0, 0.55, "CuCl", fwhm = 8.0),
        Feature(498.0, 0.55, "CuCl", fwhm = 8.0),
        Feature(488.0, 0.45, "CuCl", fwhm = 8.0),
        Feature(443.0, 0.45, "CuCl", fwhm = 8.0),
        Feature(435.0, 0.40, "CuCl", fwhm = 9.0),
        Feature(452.0, 0.30, "CuCl", fwhm = 8.0),
        Feature(324.754, 0.80, "Cu", upperEv = CU_4P, upperName = "4p", lowerName = "4s"),      // ultraviolet: invisible
        Feature(327.396, 0.40, "Cu", upperEv = CU_4P1, upperName = "4p", lowerName = "4s"),
    ), 1.0, listOf(0, 5))

    val ALL = listOf(NA, K, LI, CA, SR, BA, CU)
}

/** What is on the wire: one salt, or a mixture (equal amounts unless stated). */
class Sample(val parts: List<Pair<Salt, Double>>) {
    constructor(vararg s: Salt) : this(s.map { it to 1.0 })

    /** Every feature with its absolute weight (power x yield x amount). */
    fun weighted(): List<Pair<Feature, Double>> = parts.flatMap { (s, a) -> s.features.map { it to it.power * s.radiantYield * a } }
    val salts get() = parts.map { it.first }
}

/** The Wyman-Sloan-Shirley (2013) multi-lobe fit to the CIE 1931 2° CMFs — cross-check only (see header). */
object Wyman2013 {
    private fun g(x: Double, mu: Double, s1: Double, s2: Double): Double { val t = (x - mu) / (if (x < mu) s1 else s2); return exp(-0.5 * t * t) }
    fun xyz(nm: Double) = doubleArrayOf(
        1.056 * g(nm, 599.8, 37.9, 31.0) + 0.362 * g(nm, 442.0, 16.0, 26.7) - 0.065 * g(nm, 501.1, 20.4, 26.2),
        0.821 * g(nm, 568.8, 46.9, 40.5) + 0.286 * g(nm, 530.9, 16.3, 31.1),
        1.217 * g(nm, 437.0, 11.8, 36.0) + 0.681 * g(nm, 459.0, 26.0, 13.8),
    )
}

/** Cobalt-blue glass: T(λ) = T0 · exp(-Σ Aᵢ exp(-½((λ-cᵢ)/σᵢ)²)), the Co²⁺ triplet. */
object CobaltGlass {
    const val T0 = 0.90                                           // two surfaces (~4 % each) + bulk loss
    private val C = doubleArrayOf(540.0, 592.0, 643.0)
    private val S = doubleArrayOf(17.0, 20.0, 21.0)
    private val A = doubleArrayOf(4.0, 11.3, 5.0)                 // natural-log absorbance at each centre
    fun transmission(nm: Double): Double {
        var a = 0.0
        for (i in C.indices) { val t = (nm - C[i]) / S[i]; a += A[i] * exp(-0.5 * t * t) }
        return T0 * exp(-a)
    }
    fun opticalDensity(nm: Double) = -log10(transmission(nm))
}

/** The colour of a flame, from its spectrum. */
object FlameColour {
    /** CIE 1931 2° standard observer (the table; see the header for why not the analytic fit). */
    fun cmf(nm: Double): DoubleArray = Spectrum.cmf(nm)

    private const val BAND_STEP = 0.5

    /** Add one feature (weight [w]) to [out] = XYZ, optionally seen through cobalt glass. */
    fun addFeature(f: Feature, w: Double, cobalt: Boolean, out: DoubleArray) {
        if (f.isLine) {
            val c = cmf(f.nm); val t = if (cobalt) CobaltGlass.transmission(f.nm) else 1.0
            for (k in 0..2) out[k] += w * t * c[k]
            return
        }
        val sig = f.fwhm / (2 * sqrt(2 * ln(2.0)))
        val norm = BAND_STEP / (sig * sqrt(2 * Math.PI))          // the Gaussian integrates to 1
        var l = f.nm - 3 * f.fwhm
        while (l <= f.nm + 3 * f.fwhm + 1e-9) {
            val p = exp(-0.5 * ((l - f.nm) / sig).pow(2)) * norm
            val c = cmf(l); val t = if (cobalt) CobaltGlass.transmission(l) else 1.0
            for (k in 0..2) out[k] += w * p * t * c[k]
            l += BAND_STEP
        }
    }

    fun xyz(sample: Sample, cobalt: Boolean = false): DoubleArray {
        val out = DoubleArray(3)
        for ((f, w) in sample.weighted()) addFeature(f, w, cobalt, out)
        return out
    }

    fun chromaticity(xyz: DoubleArray): DoubleArray { val s = xyz.sum().coerceAtLeast(1e-30); return doubleArrayOf(xyz[0] / s, xyz[1] / s) }

    /** Linear sRGB with negative (out-of-gamut) channels clipped, brightest channel = 1. */
    fun chroma(xyz: DoubleArray): DoubleArray {
        val c = Spectrum.xyzToRgb(xyz[0], xyz[1], xyz[2])
        for (k in 0..2) c[k] = c[k].coerceAtLeast(0.0)
        val m = max(c[0], max(c[1], c[2]))
        return if (m <= 0.0) doubleArrayOf(0.0, 0.0, 0.0) else DoubleArray(3) { c[it] / m }
    }

    /** HSV hue (deg) of the ENCODED display colour (the colour as a screen shows it). */
    fun hueDeg(xyz: DoubleArray): Double { val c = chroma(xyz); return Spectrum.hueDeg(DoubleArray(3) { Spectrum.encode(c[it]) }) }

    // spectral locus 380..700 nm every 1 nm (beyond 700 nm the locus is a single point)
    private val LOCUS: Array<DoubleArray> by lazy { Array(321) { i -> chromaticity(cmf(380.0 + i)) } }
    /** sRGB / D65 white point. */
    const val WX = 0.3127; const val WY = 0.3290

    /**
     * Dominant wavelength (nm): where the ray from the D65 white point through the colour meets the
     * spectral locus. NaN for purples (the ray meets the purple line instead).
     */
    fun dominantNm(xyz: DoubleArray): Double {
        val (x, y) = chromaticity(xyz).let { it[0] to it[1] }
        val dx = x - WX; val dy = y - WY
        if (abs(dx) + abs(dy) < 1e-9) return Double.NaN
        var bestT = Double.MAX_VALUE; var best = Double.NaN
        for (i in 0 until LOCUS.size - 1) {
            val x0 = LOCUS[i][0]; val y0 = LOCUS[i][1]; val ex = LOCUS[i + 1][0] - x0; val ey = LOCUS[i + 1][1] - y0
            val den = dx * (-ey) + dy * ex
            if (abs(den) < 1e-15) continue
            val t = ((x0 - WX) * (-ey) + (y0 - WY) * ex) / den
            val s = (dx * (y0 - WY) - dy * (x0 - WX)) / den
            if (t > 0 && s >= 0 && s <= 1 && t < bestT) { bestT = t; best = 380.0 + i + s }
        }
        return best
    }

    /**
     * Colour name (zh, en) from the dominant wavelength — boundaries simplified after Kelly (1943,
     * J. Opt. Soc. Am. 33, 627) — and, for purples, from the hue.
     */
    fun name(xyz: DoubleArray): Pair<String, String> {
        val d = dominantNm(xyz)
        if (d.isNaN()) return if (hueDeg(xyz) < 300.0) "紫" to "violet" else "紫红" to "purple-red"
        return when {
            d >= 640 -> "深红" to "crimson"
            d >= 610 -> "红" to "red"
            d >= 595 -> "橙红" to "orange-red"
            d >= 583 -> "橙黄" to "yellow-orange"
            d >= 573 -> "黄" to "yellow"
            d >= 530 -> "黄绿" to "yellow-green"
            d >= 498 -> "绿" to "green"
            d >= 485 -> "蓝绿" to "blue-green"
            d >= 450 -> "蓝" to "blue"
            else -> "蓝紫" to "blue-violet"
        }
    }

    // ---- display brightness (a display mapping, not physics) ----
    // Every plain salt is shown at full brightness (the eye adapts to a flame). Behind cobalt glass the
    // luminance Y can drop by 5 decades (sodium); it is mapped on a log scale between Y_MIN (invisible)
    // and Y_FULL = 0.8 x the faintest plain salt (KCl), so "dimmer but there" and "gone" both read.
    private val Y_FULL: Double by lazy { 0.8 * xyz(Sample(Salts.K))[1] }
    private val Y_MIN: Double by lazy { Y_FULL / 100.0 }
    fun level(xyz: DoubleArray): Double = (log10(max(xyz[1], 1e-30) / Y_MIN) / log10(Y_FULL / Y_MIN)).coerceIn(0.0, 1.0)

    /** Brightness of a spectrometer line on the strip: radiant power, compressed (^0.45) like a log display. */
    fun lineBrightness(power: Double, maxPower: Double): Double = if (power <= 0 || maxPower <= 0) 0.0 else (power / maxPower).pow(0.45).coerceIn(0.0, 1.0)

    /** [c] (linear, max 1) exposed [k] x and clipped per channel: a bright flame core saturates toward yellow/white. */
    fun exposed(c: DoubleArray, k: Double) = DoubleArray(3) { min(1.0, c[it] * k) }
}

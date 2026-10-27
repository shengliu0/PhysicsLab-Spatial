// Glass — refractive index n(λ) of real optical glasses, pure Kotlin.
//
// The Unity PrismDemo used six hand-typed indices (1.513 ... 1.528) with no wavelengths attached —
// roughly a crown glass, but nothing could be checked. Here n(λ) comes from the three-term
// Sellmeier equation with the manufacturer's coefficients (SCHOTT optical glass datasheets):
//     n²(λ) = 1 + Σ Bᵢ λ² / (λ² − Cᵢ)          λ in µm, Cᵢ in µm²
// They reproduce the catalogue n_d (587.56 nm) to 2e-5 and the Abbe number V_d = (n_d − 1)/(n_F − n_C)
// to 0.02 (PrismCoreTest.sellmeierReproducesSchottCatalogue). Three glasses span the range a student can SEE:
//     N-BK7  crown        n_d 1.5168  V_d 64.2   (spectacle / window class)
//     F2     flint        n_d 1.6200  V_d 36.4   (lead glass)
//     N-SF11 dense flint  n_d 1.7847  V_d 25.7   (3.8x the n_F - n_C of crown; 60° fan 6.8x longer)

package com.bytedance.physicslab.solar.core.prism

import kotlin.math.sqrt

class Glass(
    val id: String,
    /** Chinese display name. */
    val label: String,
    /** English display name (the demo picks one with lab.tr; core stays language-neutral). */
    val labelEn: String,
    private val b: DoubleArray,
    private val c: DoubleArray,
) {
    /** Refractive index at vacuum wavelength [lnm] nanometres (Sellmeier). */
    fun n(lnm: Double): Double {
        val l2 = (lnm / 1000.0) * (lnm / 1000.0)
        var s = 1.0
        for (i in 0..2) s += b[i] * l2 / (l2 - c[i])
        return sqrt(s)
    }

    val nd get() = n(Fraunhofer.D)
    val nF get() = n(Fraunhofer.F)
    val nC get() = n(Fraunhofer.C)
    /** Abbe number V_d: high = little dispersion. */
    val abbe get() = (nd - 1) / (nF - nC)

    /** Two-term Cauchy n = A + B/λ² through the F and C lines (λ in nm) — the textbook model. */
    fun cauchy(): DoubleArray {
        val x1 = 1 / (Fraunhofer.F * Fraunhofer.F); val x2 = 1 / (Fraunhofer.C * Fraunhofer.C)
        val bb = (nF - nC) / (x1 - x2)
        return doubleArrayOf(nF - bb * x1, bb)
    }
}

/** Fraunhofer line wavelengths (nm) used to specify glass. */
object Fraunhofer {
    const val F = 486.1327   // hydrogen, blue
    const val D = 587.5618   // helium d line (catalogue n_d)
    const val C = 656.2725   // hydrogen, red
}

object Glasses {
    val BK7 = Glass("BK7", "冕牌玻璃 BK7", "Crown glass BK7",
        doubleArrayOf(1.03961212, 0.231792344, 1.01046945), doubleArrayOf(0.00600069867, 0.0200179144, 103.560653))
    val F2 = Glass("F2", "火石玻璃 F2", "Flint glass F2",
        doubleArrayOf(1.34533359, 0.209073176, 0.937357162), doubleArrayOf(0.00997743871, 0.0470450767, 111.886764))
    val SF11 = Glass("SF11", "重火石 SF11", "Dense flint SF11",
        doubleArrayOf(1.73759695, 0.313747346, 1.89878101), doubleArrayOf(0.013188707, 0.0623068142, 155.23629))
    val ALL = listOf(BK7, F2, SF11)
}

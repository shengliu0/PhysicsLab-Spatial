// EquilibriumCore — 2 NO₂(g, brown) ⇌ N₂O₄(g, colourless): thermodynamics, a sealed vessel that
// relaxes EXACTLY toward equilibrium, and the Beer–Lambert colour of the gas. Pure Kotlin.
//
// DATA (standard state p° = 1 bar, 298.15 K), NBS tables (Wagman et al., J. Phys. Chem. Ref. Data 11,
// Suppl. 2, 1982) as printed in Atkins' Physical Chemistry data section:
//     NO₂(g)   ΔfH° = +33.18 kJ/mol   S° = 240.06 J/(mol·K)
//     N₂O₄(g)  ΔfH° =  +9.16 kJ/mol   S° = 304.29 J/(mol·K)
//   -> ΔH° = 9.16 − 2·33.18 = −57.20 kJ/mol,  ΔS° = 304.29 − 2·240.06 = −175.83 J/(mol·K)
//   (NIST-JANAF gives −57.12 kJ/mol and −175.69 J/(mol·K): the same to 0.15 %.)
// Kp(T) = exp(−(ΔH° − TΔS°)/RT), taken constant ΔH°, ΔS° (van 't Hoff), Kp(298.15) = 6.892 bar⁻¹.
// Check against the textbook number: at 1 atm and 25 °C N₂O₄ is 18.6 % dissociated (EquilibriumCoreTest).
//
// THE SEALED VESSEL. n0 = moles of NO₂ "units" (NO₂ counts 1, N₂O₄ counts 2) never changes; the state
// is α = n(NO₂)/n0 (= the degree of dissociation of N₂O₄). With P0 = n0RT/V (the pressure if every unit
// were NO₂): p(NO₂) = αP0, p(N₂O₄) = (1−α)P0/2, p_total = (1+α)P0/2. Equilibrium solves
// Kp·p(NO₂)² = p(N₂O₄) — a quadratic with the closed form p(NO₂) = 2P0 / (1 + √(1 + 8KpP0)).
// Compressing changes P0 instantly but not α: concentration jumps first, the shift comes after.
//
// KINETICS (for watching the shift happen). Elementary steps 2NO₂ → N₂O₄ (k_f) and N₂O₄ → 2NO₂ (k_r)
// with k_f/k_r = Kp (detailed balance). Association is barrierless, so k_f is taken T-independent and
// k_r = k_f/Kp carries the whole temperature dependence (activation ≈ |ΔH°|, as measured). The rate law
// dp(NO₂)/dt = −2(k_f p(NO₂)² − k_r p(N₂O₄)) is a Riccati equation with constant coefficients, solved in
// CLOSED FORM (no integration drift, any step): with roots a₊ (equilibrium) and a₋ < 0,
//     (a − a₊)/(a − a₋) decays as exp(−k_r √(1 + 8KpP0) · t).
// The real gas re-equilibrates in far under a millisecond; the demo's k_f is a slow-motion display
// constant so the "darker at once, then paler" two-step of a compression can be seen.

package com.bytedance.physicslab.solar.core.equilibrium

import kotlin.math.exp
import kotlin.math.min
import kotlin.math.sqrt

object Dimerization {
    /** Molar gas constant, J/(mol·K) (exact since the 2019 SI). */
    const val R = 8.314462618
    /** Avogadro constant, 1/mol (exact). */
    const val NA = 6.02214076e23
    /** Standard pressure, Pa (1 bar). */
    const val P_STD = 1.0e5
    /** Reaction enthalpy and entropy of 2NO₂ → N₂O₄ (NBS 1982 / Atkins), J/mol and J/(mol·K). */
    const val DH = -57.2e3
    const val DS = -175.8
    const val KELVIN = 273.15
    const val T25 = 298.15

    /** Standard Gibbs energy of 2NO₂ → N₂O₄ at [t] (K), J/mol. */
    fun deltaG(t: Double) = DH - t * DS

    /** Kp = p(N₂O₄)p° / p(NO₂)² at [t] (K), in bar⁻¹. */
    fun kp(t: Double) = exp(-deltaG(t) / (R * t))

    /** Equilibrium p(NO₂) (bar) for a vessel whose all-NO₂ pressure is [p0] (bar), at [t] (K). */
    fun pNO2(p0: Double, t: Double): Double {
        if (p0 <= 0.0) return 0.0
        return 2 * p0 / (1 + sqrt(1 + 8 * kp(t) * p0))
    }

    /** Equilibrium α = n(NO₂)/n0. */
    fun alphaEq(p0: Double, t: Double) = if (p0 <= 0.0) 1.0 else pNO2(p0, t) / p0

    /** NO₂ mole fraction for a given α: n(NO₂)/(n(NO₂)+n(N₂O₄)) = 2α/(1+α). */
    fun moleFractionNO2(alpha: Double) = 2 * alpha / (1 + alpha)

    /** The all-NO₂ pressure P0 (bar) of a fill whose equilibrium TOTAL pressure at [t] is [pTotal] (bar). */
    fun p0ForTotal(pTotal: Double, t: Double): Double {
        // p(NO₂) = a, p(N₂O₄) = pTotal − a, Kp a² = pTotal − a  ->  a = 2 pTotal / (1 + √(1 + 4 Kp pTotal))
        val a = 2 * pTotal / (1 + sqrt(1 + 4 * kp(t) * pTotal))
        return 2 * pTotal - a
    }

    /** Relaxation rate (1/s) of p(NO₂) toward equilibrium: k_r √(1 + 8KpP0), k_r = kF/Kp. */
    fun relaxRate(p0: Double, t: Double, kF: Double): Double {
        val k = kp(t)
        return kF / k * sqrt(1 + 8 * k * p0)
    }

    /**
     * EXACT solution of the rate law over [dt] at fixed T and V: returns the new α.
     * [kF] = association rate constant in bar⁻¹ s⁻¹ (pressure units); k_r = kF/Kp.
     */
    fun relax(alpha: Double, p0: Double, t: Double, kF: Double, dt: Double): Double {
        if (dt <= 0.0 || p0 <= 0.0) return alpha
        val k = kp(t)
        val s = sqrt(1 + 8 * k * p0)
        val ap = 2 * p0 / (1 + s)              // the equilibrium root
        val am = -(1 + s) / (4 * k)            // the other (negative) root
        val a0 = alpha * p0
        val u = (a0 - ap) / (a0 - am) * exp(-kF / k * s * dt)
        val a = (ap - u * am) / (1 - u)
        return (a / p0).coerceIn(0.0, 1.0)
    }
}

/**
 * A sealed vessel of NO₂/N₂O₄: [units] mol of NO₂ units in [volume] m³ at gas temperature [tGas] K.
 * The gas exchanges heat with its bath (first-order, time constant [tauThermal] s) and reacts by the
 * exact relaxation above. Changing [volume] is instantaneous (α unchanged: same molecules, new space).
 */
class Vessel(val units: Double, volume: Double, t: Double) {
    var volume = volume
    var tGas = t
    var alpha = Dimerization.alphaEq(p0(), t)

    /** All-NO₂ pressure, bar. */
    fun p0() = units * Dimerization.R * tGas / volume / Dimerization.P_STD
    fun pNO2() = alpha * p0()
    fun pN2O4() = (1 - alpha) * p0() / 2
    fun pTotal() = (1 + alpha) * p0() / 2
    fun moleFractionNO2() = Dimerization.moleFractionNO2(alpha)
    /** NO₂ concentration, mol/m³ (= mmol/L). */
    fun cNO2() = alpha * units / volume
    fun alphaEq() = Dimerization.alphaEq(p0(), tGas)
    fun cNO2Eq() = alphaEq() * units / volume

    fun equilibrate(t: Double) { tGas = t; alpha = alphaEq() }

    /** Advance [dt] s: heat exchange with a bath at [tBath], then the exact chemical relaxation. */
    fun step(dt: Double, tBath: Double, kF: Double, tauThermal: Double) {
        if (dt <= 0.0) return
        tGas += (tBath - tGas) * (1 - exp(-dt / tauThermal))
        alpha = Dimerization.relax(alpha, p0(), tGas, kF, dt)
    }
}

/**
 * The colour: Beer–Lambert, T(λ) = exp(−σ(λ) n(NO₂) ℓ). N₂O₄ does not absorb in the visible (its bands
 * are UV), so all colour is NO₂. σ per display channel = band averages of the NO₂ cross-section
 * (Vandaele et al., JQSRT 59, 171, 1998, 294 K): the green value is anchored on the 532 nm photoacoustic
 * measurement 1.47×10⁻¹⁹ cm² (Osthoff et al. 2006: 1.45), blue sits below the 414 nm peak (7.4×10⁻¹⁹),
 * red is the weak 600–650 nm tail. Blue absorbed most, red least -> transmitted light is yellow ->
 * orange -> red-brown as the column density grows: the colour of NO₂ falls out of the cross-sections.
 */
object NO2Optics {
    /** σ for R, G, B in m² per molecule. */
    val SIGMA = doubleArrayOf(0.60e-23, 1.47e-23, 5.0e-23)

    /** Optical depth of the green channel through [path] m of gas with [cNO2] mol/m³. */
    fun tauGreen(cNO2: Double, path: Double) = SIGMA[1] * cNO2 * Dimerization.NA * path

    /** Per-channel transmittance (R, G, B) for green optical depth [tauG] (all channels scale with c·ℓ). */
    fun transmittanceFromTau(tauG: Double, out: DoubleArray = DoubleArray(3)): DoubleArray {
        for (k in 0 until 3) out[k] = exp(-tauG * SIGMA[k] / SIGMA[1])
        return out
    }

    fun transmittance(cNO2: Double, path: Double, out: DoubleArray = DoubleArray(3)) =
        transmittanceFromTau(tauGreen(cNO2, path), out)

    /** Luminance-weighted (Rec.709) transmittance — "how dark it looks". */
    fun luminance(t: DoubleArray) = 0.2126 * t[0] + 0.7152 * t[1] + 0.0722 * t[2]

    /** min over channels, for an alpha-blended stand-in of a pure absorber. */
    fun minChannel(t: DoubleArray) = min(t[0], min(t[1], t[2]))
}

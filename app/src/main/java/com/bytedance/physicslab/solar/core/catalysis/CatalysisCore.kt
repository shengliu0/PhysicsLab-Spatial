// CatalysisCore — catalytic decomposition of hydrogen peroxide ("elephant toothpaste"), pure Kotlin.
//
//     2 H2O2(aq) -> 2 H2O(l) + O2(g)        dH = -98.2 kJ per mol H2O2
//
// dH: from standard enthalpies of formation (NIST WebBook / CRC Handbook: H2O2(l) -187.8,
// H2O(l) -285.8 kJ/mol -> -98.0; the textbook figure -98.2 kJ/mol is used; for dilute aqueous
// H2O2 (dfH -191.2) it is -94.6, 4 % less — immaterial at this level).
//
// KINETICS (rates below are -d[H2O2]/dt unless stated)
//   * iodide, homogeneous, two steps (the textbook mechanism):
//         (1) H2O2 + I-  -> H2O + IO-          k1 = 1.15e-2 M^-1 s^-1 at 25 C, Ea = 56 kJ/mol  (slow)
//         (2) H2O2 + IO- -> H2O + O2 + I-      k2 = 1000 k1 (assumed: fast, so step 1 sets the rate)
//     k1: Liebhafsky & Mohammad, J. Am. Chem. Soc. 55, 3977 (1933) (H2O2 + I-, 1.1-1.2e-2 M^-1 s^-1);
//     Ea: the classic textbook comparison (uncatalysed ~75, I- ~56, catalase ~8 kJ/mol; e.g. Atkins,
//     Physical Chemistry; Chang, Chemistry). In steady state -d[H2O2]/dt = 2 k1 [H2O2][I-] and
//     d[O2]/dt = k1 [H2O2][I-]. The I-/IO- split is integrated EXACTLY per sub-step (frozen [H2O2]),
//     so total iodine I- + IO- is conserved to rounding: the catalyst is never consumed.
//   * catalase (baker's yeast), Michaelis-Menten: -d[S]/dt = kcat [E] [S] / (Km + [S]),
//     kcat = 4e7 s^-1, Km = 25 mM (Lehninger, Principles of Biochemistry, Tables 6-6/6-7);
//     [E] = 1.5 nM in the mix is an ASSUMPTION (a spoon of yeast) chosen so 3 % finishes in ~15 s like
//     the classroom version; Ea ~ 8 kJ/mol; a protein: denatures above ~55 C (assumed first order,
//     k_d = 0.05 s^-1 at 65 C, Ea_d = 300 kJ/mol — typical of protein unfolding).
//   * MnO2 powder, heterogeneous, first order in H2O2: k = 0.06 s^-1 at 25 C, Ea = 58 kJ/mol
//     (ASSUMED: a spatula of powder; reported Ea for MnO2 lie in 30-60 kJ/mol).
//   * uncatalysed: first order, k = 3e-10 s^-1 at 25 C (< 1 %/year shelf loss of stabilised H2O2,
//     supplier data sheets), Ea = 75 kJ/mol -> nothing on the time scale of a demonstration.
//
// HEAT: one well-mixed liquid, C = m cp (cp of the H2O2 solution mixed by mass from liquid H2O2,
// 89.1 J/(mol K) NIST, and water 4.18 J/(g K)), Newton cooling to the room, boiling cap at 100 C
// (excess heat boils water off: L = 2257 J/g). Heat -> T -> rate (Arrhenius) -> more heat: with
// 30 % the rate runs away and the mix boils within ~3 s; with 3 % the whole reaction warms it by
// only ~16 K (adiabatic) — not "a few degrees", but nowhere near boiling.
//
// GAS / FOAM: O2 leaves at the liquid temperature as an ideal gas (pV = nRT, 1 atm). With dish soap
// the foam traps 85 % of it (liquid fraction 5 %); without soap every bubble escapes: no foam.

package com.bytedance.physicslab.solar.core.catalysis

import kotlin.math.exp
import kotlin.math.min

object Peroxide {
    const val M = 34.0147              // g/mol H2O2 (IUPAC atomic weights)
    const val DH = 98.2e3              // J released per mol H2O2 decomposed
    const val CP_H2O2 = 89.1 / M       // J/(g K): liquid H2O2, 89.1 J/(mol K) (NIST WebBook)
    const val CP_WATER = 4.18          // J/(g K)
    const val L_VAP = 2257.0           // J/g, water at 100 C
    const val R = 8.314462618          // J/(mol K)
    const val P0 = 101325.0            // Pa
    const val T25 = 298.15
    const val T_BOIL = 373.15

    /** The three solutions the demo offers (mass fraction) and their densities at 25 C (g/mL,
     *  supplier density tables: 3 % 1.01, 10 % 1.034, 30 % 1.11). */
    val W = doubleArrayOf(0.03, 0.10, 0.30)
    val RHO = doubleArrayOf(1.010, 1.034, 1.110)

    /** Density (g/mL) of a w-fraction solution: the table, linear in between (water at w = 0). */
    fun density(w: Double): Double {
        val xs = doubleArrayOf(0.0) + W; val ys = doubleArrayOf(0.997) + RHO
        if (w >= xs.last()) return ys.last()
        var k = 0; while (k < xs.size - 2 && w > xs[k + 1]) k++
        return ys[k] + (ys[k + 1] - ys[k]) * (w - xs[k]) / (xs[k + 1] - xs[k])
    }

    /** mol H2O2 in [volMl] of a w-fraction (w/w) solution. */
    fun moles(volMl: Double, w: Double) = volMl * density(w) * w / M

    /** Litres of ideal gas for [n] mol at [tK], [p]. */
    fun gasLitres(n: Double, tK: Double = T25, p: Double = P0) = n * R * tK / p * 1000.0

    /** Specific heat of a w-fraction solution, J/(g K) (mass-weighted mix). */
    fun cp(w: Double) = w * CP_H2O2 + (1 - w) * CP_WATER

    /** Adiabatic temperature rise (K) of a pure w-fraction solution decomposing completely (no cap). */
    fun adiabaticRise(w: Double) = w * DH / (M * cp(w))

    /** Arrhenius: k(T) from k(25 C) and Ea (J/mol). */
    fun arrhenius(k25: Double, ea: Double, tK: Double) = k25 * exp(-ea / R * (1 / tK - 1 / T25))
}

enum class Catalyst { KI, CATALASE, MNO2, NONE }

object Kinetics {
    const val K1_KI = 1.15e-2          // M^-1 s^-1, step 1 at 25 C
    const val EA_KI = 56e3
    const val K2_RATIO = 1000.0        // step 2 / step 1
    const val KI_M = 1.2               // [I-] in the mix: 26 g KI in the 25 mL catalyst solution, 130 mL total
    const val KCAT = 4.0e7             // s^-1
    const val KM = 0.025               // M
    const val E_M = 1.5e-9             // M (assumption, see header)
    const val EA_CAT = 8e3
    const val KD_REF = 0.05            // s^-1 at 65 C
    const val T_KD_REF = 338.15
    const val EA_DENATURE = 300e3
    const val K_MNO2 = 0.06            // s^-1 at 25 C
    const val EA_MNO2 = 58e3
    const val K_UNCAT = 3e-10          // s^-1 at 25 C
    const val EA_UNCAT = 75e3

    /** Activation energy (J/mol) of each pathway, for the readout and the Arrhenius test. */
    fun ea(c: Catalyst) = when (c) { Catalyst.KI -> EA_KI; Catalyst.CATALASE -> EA_CAT; Catalyst.MNO2 -> EA_MNO2; Catalyst.NONE -> EA_UNCAT }
}

/**
 * One graduated cylinder from the moment the catalyst is mixed in: [vPeroxideMl] of w-fraction
 * H2O2 plus [vExtraMl] of catalyst solution + dish soap (taken as water for heat capacity).
 * Amounts in mol, temperatures in K, volumes in L.
 */
class Reactor(
    val w: Double,
    val catalyst: Catalyst,
    val t0C: Double,
    val soap: Boolean,
    val vPeroxideMl: Double = 100.0,
    val vExtraMl: Double = 30.0,
    val roomC: Double = 22.0,
) {
    val volumeL = (vPeroxideMl + vExtraMl) / 1000.0
    val n0 = Peroxide.moles(vPeroxideMl, w)
    /** J/K of the mix */
    val heatCapacity = vPeroxideMl * Peroxide.density(w) * Peroxide.cp(w) + vExtraMl * Peroxide.CP_WATER
    /** total iodine (mol), I- + IO- */
    val iodideTotal = if (catalyst == Catalyst.KI) Kinetics.KI_M * volumeL else 0.0
    /** mol of enzyme (catalase) */
    val enzymeTotal = if (catalyst == Catalyst.CATALASE) Kinetics.E_M * volumeL else 0.0

    /** Heat loss to the room, W/K (a glass cylinder with 130 mL cools with tau ~ 15 min). */
    var ua = 0.6
    /** Tests: hold the temperature (no heat effect). */
    var isothermal = false

    var t = 0.0; private set
    var n = n0; private set                 // mol H2O2 left
    var nO2 = 0.0; private set              // mol O2 released
    var tK = t0C + 273.15; private set
    var iodide = iodideTotal; private set   // mol I-
    var hypoiodite = 0.0; private set       // mol IO-
    var enzymeActive = 1.0; private set     // fraction of catalase not denatured
    var steamG = 0.0; private set           // water boiled off (g)
    var heatReleasedJ = 0.0; private set
    var heatLostJ = 0.0; private set
    var trappedL = 0.0; private set         // gas held in the foam (L, at the temperature it left at)
    var escapedL = 0.0; private set         // gas that escaped as bubbles (L)
    var substeps = 0L; private set

    val conversion get() = 1 - n / n0
    val o2MaxMol get() = n0 / 2
    /** O2 so far / at most, as litres at 25 C, 1 atm (the readout's "so far vs expected"). */
    val o2L25 get() = Peroxide.gasLitres(nO2)
    val o2MaxL25 get() = Peroxide.gasLitres(o2MaxMol)
    /** Foam volume (L): trapped gas plus its 5 % liquid. */
    val foamL get() = trappedL / (1 - LIQUID_FRACTION)
    val tC get() = tK - 273.15

    /** Pseudo-first-order rate constants (s^-1) of the catalysed path and the background, now. */
    private fun kKi(tk: Double) = Peroxide.arrhenius(Kinetics.K1_KI, Kinetics.EA_KI, tk)
    private fun kOther(tk: Double, c: Double): Double {
        var k = Peroxide.arrhenius(Kinetics.K_UNCAT, Kinetics.EA_UNCAT, tk)
        when (catalyst) {
            Catalyst.CATALASE -> k += Peroxide.arrhenius(Kinetics.KCAT, Kinetics.EA_CAT, tk) * Kinetics.E_M * enzymeActive / (Kinetics.KM + c)
            Catalyst.MNO2 -> k += Peroxide.arrhenius(Kinetics.K_MNO2, Kinetics.EA_MNO2, tk)
            else -> {}
        }
        return k
    }

    /** -d[H2O2]/dt now, mol/(L s). */
    fun rate(): Double {
        val c = n / volumeL
        var r = kOther(tK, c) * c
        if (catalyst == Catalyst.KI) { val k1 = kKi(tK); r += c * (k1 * iodide + Kinetics.K2_RATIO * k1 * hypoiodite) / volumeL }
        return r
    }

    /** Catalase denaturation rate (s^-1) at [tk]. */
    fun denatureRate(tk: Double) = Kinetics.KD_REF * exp(Kinetics.EA_DENATURE / Peroxide.R * (1 / Kinetics.T_KD_REF - 1 / tk))

    /**
     * Advance [dt] seconds. Adaptive sub-steps (<= 1 % of the H2O2 or 0.25 K each, <= 50 ms); within a
     * sub-step [H2O2] decays exponentially at the frozen pseudo-first-order rate, the I-/IO- exchange
     * is solved exactly, and every mole is booked: consumed H2O2 = 2 O2 + IO- (exactly).
     */
    fun step(dt: Double) {
        var left = dt
        var guard = 0
        val v = volumeL
        val roomK = roomC + 273.15
        while (left > 1e-12 && guard++ < 200_000) {
            val c = n / v
            val k1 = kKi(tK); val k2 = Kinetics.K2_RATIO * k1
            val ko = kOther(tK, c)
            val kki = if (catalyst == Catalyst.KI) (k1 * iodide + k2 * hypoiodite) / v else 0.0
            val kTot = ko + kki
            var h = min(left, 0.05)
            if (kTot > 0) h = min(h, 0.01 / kTot)
            if (!isothermal && tK < Peroxide.T_BOIL) {
                val dTdt = kTot * n * Peroxide.DH / heatCapacity
                if (dTdt > 0) h = min(h, 0.25 / dTdt)
            }
            // iodide two-step, exact for frozen c: d IO/dt = a I - b IO, I + IO = S
            var e1 = 0.0; var e2 = 0.0
            if (catalyst == Catalyst.KI && c > 0) {
                val a = k1 * c; val b = k2 * c; val lam = a + b; val s = iodideTotal
                val ioEq = s * a / lam
                val ex = exp(-lam * h)
                val intIO = ioEq * h + (hypoiodite - ioEq) * (1 - ex) / lam
                val intI = s * h - intIO
                e1 = a * intI; e2 = b * intIO
            }
            val eo = ko * n * h
            val est = e1 + e2 + eo
            val d = if (est > 0) n * (1 - exp(-est / n)) else 0.0
            val sc = if (est > 0) d / est else 0.0
            n -= d
            hypoiodite = (hypoiodite + sc * (e1 - e2)).coerceIn(0.0, iodideTotal)
            iodide = iodideTotal - hypoiodite
            val dO2 = sc * e2 + sc * eo / 2
            nO2 += dO2
            if (catalyst == Catalyst.CATALASE) enzymeActive *= exp(-denatureRate(tK) * h)
            val gas = Peroxide.gasLitres(dO2, tK)
            if (soap) { trappedL += F_TRAP * gas; escapedL += (1 - F_TRAP) * gas } else escapedL += gas
            val q = d * Peroxide.DH
            heatReleasedJ += q
            if (!isothermal) {
                val loss = ua * (tK - roomK) * h
                heatLostJ += loss
                tK += (q - loss) / heatCapacity
                if (tK > Peroxide.T_BOIL) { steamG += (tK - Peroxide.T_BOIL) * heatCapacity / Peroxide.L_VAP; tK = Peroxide.T_BOIL }
            }
            t += h; left -= h; substeps++
        }
    }

    /** Independent copy (for a look-ahead run: how much foam will this make?). */
    fun copy(): Reactor = Reactor(w, catalyst, t0C, soap, vPeroxideMl, vExtraMl, roomC).also { r ->
        r.ua = ua; r.isothermal = isothermal
        r.t = t; r.n = n; r.nO2 = nO2; r.tK = tK; r.iodide = iodide; r.hypoiodite = hypoiodite
        r.enzymeActive = enzymeActive; r.steamG = steamG; r.heatReleasedJ = heatReleasedJ; r.heatLostJ = heatLostJ
        r.trappedL = trappedL; r.escapedL = escapedL
    }

    companion object {
        const val F_TRAP = 0.85
        const val LIQUID_FRACTION = 0.05
    }
}

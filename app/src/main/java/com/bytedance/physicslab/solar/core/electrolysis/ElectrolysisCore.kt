// ElectrolysisCore — electrolysis of water in a Hofmann voltameter, pure Kotlin.
//
// MACRO (Faraday, 1834): the charge Q = ∫ I dt that flows through the cell is counted in electrons,
// Q / F moles of them. Each H₂ at the cathode takes z = 2 electrons, each O₂ at the anode gives up
// z = 4, so n(H₂) = Q / 2F and n(O₂) = Q / 4F — the 2 : 1 volume ratio is an electron-count ratio.
// Gas volume V = n R T / p, stated at 25 °C and 1 atm (dry gas; the real wet gas over water carries
// 3.2 kPa of vapour and sits under a few cm of water head, ~3 % more volume — same for both tubes,
// so the RATIO is unaffected). The z values are not typed in: they are read off the half reactions.
//
// CELL (simple, stated model): no sustained current below the decomposition voltage U_dec; above it
// I = (U − U_dec) / R_cell, R_cell = K_cell / κ (cell constant over electrolyte conductivity).
//   * thermodynamic minimum E° = ΔG° / 2F = 1.229 V; thermoneutral ΔH° / 2F = 1.481 V
//     (ΔfG°, ΔfH° of liquid water: −237.13, −285.83 kJ/mol, NBS tables 1982 / CODATA key values);
//   * U_dec = 2.2 V = 1.23 V + electrode overpotentials on platinum, Le Blanc's classic decomposition
//     voltage for NEUTRAL sulfate solutions like this Na₂SO₄ one (dilute H₂SO₄: 1.67 V) — the pH shifts
//     at the electrodes raise it;
//   * κ(pure water) from Kohlrausch: [H⁺] = [OH⁻] = 1.0e-7 mol/L (K_w at 25 °C) times the limiting
//     ionic conductivities λ°(H⁺) = 349.65, λ°(OH⁻) = 198.0 S cm²/mol (CRC Handbook) = 5.5 µS/m,
//     the textbook 18.2 MΩ·cm of ultrapure water;
//   * κ(Na₂SO₄, 10 mass %, ≈ 0.75 mol/L) ≈ 6.86 S/m (CRC "Electrical conductivity of aqueous
//     solutions", 68.6 mS/cm at 20 °C; Kohlrausch with Λ ≈ 45 S cm²/eq gives the same);
//   * K_cell = 36 m⁻¹ is THIS model voltameter's cell constant, chosen so that 6 V with Na₂SO₄ gives
//     0.72 A (R = 5.2 Ω). Pure water: R = 6.6 MΩ, ~1.5 µA at 12 V — that is why the salt is added.
// Na⁺ and SO₄²⁻ carry the current through the solution but are not discharged (water is easier to
// reduce than Na⁺ and easier to oxidise than SO₄²⁻), so they are spectators of the chemistry.
//
// MICRO: the two half reactions as atom tracks (which atom of which reactant ends up in which
// product). The demo animates exactly these tracks; the tests check H, O and charge conservation on
// them, and that 2 × cathode + anode is 2 H₂O → 2 H₂ + O₂ once H⁺ + OH⁻ → H₂O.

package com.bytedance.physicslab.solar.core.electrolysis

// ------------------------------------------------------------------------------------------- constants

object Echem {
    /** Faraday constant (C/mol) = N_A e, exact in the 2019 SI (CODATA 2018). */
    const val F = 96485.33212
    /** Molar gas constant (J/(mol K)) = N_A k, exact (CODATA 2018). */
    const val R = 8.314462618
    const val T25 = 298.15
    const val P_ATM = 101325.0
    /** −ΔfG° and −ΔfH° of liquid water at 25 °C (J/mol), NBS tables 1982 / CODATA key values. */
    const val DG_H2O = 237.13e3
    const val DH_H2O = 285.83e3
    /** Reversible (thermodynamic) cell voltage, 1.229 V. */
    val E_REV = DG_H2O / (2 * F)
    /** Thermoneutral voltage (all the heat supplied electrically), 1.481 V. */
    val E_TN = DH_H2O / (2 * F)

    /** Ideal-gas molar volume (m³/mol): 24.465 L/mol at 25 °C, 1 atm. */
    fun molarVolume(tK: Double = T25, p: Double = P_ATM) = R * tK / p
    /** Moles of a product that takes [z] electrons per molecule, for charge [q] (C). */
    fun moles(q: Double, z: Int) = q / (z * F)
    /** mL of that gas per coulomb at (T, p). */
    fun mlPerCoulomb(z: Int, tK: Double = T25, p: Double = P_ATM) = molarVolume(tK, p) * 1e6 / (z * F)
    /** Voltage (energy) efficiency on the higher-heating-value basis: E_tn / U. */
    fun efficiency(u: Double) = if (u <= 0) 0.0 else E_TN / u
}

// ------------------------------------------------------------------------------------------- cell model

enum class Electrolyte(val kappa: Double) {
    /** Ultrapure water, self-ionisation only (Kohlrausch, 25 °C): 5.5e-6 S/m. */
    PURE(1.0e-4 * (349.65 + 198.0) * 1e-4),   // c = 1.0e-7 mol/L = 1.0e-4 mol/m³; λ in S m²/mol
    /** ≈ 10 mass % Na₂SO₄ (CRC, 20 °C): 6.86 S/m. */
    NA2SO4(6.86),
}

object Cell {
    /** Decomposition voltage of this cell (V): 1.229 V thermodynamics + overpotentials on Pt. */
    const val U_DEC = 2.2
    /** Cell constant L/A of the model voltameter (1/m). */
    const val K_CELL = 36.0
    fun resistance(e: Electrolyte) = K_CELL / e.kappa
    /** Steady current (A): zero up to U_dec, then ohmic. */
    fun current(u: Double, e: Electrolyte) = if (u <= U_DEC) 0.0 else (u - U_DEC) / resistance(e)
}

// ------------------------------------------------------------------------------------------- micro model

enum class El { H, O }

/** A molecule or ion: its atoms (O first) and charge in units of e. */
class Species(val formula: String, val atoms: List<El>, val charge: Int) {
    fun count(e: El) = atoms.count { it == e }
}

object Sp {
    val H2O = Species("H₂O", listOf(El.O, El.H, El.H), 0)
    val H2 = Species("H₂", listOf(El.H, El.H), 0)
    val O2 = Species("O₂", listOf(El.O, El.O), 0)
    val OH = Species("OH⁻", listOf(El.O, El.H), -1)
    val HP = Species("H⁺", listOf(El.H), +1)
}

/** One atom's journey: element [el] leaves reactant molecule [from] and ends in product molecule [to]. */
class Track(val el: El, val from: Int, val to: Int)

/**
 * A half reaction as the demo draws it: reactant molecules, product molecules, electrons taken from
 * the electrode ([eIn], cathode) or given to it ([eOut], anode), and the atom tracks between them.
 */
class HalfReaction(val name: String, val reactants: List<Species>, val products: List<Species>,
                   val eIn: Int, val eOut: Int, val tracks: List<Track>) {
    fun atoms(side: List<Species>, e: El) = side.sumOf { it.count(e) }
    /** Net charge (reactants + electrons in) − (products + electrons out): 0 when balanced. */
    fun chargeImbalance() = (reactants.sumOf { it.charge } - eIn) - (products.sumOf { it.charge } - eOut)
    /** Electrons per molecule of the gas [g] this electrode makes (2 for H₂, 4 for O₂). */
    fun electronsPer(g: Species): Int = (eIn + eOut) / products.count { it === g }
    /** Index of a track's atom inside its product (0, 1, ...), in track order — where it is drawn. */
    fun slotInProduct(k: Int): Int = (0 until k).count { tracks[it].to == tracks[k].to }
    /** Index of a track's atom inside its reactant (O first, then H's) — where it starts. */
    fun slotInReactant(k: Int): Int {
        val t = tracks[k]; val mol = reactants[t.from]
        val sameEl = (0 until k).count { tracks[it].from == t.from && tracks[it].el == t.el }
        var seen = 0
        for ((i, a) in mol.atoms.withIndex()) if (a == t.el) { if (seen == sameEl) return i; seen++ }
        return -1
    }
}

object Micro {
    /** Cathode (−), reduction: 2 H₂O + 2 e⁻ → H₂ + 2 OH⁻. Each water gives one H to the H₂. */
    val CATHODE = HalfReaction("cathode", listOf(Sp.H2O, Sp.H2O), listOf(Sp.H2, Sp.OH, Sp.OH), eIn = 2, eOut = 0, tracks = listOf(
        Track(El.O, 0, 1), Track(El.H, 0, 0), Track(El.H, 0, 1),
        Track(El.O, 1, 2), Track(El.H, 1, 0), Track(El.H, 1, 2)))

    /** Anode (+), oxidation: 2 H₂O → O₂ + 4 H⁺ + 4 e⁻. Both O's pair up; every H leaves as H⁺. */
    val ANODE = HalfReaction("anode", listOf(Sp.H2O, Sp.H2O), listOf(Sp.O2, Sp.HP, Sp.HP, Sp.HP, Sp.HP), eIn = 0, eOut = 4, tracks = listOf(
        Track(El.O, 0, 0), Track(El.H, 0, 1), Track(El.H, 0, 2),
        Track(El.O, 1, 0), Track(El.H, 1, 3), Track(El.H, 1, 4)))

    /** Electrons per gas molecule, read off the half reactions (not typed in): z(H₂) = 2, z(O₂) = 4. */
    val Z_H2 get() = CATHODE.electronsPer(Sp.H2)
    val Z_O2 get() = ANODE.electronsPer(Sp.O2)
}

// ------------------------------------------------------------------------------------------- the voltameter

/**
 * A Hofmann voltameter on a DC supply. Lab time [t] (s), charge [q] (C), gas in each graduated tube
 * (mL at 25 °C, 1 atm) and gas let out through the stopcocks. Charge integrates exactly (the current
 * is constant between parameter changes). When a tube reaches [capMl] the supply trips (the gas
 * would otherwise escape round the bottom of the tube); venting a tube re-arms it.
 */
class Voltameter(val capMl: Double = 50.0) {
    val kH = Echem.mlPerCoulomb(Micro.Z_H2)        // 0.1268 mL/C
    val kO = Echem.mlPerCoulomb(Micro.Z_O2)        // 0.0634 mL/C
    var t = 0.0; private set
    var q = 0.0; private set
    var gasH = 0.0; private set
    var gasO = 0.0; private set
    var ventH = 0.0; private set
    var ventO = 0.0; private set
    var current = 0.0; private set

    val full get() = gasH >= capMl - 1e-9 || gasO >= capMl - 1e-9
    /** Everything that left the electrodes (in the tube + vented). */
    val producedH get() = gasH + ventH
    val producedO get() = gasO + ventO
    /** Faraday's prediction from the counted charge. */
    val predictedH get() = kH * q
    val predictedO get() = kO * q

    fun refill() { t = 0.0; q = 0.0; gasH = 0.0; gasO = 0.0; ventH = 0.0; ventO = 0.0; current = 0.0 }

    /** Advance [dt] s of lab time at supply voltage [u]. Returns the current that flowed (A). */
    fun step(dt: Double, u: Double, e: Electrolyte): Double {
        if (dt <= 0.0 || u <= 0.0) { current = if (u > 0.0 && !full) Cell.current(u, e) else 0.0; return current }
        if (full) { current = 0.0; return 0.0 }
        val i = Cell.current(u, e)
        current = i
        if (i <= 0.0) { t += dt; return 0.0 }
        // charge that would fill the first tube to the cap: stop exactly there
        val qMax = minOf((capMl - gasH) / kH, (capMl - gasO) / kO)
        var dq = i * dt
        var used = dt
        if (dq >= qMax) { dq = qMax.coerceAtLeast(0.0); used = dq / i; current = 0.0 }
        t += used; q += dq
        gasH += kH * dq; gasO += kO * dq
        if (full) { gasH = minOf(gasH, capMl); gasO = minOf(gasO, capMl) }
        return i
    }

    /** Let up to [ml] of gas out of the H₂ ([hydrogen]) or O₂ tube; returns what actually left. */
    fun vent(hydrogen: Boolean, ml: Double): Double {
        val out = minOf(ml, if (hydrogen) gasH else gasO).coerceAtLeast(0.0)
        if (hydrogen) { gasH -= out; ventH += out } else { gasO -= out; ventO += out }
        return out
    }
}

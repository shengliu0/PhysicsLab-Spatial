package com.bytedance.physicslab.solar.core.electrolysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ElectrolysisCoreTest {

    @Test
    fun constantsMatchTheTables() {
        // 2019 SI exact: F = N_A e, R = N_A k
        assertEquals(6.02214076e23 * 1.602176634e-19, Echem.F, 1e-5)   // CODATA rounds to 10 digits
        assertEquals(6.02214076e23 * 1.380649e-23, Echem.R, 1e-9)
        val vm = Echem.molarVolume() * 1e3
        println("ELECTRO molar volume 25 °C 1 atm = %.4f L/mol, E_rev = %.4f V, E_tn = %.4f V".format(vm, Echem.E_REV, Echem.E_TN))
        assertEquals(24.465, vm, 0.001)                 // L/mol, ideal gas at 298.15 K, 101325 Pa
        assertEquals(1.229, Echem.E_REV, 0.001)         // textbook standard potential of water splitting
        assertEquals(1.481, Echem.E_TN, 0.001)          // thermoneutral voltage
    }

    @Test
    fun faradayNumbers() {
        // 1.00 A for 965 s = 965 C = 0.010 mol e⁻ -> 5.00 mmol H₂ (122 mL) + 2.50 mmol O₂ (61 mL) at 25 °C, 1 atm
        val q = 1.00 * 965
        val nH = Echem.moles(q, Micro.Z_H2); val nO = Echem.moles(q, Micro.Z_O2)
        val vH = nH * Echem.molarVolume() * 1e6; val vO = nO * Echem.molarVolume() * 1e6
        println("ELECTRO 1.00 A x 965 s: n(H2) = %.4f mmol, V(H2) = %.2f mL; n(O2) = %.4f mmol, V(O2) = %.2f mL".format(nH * 1e3, vH, nO * 1e3, vO))
        assertEquals(5.000, nH * 1e3, 0.001)
        assertEquals(2.500, nO * 1e3, 0.001)
        assertEquals(122.35, vH, 0.02)
        assertEquals(61.17, vO, 0.02)

        // the same through the voltameter: pick U so the cell draws exactly 1.00 A with Na₂SO₄
        val u = Cell.U_DEC + 1.00 * Cell.resistance(Electrolyte.NA2SO4)
        val cell = Voltameter(capMl = 500.0)
        repeat(965 * 60) { cell.step(1.0 / 60, u, Electrolyte.NA2SO4) }
        println("ELECTRO voltameter at %.3f V: I = %.4f A, t = %.2f s, Q = %.3f C, H2 %.3f mL, O2 %.3f mL".format(u, cell.current, cell.t, cell.q, cell.gasH, cell.gasO))
        assertEquals(965.0, cell.t, 1e-6)
        assertEquals(965.0, cell.q, 1e-6)
        assertEquals(vH, cell.gasH, 1e-6)
        assertEquals(vO, cell.gasO, 1e-6)
    }

    @Test
    fun ratioIsExactlyTwoWhateverTheHistory() {
        assertEquals(2, Micro.Z_H2); assertEquals(4, Micro.Z_O2)
        val cell = Voltameter()
        // knob twiddling, electrolyte on/off, a vent in the middle: produced H₂ / O₂ stays 2, and Faraday holds
        val schedule = listOf(6.0 to 40.0, 2.5 to 30.0, 12.0 to 25.0, 1.0 to 20.0, 8.0 to 15.0)
        for ((k, s) in schedule.withIndex()) {
            val e = if (k == 3) Electrolyte.PURE else Electrolyte.NA2SO4
            var tt = 0.0
            while (tt < s.second) { cell.step(0.05, s.first, e); tt += 0.05 }
            if (k == 2) cell.vent(true, 5.0)
        }
        println("ELECTRO history: Q = %.2f C, produced H2 %.3f mL, O2 %.3f mL, ratio %.9f, Faraday H2 %.3f mL".format(
            cell.q, cell.producedH, cell.producedO, cell.producedH / cell.producedO, cell.predictedH))
        assertEquals(2.0, cell.producedH / cell.producedO, 1e-12)
        assertEquals(cell.predictedH, cell.producedH, 1e-9)
        assertEquals(cell.predictedO, cell.producedO, 1e-9)
        assertEquals(5.0, cell.ventH, 1e-12)
    }

    @Test
    fun noCurrentBelowTheDecompositionVoltage() {
        for (u in listOf(0.0, 0.5, 1.229, 1.5, 1.8, 2.0, 2.2)) assertEquals(0.0, Cell.current(u, Electrolyte.NA2SO4), 0.0)
        assertTrue(Cell.current(2.21, Electrolyte.NA2SO4) > 0.0)
        assertTrue(Cell.U_DEC > Echem.E_REV)            // overpotentials: you always need more than 1.23 V
        val cell = Voltameter()
        repeat(3600) { cell.step(1.0, 1.7, Electrolyte.NA2SO4) }
        assertEquals(0.0, cell.q, 0.0); assertEquals(0.0, cell.gasH, 0.0)
        assertEquals(3600.0, cell.t, 1e-9)             // the supply was on, nothing happened
        // the demo's default: 6 V with Na₂SO₄ -> (6 − 2.2) V / 5.25 Ω = 0.72 A
        println("ELECTRO I(6 V, Na2SO4) = %.4f A, R = %.3f ohm".format(Cell.current(6.0, Electrolyte.NA2SO4), Cell.resistance(Electrolyte.NA2SO4)))
        assertEquals(0.724, Cell.current(6.0, Electrolyte.NA2SO4), 0.003)
    }

    @Test
    fun pureWaterBarelyConducts() {
        val kp = Electrolyte.PURE.kappa
        println("ELECTRO kappa pure = %.3e S/m (%.2f MOhm cm), R = %.2f MOhm, I(12 V) = %.3f uA vs %.3f A with Na2SO4".format(
            kp, 100.0 / kp / 1e6, Cell.resistance(Electrolyte.PURE) / 1e6,
            Cell.current(12.0, Electrolyte.PURE) * 1e6, Cell.current(12.0, Electrolyte.NA2SO4)))
        assertEquals(5.5e-6, kp, 0.1e-6)               // 18.2 MOhm cm ultrapure water
        assertEquals(18.2, 100.0 / kp / 1e6, 0.4)       // resistivity in MOhm cm
        val ratio = Cell.current(12.0, Electrolyte.PURE) / Cell.current(12.0, Electrolyte.NA2SO4)
        assertTrue(ratio < 1e-5)
        // an hour at 12 V in pure water makes less than a microlitre of hydrogen
        val cell = Voltameter()
        repeat(3600) { cell.step(1.0, 12.0, Electrolyte.PURE) }
        println("ELECTRO pure water, 1 h at 12 V: Q = %.4f C, H2 = %.5f mL".format(cell.q, cell.gasH))
        assertTrue(cell.gasH < 0.001)
    }

    @Test
    fun microModelConservesAtomsAndCharge() {
        for (r in listOf(Micro.CATHODE, Micro.ANODE)) {
            for (e in El.values()) assertEquals("${r.name} $e", r.atoms(r.reactants, e), r.atoms(r.products, e))
            assertEquals("${r.name} charge", 0, r.chargeImbalance())
            // the tracks the demo animates: every reactant atom leaves exactly once, every product atom arrives exactly once
            for ((i, m) in r.reactants.withIndex()) for (e in El.values())
                assertEquals("${r.name} reactant $i $e", m.count(e), r.tracks.count { it.from == i && it.el == e })
            for ((j, m) in r.products.withIndex()) for (e in El.values())
                assertEquals("${r.name} product $j $e", m.count(e), r.tracks.count { it.to == j && it.el == e })
            for (k in r.tracks.indices) {
                val t = r.tracks[k]
                assertEquals(t.el, r.reactants[t.from].atoms[r.slotInReactant(k)])
                assertTrue(r.slotInProduct(k) < r.products[t.to].atoms.size)
            }
        }
        // overall: 2 x cathode + 1 x anode; electrons cancel, then 4 H⁺ + 4 OH⁻ -> 4 H₂O
        val c = Micro.CATHODE; val a = Micro.ANODE
        assertEquals(2 * c.eIn, a.eOut)
        val count = HashMap<String, Int>()
        fun add(list: List<Species>, k: Int) = list.forEach { count[it.formula] = (count[it.formula] ?: 0) + k }
        add(c.products, 2); add(a.products, 1); add(c.reactants, -2); add(a.reactants, -1)
        val neutral = minOf(count["H⁺"]!!, count["OH⁻"]!!)
        count["H⁺"] = count["H⁺"]!! - neutral; count["OH⁻"] = count["OH⁻"]!! - neutral; count["H₂O"] = count["H₂O"]!! + neutral
        println("ELECTRO overall (net): $count")
        assertEquals(-2, count["H₂O"]); assertEquals(2, count["H₂"]); assertEquals(1, count["O₂"])
        assertEquals(0, count["H⁺"]); assertEquals(0, count["OH⁻"])
    }

    @Test
    fun tubeFillsThenTripsAndVentingReArms() {
        val cell = Voltameter(capMl = 50.0)
        repeat(20000) { cell.step(0.1, 12.0, Electrolyte.NA2SO4) }
        println("ELECTRO full: H2 %.6f mL, O2 %.6f mL, Q %.3f C, t %.2f s".format(cell.gasH, cell.gasO, cell.q, cell.t))
        assertTrue(cell.full)
        assertEquals(50.0, cell.gasH, 1e-9); assertEquals(25.0, cell.gasO, 1e-9)
        assertEquals(50.0 / cell.kH, cell.q, 1e-6)
        assertEquals(0.0, cell.current, 0.0)
        val t0 = cell.t
        cell.step(10.0, 12.0, Electrolyte.NA2SO4)
        assertEquals(t0, cell.t, 0.0)                 // tripped: nothing moves
        cell.vent(true, 100.0)
        assertFalse(cell.full)
        assertEquals(50.0, cell.ventH, 1e-9)
        cell.step(1.0, 12.0, Electrolyte.NA2SO4)
        assertTrue(cell.current > 1.0)
    }

    @Test
    fun energyEfficiency() {
        // HHV voltage efficiency 1.481 V / U: 25 % at the default 6 V, 67 % at U_dec = 2.2 V
        assertEquals(0.2469, Echem.efficiency(6.0), 0.001)
        // electrical energy per mol H₂ = 2 F U; at 6 V: 1.158 MJ/mol vs 285.8 kJ/mol stored
        val e = 2 * Echem.F * 6.0
        println("ELECTRO 6 V: %.3f MJ per mol H2, efficiency %.1f %%".format(e / 1e6, 100 * Echem.efficiency(6.0)))
        assertEquals(Echem.DH_H2O / e, Echem.efficiency(6.0), 1e-12)
    }
}

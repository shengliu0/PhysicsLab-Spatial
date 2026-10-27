package com.bytedance.physicslab.solar.core.catalysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp

class CatalysisCoreTest {

    private fun runToEnd(r: Reactor, maxS: Double = 600.0, every: Double = 0.1, each: (Reactor) -> Unit = {}): Reactor {
        var s = 0.0
        while (s < maxS && r.conversion < 0.9999) { r.step(every); s += every; each(r) }
        return r
    }

    @Test
    fun stoichiometry100mLOf30Percent() {
        // 100 mL x 1.11 g/mL x 0.30 = 33.3 g H2O2 / 34.0147 g/mol = 0.979 mol -> 0.4895 mol O2
        // -> V = nRT/p = 0.4895 x 0.082057 L atm/(mol K) x 298.15 K / 1 atm = 11.98 L
        val n = Peroxide.moles(100.0, 0.30)
        val vO2 = Peroxide.gasLitres(n / 2)
        val byHand = 100 * 1.11 * 0.30 / 34.0147
        println("100 mL 30 %% H2O2: %.4f mol H2O2 -> %.4f mol O2 -> %.2f L O2 at 25 C, 1 atm".format(n, n / 2, vO2))
        assertEquals(byHand, n, 1e-9)
        assertEquals(0.979, n, 1e-3)
        assertEquals(n / 2 * 0.0820574 * 298.15, vO2, 1e-3)
        assertEquals(11.98, vO2, 0.02)
        // and 3 %: ~1.1 L — about one cylinder full of O2, which is why 3 % only just foams over
        val v3 = Peroxide.gasLitres(Peroxide.moles(100.0, 0.03) / 2)
        println("100 mL 3 %% H2O2 -> %.2f L O2".format(v3))
        assertEquals(1.09, v3, 0.02)
    }

    @Test
    fun adiabaticTemperatureRise() {
        // pure solution, closed form: dT = w dH / (M cp(w)); cp(w) = w 2.62 + (1-w) 4.18 J/(g K)
        val d3 = Peroxide.adiabaticRise(0.03); val d30 = Peroxide.adiabaticRise(0.30)
        val cp3 = 0.03 * 89.1 / 34.0147 + 0.97 * 4.18; val cp30 = 0.30 * 89.1 / 34.0147 + 0.70 * 4.18
        println("adiabatic rise, pure solution: 3 %% %.1f K, 30 %% %.1f K (-> capped at 100 C)".format(d3, d30))
        assertEquals(0.03 * 98200 / (34.0147 * cp3), d3, 1e-9)
        assertEquals(20.95, d3, 0.05)
        assertEquals(233.3, d30, 0.3)
        // the integrated energy balance of the actual mix (100 mL + 30 mL additives), no heat loss
        for (w in doubleArrayOf(0.03, 0.30)) {
            val r = Reactor(w, Catalyst.KI, 25.0, soap = true).also { it.ua = 0.0 }
            val q = r.n0 * Peroxide.DH
            val rise = q / r.heatCapacity
            runToEnd(r)
            if (25 + rise < 100) {
                println("mix %.0f %%: closed form +%.2f K, integrated %.2f -> %.2f C".format(w * 100, rise, 25.0, r.tC))
                assertEquals(25 + rise * r.conversion, r.tC, 0.01)
                assertTrue(r.tC < 45)
            } else {
                val steam = (q * r.conversion - r.heatCapacity * 75.0) / Peroxide.L_VAP
                println("mix %.0f %%: would rise %.0f K -> boils at 100 C, %.1f g of water boiled off (closed form %.1f g)".format(w * 100, rise, r.steamG, steam))
                assertEquals(100.0, r.tC, 1e-6)
                assertEquals(steam, r.steamG, 0.01 * steam)
            }
        }
    }

    @Test
    fun arrheniusRatio25To50() {
        // k(50)/k(25) = exp(Ea/R (1/298.15 - 1/323.15)); Ea 56 kJ/mol -> 5.74; 75 kJ/mol -> 10.4
        val x = (1 / 298.15 - 1 / 323.15) / 8.314462618
        val rKi = Peroxide.arrhenius(1.0, Kinetics.EA_KI, 323.15)
        val rUn = Peroxide.arrhenius(1.0, Kinetics.EA_UNCAT, 323.15)
        println("k(50 C)/k(25 C): iodide %.3f, uncatalysed %.2f, catalase %.3f".format(rKi, rUn, Peroxide.arrhenius(1.0, Kinetics.EA_CAT, 323.15)))
        assertEquals(exp(56e3 * x), rKi, 1e-12)
        assertEquals(5.74, rKi, 0.01)
        assertEquals(10.39, rUn, 0.02)
        // catalysis = a lower barrier: at 25 C the I- path is exp((75-56)/RT) = 2100x more probable per collision
        val factor = exp((Kinetics.EA_UNCAT - Kinetics.EA_KI) / (Peroxide.R * 298.15))
        println("Boltzmann factor gained by lowering Ea 75 -> 56 kJ/mol at 25 C: %.0f".format(factor))
        assertTrue(factor in 2000.0..2300.0)
    }

    @Test
    fun isothermalIodideRunIsFirstOrder() {
        // held at 25 C the two-step scheme must decay as exp(-k_eff t), k_eff = 2 k1 k2 [I]tot / (k1 + k2)
        val r = Reactor(0.10, Catalyst.KI, 25.0, soap = true).also { it.isothermal = true }
        val k1 = Kinetics.K1_KI; val k2 = Kinetics.K2_RATIO * k1
        val kEff = 2 * k1 * k2 * Kinetics.KI_M / (k1 + k2)
        var worst = 0.0
        for (k in 1..120) {
            r.step(1.0)
            if (k < 3) continue      // the first ~0.1 s builds up the IO- steady state
            val want = exp(-kEff * r.t)
            worst = maxOf(worst, abs(r.n / r.n0 - want) / want)
        }
        println("isothermal KI (25 C): k_eff = %.4f s^-1, half-life %.1f s, worst relative deviation %.2e".format(kEff, 0.693 / kEff, worst))
        assertTrue(worst < 2e-3)
    }

    @Test
    fun integratedKineticsConserveMass() {
        for (cat in listOf(Catalyst.KI, Catalyst.MNO2, Catalyst.CATALASE)) for (w in Peroxide.W) {
            val r = Reactor(w, cat, 25.0, soap = true)
            var worst = 0.0
            runToEnd(r) { worst = maxOf(worst, abs((it.n0 - it.n) - (2 * it.nO2 + it.hypoiodite)) / it.n0) }
            println("%-8s %2.0f %%: conversion %.4f, H2O2 used %.5f mol = 2 x O2 %.5f + IO- %.1e; worst imbalance %.1e; peak-to-end %.1f C, %d substeps"
                .format(cat, w * 100, r.conversion, r.n0 - r.n, r.nO2, r.hypoiodite, worst, r.tC, r.substeps))
            assertTrue(worst < 1e-12)
            if (cat != Catalyst.CATALASE) assertTrue(r.conversion > 0.999)
        }
    }

    @Test
    fun catalystIsNotConsumed() {
        val r = Reactor(0.30, Catalyst.KI, 25.0, soap = true)
        var worst = 0.0; var maxIo = 0.0
        runToEnd(r) { worst = maxOf(worst, abs(it.iodide + it.hypoiodite - it.iodideTotal) / it.iodideTotal); maxIo = maxOf(maxIo, it.hypoiodite / it.iodideTotal) }
        val turnovers = r.nO2 / r.iodideTotal
        println("KI 30 %%: I- total %.4f mol, end: I- %.4f + IO- %.1e (max IO- share %.3f %%), drift %.1e; each I- cycled %.1f times"
            .format(r.iodideTotal, r.iodide, r.hypoiodite, maxIo * 100, worst, turnovers))
        assertTrue(worst < 1e-12)
        assertTrue(r.iodide / r.iodideTotal > 0.995)
        assertTrue(turnovers > 3.0)
        // catalase at 3 % stays below 45 C: the enzyme comes out as it went in, after ~1e8 turnovers each
        val y = runToEnd(Reactor(0.03, Catalyst.CATALASE, 25.0, soap = true))
        val perEnzyme = (y.n0 - y.n) / y.enzymeTotal
        println("catalase 3 %%: done in %.1f s, max T %.1f C, active enzyme %.4f, each enzyme decomposed %.2e H2O2".format(y.t, y.tC, y.enzymeActive, perEnzyme))
        assertTrue(y.enzymeActive > 0.999)
        assertTrue(perEnzyme > 1e8)
        // ... but cooked by 30 %: denatured near 60 C before most of the H2O2 is gone
        val hot = Reactor(0.30, Catalyst.CATALASE, 25.0, soap = true)
        var tMax = 0.0
        runToEnd(hot, maxS = 300.0) { tMax = maxOf(tMax, it.tC) }
        println("catalase 30 %%: peak %.1f C, enzyme left %.3f, conversion stalls at %.2f".format(tMax, hot.enzymeActive, hot.conversion))
        assertTrue(hot.enzymeActive < 0.01 && hot.conversion < 0.5 && tMax in 50.0..75.0)
    }

    @Test
    fun uncatalysedRunMakesNoOxygenInDemoTime() {
        // worst case the panel allows: 30 %, started at 60 C, watched for 10 minutes
        val r = Reactor(0.30, Catalyst.NONE, 60.0, soap = true)
        r.step(600.0)
        println("uncatalysed 30 %% from 60 C, 600 s: conversion %.2e, O2 %.4f L".format(r.conversion, r.o2L25))
        assertTrue(r.conversion < 0.01)
        assertTrue(r.conversion > 0.0)
    }

    @Test
    fun heatFeedbackMakes30PercentRunAway() {
        val tl = HashMap<String, Triple<Double, Double, Double>>()
        for (w in doubleArrayOf(0.03, 0.10, 0.30)) {
            val r = Reactor(w, Catalyst.KI, 25.0, soap = true)
            var t5 = -1.0; var t50 = -1.0; var t95 = -1.0; var tMax = 0.0
            runToEnd(r, every = 0.01) {
                if (t5 < 0 && it.conversion >= 0.05) t5 = it.t
                if (t50 < 0 && it.conversion >= 0.5) t50 = it.t
                if (t95 < 0 && it.conversion >= 0.95) t95 = it.t
                tMax = maxOf(tMax, it.tC)
            }
            println("KI %2.0f %%: 5 %% at %.2f s, 50 %% at %.2f s, 95 %% at %.2f s, peak %.1f C, steam %.1f g, foam %.1f L".format(w * 100, t5, t50, t95, tMax, r.steamG, r.foamL))
            tl["$w"] = Triple(t50, t95, tMax)
        }
        // 30 %: boils within seconds; 3 %: a gentle minute-long fizz that stays below 45 C
        assertTrue(tl["0.3"]!!.second < 6.0 && tl["0.3"]!!.third >= 99.99)
        assertTrue(tl["0.03"]!!.second > 30.0 && tl["0.03"]!!.third < 45.0)
        // the rate rises ~6x from 25 to 50 C at equal concentration
        val a = Reactor(0.10, Catalyst.KI, 25.0, soap = true).rate(); val b = Reactor(0.10, Catalyst.KI, 50.0, soap = true).rate()
        assertEquals(5.74, b / a, 0.02)
    }

    @Test
    fun foamNeedsSoap() {
        val with = runToEnd(Reactor(0.10, Catalyst.KI, 25.0, soap = true))
        val without = runToEnd(Reactor(0.10, Catalyst.KI, 25.0, soap = false))
        println("10 %% KI: foam %.2f L with soap, %.2f L without (gas escaped %.2f L)".format(with.foamL, without.foamL, without.escapedL))
        assertEquals(0.0, without.foamL, 0.0)
        // gas left at the liquid temperature: more litres than at 25 C, never less
        assertTrue(with.trappedL + with.escapedL >= with.o2L25 * 0.999)
        assertEquals(Reactor.F_TRAP, with.trappedL / (with.trappedL + with.escapedL), 1e-9)
    }
}

package com.bytedance.physicslab.solar.core.flame

import com.bytedance.physicslab.solar.core.prism.Spectrum
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class FlameCoreTest {
    private fun xyz(vararg s: Salt, cobalt: Boolean = false) = FlameColour.xyz(Sample(*s), cobalt)
    private fun peakNm(f: (Double) -> Double): Double { var best = 380.0; var v = -1.0; var l = 380.0; while (l <= 780.0) { val y = f(l); if (y > v) { v = y; best = l }; l += 0.5 }; return best }

    // ------------------------------------------------------------------ colour matching functions

    @Test
    fun cmfFitAgreesWithTheCieTableWhereItShould() {
        // CIE 1931 2° at 600 nm (tabulated): x̄ 1.0622, ȳ 0.6310, z̄ 0.0008
        val t = FlameColour.cmf(600.0); val w = Wyman2013.xyz(600.0)
        println("CMF 600 nm  table %.4f %.4f %.5f   WSS2013 fit %.4f %.4f %.5f".format(t[0], t[1], t[2], w[0], w[1], w[2]))
        assertEquals(1.0622, t[0], 1e-4); assertEquals(0.6310, t[1], 1e-4)
        assertEquals(t[0], w[0], 0.02 * t[0]); assertEquals(t[1], w[1], 0.02 * t[1])        // within 2 %
        // peak positions (CIE: x̄ ~599, ȳ 555, z̄ ~446 nm)
        val px = peakNm { Wyman2013.xyz(it)[0] }; val py = peakNm { Wyman2013.xyz(it)[1] }; val pz = peakNm { Wyman2013.xyz(it)[2] }
        val tx = peakNm { FlameColour.cmf(it)[0] }; val ty = peakNm { FlameColour.cmf(it)[1] }; val tz = peakNm { FlameColour.cmf(it)[2] }
        println("CMF peaks   table x̄ %.1f ȳ %.1f z̄ %.1f   fit x̄ %.1f ȳ %.1f z̄ %.1f nm".format(tx, ty, tz, px, py, pz))
        assertEquals(599.0, px, 3.0); assertEquals(555.0, py, 5.0); assertEquals(446.0, pz, 5.0)
        assertEquals(600.0, tx, 3.0); assertEquals(555.0, ty, 6.0); assertEquals(448.0, tz, 4.0)
        // the fit tracks the dominant channel to a few % over 450-650 nm ...
        for (l in 450..650 step 10) {
            val a = FlameColour.cmf(l.toDouble()); val b = Wyman2013.xyz(l.toDouble())
            val k = (0..2).maxByOrNull { a[it] }!!
            assertEquals("dominant CMF channel at $l nm", a[k], b[k], 0.06 * a[k])
        }
        // ... but its Gaussian tail is ~1000x too low at potassium's red line: why the table is used
        val r766 = Wyman2013.xyz(766.49)[0] / FlameColour.cmf(766.49)[0]
        println("CMF x̄(766.5 nm): fit / table = %.2e  -> the analytic fit would erase K's red line".format(r766))
        assertTrue(r766 < 0.01)
    }

    // ------------------------------------------------------------------ photon energy and NIST data

    @Test
    fun photonEnergyIsHcOverLambda() {
        val e = Photon.energyEv(589.0)
        println("E(589 nm) = hc/λ = %.4f eV;  E(766.5) = %.4f eV;  E(670.8) = %.4f eV".format(e, Photon.energyEv(766.49), Photon.energyEv(670.78)))
        assertEquals(2.105, e, 0.0005)
        // h c / e from the exact SI constants
        assertEquals(6.62607015e-34 * 299792458.0 / 1.602176634e-19 * 1e9, Photon.HC_EV_NM, 1e-6)
    }

    @Test
    fun everyAtomicLineMatchesItsNistLevels() {
        // λ_air = hc/(E_upper - E_lower) / n_air: the line list and the level diagram are the same data
        var worst = 0.0
        for (s in Salts.ALL) for (f in s.features) if (f.isLine) {
            val nm = Photon.airNmOf(f.upperEv - f.lowerEv)
            worst = maxOf(worst, abs(nm - f.nm))
            assertEquals("${s.symbol} ${f.nm} nm (${f.upperName} -> ${f.lowerName})", f.nm, nm, 0.02)
        }
        println("NIST levels -> lines: worst |Δλ| = %.4f nm".format(worst))
        // sodium D doublet: 0.597 nm apart; D2 = 2 x D1 in gA (statistical weights 4:2)
        assertEquals(0.597, Salts.NA.features[1].nm - Salts.NA.features[0].nm, 1e-3)
        assertEquals(2.0, Salts.NA.features[0].power / Salts.NA.features[1].power, 1e-9)
    }

    // ------------------------------------------------------------------ flame colours from spectra

    @Test
    fun flameColoursComeOutOfTheSpectra() {
        val rows = StringBuilder()
        for (s in Salts.ALL) {
            val x = xyz(s); val c = FlameColour.chromaticity(x); val n = FlameColour.name(x)
            rows.append("\n  %-6s xy=(%.3f, %.3f)  dominant %s  hue %3.0f°  -> %s / %s".format(s.formula, c[0], c[1],
                FlameColour.dominantNm(x).let { if (it.isNaN()) " purple " else "%.1f nm".format(it) }, FlameColour.hueDeg(x), n.first, n.second))
        }
        println("FLAME COLOURS (relative powers as in Salts):$rows")
        // sodium: the D lines ARE the colour — dominant 589 nm, a yellow-orange hue on screen
        val na = xyz(Salts.NA)
        assertEquals(589.2, FlameColour.dominantNm(na), 0.5)
        assertTrue(FlameColour.hueDeg(na) in 25.0..50.0)
        assertEquals("yellow-orange", FlameColour.name(na).second)
        // lithium 670.8 nm: red (crimson)
        val li = xyz(Salts.LI)
        assertTrue(FlameColour.dominantNm(li) > 640.0)
        assertTrue(FlameColour.hueDeg(li).let { it > 340.0 || it < 10.0 })
        // potassium: red 766 + violet 404 -> a purple (no dominant wavelength), violet on screen
        val k = xyz(Salts.K)
        assertTrue(FlameColour.dominantNm(k).isNaN())
        assertTrue(FlameColour.hueDeg(k) in 255.0..300.0)
        // the textbook names follow from the spectra
        assertEquals("orange-red", FlameColour.name(xyz(Salts.CA)).second)
        assertEquals("red", FlameColour.name(xyz(Salts.SR)).second)
        assertEquals("yellow-green", FlameColour.name(xyz(Salts.BA)).second)
        assertEquals("blue-green", FlameColour.name(xyz(Salts.CU)).second)
    }

    @Test
    fun mixtureColourIsTheXyzSum() {
        // Grassmann additivity: the mixture's tristimulus values are the sum of its parts
        for (cobalt in listOf(false, true)) {
            val mix = xyz(Salts.NA, Salts.K, cobalt = cobalt); val a = xyz(Salts.NA, cobalt = cobalt); val b = xyz(Salts.K, cobalt = cobalt)
            for (i in 0..2) assertEquals(a[i] + b[i], mix[i], 1e-12 * (a[i] + b[i] + 1e-30))
        }
        // so its chromaticity lies on the straight line between the two
        val m = FlameColour.chromaticity(xyz(Salts.NA, Salts.CU)); val p = FlameColour.chromaticity(xyz(Salts.NA)); val q = FlameColour.chromaticity(xyz(Salts.CU))
        val cross = (q[0] - p[0]) * (m[1] - p[1]) - (q[1] - p[1]) * (m[0] - p[0])
        assertEquals(0.0, cross, 1e-12)
        // NaCl + KCl by eye: sodium's yellow swamps potassium (equal amounts, K radiates 6x the power)
        val nk = xyz(Salts.NA, Salts.K)
        println("NaCl+KCl: dominant %.1f nm (%s); through cobalt glass: %s, hue %.0f°".format(FlameColour.dominantNm(nk), FlameColour.name(nk).second,
            FlameColour.name(xyz(Salts.NA, Salts.K, cobalt = true)).second, FlameColour.hueDeg(xyz(Salts.NA, Salts.K, cobalt = true))))
        assertEquals(589.0, FlameColour.dominantNm(nk), 1.5)
    }

    // ------------------------------------------------------------------ cobalt glass

    @Test
    fun cobaltGlassRemovesSodiumKeepsPotassium() {
        val t = CobaltGlass::transmission
        println("Cobalt glass T: 404.4 %.3f · 500 %.2f · 540 %.1e · 589.0 %.1e · 589.6 %.1e · 622 %.1e · 670.8 %.2f · 766.5 %.3f".format(
            t(404.414), t(500.0), t(540.0), t(588.995), t(589.592), t(622.0), t(670.78), t(766.49)))
        assertTrue(t(588.995) < 0.02 && t(589.592) < 0.02)
        assertTrue(t(404.414) > 0.5 && t(766.49) > 0.5 && t(769.896) > 0.5)
        // seen through the glass: sodium disappears, potassium's violet survives, the mixture turns violet
        val na0 = xyz(Salts.NA); val na1 = xyz(Salts.NA, cobalt = true)
        val k0 = xyz(Salts.K); val k1 = xyz(Salts.K, cobalt = true)
        println("Through cobalt: Na luminance x%.1e (level %.2f -> %.2f), K x%.2f (level %.2f -> %.2f)".format(
            na1[1] / na0[1], FlameColour.level(na0), FlameColour.level(na1), k1[1] / k0[1], FlameColour.level(k0), FlameColour.level(k1)))
        assertTrue(FlameColour.level(na1) < 0.05)
        assertTrue(FlameColour.level(k1) > 0.5)
        val mix = xyz(Salts.NA, Salts.K, cobalt = true)
        assertTrue(FlameColour.dominantNm(mix).isNaN())                       // a purple
        assertTrue(FlameColour.hueDeg(mix) in 255.0..300.0)
        assertTrue(FlameColour.level(mix) > 0.5)
        // every plain salt is shown at full brightness without the glass
        for (s in Salts.ALL) assertEquals(s.formula, 1.0, FlameColour.level(xyz(s)), 1e-9)
    }

    @Test
    fun displayChromaIsInGamutAndNormalised() {
        for (s in Salts.ALL) for (cob in listOf(false, true)) {
            val c = FlameColour.chroma(xyz(s, cobalt = cob))
            assertTrue(c.all { it in 0.0..1.0 }); assertEquals(1.0, c.max(), 1e-12)
        }
        // the strip's line brightness: monotone, 1 at the strongest line, weak K 404 still visible
        assertEquals(1.0, FlameColour.lineBrightness(1.0, 1.0), 1e-12)
        assertTrue(FlameColour.lineBrightness(0.01, 1.0) in 0.1..0.2)
        // the exposed core of a sodium flame shifts toward yellow (camera-like channel clipping)
        val na = FlameColour.chroma(xyz(Salts.NA)); val core = FlameColour.exposed(na, 2.2)
        assertTrue(Spectrum.hueDeg(DoubleArray(3) { Spectrum.encode(core[it]) }) > Spectrum.hueDeg(DoubleArray(3) { Spectrum.encode(na[it]) }) + 5)
    }
}

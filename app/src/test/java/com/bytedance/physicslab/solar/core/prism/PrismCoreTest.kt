package com.bytedance.physicslab.solar.core.prism

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin

class PrismCoreTest {
    private fun deg(r: Double) = Math.toDegrees(r)
    private fun rad(d: Double) = Math.toRadians(d)

    // ------------------------------------------------------------------ colour

    @Test
    fun cieTableIsTheStandardObserver() {
        // spectral locus chromaticities (CIE 1931): 520 nm (0.0743, 0.8338), 700 nm (0.7347, 0.2653)
        for ((l, xy) in mapOf(520.0 to (0.0743 to 0.8338), 700.0 to (0.7347 to 0.2653), 480.0 to (0.0913 to 0.1327))) {
            val c = Spectrum.cmf(l); val s = c.sum()
            assertEquals(xy.first, c[0] / s, 2e-4); assertEquals(xy.second, c[1] / s, 2e-4)
        }
        // equal-energy white sits at (1/3, 1/3): the three columns integrate to the same area
        val sum = DoubleArray(3)
        for (l in 380..780 step 10) { val c = Spectrum.cmf(l.toDouble()); for (k in 0..2) sum[k] += c[k] }
        println("CIE column sums: ${sum.joinToString { "%.4f".format(it) }} (expected ~10.684 each)")
        for (k in 0..2) assertEquals(10.684, sum[k], 0.01)
        assertEquals(1.0, Spectrum.cmf(555.0)[1], 0.006)
    }

    @Test
    fun whiteBalancedWeightsSumToWhite() {
        val w = DoubleArray(3)
        for (l in Spectrum.GRID) { val c = Spectrum.weights(l); for (k in 0..2) w[k] += c[k] }
        for (k in 0..2) assertEquals(1.0, w[k], 1e-12)
    }

    @Test
    fun rainbowHueOrderRedToViolet() {
        // unwrapped HSV hue must rise monotonically as the wavelength falls: red, orange, yellow,
        // green, cyan, blue, violet — the colour order on any spectrum or rainbow
        var prev = -1e9
        val names = StringBuilder()
        for (l in 700 downTo 400 step 10) {
            val h0 = Spectrum.hueDeg(Spectrum.display(l.toDouble()))
            val h = if (h0 > 300) h0 - 360 else h0
            assertTrue("hue not monotone at $l nm ($h after $prev)", h >= prev - 0.05)
            prev = h
            if (l % 50 == 0) names.append("$l:${Spectrum.name(l.toDouble())}%.0f° ".format(h))
        }
        println("hue order: $names")
        val d650 = Spectrum.display(650.0); val d532 = Spectrum.display(532.0); val d450 = Spectrum.display(450.0)
        assertTrue(d650[0] == 1.0 && d650[1] < 0.05); assertTrue(d532[1] == 1.0 && d532[0] < 0.05)
        assertTrue(d450[2] == 1.0 && d450[1] < 0.05)
        assertEquals("红", Spectrum.name(650.0)); assertEquals("紫", Spectrum.name(410.0))
    }

    // ------------------------------------------------------------------ glass

    @Test
    fun sellmeierReproducesSchottCatalogue() {
        // catalogue n_d and V_d (SCHOTT datasheets)
        val cat = mapOf(Glasses.BK7 to (1.51680 to 64.17), Glasses.F2 to (1.62004 to 36.37), Glasses.SF11 to (1.78472 to 25.68))
        for ((g, v) in cat) {
            println("${g.id}: n_d=%.5f n_F=%.5f n_C=%.5f V_d=%.2f  n(400)=%.4f n(700)=%.4f".format(g.nd, g.nF, g.nC, g.abbe, g.n(400.0), g.n(700.0)))
            assertEquals(v.first, g.nd, 2e-5)
            assertEquals(v.second, g.abbe, 0.02)
        }
        assertEquals(1.52238, Glasses.BK7.nF, 2e-5); assertEquals(1.51432, Glasses.BK7.nC, 2e-5)
        // normal dispersion: n falls monotonically with wavelength across the visible
        for (g in Glasses.ALL) for (l in 380 until 730) assertTrue(g.n(l.toDouble()) > g.n(l + 1.0))
    }

    @Test
    fun cauchyIsGoodForCrownPoorForDenseFlint() {
        fun worst(g: Glass): Double {
            val (a, b) = g.cauchy().let { it[0] to it[1] }
            var w = 0.0
            for (l in 400..700) w = maxOf(w, abs(a + b / (l.toDouble() * l) - g.n(l.toDouble())))
            return w
        }
        val bk7 = worst(Glasses.BK7); val sf11 = worst(Glasses.SF11)
        println("2-term Cauchy vs Sellmeier, worst |dn| over 400-700 nm: BK7 %.1e, SF11 %.1e".format(bk7, sf11))
        assertTrue(bk7 < 6e-4)
        assertTrue(sf11 > bk7)
    }

    // ------------------------------------------------------------------ prism

    /** Trace a ray through a prism oriented for incidence i1 and return the deviation (rad). */
    private fun traceDev(n: Double, a: Double, i1: Double, clockwise: Boolean = true): Double? {
        val poly = Ray2.prism(0.0, 0.0, a, 0.1, 1.0, 0.0, i1, clockwise)
        // aim at the midpoint of the entry face (apex -> entry-side base corner: vertex 1 if
        // clockwise, vertex 2 otherwise — the CCW order flips with the mirror)
        val e = if (clockwise) 2 else 4
        val mx = (poly[0] + poly[e]) / 2; val my = (poly[1] + poly[e + 1]) / 2
        val tr = Ray2.traceConvex(poly, n, mx - 1.0, my, 1.0, 0.0)
        val o = tr.out ?: return null
        if (tr.tir > 0) return null
        return -Ray2.angleBetween(1.0, 0.0, o[0], o[1]) * if (clockwise) 1 else -1
    }

    @Test
    fun tracerMatchesAnalyticDeviationAndMinimum() {
        val a = rad(60.0)
        for (g in Glasses.ALL) {
            val n = g.nd
            val im = PrismOptics.minDeviationIncidence(n, a)
            val dm = PrismOptics.minDeviation(n, a)
            assertEquals(dm, traceDev(n, a, im)!!, 1e-9)
            assertEquals(dm, traceDev(n, a, im, clockwise = false)!!, 1e-9)
            // analytic D(i1) == traced D(i1), and nothing beats the symmetric passage
            for (di in -8..25) {
                val i1 = im + rad(di.toDouble())
                val an = PrismOptics.deviation(n, a, i1)
                val tr = traceDev(n, a, i1)
                if (an.isNaN()) { assertNull(tr); continue }
                assertEquals(an, tr!!, 1e-9)
                assertTrue(an >= dm - 1e-12)
            }
            println("${g.id} 60°: D_min = %.3f° at i1 = %.3f°".format(deg(dm), deg(im)))
        }
        assertEquals(38.647, deg(PrismOptics.minDeviation(Glasses.BK7.nd, a)), 2e-3)
    }

    @Test
    fun symmetricPassageAtMinimumDeviation() {
        // at D_min the ray inside runs parallel to the base: entry and exit points at equal height
        // above the base, i.e. the internal segment is perpendicular to the apex bisector
        val a = rad(60.0); val n = Glasses.F2.nd
        val im = PrismOptics.minDeviationIncidence(n, a)
        val poly = Ray2.prism(0.0, 0.0, a, 0.1, 1.0, 0.0, im, true)
        val mx = (poly[0] + poly[2]) / 2; val my = (poly[1] + poly[3]) / 2
        val tr = Ray2.traceConvex(poly, n, mx - 1.0, my, 1.0, 0.0)
        val bx = poly[4] - poly[2]; val by = poly[5] - poly[3]           // base direction
        val ix = tr.pts[2] - tr.pts[0]; val iy = tr.pts[3] - tr.pts[1]   // internal ray
        assertEquals(0.0, Ray2.angleBetween(bx, by, ix, iy), 1e-9)
        // and Snell holds at the entry face: sin i1 = n sin r1, r1 = A/2
        assertEquals(sin(im), n * sin(a / 2), 1e-12)
    }

    @Test
    fun totalInternalReflectionAtTheExitFace() {
        // SF11, 60°: critical angle 34.1°; light only passes when r2 = A - r1 < θc, i.e. i1 > i1c
        val a = rad(60.0); val n = Glasses.SF11.nd
        val tc = PrismOptics.criticalAngle(n)
        val i1c = asin(n * sin(a - tc))
        println("SF11 60°: θc = %.2f°, light gets through only for i1 > %.2f°".format(deg(tc), deg(i1c)))
        assertEquals(34.07, deg(tc), 0.01)
        assertNotNull(traceDev(n, a, i1c + rad(0.5)))
        assertNull(traceDev(n, a, i1c - rad(0.5)))
        val poly = Ray2.prism(0.0, 0.0, a, 0.1, 1.0, 0.0, i1c - rad(2.0), true)
        val mx = (poly[0] + poly[2]) / 2; val my = (poly[1] + poly[3]) / 2
        val tr = Ray2.traceConvex(poly, n, mx - 1.0, my, 1.0, 0.0)
        assertTrue("expected TIR, got ${tr.tir}", tr.tir >= 1)
    }

    @Test
    fun unityThinPrismFormulaAudit() {
        // Unity: δ = (n − 1)A with n = 1.513 (red) ... 1.528 (violet), yellow 1.517
        val nY = 1.517; val nR = 1.513; val nV = 1.528
        for (aDeg in listOf(30.0, 45.0, 60.0)) {
            val a = rad(aDeg)
            val thin = deg(PrismOptics.thinPrismDeviation(nY, a)); val exact = deg(PrismOptics.minDeviation(nY, a))
            val sThin = deg(PrismOptics.thinPrismDeviation(nV, a) - PrismOptics.thinPrismDeviation(nR, a))
            val sExact = deg(PrismOptics.minDeviation(nV, a) - PrismOptics.minDeviation(nR, a))
            println("A=%2.0f°: deviation thin %.2f° vs exact %.2f° (%.1f%%); red-violet spread thin %.2f° vs %.2f° (%.0f%%)".format(
                aDeg, thin, exact, (thin / exact - 1) * 100, sThin, sExact, (sThin / sExact - 1) * 100))
        }
        val a = rad(60.0)
        assertEquals(31.02, deg(PrismOptics.thinPrismDeviation(nY, a)), 0.01)
        assertEquals(38.66, deg(PrismOptics.minDeviation(nY, a)), 0.01)
        assertEquals(1.32, deg(PrismOptics.minDeviation(nV, a) - PrismOptics.minDeviation(nR, a)), 0.02)
        // Unity measured δ from (1, −0.15) instead of the incoming beam (0.55, −0.18): 9.59° lost
        val lost = deg(kotlin.math.atan2(0.15, 1.0) - kotlin.math.atan2(0.18, 0.55)).let { abs(it) }
        assertEquals(9.59, lost, 0.01)
        // and its drawn glass had apex 2·atan(halfBase / 0.32) with halfBase = 0.18 tan(A/2) + 0.13
        for ((aDeg, drawn) in listOf(30.0 to 58.2, 45.0 to 65.2, 60.0 to 72.3)) {
            val hb = 0.18 * kotlin.math.tan(rad(aDeg) / 2) + 0.05 + 0.08
            assertEquals(drawn, deg(2 * kotlin.math.atan(hb / 0.32)), 0.1)
        }
    }

    @Test
    fun violetDeviatesMostRedLeast() {
        // fixed prism orientation (min deviation at the d line), every wavelength traced
        val a = rad(60.0)
        for (g in Glasses.ALL) {
            val i1 = PrismOptics.minDeviationIncidence(g.nd, a)
            var prev = -1.0
            for (l in 700 downTo 400 step 10) {
                val d = traceDev(g.n(l.toDouble()), a, i1)!!
                assertTrue("${g.id}: D($l) = $d not above $prev", d > prev)
                prev = d
            }
            val spread = deg(traceDev(g.n(400.0), a, i1)!! - traceDev(g.n(700.0), a, i1)!!)
            val exact = deg(PrismOptics.deviation(g.n(400.0), a, i1) - PrismOptics.deviation(g.n(700.0), a, i1))
            val linear = deg((g.n(400.0) - g.n(700.0)) * PrismOptics.dDdnAtMin(g.nd, a))
            println("${g.id}: 400-700 nm fan = %.2f° (analytic %.2f°, linear dD/dn estimate %.2f°)".format(spread, exact, linear))
            assertEquals(exact, spread, 1e-7)
            assertEquals(linear, spread, 0.2 * linear)
        }
    }

    @Test
    fun experimentumCrucisSecondPrismDoesNotSplitAndKeepsOrder() {
        // Newton 1666: a single colour picked out of the spectrum is refracted again by a second
        // prism but no new colours appear; and at the SAME incidence the more refrangible (violet)
        // is bent more than red. Same glass, same orientation, same entry ray.
        val a = rad(60.0); val g = Glasses.F2
        val i1 = PrismOptics.minDeviationIncidence(g.nd, a)
        val dRed = traceDev(g.n(650.0), a, i1, clockwise = false)!!
        val dViolet = traceDev(g.n(420.0), a, i1, clockwise = false)!!
        assertTrue(dViolet > dRed)
        // a 4 nm slice of green stays a 4 nm slice: its angular width after prism 2 is ~1/75 of
        // the full white fan's width
        val slice = traceDev(g.n(532.0), a, i1)!! - traceDev(g.n(536.0), a, i1)!!
        val full = traceDev(g.n(400.0), a, i1)!! - traceDev(g.n(700.0), a, i1)!!
        println("crucis: red %.2f°, violet %.2f°; 4 nm slice spreads %.3f° vs white %.2f°".format(deg(dRed), deg(dViolet), deg(slice), deg(full)))
        assertTrue(slice / full < 0.03)
    }

    @Test
    fun snellVectorFormIsSnell() {
        for (iDeg in listOf(0.0, 10.0, 30.0, 60.0, 80.0)) {
            val i = rad(iDeg)
            val t = Ray2.refract(sin(i), -cos(i), 0.0, 1.0, 1 / 1.5)!!
            assertEquals(sin(i), 1.5 * t[0], 1e-12)
            assertEquals(1.0, t[0] * t[0] + t[1] * t[1], 1e-12)
        }
        // inside glass beyond the critical angle: null
        assertNull(Ray2.refract(sin(rad(45.0)), -cos(rad(45.0)), 0.0, 1.0, 1.5))
    }
}

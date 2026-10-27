package com.bytedance.physicslab.solar.core.young

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sqrt

class DoubleSlitTest {
    // a classroom double slit (PASCO-like): a = 0.05 mm, d = 0.25 mm, green laser, 1 m screen
    private val lam = 532e-9; private val d = 0.25e-3; private val a = 0.05e-3; private val L = 1.0

    private fun onScreen(y: Double, l: Double = L) = y / sqrt(y * y + l * l)

    /** Local maxima of the screen pattern in [-yMax, yMax] (step dy). */
    private fun peaks(f: (Double) -> Double, yMax: Double, dy: Double): List<Double> {
        val out = ArrayList<Double>()
        var y = -yMax
        var p0 = f(y - dy); var p1 = f(y)
        while (y < yMax) {
            val p2 = f(y + dy)
            if (p1 > p0 && p1 >= p2 && p1 > 1e-3) {
                // parabolic refinement
                val den = p0 - 2 * p1 + p2
                out += y + if (abs(den) > 1e-300) 0.5 * dy * (p0 - p2) / den else 0.0
            }
            p0 = p1; p1 = p2; y += dy
        }
        return out
    }

    @Test
    fun brightFringesAreLambdaLOverDApart() {
        val dy = DoubleSlit.fringeSpacing(lam, L, d)
        assertEquals(2.128e-3, dy, 1e-6)
        val pk = peaks({ DoubleSlit.intensity(onScreen(it), lam, d, a) }, 4.5 * dy, 2e-7)
        val central = pk.minByOrNull { abs(it) }!!
        assertEquals(0.0, central, 1e-9)
        for (m in 1..3) {
            val ym = pk.filter { it > 0 }.sorted()[m - 1]
            // the sinc² envelope pulls each MAXIMUM inward a little (1.4% of y at orders 1-3 for
            // d/a = 5) — the dark fringes are where the formula is exact: d sinθ = (m + ½)λ
            println("order $m: peak %.4f mm, mλL/d = %.4f mm, exact d sinθ = mλ: %.4f mm".format(
                ym * 1e3, m * dy * 1e3, DoubleSlit.brightY(m, lam, L, d) * 1e3))
            assertEquals(m * dy, ym, 0.02 * m * dy)
        }
        for (m in 0..3) {
            val s = (m + 0.5) * lam / d
            val yDark = L * s / sqrt(1 - s * s)
            assertTrue(DoubleSlit.intensity(onScreen(yDark), lam, d, a) < 1e-20)
            assertEquals((m + 0.5) * dy, yDark, 1e-4 * dy)       // small-angle (tan vs sin) error ~ s²/2 = 1.4e-5
        }
    }

    @Test
    fun farFieldFormulaAgreesWithHuygensSum() {
        // direct Huygens-Fresnel summation over both apertures, no far-field approximation
        val i0 = DoubleSlit.huygens(0.0, lam, d, a, L)
        var worst = 0.0
        var y = -10e-3
        while (y <= 10e-3) {
            val h = DoubleSlit.huygens(y, lam, d, a, L) / i0
            val f = DoubleSlit.intensity(onScreen(y), lam, d, a)
            worst = maxOf(worst, abs(h - f))
            y += 0.05e-3
        }
        println("Fraunhofer vs Huygens at L = 1 m over ±10 mm: worst |ΔI| = %.4f".format(worst))
        assertTrue(worst < 0.03)
    }

    @Test
    fun missingOrderWhereDOverAIsAnInteger() {
        // d/a = 5: the 5th bright fringe falls on the first zero of the single-slit envelope
        val y5 = DoubleSlit.brightY(5, lam, L, d)
        val y4 = DoubleSlit.brightY(4, lam, L, d)
        val i5 = DoubleSlit.intensity(onScreen(y5), lam, d, a)
        val i4 = DoubleSlit.intensity(onScreen(y4), lam, d, a)
        println("d/a = 5: I(order 4) = %.4f, I(order 5) = %.2e".format(i4, i5))
        assertTrue(i5 < 1e-6)
        assertTrue(i4 > 0.02)
    }

    @Test
    fun coveringOneSlitBrightensTheDarkFringes() {
        val dy = DoubleSlit.fringeSpacing(lam, L, d)
        for (m in 0..2) {
            val y = (m + 0.5) * dy
            val two = DoubleSlit.intensity(onScreen(y), lam, d, a)
            val one = DoubleSlit.intensity(onScreen(y), lam, d, a, open2 = false)
            assertTrue("dark fringe $m: two=$two one=$one", two < 1e-6 && one > 0.05)
            // and the Huygens sum says the same with one aperture
            val h1 = DoubleSlit.huygens(y, lam, d, a, L, open2 = false) / DoubleSlit.huygens(0.0, lam, d, a, L)
            assertEquals(one, h1, 0.02)
        }
        // one slit alone: 1/4 of the two-slit peak on the axis (amplitude halves)
        assertEquals(0.25, DoubleSlit.intensity(0.0, lam, d, a, open1 = false), 1e-12)
    }

    @Test
    fun finiteSourceSlitVisibility() {
        // incoherent points across a source slit a0 at L0: each shifts the fringes; their sum has
        // contrast |sinc(π a0 d / (λ L0))| (van Cittert–Zernike). Check by brute force.
        val a0 = 0.08e-3; val l0 = 0.20
        for (dd in listOf(0.15e-3, 0.25e-3, 0.40e-3)) {
            fun summed(phase: Double): Double {
                var s = 0.0; val n = 400
                for (j in 0 until n) {
                    val x0 = -a0 / 2 + a0 * (j + 0.5) / n
                    val p1 = sqrt(l0 * l0 + (dd / 2 - x0) * (dd / 2 - x0))
                    val p2 = sqrt(l0 * l0 + (dd / 2 + x0) * (dd / 2 + x0))
                    s += 1 + cos(phase + 2 * PI * (p1 - p2) / lam)
                }
                return s / n
            }
            val iMax = (0 until 360).maxOf { summed(it * PI / 180) }
            val iMin = (0 until 360).minOf { summed(it * PI / 180) }
            val vNum = (iMax - iMin) / (iMax + iMin)
            val v = DoubleSlit.visibility(a0, dd, lam, l0)
            println("d = %.2f mm: visibility formula %.4f, brute force %.4f".format(dd * 1e3, v, vNum))
            assertEquals(v, vNum, 0.005)
        }
    }

    @Test
    fun whiteLightHasAWhiteCentreAndColouredOrders() {
        val c0 = DoubleSlit.whiteRgb(0.0, d, a)
        for (k in 0..2) assertEquals(1.0, c0[k], 1e-9)
        // first order: the red channel peaks farther out than the blue channel (λ_red > λ_blue)
        fun channelPeak(ch: Int): Double {
            var best = 0.0; var arg = 0.0; var y = 1.0e-3
            while (y < 3.6e-3) {
                val v = DoubleSlit.whiteRgb(onScreen(y), d, a)[ch]
                if (v > best) { best = v; arg = y }
                y += 0.005e-3
            }
            return arg
        }
        val yr = channelPeak(0); val yb = channelPeak(2)
        println("white light, 1st order: red peak %.3f mm, blue peak %.3f mm".format(yr * 1e3, yb * 1e3))
        assertTrue(yr > yb + 0.3e-3)
        // fringes wash out with order (white light is coherent over only ~2 wavelengths). Use a
        // narrow slit (a = 0.01 mm) so the single-slit envelope does not add its own contrast.
        fun contrast(m: Double): Double {
            val dy = DoubleSlit.fringeSpacing(550e-9, L, d)
            var hi = 0.0; var lo = 1e9; var y = (m - 0.5) * dy
            while (y < (m + 0.5) * dy) {
                val c = DoubleSlit.whiteRgb(onScreen(y), d, 0.01e-3)
                val lum = 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]
                hi = maxOf(hi, lum); lo = minOf(lo, lum); y += 0.005e-3
            }
            return (hi - lo) / (hi + lo)
        }
        println("white-light contrast by order: " + (1..6).joinToString { "%d: %.2f".format(it, contrast(it.toDouble())) })
        assertTrue(contrast(1.0) > 0.9 && contrast(4.0) < 0.35 && contrast(6.0) < 0.1)
    }

    @Test
    fun unityTwoSpeakerAudit() {
        // 1) Unity coloured by amplitude |cos(πΔ/λ)|: the "bright" part (> half max) of each period
        val n = 100000
        var ampBright = 0; var intBright = 0
        for (j in 0 until n) {
            val x = PI * j / n
            if (abs(cos(x)) > 0.5) ampBright++
            if (cos(x) * cos(x) > 0.5) intBright++
        }
        println("bright fraction of a period: amplitude %.3f vs intensity %.3f".format(ampBright.toDouble() / n, intBright.toDouble() / n))
        assertEquals(2.0 / 3, ampBright.toDouble() / n, 1e-3)
        assertEquals(0.5, intBright.toDouble() / n, 1e-3)
        // 2) "walk into a dark lane: loud to silent". Speakers at (±0.2, -0.35), grid point
        //    (-0.55, -0.25): amplitudes ∝ 1/r give a minimum of -9.1 dB, not silence
        val r1 = sqrt(0.35 * 0.35 + 0.1 * 0.1); val r2 = sqrt(0.75 * 0.75 + 0.1 * 0.1)
        val db = 10 * log10(DoubleSlit.nodalMinimum(1 / r1, 1 / r2))
        println("Unity nodal lane at (-0.55, -0.25): minimum = %.1f dB re maximum".format(db))
        assertEquals(-9.1, db, 0.1)
        assertEquals(0.0, DoubleSlit.nodalMinimum(1.0, 1.0), 0.0)
    }
}

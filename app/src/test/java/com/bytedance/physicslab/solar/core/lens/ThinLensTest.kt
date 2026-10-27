package com.bytedance.physicslab.solar.core.lens

import com.bytedance.physicslab.solar.core.lens.ThinLens.Case
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The thin-lens model (real-is-positive convention: u > 0 real object, v > 0 real image behind the
 * lens, v < 0 virtual image on the object's side, m = −v/u). References: Hecht, Optics 5th ed.
 * §5.2.3 (Gaussian 1/s_o + 1/s_i = 1/f, Newtonian x_o x_i = f², M_T = −s_i/s_o); 人教版 物理 八年级上
 * §5.3 table "凸透镜成像的规律".
 */
class ThinLensTest {
    private val fs = doubleArrayOf(10.0, 15.0, 20.0)

    /** u from 0.1f to 6f in 0.05f steps, skipping u = f. */
    private fun us(f: Double) = (2..120).map { it * 0.05 * f }.filter { abs(it - f) > 1e-9 }

    @Test
    fun thinLensEquationHoldsForRealAndVirtualImages() {
        var worst = 0.0; var n = 0
        for (f in fs) for (u in us(f)) {
            val v = ThinLens.imageDistance(u, f)
            worst = max(worst, abs(1 / u + 1 / v - 1 / f) * f)                        // relative residual
            assertEquals(u * f / (u - f), v, 1e-12 * abs(v))                           // closed form
            assertEquals(f * f, (u - f) * (v - f), 1e-9 * f * f)                       // Newtonian form
            assertTrue("u=$u f=$f v=$v", if (u > f) v > 0 else v < 0)                  // real beyond F, virtual inside
            n++
        }
        assertTrue("residual $worst", worst < 1e-12)
        println("thin lens: $n (u, f) pairs, worst |1/u + 1/v − 1/f|·f = %.1e".format(worst))
        // the classroom table with f = 10 cm
        val f = 10.0
        for (u in doubleArrayOf(50.0, 30.0, 20.0, 15.0, 12.0, 6.0, 5.0)) {
            val v = ThinLens.imageDistance(u, f)
            println("f = 10 cm  u = %5.1f cm  ->  v = %+7.2f cm  m = %+6.3f  %s".format(u, v, ThinLens.magnification(u, v), ThinLens.classify(u, f)))
        }
        assertEquals(15.0, ThinLens.imageDistance(30.0, 10.0), 1e-12)
        assertEquals(-0.5, ThinLens.magnification(30.0, 15.0), 1e-12)
        assertEquals(30.0, ThinLens.imageDistance(15.0, 10.0), 1e-12)
        assertEquals(-2.0, ThinLens.magnification(15.0, 30.0), 1e-12)
        assertEquals(-15.0, ThinLens.imageDistance(6.0, 10.0), 1e-12)                 // virtual, same side
        assertEquals(2.5, ThinLens.magnification(6.0, -15.0), 1e-12)                  // upright, ×2.5
    }

    @Test
    fun objectAtTwiceFocalLengthGivesAnEqualInvertedImage() {
        for (f in fs) {
            val v = ThinLens.imageDistance(2 * f, f)
            assertEquals(2 * f, v, 1e-12)
            assertEquals(-1.0, ThinLens.magnification(2 * f, v), 1e-12)
            assertEquals(Case.AT_2F, ThinLens.classify(2 * f, f))
        }
        println("u = 2f -> v = 2f, m = -1 for f = 10, 15, 20 cm")
    }

    @Test
    fun imageRunsOffToInfinityAsTheObjectApproachesF() {
        val f = 10.0
        var prev = 0.0
        for (k in 1..6) {
            val e = Math.pow(10.0, -k.toDouble())
            val vOut = ThinLens.imageDistance(f * (1 + e), f)     // just outside F: far real image
            val vIn = ThinLens.imageDistance(f * (1 - e), f)      // just inside F: far virtual image
            assertEquals(f * (1 + e) / e, vOut, 1e-6 * vOut)
            assertEquals(-f * (1 - e) / e, vIn, 1e-6 * abs(vIn))
            assertTrue(vOut > prev); prev = vOut
            println("u = f(1 ± 1e-$k): v = %.4g cm (real) / %.4g cm (virtual)".format(vOut, vIn))
        }
        assertTrue(ThinLens.imageDistance(f, f).isInfinite())
        // at u = f the image-side blur on any screen is the full aperture (parallel beam)
        assertEquals(6.0, ThinLens.blurDiameter(6.0, 25.0, ThinLens.imageDistance(f, f)), 0.0)
    }

    @Test
    fun magnificationIsMinusVOverUAndAgreesWithTheNewtonianForms() {
        for (f in fs) for (u in us(f)) {
            val v = ThinLens.imageDistance(u, f)
            val m = ThinLens.magnification(u, v)
            assertEquals(-f / (u - f), m, 1e-9 * max(1.0, abs(m)))     // Hecht eq. 5.26: M_T = −f/x_o
            assertEquals(-(v - f) / f, m, 1e-9 * max(1.0, abs(m)))     // M_T = −x_i/f
            when (ThinLens.classify(u, f, 1e-9)) {
                Case.BEYOND_2F -> assertTrue(m < 0 && abs(m) < 1 && v > f && v < 2 * f)   // camera
                Case.BETWEEN -> assertTrue(m < -1 && v > 2 * f)                            // projector
                Case.INSIDE_F -> assertTrue(m > 1 && v < 0)                                // magnifier
                else -> {}
            }
        }
    }

    @Test
    fun blurDiameterEqualsTheTracedLightConeOnTheScreen() {
        val d = 6.0; val f = 10.0
        val out = DoubleArray(2)
        var worst = 0.0
        for (u in doubleArrayOf(6.0, 8.0, 13.0, 15.0, 20.0, 30.0, 45.0)) for (s in doubleArrayOf(4.0, 12.0, 15.0, 22.0, 30.0, 48.0)) {
            val v = ThinLens.imageDistance(u, f)
            val c = ThinLens.blurDiameter(d, s, v)
            // on-axis object point: the two marginal rays (lens edge, top and bottom)
            ThinLens.traceToPlane(u, 0.0, 0.0, f, d / 2, 0.0, s, out); val yTop = out[0]
            ThinLens.traceToPlane(u, 0.0, 0.0, f, -d / 2, 0.0, s, out); val yBot = out[0]
            worst = max(worst, abs(abs(yTop - yBot) - c))
            // off-axis point (flame tip 2.5 cm up): the footprint of the whole rim is still a circle of diameter c
            val n = 36; val cx = DoubleArray(n); val cy = DoubleArray(n)
            for (k in 0 until n) {
                val a = 2 * PI * k / n
                ThinLens.traceToPlane(u, 2.5, 0.0, f, d / 2 * cos(a), d / 2 * sin(a), s, out); cx[k] = out[0]; cy[k] = out[1]
            }
            val mx = cx.average(); val my = cy.average()
            for (k in 0 until n) worst = max(worst, abs(2 * sqrt((cx[k] - mx) * (cx[k] - mx) + (cy[k] - my) * (cy[k] - my)) - c))
            // and its centre is where the chief ray lands: y = −(s/u)·h
            worst = max(worst, abs(mx - ThinLens.screenScale(u, s) * 2.5))
        }
        assertTrue("blur formula vs traced cone: $worst cm", worst < 1e-9)
        println("blur c = D|s−v|/|v| matches the traced cone to %.1e cm (real and virtual images)".format(worst))
        assertEquals(0.0, ThinLens.blurDiameter(d, 15.0, 15.0), 0.0)
        assertEquals(2.0, ThinLens.blurDiameter(d, 20.0, 15.0), 1e-12)        // f=10,u=30: screen 5 cm too far -> 2 cm
        println("f = 10, u = 30 (v = 15), screen at 20 cm: c = %.2f cm; depth of focus for c < 1 mm: ±%.2f cm".format(
            ThinLens.blurDiameter(d, 20.0, 15.0), ThinLens.focusTolerance(d, 15.0, 0.1)))
    }

    @Test
    fun principalRaysMeetAtTheImagePoint() {
        val h = 2.5
        var worst = 0.0; var n = 0
        for (f in fs) for (u in us(f)) {
            val v = ThinLens.imageDistance(u, f); val m = ThinLens.magnification(u, v)
            val rays = ThinLens.principalRays(u, h, f)
            // each ray, traced back to the object plane, starts at the tip (−u, h)
            assertEquals(h, rays[0]!!.y0, 0.0)                                       // parallel ray enters at height h
            assertEquals(h, rays[1]!!.y(-u), 1e-12)                                  // chief ray passes the tip
            val r3 = rays[2]!!
            assertEquals(h, r3.y0 + r3.y0 / f * (-u), 1e-9 * max(1.0, abs(r3.y0)))     // incident line through F hits the tip
            for ((a, b) in listOf(0 to 1, 0 to 2, 1 to 2)) {
                val p = ThinLens.intersect(rays[a]!!, rays[b]!!)!!
                worst = max(worst, max(abs(p[0] - v) / max(1.0, abs(v)), abs(p[1] - m * h) / max(1.0, abs(m * h))))
                n++
            }
        }
        assertTrue("principal-ray intersections off by $worst", worst < 1e-9)
        println("principal rays: $n pairwise intersections (real + virtual), worst relative error %.1e".format(worst))
        // u = f: no ray 3, rays 1 and 2 leave parallel -> no image
        val r = ThinLens.principalRays(10.0, h, 10.0)
        assertNull(r[2]); assertNull(ThinLens.intersect(r[0]!!, r[1]!!))
    }

    @Test
    fun everyApertureRayPassesThroughTheImagePoint() {
        val out = DoubleArray(2)
        var worst = 0.0
        for (f in fs) for (u in us(f)) {
            val v = ThinLens.imageDistance(u, f); val m = ThinLens.magnification(u, v)
            val h = 2.5; val z0 = 0.7                                                   // an off-axis, out-of-plane object point
            for (i in -3..3) for (j in -3..3) {
                val yl = i * 1.0; val zl = j * 1.0
                if (yl * yl + zl * zl > 9.0) continue                                   // 6 cm aperture
                ThinLens.traceToPlane(u, h, z0, f, yl, zl, v, out)                      // x = v < 0: backward extension
                worst = max(worst, max(abs(out[0] - m * h), abs(out[1] - m * z0)) / max(1.0, abs(m)))
            }
        }
        assertTrue("aperture rays miss the image point by $worst", worst < 1e-9)
        println("aperture rays: all pass through (v, m·h, m·z0), worst %.1e cm (scaled by |m|)".format(worst))
    }

    @Test
    fun presetsLandInTheirCasesAndFitTheBench() {
        val uMax = 48.0; val vMax = 46.0
        for (f in fs) {
            for (c in Case.values()) {
                val u = ThinLens.presetU(c, f, uMax, vMax)
                assertEquals("f=$f $c u=$u", c, ThinLens.classify(u, f))
                assertTrue(u in 3.0..uMax)
                val v = ThinLens.imageDistance(u, f)
                if (v > 0 && !v.isInfinite()) assertTrue("f=$f $c v=$v", v <= vMax + 1e-9)
                println("f = %2.0f  %-9s u = %5.2f  v = %s".format(f, c, u, if (v.isInfinite()) "∞" else "%+.2f".format(v)))
            }
        }
        // lensmaker: a symmetric BK7 (n = 1.5168) biconvex with f = 10 cm has R = 10.34 cm
        assertEquals(10.336, ThinLens.biconvexRadius(10.0, 1.5168), 1e-9)
        assertNotNull(ThinLens.sag(10.336, 3.0))
        assertEquals(0.44495, ThinLens.sag(10.336, 3.0), 1e-4)
    }
}

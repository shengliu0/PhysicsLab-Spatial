// 凸透镜成像规律 — the ideal thin converging lens behind demos/lens (pure Kotlin, JVM-tested).
//
// SIGN CONVENTION (the school "real-is-positive" one, 人教版 物理 八年级上 第五章 §3 / 选修 3-4):
//   u > 0   object distance (a real object left of the lens; light travels toward +x)
//   v > 0   REAL image on the far side of the lens (it can be caught on a screen)
//   v < 0   VIRTUAL image on the object's side (seen only by looking through the lens)
//   f > 0   converging lens
//   Gaussian thin-lens equation   1/u + 1/v = 1/f          lateral magnification   m = −v/u
//   (m < 0: inverted; |m| > 1: magnified). This is Hecht, Optics 5th ed. §5.2.3 eqs (5.17), (5.25)
//   with s_o = u, s_i = v. The Newtonian form x_o·x_i = f² (x_o = u − f, x_i = v − f, Hecht eq. 5.23)
//   is used by the tests as an independent check.
//
// GEOMETRY (bench coordinates, cm): lens in the plane x = 0, optical axis y = z = 0, object at x = −u.
// An ideal paraxial thin lens bends a ray that meets it at height (y, z) by reducing its slopes by
// y/f and z/f (ray-transfer matrix [[1, 0], [−1/f, 1]], Hecht §6.2.1). Every ray from one object
// point then passes through ONE image point (real) or diverges from one point (virtual) — which is
// exactly what the demo draws in 3-D and what the tests verify ray by ray.
//
// HISTORY: Kepler's Dioptrice (1611) first explained how a convex lens forms an inverted real image
// beyond it and an upright virtual one when the object is inside the focus.

package com.bytedance.physicslab.solar.core.lens

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.sqrt

object ThinLens {

    /** Image distance v from 1/u + 1/v = 1/f. u = f exactly -> +Infinity (rays leave parallel). */
    fun imageDistance(u: Double, f: Double): Double {
        val inv = 1.0 / f - 1.0 / u
        return if (inv == 0.0) Double.POSITIVE_INFINITY else 1.0 / inv
    }

    /** Lateral magnification m = −v/u (negative = inverted). */
    fun magnification(u: Double, v: Double): Double = -v / u

    /**
     * Diameter of the blur circle on a screen at distance [s] behind a lens of clear aperture [d] when
     * the image of a point is at [v]: the light cone (aperture -> image point) cut by the screen plane.
     * Similar triangles: c = d·|s − v| / |v|. Holds for v < 0 too (the cone then diverges from the
     * virtual point), and tends to c = d (a parallel beam) as v -> ∞.
     */
    fun blurDiameter(d: Double, s: Double, v: Double): Double = if (v.isInfinite()) d else d * abs(s - v) / abs(v)

    /**
     * Scale of the (possibly blurred) picture on a screen at [s]: each object point lands where its CHIEF
     * ray (through the optical centre, undeviated) meets the screen, y_screen = −(s/u)·y. Equals m when s = v.
     */
    fun screenScale(u: Double, s: Double): Double = -s / u

    /** Paraxial refraction at the lens plane: outgoing slope for a ray of [slope] meeting the lens at [height]. */
    fun refract(slope: Double, height: Double, f: Double): Double = slope - height / f

    /**
     * Trace one ray from the object point (−u, h, z0) through the lens point (0, yl, zl) to the plane x
     * (x > 0 behind the lens; x < 0 gives the backward extension of the refracted ray).
     * Writes (y, z) into [out].
     */
    fun traceToPlane(u: Double, h: Double, z0: Double, f: Double, yl: Double, zl: Double, x: Double, out: DoubleArray) {
        val sy = refract((yl - h) / u, yl, f)
        val sz = refract((zl - z0) / u, zl, f)
        out[0] = yl + sy * x
        out[1] = zl + sz * x
    }

    /** A straight line y = y0 + slope·x in the meridional (x, y) plane; y0 is its height at the lens. */
    data class Line(val y0: Double, val slope: Double) {
        fun y(x: Double) = y0 + slope * x
    }

    /**
     * The three principal rays from the object tip (−u, h), AFTER the lens (lines valid for any x;
     * x < 0 = their backward extension):
     *   [0] parallel to the axis -> meets the lens at h -> through F′ (f, 0)
     *   [1] through the optical centre -> undeviated
     *   [2] through (or, for u < f, from the direction of) F (−f, 0) -> leaves parallel to the axis;
     *       null when u = f (that ray runs parallel to the lens and never reaches it)
     */
    fun principalRays(u: Double, h: Double, f: Double): Array<Line?> {
        val r1 = Line(h, -h / f)
        val r2 = Line(0.0, -h / u)
        val r3 = if (u == f) null else Line(-h * f / (u - f), 0.0)
        return arrayOf(r1, r2, r3)
    }

    /** Intersection (x, y) of two lines, null if parallel. */
    fun intersect(a: Line, b: Line): DoubleArray? {
        val ds = a.slope - b.slope
        if (ds == 0.0) return null
        val x = (b.y0 - a.y0) / ds
        return doubleArrayOf(x, a.y(x))
    }

    /** Lensmaker (thin, symmetric biconvex, R1 = −R2 = R): 1/f = (n − 1)(2/R)  ->  R = 2(n − 1)f. */
    fun biconvexRadius(f: Double, n: Double): Double = 2.0 * (n - 1.0) * f

    /** Sagitta of a spherical cap of radius [r] over a half-chord [a]. */
    fun sag(r: Double, a: Double): Double = r - sqrt(r * r - a * a)

    // ---------------------------------------------------------------- the textbook table

    /** The five textbook cases (人教版 八年级上 §5.3 "凸透镜成像的规律"). */
    enum class Case { BEYOND_2F, AT_2F, BETWEEN, AT_F, INSIDE_F }

    /** Which case u is in; |u − f| or |u − 2f| below [tol] counts as "equal". */
    fun classify(u: Double, f: Double, tol: Double = 0.05): Case = when {
        abs(u - 2 * f) <= tol -> Case.AT_2F
        abs(u - f) <= tol -> Case.AT_F
        u > 2 * f -> Case.BEYOND_2F
        u > f -> Case.BETWEEN
        else -> Case.INSIDE_F
    }

    /**
     * A representative object distance for each case on a bench that allows u <= [uMax] and image
     * distances v <= [vMax] (so the real image can still be caught on the screen):
     * u > 2f -> 3f (or uMax); u = 2f; f < u < 2f -> 1.5f (moved toward 2f in 0.5 cm steps until v fits);
     * u = f; u < f -> 0.6f.
     */
    fun presetU(case: Case, f: Double, uMax: Double, vMax: Double): Double = when (case) {
        Case.BEYOND_2F -> min(3 * f, uMax)
        Case.AT_2F -> 2 * f
        Case.BETWEEN -> {
            var u = 1.5 * f
            if (imageDistance(u, f) > vMax) u = ceil(vMax * f / (vMax - f) * 2.0) / 2.0   // v(u) = vMax, rounded up
            u
        }
        Case.AT_F -> f
        Case.INSIDE_F -> 0.6 * f
    }

    /** Screen travel |s − v| that keeps the blur circle below [c] (depth of focus on the image side). */
    fun focusTolerance(d: Double, v: Double, c: Double): Double = c * abs(v) / d
}

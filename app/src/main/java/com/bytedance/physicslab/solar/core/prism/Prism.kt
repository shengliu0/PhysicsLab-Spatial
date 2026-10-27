// Prism — exact refraction through a prism in its principal section, pure Kotlin.
//
// UNITY AUDIT. PrismDemo bent each colour by the THIN-PRISM formula δ = (n − 1)A at apex angles of
// 30, 45 and 60 degrees. That formula is the small-angle limit of the minimum deviation
//     D_min = 2 asin(n sin(A/2)) − A
// and at A = 60° with n = 1.517 it gives 31.0° instead of 38.7° (20% short); the red-to-violet spread
// with its own six indices came out 0.90° instead of 1.32° (32% short). It also measured the
// deviation from a fixed direction (1, −0.15) instead of from the incoming beam (0.55, −0.18), so
// every drawn ray was 9.6° LESS deviated than even the thin formula said, and its glass outline was
// drawn with apex angles of 58°/65°/72° while the label said 30°/45°/60°. The coloured rays started
// at the entry point and went straight through the glass, with no refraction at either face.
//
// Here: Snell's law in vector form at each face, total internal reflection, any incidence angle,
// any wavelength (n from Glass), and the analytic minimum deviation to check the tracer against.

package com.bytedance.physicslab.solar.core.prism

import kotlin.math.asin
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object PrismOptics {
    /** Minimum deviation (rad) for apex [a] (rad); NaN if light cannot pass at all. */
    fun minDeviation(n: Double, a: Double): Double {
        val s = n * sin(a / 2); return if (s > 1) Double.NaN else 2 * asin(s) - a
    }

    /** Angle of incidence (rad) that gives minimum deviation (symmetric passage). */
    fun minDeviationIncidence(n: Double, a: Double): Double = asin((n * sin(a / 2)).coerceAtMost(1.0))

    /**
     * Deviation (rad) for incidence [i1] (rad) on the entry face: r1 = asin(sin i1 / n), r2 = A − r1,
     * sin i2 = n sin r2, D = i1 + i2 − A. NaN when the exit face totally reflects (n sin r2 > 1).
     */
    fun deviation(n: Double, a: Double, i1: Double): Double {
        val r1 = asin(sin(i1) / n)
        val s = n * sin(a - r1)
        return if (abs(s) > 1) Double.NaN else i1 + asin(s) - a
    }

    /** The approximation the Unity build used: δ = (n − 1) A. */
    fun thinPrismDeviation(n: Double, a: Double) = (n - 1) * a

    fun criticalAngle(n: Double) = asin(1 / n)

    /** dD/dn at minimum deviation = 2 sin(A/2) / cos((D_min + A)/2): angular dispersion per unit Δn. */
    fun dDdnAtMin(n: Double, a: Double) = 2 * sin(a / 2) / cos((minDeviation(n, a) + a) / 2)
}

/** Result of tracing one ray through a convex glass polygon. */
class Trace2(
    /** entry point, internal reflection points, exit point (x, y pairs). Empty if the ray missed. */
    val pts: DoubleArray,
    /** outgoing unit direction, or null if the ray never left (trapped) or missed. */
    val out: DoubleArray?,
    /** number of total internal reflections on the way. */
    val tir: Int,
) {
    val missed get() = pts.isEmpty()
    val count get() = pts.size / 2
}

object Ray2 {
    /**
     * Vector Snell: [d] unit incident direction, [nrm] unit normal pointing back toward the incident
     * side, [eta] = n_incident / n_transmitted. Null on total internal reflection.
     */
    fun refract(dx: Double, dy: Double, nx: Double, ny: Double, eta: Double): DoubleArray? {
        val cosi = -(nx * dx + ny * dy)
        val k = 1 - eta * eta * (1 - cosi * cosi)
        if (k < 0) return null
        val f = eta * cosi - sqrt(k)
        return doubleArrayOf(eta * dx + f * nx, eta * dy + f * ny)
    }

    fun reflect(dx: Double, dy: Double, nx: Double, ny: Double): DoubleArray {
        val d = dx * nx + dy * ny
        return doubleArrayOf(dx - 2 * d * nx, dy - 2 * d * ny)
    }

    /** Nearest edge hit of the ray (o + t d, t > eps) with polygon [poly] (CCW x,y pairs): (t, edge) or null. */
    private fun firstHit(poly: DoubleArray, ox: Double, oy: Double, dx: Double, dy: Double): Pair<Double, Int>? {
        val m = poly.size / 2
        var best = Double.MAX_VALUE; var edge = -1
        for (i in 0 until m) {
            val ax = poly[i * 2]; val ay = poly[i * 2 + 1]
            val bx = poly[((i + 1) % m) * 2]; val by = poly[((i + 1) % m) * 2 + 1]
            val ex = bx - ax; val ey = by - ay
            val den = dx * ey - dy * ex
            if (abs(den) < 1e-15) continue
            val t = ((ax - ox) * ey - (ay - oy) * ex) / den
            val s = ((ax - ox) * dy - (ay - oy) * dx) / den
            if (t > 1e-9 && s >= 0 && s <= 1 && t < best) { best = t; edge = i }
        }
        return if (edge < 0) null else best to edge
    }

    /** Outward unit normal of edge [i] of a CCW polygon. */
    fun outwardNormal(poly: DoubleArray, i: Int): DoubleArray {
        val m = poly.size / 2
        val ex = poly[((i + 1) % m) * 2] - poly[i * 2]; val ey = poly[((i + 1) % m) * 2 + 1] - poly[i * 2 + 1]
        val l = sqrt(ex * ex + ey * ey)
        return doubleArrayOf(ey / l, -ex / l)
    }

    /** Trace a ray from outside through a convex polygon of index [n] (surrounded by air). */
    fun traceConvex(poly: DoubleArray, n: Double, ox: Double, oy: Double, dx0: Double, dy0: Double, maxBounces: Int = 6): Trace2 {
        val h0 = firstHit(poly, ox, oy, dx0, dy0) ?: return Trace2(DoubleArray(0), null, 0)
        val pts = ArrayList<Double>()
        var px = ox + dx0 * h0.first; var py = oy + dy0 * h0.first
        pts += px; pts += py
        val n0 = outwardNormal(poly, h0.second)
        var d = refract(dx0, dy0, n0[0], n0[1], 1 / n) ?: return Trace2(pts.toDoubleArray(), null, 0)
        var tir = 0
        repeat(maxBounces + 1) {
            val h = firstHit(poly, px, py, d[0], d[1]) ?: return Trace2(pts.toDoubleArray(), null, tir)
            px += d[0] * h.first; py += d[1] * h.first
            pts += px; pts += py
            val no = outwardNormal(poly, h.second)
            val t = refract(d[0], d[1], -no[0], -no[1], n)
            if (t != null) return Trace2(pts.toDoubleArray(), t, tir)
            d = reflect(d[0], d[1], no[0], no[1]); tir++
        }
        return Trace2(pts.toDoubleArray(), null, tir)
    }

    /**
     * An isosceles prism (apex angle [a], equal sides [side]) whose ENTRY face sees a beam travelling
     * along unit ([dx], [dy]) at incidence [i1]. [clockwise] = true bends the beam clockwise (for a
     * beam going right: downward, apex up). Centroid at ([cx], [cy]). Returns CCW vertices
     * (apex, base corner on the entry side, base corner on the exit side — order fixed so CCW).
     */
    fun prism(cx: Double, cy: Double, a: Double, side: Double, dx: Double, dy: Double, i1: Double, clockwise: Boolean): DoubleArray {
        val sg = if (clockwise) 1.0 else -1.0
        // local frame: beam along +x, "up" = side the apex is on
        val n1x = -cos(i1); val n1y = sg * sin(i1)
        val n2x = cos(a - i1); val n2y = sg * sin(a - i1)
        val t1x = -sin(i1); val t1y = -sg * cos(i1)
        val t2x = sin(a - i1); val t2y = -sg * cos(a - i1)
        var ax = n1x + n2x; var ay = n1y + n2y
        val al = sqrt(ax * ax + ay * ay); ax /= al; ay /= al
        val k = 2.0 / 3.0 * side * cos(a / 2)
        val apx = ax * k; val apy = ay * k
        val lx = doubleArrayOf(apx, apx + side * t1x, apx + side * t2x)
        val ly = doubleArrayOf(apy, apy + side * t1y, apy + side * t2y)
        val order = if (clockwise) intArrayOf(0, 1, 2) else intArrayOf(0, 2, 1)
        val out = DoubleArray(6)
        for ((j, v) in order.withIndex()) {
            out[j * 2] = cx + lx[v] * dx - ly[v] * dy
            out[j * 2 + 1] = cy + lx[v] * dy + ly[v] * dx
        }
        return out
    }

    /** Signed angle (rad) from direction a to direction b (CCW positive). */
    fun angleBetween(ax: Double, ay: Double, bx: Double, by: Double) = kotlin.math.atan2(ax * by - ay * bx, ax * bx + ay * by)
}

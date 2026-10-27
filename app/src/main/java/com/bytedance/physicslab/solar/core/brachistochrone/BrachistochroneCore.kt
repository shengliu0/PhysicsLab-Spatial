// BrachistochroneCore — the exact brachistochrone / tautochrone maths, pure Kotlin, double precision.
// Port of the Unity PhysicsLab/CycloidCore.cs (+ the lane shapes of BrachistochroneDemo.cs), audited.
//
// MODEL: a BEAD THREADED ON A FRICTIONLESS WIRE (Bernoulli's own statement of the problem, 1696).
// The bead slides, it does not roll, and the wire holds it on both sides, so no shape can throw it
// off. The demo draws exactly that: beads threaded on rods, no spin.
//
// AUDIT OF THE UNITY BUILD (numbers from BrachistochroneCoreTest, default drop 0.50 m / run 1.50 m):
//   1. It drew ROLLING balls (rotation = -s/R, "visibly ROLL") on frictionless POINT-BEAD physics.
//      A real solid ball rolling without slipping has a = (5/7) g sin(alpha): every time would be
//      sqrt(7/5) = 1.183x longer (cycloid 0.720 s -> 0.852 s). Worse, rolling is IMPOSSIBLE where the
//      race starts: the cycloid leaves its cusp vertically, where N = 0 from rest, so a ball must skid
//      until tan(theta/2) >= 1/(7 mu) — the first 12 cm of the curve at mu = 0.3. The drawing and the
//      stopwatch described two different experiments.
//   2. The "HIGH ROAD" lane was a polyline with a sharp CONVEX kink (8.1 deg -> 37.9 deg) inside an
//      OPEN chute. A ball reaching it at 1.72 m/s leaves the chute and flies 0.374 m of the 0.45 m
//      final ramp before it lands; the integrator silently glued it to the track.
//   3. The "ARC" lane was a quarter-ELLIPSE with a 3:1 aspect, not a circular arc. The historically
//      meaningful contender — Galileo's circle (Two New Sciences, 1638), which he showed beats the
//      chord and wrongly suspected was fastest of all — was missing. It is lane 2 here, and its time
//      is exact: a bead on a circle IS a pendulum, t = K(sin(phi0/2)) sqrt(R/g).
//   The Unity numerical claims themselves check out: ellipse penalty +2.8 % (drop 0.5) / +0.9 %
//   (0.65) / +35.7 % (0.15); semi-implicit Euler vs exact 3e-4 relative; tautochrone arrival spread
//   0.42 ms (claimed "~1 ms").
//
// CURVE FRAME: start (release point) at the ORIGIN, x to the right, y UP, so a descending curve has
// y <= 0 and "depth" = -y.

package com.bytedance.physicslab.solar.core.brachistochrone

import com.bytedance.physicslab.solar.core.pendulum.Pendulum
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** The cycloid: x = r(theta - sin theta), y = -r(1 - cos theta), cusp at the origin. */
object Cycloid {
    fun x(r: Double, th: Double) = r * (th - sin(th))
    fun y(r: Double, th: Double) = -r * (1 - cos(th))

    /** run/drop of a cycloid ending at theta, strictly increasing on (0, 2 pi). */
    fun ratio(th: Double): Double { val h = sin(th / 2); val d = 2 * h * h; return if (d <= 0) Double.POSITIVE_INFINITY else (th - sin(th)) / d }

    /** theta_end of the unique cycloid from (0,0) through (run, -drop), by bisection. */
    fun solveTheta(run: Double, drop: Double): Double {
        require(run > 0 && drop > 0)
        val target = run / drop
        var lo = 1e-9; var hi = 2 * PI - 1e-9
        repeat(200) { val m = (lo + hi) / 2; if (ratio(m) < target) lo = m else hi = m }
        return (lo + hi) / 2
    }

    data class Fit(val r: Double, val thetaEnd: Double)
    fun fit(run: Double, drop: Double): Fit { val th = solveTheta(run, drop); return Fit(drop / (1 - cos(th)), th) }

    /** Arc length from the cusp: s = 4r(1 - cos(theta/2)); a full arch is 8r. */
    fun arcLength(r: Double, th: Double) = 4 * r * (1 - cos(th / 2))
    fun thetaAtArc(r: Double, s: Double) = 2 * acos((1 - s / (4 * r)).coerceIn(-1.0, 1.0))

    /** Descent time from rest at the cusp: t = theta sqrt(r/g) — the parameter is LINEAR in time. */
    fun descentTime(r: Double, th: Double, g: Double) = th * sqrt(r / g)
    /** The rolling-circle rate omega = sqrt(g/r): a wheel turning steadily traces the bead exactly. */
    fun omega(r: Double, g: Double) = sqrt(g / r)
    fun speedAt(r: Double, th: Double, g: Double) = 2 * sqrt(g * r) * sin(th / 2)

    /** TAUTOCHRONE: from rest anywhere on a cycloid bowl to its bottom takes pi sqrt(r/g). */
    fun tautochroneTime(r: Double, g: Double) = PI * sqrt(r / g)

    /**
     * Exact bowl motion. Arc length measured from the BOTTOM, u, obeys u'' = -(g/4r) u exactly
     * (height above the bottom is u^2/8r), so u(t) = u0 cos(t sqrt(g/4r)). Returns the bowl
     * parameter theta in [0, 2 pi] (bottom = pi) for a bead released from rest at theta0.
     */
    fun bowlTheta(r: Double, theta0: Double, g: Double, t: Double): Double {
        val u0 = arcLength(r, theta0) - 4 * r
        val u = u0 * cos(0.5 * sqrt(g / r) * t)
        return thetaAtArc(r, 4 * r + u)
    }

    /** Snell: sin(alpha)/sqrt(depth) is the same 1/sqrt(2r) everywhere on the curve. */
    fun snellRatio(r: Double, th: Double): Double {
        val depth = 2 * r * sin(th / 2) * sin(th / 2)
        return if (depth <= 0) 1 / sqrt(2 * r) else sin(th / 2) / sqrt(depth)
    }
}

/** Closed-form (or exact-quadrature) descent times from rest, start at (0,0), finish at (run, -drop). */
object Descent {
    fun straight(run: Double, drop: Double, g: Double) = sqrt(run * run + drop * drop) * sqrt(2 / (g * drop))

    fun cycloid(run: Double, drop: Double, g: Double): Double { val f = Cycloid.fit(run, drop); return Cycloid.descentTime(f.r, f.thetaEnd, g) }

    /** Galileo's circle: the circle through the start whose LOWEST point is the finish. */
    fun arcRadius(run: Double, drop: Double) = (run * run + drop * drop) / (2 * drop)
    /** Its half-angle at the start, measured from the vertical through the centre. */
    fun arcPhi0(run: Double, drop: Double) = asin((run / arcRadius(run, drop)).coerceAtMost(1.0))
    /** A bead on a circle is a pendulum of length R: quarter period = K(sin(phi0/2)) sqrt(R/g). */
    fun arc(run: Double, drop: Double, g: Double): Double {
        val R = arcRadius(run, drop)
        return Pendulum.ellipK(sin(arcPhi0(run, drop) / 2)) * sqrt(R / g)
    }

    /**
     * The "cliff": fall vertically, turn through a quarter-circle fillet of radius [fillet], run
     * flat at the finish height. Free fall and the flat run are closed form; the fillet is a smooth
     * integrand (speed never zero there) done by 2000-panel Simpson.
     */
    fun cliff(run: Double, drop: Double, fillet: Double, g: Double): Double {
        val h1 = drop - fillet
        val t1 = sqrt(2 * h1 / g)
        val n = 2000; var acc = 0.0
        for (i in 0..n) {
            val p = PI / 2 * i / n
            val f = fillet / sqrt(2 * g * (h1 + fillet * sin(p)))
            acc += f * (if (i == 0 || i == n) 1.0 else if (i % 2 == 1) 4.0 else 2.0)
        }
        val t2 = acc * (PI / 2 / n) / 3
        return t1 + t2 + (run - fillet) / sqrt(2 * g * drop)
    }

    /** Energy conservation: every frictionless path to the same finish arrives at sqrt(2 g h). */
    fun finishSpeed(drop: Double, g: Double) = sqrt(2 * g * drop)

    // ---------------------------------------------------------------- the audit, as numbers

    /** Rolling solid sphere (I = 2/5 m R^2, no slip): every time is sqrt(1 + 2/5) longer. */
    val ROLLING_TIME_FACTOR = sqrt(7.0 / 5.0)

    /**
     * Where a solid ball released at a cycloid's cusp can first roll without slipping: needed
     * friction (2/7) m g cos(theta/2) <= mu N with N = 2 m g sin(theta/2) (gravity + centripetal).
     * Returns the arc length from the cusp it must skid over.
     */
    fun cycloidSkidLength(r: Double, mu: Double): Double {
        val th = 2 * kotlin.math.atan(1 / (7 * mu))
        return Cycloid.arcLength(r, th)
    }

    /**
     * A ball reaching a convex kink at speed v, slope a1 (rad below horizontal) -> a2 (steeper), in
     * an OPEN chute, launches as a projectile. Horizontal distance flown before it meets the steeper
     * ramp again: x = 2 v^2 cos^2(a1) (tan a2 - tan a1) / g.
     */
    fun kinkFlight(v: Double, a1: Double, a2: Double, g: Double): Double {
        val c = cos(a1)
        return 2 * v * v * c * c * (kotlin.math.tan(a2) - kotlin.math.tan(a1)) / g
    }
}

/**
 * A frictionless bead track: any polyline in the vertical plane, parameterised by arc length.
 * Each segment has a constant slope, so constant tangential acceleration a = -g dy/ds, and the
 * motion inside it is EXACT: v1 = sqrt(v0^2 + 2 a L), t = (v1 - v0)/a. The cumulative-time table
 * lets [stateAt] return the exact (s, v) at any time — no integrator, no drift, any frame rate,
 * and v^2 = 2 g depth holds at every instant (energy is conserved by construction).
 */
class Track(xs: DoubleArray, ys: DoubleArray, val g: Double) {
    val n = xs.size
    val x = xs.copyOf(); val y = ys.copyOf()
    val s = DoubleArray(n)
    /** time of arrival at vertex i (NaN beyond a stall) */
    val tAt = DoubleArray(n)
    private val vAt = DoubleArray(n)
    private val acc = DoubleArray(n)

    init {
        require(n >= 2 && ys.size == n)
        for (i in 1 until n) s[i] = s[i - 1] + kotlin.math.hypot(x[i] - x[i - 1], y[i] - y[i - 1])
        var v = 0.0; var t = 0.0
        for (i in 0 until n - 1) {
            val L = s[i + 1] - s[i]
            val a = if (L > 1e-15) -g * (y[i + 1] - y[i]) / L else 0.0
            acc[i] = a
            if (t.isNaN() || L <= 1e-15) { tAt[i + 1] = t; vAt[i + 1] = v; continue }
            val v2 = v * v + 2 * a * L
            if (v2 <= 0) { t = Double.NaN; tAt[i + 1] = t; vAt[i + 1] = 0.0; continue }
            val vn = sqrt(v2)
            t += if (abs(a) < 1e-12) (if (v > 1e-12) L / v else Double.NaN) else (vn - v) / a
            v = vn; tAt[i + 1] = t; vAt[i + 1] = v
        }
    }

    val length get() = s[n - 1]
    /** exact descent time of the polyline, from rest */
    val time get() = tAt[n - 1]
    val minY get() = y.min()

    /** Point and unit tangent at arc length [sa]. */
    fun eval(sa: Double, out: DoubleArray) {
        val sc = sa.coerceIn(0.0, length)
        val i = seg(sc)
        val ds = s[i + 1] - s[i]; val f = if (ds > 1e-15) (sc - s[i]) / ds else 0.0
        out[0] = x[i] + (x[i + 1] - x[i]) * f; out[1] = y[i] + (y[i + 1] - y[i]) * f
        out[2] = if (ds > 1e-15) (x[i + 1] - x[i]) / ds else 1.0; out[3] = if (ds > 1e-15) (y[i + 1] - y[i]) / ds else 0.0
    }

    private fun seg(sa: Double): Int {
        var lo = 0; var hi = n - 2
        while (lo < hi) { val m = (lo + hi + 1) ushr 1; if (s[m] <= sa) lo = m else hi = m - 1 }
        return lo
    }

    /** Exact (s, v) at time [t] after release from rest; clamps at the finish. */
    fun stateAt(t: Double, out: DoubleArray) {
        if (t <= 0) { out[0] = 0.0; out[1] = 0.0; return }
        if (!(t < time)) { out[0] = length; out[1] = vAt[n - 1]; return }
        var lo = 0; var hi = n - 2
        while (lo < hi) { val m = (lo + hi + 1) ushr 1; if (tAt[m] <= t) lo = m else hi = m - 1 }
        val tau = t - tAt[lo]; val v0 = vAt[lo]; val a = acc[lo]
        out[0] = (s[lo] + v0 * tau + 0.5 * a * tau * tau).coerceAtMost(s[lo + 1])
        out[1] = v0 + a * tau
    }

    /** Arc length reached at time t (convenience). */
    fun sAt(t: Double): Double { val o = DoubleArray(2); stateAt(t, o); return o[0] }
}

/** The race lanes: sample generators in the curve frame (start at the origin). */
object Lanes {
    const val N = 240

    fun straight(run: Double, drop: Double, n: Int = N) =
        DoubleArray(n) { run * it / (n - 1) } to DoubleArray(n) { -drop * it / (n - 1) }

    /** The cycloid, sampled uniformly in ARC LENGTH (even mesh, well-conditioned table). */
    fun cycloid(run: Double, drop: Double, n: Int = N): Pair<DoubleArray, DoubleArray> {
        val f = Cycloid.fit(run, drop); val sEnd = Cycloid.arcLength(f.r, f.thetaEnd)
        val th = DoubleArray(n) { Cycloid.thetaAtArc(f.r, sEnd * it / (n - 1)) }
        return DoubleArray(n) { Cycloid.x(f.r, th[it]) } to DoubleArray(n) { Cycloid.y(f.r, th[it]) }
    }

    /** Galileo's arc to the lowest point of the circle (centre right above the finish). */
    fun arc(run: Double, drop: Double, n: Int = N): Pair<DoubleArray, DoubleArray> {
        val R = Descent.arcRadius(run, drop); val p0 = Descent.arcPhi0(run, drop)
        val cx = run; val cy = -drop + R
        val ph = DoubleArray(n) { p0 * (1 - it / (n - 1.0)) }                 // phi0 -> 0
        return DoubleArray(n) { cx - R * sin(ph[it]) } to DoubleArray(n) { cy - R * cos(ph[it]) }
    }

    /** Vertical drop, quarter-circle fillet, flat run. Fixed point budget per piece (same topology for any drop). */
    fun cliff(run: Double, drop: Double, fillet: Double, n: Int = N): Pair<DoubleArray, DoubleArray> {
        val nv = n / 4; val nf = n / 5; val nr = n - nv - nf
        val xs = DoubleArray(n); val ys = DoubleArray(n); var k = 0
        for (i in 0 until nv) { xs[k] = 0.0; ys[k] = -(drop - fillet) * i / nv; k++ }   // fillet starts at i = nv
        for (i in 0 until nf) {
            val p = PI / 2 * i / (nf - 1.0)
            xs[k] = fillet * (1 - cos(p)); ys[k] = -(drop - fillet) - fillet * sin(p); k++
        }
        for (i in 1..nr) { xs[k] = fillet + (run - fillet) * i / nr; ys[k] = -drop; k++ }
        return xs to ys
    }

    fun filletFor(drop: Double) = minOf(0.05, drop * 0.25)
}

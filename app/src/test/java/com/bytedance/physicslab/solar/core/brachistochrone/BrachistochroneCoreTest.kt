package com.bytedance.physicslab.solar.core.brachistochrone

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

class BrachistochroneCoreTest {
    private val g = 9.81

    private fun track(p: Pair<DoubleArray, DoubleArray>) = Track(p.first, p.second, g)

    @Test
    fun cycloidFitHitsTheFinishAndMatchesUnityDefault() {
        for ((run, drop) in listOf(1.5 to 0.5, 1.4 to 0.3, 1.0 to 1.0, 2.0 to 0.1)) {
            val f = Cycloid.fit(run, drop)
            assertEquals(run, Cycloid.x(f.r, f.thetaEnd), 1e-10)
            assertEquals(-drop, Cycloid.y(f.r, f.thetaEnd), 1e-10)
        }
        // Unity default (run 1.5, drop 0.5): r = 0.3098, theta_end = 4.0516 > pi, dips 0.1197 below the finish
        val f = Cycloid.fit(1.5, 0.5)
        println("Unity default cycloid: r=${f.r} th=${f.thetaEnd} dip below finish=${2 * f.r - 0.5} t=${Descent.cycloid(1.5, 0.5, g)}")
        assertEquals(0.3098, f.r, 1e-4); assertEquals(4.0516, f.thetaEnd, 1e-4)
        assertEquals(0.7201, Descent.cycloid(1.5, 0.5, g), 1e-4)
    }

    @Test
    fun closedFormsAgreeWithTheExactPolylineTimes() {
        // the polyline time is exact for its own shape and converges O(1/n^2) to the smooth curve
        for (drop in listOf(0.2, 0.3, 0.5)) {
            val run = 1.4; val n = 4000
            val cyc = track(Lanes.cycloid(run, drop, n)).time
            val line = track(Lanes.straight(run, drop, n)).time
            val arc = track(Lanes.arc(run, drop, n)).time
            val fil = Lanes.filletFor(drop)
            val cliff = track(Lanes.cliff(run, drop, fil, n)).time
            println("drop $drop: cycloid ${Descent.cycloid(run, drop, g)} / $cyc  line ${Descent.straight(run, drop, g)} / $line  " +
                "arc ${Descent.arc(run, drop, g)} / $arc  cliff ${Descent.cliff(run, drop, fil, g)} / $cliff")
            assertEquals(Descent.cycloid(run, drop, g), cyc, 2e-6 * cyc)
            assertEquals(Descent.straight(run, drop, g), line, 1e-12)
            assertEquals(Descent.arc(run, drop, g), arc, 2e-6 * arc)
            assertEquals(Descent.cliff(run, drop, fil, g), cliff, 2e-5 * cliff)
        }
    }

    @Test
    fun galileoArcIsAPendulumAndBeatsTheChord() {
        // small-arc limit: K(k) = (pi/2)(1 + k^2/4 + ...), so t -> the quarter small-angle period
        val R = Descent.arcRadius(0.05, 0.0005); val k = sin(Descent.arcPhi0(0.05, 0.0005) / 2)
        assertEquals(PI / 2 * (1 + k * k / 4) * sqrt(R / g), Descent.arc(0.05, 0.0005, g), 1e-8)
        // Galileo 1638: the arc to the lowest point of a circle is faster than the chord
        for (drop in listOf(0.05, 0.2, 0.5, 1.0, 1.4))
            assertTrue("drop $drop", Descent.arc(1.4, drop, g) < Descent.straight(1.4, drop, g))
    }

    @Test
    fun raceOrderAndNumbersAtTheDemoDefault() {
        val run = 1.4; val drop = 0.30; val fil = Lanes.filletFor(drop)
        val tc = Descent.cycloid(run, drop, g); val tk = Descent.cliff(run, drop, fil, g)
        val ta = Descent.arc(run, drop, g); val tl = Descent.straight(run, drop, g)
        println("demo default: cycloid $tc  cliff $tk  arc $ta  line $tl  v_finish ${Descent.finishSpeed(drop, g)}")
        assertEquals(0.729, tc, 1e-3); assertEquals(1.180, tl, 1e-3); assertEquals(0.937, ta, 1e-3)
        assertTrue(tc < tk && tk < ta && ta < tl)
        // across the whole slider range the cycloid always wins and the straight line always loses
        var d = 0.20
        while (d <= 0.5501) {
            val f = Lanes.filletFor(d)
            val c = Descent.cycloid(run, d, g)
            assertTrue(c < Descent.cliff(run, d, f, g) && c < Descent.arc(run, d, g) && c < Descent.straight(run, d, g))
            assertTrue(Descent.straight(run, d, g) > maxOf(Descent.arc(run, d, g), Descent.cliff(run, d, f, g)))
            d += 0.01
        }
    }

    @Test
    fun cycloidIsAMinimumAgainstPerturbations() {
        val run = 1.4; val drop = 0.3; val n = 3000
        val (xs, ys) = Lanes.cycloid(run, drop, n)
        val best = Track(xs, ys, g).time
        for (eps in listOf(-0.02, -0.005, 0.005, 0.02)) for (mode in 1..3) {
            val yp = DoubleArray(n) { ys[it] + eps * sin(mode * PI * xs[it] / run) }
            val t = Track(xs, yp, g).time
            assertTrue("eps $eps mode $mode: $t vs $best", t.isNaN() || t > best)
        }
        // and the excess grows ~ eps^2 (a true stationary minimum, not just a lucky ranking)
        fun excess(e: Double) = Track(xs, DoubleArray(n) { ys[it] + e * sin(PI * xs[it] / run) }, g).time - best
        val r = excess(0.02) / excess(0.01)
        println("perturbation excess ratio (2 eps / eps) = $r  (quadratic -> 4)")
        assertEquals(4.0, r, 0.4)
    }

    @Test
    fun energyIsConservedAndEveryLaneArrivesAtTheSameSpeed() {
        val run = 1.4; val drop = 0.35
        val lanes = listOf(Lanes.cycloid(run, drop), Lanes.straight(run, drop), Lanes.arc(run, drop), Lanes.cliff(run, drop, Lanes.filletFor(drop)))
        val st = DoubleArray(2); val p = DoubleArray(4)
        for (l in lanes) {
            val tr = track(l)
            var worst = 0.0
            for (k in 1..400) {
                val t = tr.time * k / 401.0
                tr.stateAt(t, st); tr.eval(st[0], p)
                worst = maxOf(worst, abs(0.5 * st[1] * st[1] + g * p[1]))      // released from rest at y = 0
            }
            assertTrue("energy drift $worst", worst < 1e-9)
            tr.stateAt(tr.time + 1, st)
            assertEquals(Descent.finishSpeed(drop, g), st[1], 1e-9)
        }
    }

    @Test
    fun stateAtIsContinuousAndMonotone() {
        val tr = track(Lanes.cycloid(1.4, 0.3))
        val st = DoubleArray(2); var prev = -1.0
        for (k in 0..2000) { tr.stateAt(tr.time * k / 2000, st); assertTrue(st[0] >= prev - 1e-12); prev = st[0] }
        assertEquals(tr.length, prev, 1e-9)
    }

    @Test
    fun tautochroneEqualTimesFromAnyHeight() {
        val r = 0.20; val T = Cycloid.tautochroneTime(r, g)
        assertEquals(0.448570, T, 1e-6)
        for (th0 in listOf(0.3, 0.6, 1.2, 1.8, 2.6, 3.0)) {
            // INDEPENDENT check: exact polyline time from rest at theta0 down to the bottom
            val n = 4000
            val xs = DoubleArray(n); val ys = DoubleArray(n)
            val s0 = Cycloid.arcLength(r, th0); val s1 = 4 * r
            for (i in 0 until n) { val th = Cycloid.thetaAtArc(r, s0 + (s1 - s0) * i / (n - 1)); xs[i] = Cycloid.x(r, th); ys[i] = Cycloid.y(r, th) }
            val x0 = xs[0]; val y0 = ys[0]
            for (i in 0 until n) { xs[i] -= x0; ys[i] -= y0 }
            val t = Track(xs, ys, g).time
            println("tautochrone theta0=$th0 (h=${"%.3f".format(2 * r * cos(th0 / 2) * cos(th0 / 2))} m): polyline $t vs pi sqrt(r/g) $T")
            assertEquals(T, t, 2e-5 * T)
            assertEquals(PI, Cycloid.bowlTheta(r, th0, g, T), 1e-9)
        }
    }

    @Test
    fun bowlClosedFormAgreesWithRk4OnTheRawCurve() {
        // integrate s'' = -g dy/ds with dy/ds from FINITE DIFFERENCES of the parametric curve
        // (no use of the u^2/8r result), over 3 full oscillations
        val r = 0.2; val th0 = 0.7
        fun slope(s: Double): Double {
            val h = 1e-6; val a = Cycloid.thetaAtArc(r, s - h); val b = Cycloid.thetaAtArc(r, s + h)
            return (Cycloid.y(r, b) - Cycloid.y(r, a)) / (Cycloid.arcLength(r, b) - Cycloid.arcLength(r, a))
        }
        var s = Cycloid.arcLength(r, th0); var v = 0.0; var t = 0.0; val h = 2e-5; var worst = 0.0
        while (t < 3 * 4 * Cycloid.tautochroneTime(r, g)) {
            fun acc(x: Double) = -g * slope(x)
            val k1s = v; val k1v = acc(s)
            val k2s = v + h / 2 * k1v; val k2v = acc(s + h / 2 * k1s)
            val k3s = v + h / 2 * k2v; val k3v = acc(s + h / 2 * k2s)
            val k4s = v + h * k3v; val k4v = acc(s + h * k3s)
            s += h / 6 * (k1s + 2 * k2s + 2 * k3s + k4s); v += h / 6 * (k1v + 2 * k2v + 2 * k3v + k4v); t += h
            worst = maxOf(worst, abs(s - Cycloid.arcLength(r, Cycloid.bowlTheta(r, th0, g, t))))
        }
        println("bowl exact vs RK4 over 3 periods: worst |ds| = $worst m")
        assertTrue(worst < 2e-6)
    }

    @Test
    fun wheelPointRidesExactlyWithTheBead() {
        // the generating wheel turns at constant omega = sqrt(g/r): its rim point IS the bead
        val run = 1.4; val drop = 0.3; val f = Cycloid.fit(run, drop)
        val tr = track(Lanes.cycloid(run, drop, 2000)); val st = DoubleArray(2); val p = DoubleArray(4)
        var worst = 0.0
        for (k in 1..50) {
            val t = tr.time * k / 51
            val th = Cycloid.omega(f.r, g) * t
            tr.stateAt(t, st); tr.eval(st[0], p)
            worst = maxOf(worst, hypot(p[0] - Cycloid.x(f.r, th), p[1] - Cycloid.y(f.r, th)))
        }
        println("wheel rim point vs bead: worst ${worst * 1000} mm")
        assertTrue(worst < 5e-4)
        // and Snell's constant holds along the whole curve
        for (k in 1..20) assertEquals(1 / sqrt(2 * f.r), Cycloid.snellRatio(f.r, f.thetaEnd * k / 20), 1e-12)
    }

    // ---------------------------------------------------------------- the Unity audit, in numbers

    @Test
    fun auditRollingBallsWereNotWhatTheClockMeasured() {
        assertEquals(1.18322, Descent.ROLLING_TIME_FACTOR, 1e-5)
        val f = Cycloid.fit(1.5, 0.5)
        val rolled = Descent.cycloid(1.5, 0.5, g) * Descent.ROLLING_TIME_FACTOR
        val skid = Descent.cycloidSkidLength(f.r, 0.3)
        println("Unity default: sliding 0.720 s, a rolling solid ball would take ${"%.3f".format(rolled)} s; it must skid the first ${"%.3f".format(skid)} m (mu 0.3)")
        assertEquals(0.852, rolled, 1e-3)
        assertEquals(0.12, skid, 0.01)
        // skid condition derived from tan(theta/2) >= 1/(7 mu): check it at the boundary directly
        val th = 2 * atan(1 / (7 * 0.3))
        val need = 2.0 / 7.0 * cos(th / 2); val have = 0.3 * 2 * sin(th / 2)
        assertEquals(need, have, 1e-12)
    }

    @Test
    fun auditHighRoadKinkLaunchesABallOutOfAnOpenChute() {
        // Unity HIGH ROAD at drop 0.5 / run 1.5: (0,0) -> (1.05,-0.15) -> (1.5,-0.5)
        val a1 = atan(0.15 / 1.05); val a2 = atan(0.35 / 0.45)
        val v = sqrt(2 * g * 0.15)
        val x = Descent.kinkFlight(v, a1, a2, g)
        println("HIGH ROAD kink ${"%.1f".format(Math.toDegrees(a1))} -> ${"%.1f".format(Math.toDegrees(a2))} deg at ${"%.2f".format(v)} m/s: flies $x m of 0.45 m")
        assertEquals(1.716, v, 1e-3)
        assertEquals(0.374, x, 2e-3)
    }

    @Test
    fun auditUnityQuarterEllipseAndEulerClaimsHold() {
        // SampleArc was a quarter ELLIPSE (not a circle); its quoted penalties are right
        fun ellipse(run: Double, drop: Double): Double {
            val n = 20001
            return Track(DoubleArray(n) { run * (1 - cos(PI / 2 * it / (n - 1))) }, DoubleArray(n) { -drop * sin(PI / 2 * it / (n - 1)) }, g).time
        }
        for ((drop, pct) in listOf(0.65 to 0.9, 0.5 to 2.8, 0.15 to 35.7)) {
            val p = 100 * (ellipse(1.5, drop) / Descent.cycloid(1.5, drop, g) - 1)
            println("Unity ellipse 'ARC' at drop $drop: +${"%.2f".format(p)} % (claimed +$pct %)")
            assertEquals(pct, p, 0.06)
        }
        // semi-implicit Euler at H = 1/1920 on 161 samples (the Unity integrator) vs exact
        val n = 161; val (xs, ys) = Lanes.cycloid(1.5, 0.5, n); val tr = Track(xs, ys, g)
        var s = 0.0; var v = 0.0; var t = 0.0; val H = 1.0 / 1920; val p = DoubleArray(4)
        while (s < tr.length) { tr.eval(s, p); v += -g * p[3] * H; val sp = s; s += v * H; t += H; if (s >= tr.length) { t -= (s - tr.length) / v; break }; if (sp == s) break }
        val rel = t / Descent.cycloid(1.5, 0.5, g) - 1
        println("Unity Euler vs exact: rel $rel (claimed ~4e-4)")
        assertTrue(abs(rel) < 5e-4)
    }
}

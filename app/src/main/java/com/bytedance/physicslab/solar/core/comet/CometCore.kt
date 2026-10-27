// CometCore — throwing comets into the drawn solar system. Pure Kotlin, JVM-tested.
//
// UNITS. Positions in sun-root display metres (the frame the planets are drawn in), time in DISPLAY
// DAYS. Gravity is derived from the drawn Earth: mu = 4 pi^2 a_E^3 / (365.25 d)^2, so a body launched
// at Earth's drawn radius with circular speed goes round in exactly one display year — whatever the
// time-rate slider says, and paused/reversed time behaves like it does for the planets.
//
// THE THROW. A hand flick is 0.2 - 3 m/s over a reach of ~0.3 m; an orbit needs a speed relative to
// the local circular speed. Mapping one onto the other linearly makes the useful range a sliver
// (everything escapes or everything falls in). The mapping is a soft-saturating curve:
//     k(v) = 0.2 + 1.441 tanh(0.695 (v - 0.1)),   v_launch = k * sqrt(mu / r)
//   v <= 0.1 m/s (just let go) -> k = 0.2   a plunging, comet-like ellipse that grazes the sun
//   v  = 0.5 m/s  (gentle toss) -> k ~ 0.59  eccentric ellipse
//   v  = 1.0 m/s  (easy throw)  -> k ~ 1.00  near-circular
//   v  = 2.0 m/s  (hard throw)  -> k ~ 1.45  escapes (sqrt 2 = 1.414)
//   v -> big                    -> k -> 1.64 saturates, so a wild swing is still a readable path
// The hand speed is the PEAK over the last 0.25 s before release (people often decelerate for up to
// ~0.2 s before they actually open the pinch; the instantaneous speed at release under-reads the throw).

package com.bytedance.physicslab.solar.core.comet

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlin.math.tanh

object CometThrow {
    const val DEAD = 0.10           // m/s ignored (tremor / indecisive release)
    const val FLOOR = 0.20          // fraction of circular speed for a plain drop
    const val A = 1.441
    const val B = 0.695
    const val OUT_OF_PLANE = 0.30   // keep 30 % of the vertical component of a throw

    fun launchFactor(handSpeed: Double): Double = FLOOR + A * tanh(B * max(0.0, handSpeed - DEAD))

    /** mu in display m^3 / day^2 from Earth's drawn orbit radius. */
    fun mu(aEarth: Double): Double = 4 * PI * PI * aEarth * aEarth * aEarth / (365.25 * 365.25)
}

/** Hand-motion buffer: accumulated displacement samples (s, m) -> robust release velocity (m/s). */
class HandTracker(private val capacity: Int = 64) {
    private val t = DoubleArray(capacity); private val x = DoubleArray(capacity)
    private val y = DoubleArray(capacity); private val z = DoubleArray(capacity)
    private var n = 0; private var head = 0
    var px = 0.0; var py = 0.0; var pz = 0.0          // accumulated hand displacement

    fun reset() { n = 0; head = 0; px = 0.0; py = 0.0; pz = 0.0 }

    fun add(time: Double, dx: Double, dy: Double, dz: Double) {
        px += dx; py += dy; pz += dz
        t[head] = time; x[head] = px; y[head] = py; z[head] = pz
        head = (head + 1) % capacity; if (n < capacity) n++
    }

    private fun at(k: Int) = (head - n + k + capacity * 2) % capacity   // k = 0 oldest

    /**
     * Peak velocity over sliding windows of [win] s ending within the last [look] s before the newest
     * sample. Returns (vx, vy, vz) in m/s, or zeros if there is too little data.
     */
    fun releaseVelocity(win: Double = 0.04, look: Double = 0.25): DoubleArray {
        if (n < 2) return DoubleArray(3)
        val tEnd = t[at(n - 1)]
        var best = DoubleArray(3); var bestS = -1.0
        for (j in n - 1 downTo 1) {
            val tj = t[at(j)]
            if (tEnd - tj > look) break
            // find the sample ~win before j
            var i = j - 1
            while (i > 0 && tj - t[at(i)] < win) i--
            val dt = tj - t[at(i)]
            if (dt <= 1e-4) continue
            val vx = (x[at(j)] - x[at(i)]) / dt; val vy = (y[at(j)] - y[at(i)]) / dt; val vz = (z[at(j)] - z[at(i)]) / dt
            val s = sqrt(vx * vx + vy * vy + vz * vz)
            if (s > bestS) { bestS = s; best = doubleArrayOf(vx, vy, vz) }
        }
        return best
    }
}

/** One comet in sun-root display coordinates (metres, metres/day). */
class Comet(var x: Double, var y: Double, var z: Double) {
    var vx = 0.0; var vy = 0.0; var vz = 0.0
    var held = true
    var alive = true
    var fate = ""                 // "", "sun", "escaped"
    val r get() = sqrt(x * x + y * y + z * z)
    val speed get() = sqrt(vx * vx + vy * vy + vz * vz)

    /** Specific orbital energy (display units); < 0 bound, >= 0 escapes. */
    fun energy(mu: Double) = 0.5 * (vx * vx + vy * vy + vz * vz) - mu / max(r, 1e-9)

    /** Osculating semi-major axis (negative/infinite for unbound) and eccentricity. */
    fun elements(mu: Double): Pair<Double, Double> {
        val e = energy(mu)
        val a = if (e < 0) -mu / (2 * e) else Double.POSITIVE_INFINITY
        // h = r x v ; e_vec = (v x h)/mu - r/|r|
        val hx = y * vz - z * vy; val hy = z * vx - x * vz; val hz = x * vy - y * vx
        val ex = (vy * hz - vz * hy) / mu - x / r
        val ey = (vz * hx - vx * hz) / mu - y / r
        val ez = (vx * hy - vy * hx) / mu - z / r
        return a to sqrt(ex * ex + ey * ey + ez * ez)
    }

    /**
     * Launch from the current position. [dir] is the throw direction in sun-root coordinates (any
     * length); its out-of-plane part (y = ecliptic normal) is kept at [CometThrow.OUT_OF_PLANE]. A
     * mostly vertical throw falls back to the prograde tangent. Speed = k * circular speed here.
     */
    fun launch(mu: Double, k: Double, dirX: Double, dirY: Double, dirZ: Double) {
        var dx = dirX; var dy = dirY * CometThrow.OUT_OF_PLANE; var dz = dirZ
        val inPlane = sqrt(dx * dx + dz * dz)
        if (inPlane < 0.2 * sqrt(dirX * dirX + dirY * dirY + dirZ * dirZ) || inPlane < 1e-9) {
            // prograde tangent (planets run counter-clockwise seen from +Y): t = y_hat x r_hat
            val rl = max(sqrt(x * x + z * z), 1e-9)
            dx = z / rl; dz = -x / rl; dy = 0.0
        }
        val l = max(sqrt(dx * dx + dy * dy + dz * dz), 1e-12)
        val v = k * sqrt(mu / max(r, 1e-6))
        vx = dx / l * v; vy = dy / l * v; vz = dz / l * v
        held = false
    }
}

object CometSim {
    /**
     * Advance [c] by [days] (may be negative) under the sun alone, kick-drift-kick leapfrog with a
     * step of at most 1/200 of the local orbital period. Kills it on hitting the sun or leaving [escapeR].
     */
    fun step(c: Comet, mu: Double, days: Double, sunR: Double, escapeR: Double) {
        if (!c.alive || c.held || days == 0.0) return
        var left = days
        var guard = 0
        while (abs(left) > 1e-12 && guard++ < 4000) {
            val r = max(c.r, 1e-6)
            val period = 2 * PI * sqrt(r * r * r / mu)
            val h = (period / 200.0).coerceAtMost(abs(left)) * if (left > 0) 1 else -1
            kick(c, mu, h / 2); c.x += c.vx * h; c.y += c.vy * h; c.z += c.vz * h; kick(c, mu, h / 2)
            left -= h
            if (c.r < sunR) { c.alive = false; c.fate = "sun"; return }
            if (c.r > escapeR) { c.alive = false; c.fate = "escaped"; return }
        }
    }

    private fun kick(c: Comet, mu: Double, h: Double) {
        val r = max(c.r, 1e-6); val f = -mu / (r * r * r) * h
        c.vx += c.x * f; c.vy += c.y * f; c.vz += c.z * f
    }

    /** Predicted path from the current state: up to [maxDays] or one revolution, [n] points (xyz). */
    fun predict(c: Comet, mu: Double, sunR: Double, escapeR: Double, n: Int, maxDays: Double): FloatArray {
        val g = Comet(c.x, c.y, c.z).also { it.vx = c.vx; it.vy = c.vy; it.vz = c.vz; it.held = false }
        val (a, _) = g.elements(mu)
        val span = if (a.isFinite() && a > 0) minOf(maxDays, 2 * PI * sqrt(a * a * a / mu)) else maxDays
        val out = FloatArray(n * 3)
        for (k in 0 until n) {
            out[k * 3] = g.x.toFloat(); out[k * 3 + 1] = g.y.toFloat(); out[k * 3 + 2] = g.z.toFloat()
            if (g.alive) step(g, mu, span / (n - 1), sunR, escapeR)
        }
        return out
    }
}

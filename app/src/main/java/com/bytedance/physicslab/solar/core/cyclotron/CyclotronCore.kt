// CyclotronCore — the Lorentz force and Lawrence's cyclotron, pure Kotlin, SI units.
//
// AUDIT OF THE UNITY SOURCE (LorentzDemo.cs + LorentzCore.cs):
//   * The title was "CYCLOTRON & HELIX", but there was no cyclotron: no dees, no gap, no electric
//     field, no energy gain. A single charge circled/helixed at constant speed in a Helmholtz
//     field — which is the Lorentz force, not the machine. The speed-independent period was only
//     ASSERTED in the text ("same at any speed"); every launch used the same speed v_perp = 0.5,
//     so nothing on screen could show it.
//   * Units were dimensionless (q/m = +-1, B = 1.5..4, v = 0.5): r = 0.20 "m", T = 2.51 "s".
//     No real particle has those numbers. Here everything is SI: a proton in 1.3 T circles at
//     f = qB/(2 pi m) = 19.8 MHz, r = mv/(qB), and the drawing is a scaled view of those numbers.
//   * The Boris rotation itself was right (|v| conserved exactly), and is kept — extended with the
//     electric half-kicks the gap needs, in its relativistic form (u = gamma v), so the classic
//     cyclotron limit (f drops as gamma grows) is also in the physics.
//
// THE MACHINE: two hollow dees (field-free inside, a Faraday cage), a gap |x| < g/2 between them
// with E_x = U(t)/g, U(t) = U0 cos(2 pi f_RF t), and a uniform B along +z (or -z). A particle
// released at rest in the gap centre is kicked by qU on every crossing; inside the dees it moves
// on semicircles whose period does not depend on the radius, so a FIXED-frequency RF stays in
// step for every turn — that is the whole invention. It spirals out to the extraction radius R
// with E = (qBR)^2/(2m), independent of U (U only sets how many turns it takes).

package com.bytedance.physicslab.solar.core.cyclotron

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sqrt

object Phys {
    const val QE = 1.602176634e-19        // C
    const val C = 299_792_458.0           // m/s
    const val M_P = 1.67262192369e-27     // proton, kg
    const val M_D = 3.3435837724e-27      // deuteron
    const val M_ALPHA = 6.6446573357e-27  // alpha (He-4 nucleus)
    const val MEV = 1e6 * QE              // J
}

/** [name] is the Chinese display name, [nameEn] the English one (the demo picks one with tr()). */
data class Species(val name: String, val nameEn: String, val q: Double, val m: Double)

object Lorentz {
    val PROTON = Species("质子 p", "Proton p", Phys.QE, Phys.M_P)
    val DEUTERON = Species("氘核 d", "Deuteron d", Phys.QE, Phys.M_D)
    val ALPHA = Species("α 粒子", "α particle", 2 * Phys.QE, Phys.M_ALPHA)
    val SPECIES = listOf(PROTON, DEUTERON, ALPHA)

    /** Non-relativistic cyclotron frequency f = |q| B / (2 pi m) (Hz) — no v, no r in it. */
    fun cyclotronFreq(q: Double, m: Double, b: Double) = abs(q) * abs(b) / (2 * PI * m)
    /** Gyro-period at Lorentz factor gamma: T = 2 pi gamma m / (|q| B). */
    fun period(q: Double, m: Double, b: Double, gamma: Double = 1.0) = 2 * PI * gamma * m / (abs(q) * abs(b))
    /** Gyroradius r = m v_perp / (|q| B) (non-relativistic; multiply m by gamma otherwise). */
    fun gyroRadius(m: Double, vPerp: Double, q: Double, b: Double) = m * vPerp / (abs(q) * abs(b))
    /** Helix pitch: distance along B per turn, p = v_par * T. */
    fun pitch(vPar: Double, q: Double, m: Double, b: Double) = vPar * period(q, m, b)
    /** Kinetic energy at radius r in field B (non-relativistic): E = (qBr)^2 / (2m). */
    fun energyAtRadius(q: Double, m: Double, b: Double, r: Double) = (q * b * r) * (q * b * r) / (2 * m)

    /**
     * One relativistic Boris step (Birdsall & Langdon): half electric kick, exact magnetic
     * rotation, half electric kick, drift. State r (m) and u = gamma v (m/s), updated in place.
     * With E = 0 the rotation preserves |u| to round-off, so the magnetic force does no work.
     */
    fun boris(r: DoubleArray, u: DoubleArray, qm: Double,
              ex: Double, ey: Double, ez: Double, bx: Double, by: Double, bz: Double, dt: Double) {
        val h = qm * dt / 2
        var ux = u[0] + h * ex; var uy = u[1] + h * ey; var uz = u[2] + h * ez
        val g = sqrt(1 + (ux * ux + uy * uy + uz * uz) / (Phys.C * Phys.C))
        val tx = h * bx / g; val ty = h * by / g; val tz = h * bz / g
        val t2 = tx * tx + ty * ty + tz * tz
        val sx = 2 * tx / (1 + t2); val sy = 2 * ty / (1 + t2); val sz = 2 * tz / (1 + t2)
        val px = ux + (uy * tz - uz * ty); val py = uy + (uz * tx - ux * tz); val pz = uz + (ux * ty - uy * tx)
        ux += py * sz - pz * sy; uy += pz * sx - px * sz; uz += px * sy - py * sx
        ux += h * ex; uy += h * ey; uz += h * ez
        u[0] = ux; u[1] = uy; u[2] = uz
        val g2 = sqrt(1 + (ux * ux + uy * uy + uz * uz) / (Phys.C * Phys.C))
        r[0] += ux / g2 * dt; r[1] += uy / g2 * dt; r[2] += uz / g2 * dt
    }

    fun gammaOf(u: DoubleArray) = sqrt(1 + (u[0] * u[0] + u[1] * u[1] + u[2] * u[2]) / (Phys.C * Phys.C))
    fun kinetic(u: DoubleArray, m: Double) = (gammaOf(u) - 1) * m * Phys.C * Phys.C
}

/** One gap crossing: when it left the gap, the energy it gained, and the RF phase it saw. */
data class Crossing(val t: Double, val dE: Double, val phaseDeg: Double, val x: Double, val y: Double)

/** A full flight, sampled every integration step. Positions in metres, energies in joules. */
class Track(
    val dt: Double,
    val x: DoubleArray, val y: DoubleArray, val ek: DoubleArray,
    val crossings: List<Crossing>,
    /** index of the sample where the extractor took the particle, or -1 */
    val extractIdx: Int,
    val turns: Double,
) {
    val n get() = x.size
    val tEnd get() = (n - 1) * dt
    val extracted get() = extractIdx >= 0
    /** Linear interpolation at flight time tau (clamped). */
    fun at(tau: Double, out: DoubleArray) {
        val f = (tau / dt).coerceIn(0.0, (n - 1).toDouble())
        val i = f.toInt().coerceAtMost(n - 2); val w = f - i
        out[0] = x[i] + (x[i + 1] - x[i]) * w; out[1] = y[i] + (y[i + 1] - y[i]) * w; out[2] = ek[i] + (ek[i + 1] - ek[i]) * w
    }
    val maxEk get() = ek.max()
}

/**
 * Lawrence's machine. [b] in tesla (sign = direction along +z), [u0] peak dee-to-dee voltage (V),
 * [gap] metres, [rExtract] radius at which the deflector at azimuth [extractAzDeg] takes the beam,
 * [rfRatio] = f_RF / f_c (1 = tuned).
 */
class Cyclotron(
    val sp: Species,
    val b: Double,
    val u0: Double,
    val gap: Double = 0.010,
    val rExtract: Double = 0.120,
    val rfRatio: Double = 1.0,
    /** Deflector azimuth for B > 0 (clockwise orbits seen from +z); mirrored for B < 0. Inside a
     *  dee, never in the gap (the gap crossings are at azimuth +-90 deg). */
    val extractAzDeg: Double = 60.0,
    /** RF phase at the moment the ion leaves the source. The first half-gap from rest is slow, so
     *  a bunch released ON the crest arrives 12 deg late at every later crossing; releasing it
     *  12 deg early centres the crossings on the crests (cos 0 = full qU0 per crossing). */
    val injectPhaseDeg: Double = -12.0,
) {
    val fc = Lorentz.cyclotronFreq(sp.q, sp.m, b)
    val frf = fc * rfRatio
    val tc = 1 / fc
    val trf = 1 / frf
    /** The textbook end energy, (qBR)^2/2m, which the voltage does not enter. */
    val eFinal = Lorentz.energyAtRadius(sp.q, sp.m, b, rExtract)

    private val ph0 = Math.toRadians(injectPhaseDeg)
    fun gapVoltage(t: Double) = u0 * cos(2 * PI * frf * t + ph0)

    /**
     * Fly one particle from rest at the source (gap centre) until the deflector extracts it, then
     * [exitRun] metres of straight flight to the target; or until [maxTurns] RF periods pass.
     */
    fun fly(stepsPerTurn: Int = 720, maxTurns: Int = 40, exitRun: Double = 0.06): Track {
        val dt = tc / stepsPerTurn
        val r = doubleArrayOf(0.0, 0.0, 0.0); val u = doubleArrayOf(0.0, 0.0, 0.0)
        val qm = sp.q / sp.m
        val xs = ArrayList<Double>(); val ys = ArrayList<Double>(); val es = ArrayList<Double>()
        val cross = ArrayList<Crossing>()
        var t = 0.0
        var inGap = true; var eIn = 0.0; var tIn = 0.0
        var extractIdx = -1; var exitX = 0.0; var exitY = 0.0
        var lastAz = atan2(0.0, 0.0)
        var peak = 0.0
        val az0 = Math.toRadians(if (b >= 0) extractAzDeg else 180.0 - extractAzDeg)
        val maxSteps = maxTurns * stepsPerTurn
        var step = 0
        fun push() { xs += r[0]; ys += r[1]; es += Lorentz.kinetic(u, sp.m) }
        push()
        while (step < maxSteps) {
            if (extractIdx >= 0) {
                // straight flight in the field-free extraction channel
                Lorentz.boris(r, u, qm, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, dt)
                t += dt; step++; push()
                if (hypot(r[0] - exitX, r[1] - exitY) >= exitRun) break
                continue
            }
            // near the gap: 32 sub-steps, so the kick is not quantised by how many coarse steps
            // happen to land inside a 1 cm gap (at 15 Mm/s one coarse step is 1 mm = 10 % of qU)
            val vx = abs(u[0]) / Lorentz.gammaOf(u)
            if (abs(r[0]) < gap / 2 + 1.5 * vx * dt) {
                val h = dt / SUB
                for (k in 0 until SUB) {
                    val ex = if (abs(r[0]) < gap / 2) gapVoltage(t + (k + 0.5) * h) / gap else 0.0
                    Lorentz.boris(r, u, qm, ex, 0.0, 0.0, 0.0, 0.0, b, h)
                }
            } else Lorentz.boris(r, u, qm, 0.0, 0.0, 0.0, 0.0, 0.0, b, dt)
            t += dt; step++; push()
            val nowGap = abs(r[0]) < gap / 2
            if (!inGap && nowGap) tIn = t - dt / 2
            if (inGap && !nowGap) {
                // energy only changes in the gap, so the gain is "now" minus "at the last exit"
                val tm = (tIn + t - dt / 2) / 2
                val ph = ((Math.toDegrees(2 * PI * frf * tm + ph0) % 360.0) + 360.0) % 360.0
                cross += Crossing(t, es.last() - eIn, ph, r[0], r[1])
                eIn = es.last()
            }
            inGap = nowGap
            // the deflector: an electrostatic septum at a fixed azimuth that takes the particle once
            // its ORBIT radius r = p/(qB) has reached rExtract (the orbit centre is not the machine
            // centre — it walks by ~r1 — so the distance from the centre would over-shoot E by 20 %)
            val az = atan2(r[1], r[0])
            val rg = sp.m * hypot(u[0], u[1]) / (abs(sp.q) * abs(b))
            if (rg >= rExtract && !nowGap && crossedAz(lastAz, az, az0)) { extractIdx = xs.size - 1; exitX = r[0]; exitY = r[1] }
            lastAz = az
            // out of step: once it has given back most of its peak energy the bunch is lost
            peak = maxOf(peak, es.last())
            if (peak > 4 * abs(sp.q) * u0 && es.last() < 0.12 * peak) break
        }
        val turns = (if (extractIdx >= 0) extractIdx else xs.size - 1) * dt / tc
        return Track(dt, xs.toDoubleArray(), ys.toDoubleArray(), es.toDoubleArray(), cross, extractIdx, turns)
    }

    private companion object { const val SUB = 32 }

    private fun crossedAz(a0: Double, a1: Double, target: Double): Boolean {
        fun wrap(a: Double): Double { var v = a; while (v > PI) v -= 2 * PI; while (v < -PI) v += 2 * PI; return v }
        val d0 = wrap(a0 - target); val d1 = wrap(a1 - target)
        return d0 * d1 <= 0 && abs(d0 - d1) < PI      // sign change, not across the +-pi seam
    }
}

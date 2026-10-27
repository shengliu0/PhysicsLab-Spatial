// GasCore — a hard-sphere gas in a box with a moving piston and a heater floor. Pure Kotlin.
//
// WHAT THE UNITY BUILD DID (GasBoxDemo.cs + GasCore.cs) AND WHAT WAS WRONG WITH IT
//   * Molecules bounced off the walls only — no molecule-molecule collisions. Specular walls keep
//     every |v| constant, so the speed distribution was FROZEN at the initial random draw: it
//     could never "become" Maxwell-Boltzmann, and equipartition could never be established.
//     Boltzmann's 1872 point (the H-theorem) is precisely that collisions drive any start to
//     Maxwell. Here molecules collide elastically (equal-mass hard spheres).
//   * RandN() = (U+U+U-1.5)*1.1547 was commented "~N(0,1)" but its variance is 3/12*4/3 = 1/3.
//     Every reset therefore started the gas at ONE THIRD of the labelled temperature, and the
//     printed "P·V/(N·T) ≈ (constant)" read ≈0.35 until the user touched the temperature button
//     (Rescale() fixed <v²>, the next reset broke it again). Here: true Gaussians, then an exact
//     rescale so the gas starts at the requested T.
//   * Pressure was impulse / (geometric wall area) while centres were confined to L-2R per axis:
//     the gauge read 5.7 % (100 % volume) to 6.4 % (57 %) above N m<v²>/(3V). Here the walls are
//     the planes the CENTRES reach (the glass is drawn one radius further out), so for point-like
//     molecules p V = N k T exactly; what remains is the real hard-sphere excess (van der Waals b).
//   * The piston teleported molecules (ClampInside) — no work, no heating. Here a moving piston
//     reflects in its own frame (v' = 2u - v), so pushing it in HEATS the gas (adiabatic,
//     T V^(2/3) = const for a monatomic gas) and the heater floor then brings it back to T_set.
//   * "Halve the volume -> pressure doubles": the three piston stops were 100/77/57 %, so the UI
//     could never halve the volume. Here V runs 40-100 % and the gaze target cycles 100/75/50 %.
//
// UNITS. Model units: metres (as drawn), seconds (simulation time), molecule mass m = 1, k_B = 1,
// so the model temperature is T = m<v²>/3 in m²/s². GasScale maps it to kelvin (300 K <->
// v_rms 0.40 m/s on screen) and to nitrogen (v_rms 517 m/s at 300 K) for the readout.

package com.bytedance.physicslab.solar.core.gas

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * N equal hard spheres (diameter [diameter]) in a box whose CENTRE region is
 * x in [xLo, xHi], y in [-hy, hy], z in [-hz, hz]. The x-min wall is a piston that can move;
 * the y-min wall can be a thermal (diffusely re-emitting) heater at [wallT]; all other walls are
 * specular. Time-stepped with sort-and-sweep pair detection; a pair that overlaps AND approaches
 * exchanges the normal components of velocity (equal masses, elastic) — momentum and energy are
 * conserved exactly per collision.
 */
class HardSphereGas(val capacity: Int, val diameter: Double, seed: Long = 1872L) {
    val x = DoubleArray(capacity); val y = DoubleArray(capacity); val z = DoubleArray(capacity)
    val vx = DoubleArray(capacity); val vy = DoubleArray(capacity); val vz = DoubleArray(capacity)
    var n = 0; private set

    var xLo = -0.5; var xHi = 0.5; var hy = 0.2; var hz = 0.2
    /** Piston (x-min wall) target position and its maximum speed. */
    var pistonTarget = xLo
    var pistonMaxSpeed = 0.12
    /** Current piston velocity (m/s, + = compressing). */
    var pistonU = 0.0; private set

    var collisions = true
    var thermalFloor = false
    var wallT = 1.0

    /** Largest substep (s). Displacement per step must stay well under the diameter. */
    var maxStep = 1.0 / 600.0

    // ---- statistics since the last takePressure()
    private val impAxis = DoubleArray(3)      // impulse on the x, y, z wall pairs
    private val atAxis = DoubleArray(3)       // area x time of those wall pairs
    var collisionCount = 0L; private set
    var floorHits = 0L; private set

    private val rnd = java.util.Random(seed)
    private val ord = IntArray(capacity)

    val volume get() = (xHi - xLo) * (2 * hy) * (2 * hz)
    val wallArea get() = 2 * ((2 * hy) * (2 * hz) + (xHi - xLo) * (2 * hz) + (xHi - xLo) * (2 * hy))

    // ------------------------------------------------------------------ set-up

    fun setBox(xLo: Double, xHi: Double, hy: Double, hz: Double) {
        this.xLo = xLo; this.xHi = xHi; this.hy = hy; this.hz = hz; pistonTarget = xLo
    }

    /** [count] molecules at random non-overlapping positions, Maxwell velocities, exactly at T. */
    fun initMaxwell(count: Int, t: Double) {
        n = 0
        addMolecules(count, t)
        rescaleTo(t)
        resetStats()
    }

    /** Boltzmann's thought experiment: every molecule at the SAME speed sqrt(3T), random direction. */
    fun initEqualSpeed(count: Int, t: Double) {
        n = 0
        addMolecules(count, t)
        val s = sqrt(3 * t)
        for (i in 0 until n) {
            val (ux, uy, uz) = randomUnit()
            vx[i] = s * ux; vy[i] = s * uy; vz[i] = s * uz
        }
        resetStats()
    }

    /** Equipartition test start: all kinetic energy in x (vx = +-sqrt(3T), vy = vz = 0). */
    fun initAllInX(count: Int, t: Double) {
        n = 0
        addMolecules(count, t)
        val s = sqrt(3 * t)
        for (i in 0 until n) { vx[i] = if (rnd.nextBoolean()) s else -s; vy[i] = 0.0; vz[i] = 0.0 }
        resetStats()
    }

    /** Grow or shrink the population; new molecules arrive at temperature [t] (Maxwell). */
    fun setCount(count: Int, t: Double) {
        val c = count.coerceIn(0, capacity)
        if (c < n) n = c else if (c > n) addMolecules(c - n, t)
    }

    private fun addMolecules(k: Int, t: Double) {
        val sig = sqrt(t)
        val d2 = diameter * diameter * 1.02
        repeat(k) {
            if (n >= capacity) return
            var px = 0.0; var py = 0.0; var pz = 0.0
            for (attempt in 0 until 400) {
                px = xLo + (xHi - xLo) * rnd.nextDouble(); py = -hy + 2 * hy * rnd.nextDouble(); pz = -hz + 2 * hz * rnd.nextDouble()
                var ok = true
                for (j in 0 until n) {
                    val dx = x[j] - px; val dy = y[j] - py; val dz = z[j] - pz
                    if (dx * dx + dy * dy + dz * dz < d2) { ok = false; break }
                }
                if (ok) break
            }
            x[n] = px; y[n] = py; z[n] = pz
            vx[n] = sig * rnd.nextGaussian(); vy[n] = sig * rnd.nextGaussian(); vz[n] = sig * rnd.nextGaussian()
            ord[n] = n
            n++
        }
        for (i in 0 until n) ord[i] = i
    }

    private fun randomUnit(): Triple<Double, Double, Double> {
        val u = 2 * rnd.nextDouble() - 1; val ph = 2 * PI * rnd.nextDouble(); val s = sqrt(1 - u * u)
        return Triple(s * kotlin.math.cos(ph), s * kotlin.math.sin(ph), u)
    }

    /** Boltzmann's start without moving anything: every speed set to sqrt(3T), directions kept (energy unchanged). */
    fun equalizeSpeeds() {
        val s = sqrt(3 * temperature())
        for (i in 0 until n) { val v = speed(i); if (v > 1e-12) { val k = s / v; vx[i] *= k; vy[i] *= k; vz[i] *= k } }
    }

    /** Scale every velocity so the gas temperature is exactly [t] (instant heating/cooling). */
    fun rescaleTo(t: Double) {
        val cur = temperature()
        if (cur <= 0) return
        val k = sqrt(t / cur)
        for (i in 0 until n) { vx[i] *= k; vy[i] *= k; vz[i] *= k }
    }

    // ------------------------------------------------------------------ observables

    fun kineticEnergy(): Double { var e = 0.0; for (i in 0 until n) e += vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i]; return 0.5 * e }

    /** T = m<v²>/3 (k_B = m = 1). Total momentum is not conserved (walls), so all 3N dof count. */
    fun temperature(): Double = if (n == 0) 0.0 else 2 * kineticEnergy() / (3.0 * n)

    fun momentum(out: DoubleArray) { out[0] = 0.0; out[1] = 0.0; out[2] = 0.0; for (i in 0 until n) { out[0] += vx[i]; out[1] += vy[i]; out[2] += vz[i] } }

    fun speed(i: Int) = sqrt(vx[i] * vx[i] + vy[i] * vy[i] + vz[i] * vz[i])

    /** Fraction of molecules per speed bin on [0, vMax); molecules faster than vMax are not counted. */
    fun speedHistogram(out: DoubleArray, vMax: Double) {
        out.fill(0.0)
        if (n == 0) return
        val nb = out.size; val w = 1.0 / n
        for (i in 0 until n) { val b = (speed(i) / vMax * nb).toInt(); if (b < nb) out[b] += w }
    }

    /** Mean wall pressure (all six faces: impulse / (area x time)) since the last call; resets. */
    fun takePressure(): Double {
        val a = atAxis[0] + atAxis[1] + atAxis[2]
        val p = if (a > 0) (impAxis[0] + impAxis[1] + impAxis[2]) / a else 0.0
        impAxis.fill(0.0); atAxis.fill(0.0)
        return p
    }

    /** Pressure on the x, y and z wall pairs separately since the last takePressure() (no reset). */
    fun pressureAxes(out: DoubleArray) { for (k in 0 until 3) out[k] = if (atAxis[k] > 0) impAxis[k] / atAxis[k] else 0.0 }

    fun resetStats() { impAxis.fill(0.0); atAxis.fill(0.0); collisionCount = 0; floorHits = 0 }

    // ------------------------------------------------------------------ dynamics

    /** Advance [dt] seconds of simulation time (split into substeps <= maxStep). */
    fun step(dt: Double) {
        if (dt <= 0) return
        val k = ceil(dt / maxStep).toInt().coerceAtLeast(1)
        val h = dt / k
        repeat(k) { substep(h) }
    }

    private fun substep(h: Double) {
        // piston: move toward the target at <= pistonMaxSpeed
        val want = (pistonTarget - xLo) / h
        pistonU = want.coerceIn(-pistonMaxSpeed, pistonMaxSpeed)
        xLo += pistonU * h
        val lx = xHi - xLo
        atAxis[0] += 2 * (4 * hy * hz) * h; atAxis[1] += 2 * (lx * 2 * hz) * h; atAxis[2] += 2 * (lx * 2 * hy) * h

        for (i in 0 until n) {
            var px = x[i] + vx[i] * h; var py = y[i] + vy[i] * h; var pz = z[i] + vz[i] * h
            // piston (moving wall): reflect in the piston frame, v' = 2u - v
            if (px < xLo && vx[i] < pistonU) {
                val v0 = vx[i]; vx[i] = 2 * pistonU - v0; px = 2 * xLo - px
                impAxis[0] += abs(vx[i] - v0)
            } else if (px > xHi && vx[i] > 0) {
                impAxis[0] += 2 * vx[i]; vx[i] = -vx[i]; px = 2 * xHi - px
            }
            if (px < xLo) px = xLo   // a molecule outrun by a fast piston is swept along
            if (py < -hy && vy[i] < 0) {
                if (thermalFloor) {
                    // diffuse re-emission from a wall at wallT: normal speed is flux-weighted
                    // (Rayleigh), tangential components Gaussian — the exact equilibrium kernel
                    val vin = -vy[i]
                    val sig = sqrt(wallT)
                    vy[i] = sig * sqrt(-2 * ln(1 - rnd.nextDouble()))
                    vx[i] = sig * rnd.nextGaussian(); vz[i] = sig * rnd.nextGaussian()
                    impAxis[1] += vin + vy[i]
                    floorHits++
                } else { impAxis[1] += -2 * vy[i]; vy[i] = -vy[i] }
                py = -2 * hy - py
            } else if (py > hy && vy[i] > 0) {
                impAxis[1] += 2 * vy[i]; vy[i] = -vy[i]; py = 2 * hy - py
            }
            if (pz < -hz && vz[i] < 0) { impAxis[2] += -2 * vz[i]; vz[i] = -vz[i]; pz = -2 * hz - pz }
            else if (pz > hz && vz[i] > 0) { impAxis[2] += 2 * vz[i]; vz[i] = -vz[i]; pz = 2 * hz - pz }
            x[i] = px; y[i] = py; z[i] = pz
        }
        if (collisions) collide()
    }

    /** Sort-and-sweep along x (insertion sort: the order barely changes between substeps). */
    private fun collide() {
        for (a in 1 until n) {
            val id = ord[a]; val xv = x[id]; var b = a - 1
            while (b >= 0 && x[ord[b]] > xv) { ord[b + 1] = ord[b]; b-- }
            ord[b + 1] = id
        }
        val d = diameter; val d2 = d * d
        for (a in 0 until n) {
            val i = ord[a]; val xi = x[i]
            var b = a + 1
            while (b < n) {
                val j = ord[b]
                val dx = x[j] - xi
                if (dx >= d) break
                val dy = y[j] - y[i]; val dz = z[j] - z[i]
                val r2 = dx * dx + dy * dy + dz * dz
                if (r2 < d2 && r2 > 1e-18) {
                    val dot = dx * (vx[j] - vx[i]) + dy * (vy[j] - vy[i]) + dz * (vz[j] - vz[i])
                    if (dot < 0) { exchange(i, j, dx, dy, dz, dot / r2); collisionCount++ }
                }
                b++
            }
        }
    }

    private fun exchange(i: Int, j: Int, dx: Double, dy: Double, dz: Double, k: Double) {
        vx[i] += k * dx; vy[i] += k * dy; vz[i] += k * dz
        vx[j] -= k * dx; vy[j] -= k * dy; vz[j] -= k * dz
    }

    /** Elastic collision of two molecules at the given offset (exposed for the conservation test). */
    fun collidePair(i: Int, j: Int) {
        val dx = x[j] - x[i]; val dy = y[j] - y[i]; val dz = z[j] - z[i]
        val r2 = dx * dx + dy * dy + dz * dz
        val dot = dx * (vx[j] - vx[i]) + dy * (vy[j] - vy[i]) + dz * (vz[j] - vz[i])
        if (dot < 0) exchange(i, j, dx, dy, dz, dot / r2)
    }

    /** Place molecule [i] directly (tests). */
    fun put(i: Int, px: Double, py: Double, pz: Double, ux: Double, uy: Double, uz: Double) {
        if (i >= n) { n = i + 1; for (k in 0 until n) ord[k] = k }
        x[i] = px; y[i] = py; z[i] = pz; vx[i] = ux; vy[i] = uy; vz[i] = uz
    }
}

/** The Maxwell-Boltzmann speed distribution in model units (m = k_B = 1). */
object Maxwell {
    /** f(v) = 4 pi v² (1/(2 pi T))^(3/2) exp(-v²/2T). */
    fun pdf(v: Double, t: Double): Double = 4 * PI * v * v * Math.pow(2 * PI * t, -1.5) * exp(-v * v / (2 * t))

    /** P(speed < v) = erf(v/(a sqrt2)) - sqrt(2/pi) (v/a) exp(-v²/2a²), a = sqrt(T). */
    fun cdf(v: Double, t: Double): Double {
        val a = sqrt(t); val s = v / a
        return erf(s / sqrt(2.0)) - sqrt(2 / PI) * s * exp(-s * s / 2)
    }

    fun binFraction(v0: Double, v1: Double, t: Double) = cdf(v1, t) - cdf(v0, t)

    fun meanSpeed(t: Double) = sqrt(8 * t / PI)
    fun rmsSpeed(t: Double) = sqrt(3 * t)
    fun mostProbable(t: Double) = sqrt(2 * t)

    /** erf via the Numerical Recipes Chebyshev fit of erfc (fractional error < 1.2e-7 everywhere). */
    fun erf(x: Double): Double {
        val sgn = if (x < 0) -1.0 else 1.0
        val ax = abs(x)
        val t = 1.0 / (1.0 + 0.5 * ax)
        val ans = t * exp(-ax * ax - 1.26551223 + t * (1.00002368 + t * (0.37409196 + t * (0.09678418 +
            t * (-0.18628806 + t * (0.27886807 + t * (-1.13520398 + t * (1.48851587 +
            t * (-0.82215223 + t * 0.17087277)))))))))
        return sgn * (1 - ans)
    }
}

/** Hard-sphere equation of state (what the wall pressure should read beyond the ideal gas). */
object HardSphereEos {
    /** Packing fraction eta = n pi d³ / 6. */
    fun packing(nDensity: Double, d: Double) = nDensity * PI * d * d * d / 6
    /** Carnahan-Starling compressibility Z = pV/NkT = (1 + eta + eta² - eta³)/(1 - eta)³. */
    fun zCarnahanStarling(eta: Double): Double { val q = 1 - eta; return (1 + eta + eta * eta - eta * eta * eta) / (q * q * q) }
}

/**
 * The stated scaling between the model and the real world.
 *   * temperature: 300 K <-> on-screen v_rms = [V_RMS_300] m/s at playback x1 (T_model ∝ T_K);
 *   * speeds are quoted for nitrogen N₂: v_rms = sqrt(3 k T / m) = 517 m/s at 300 K;
 *   * pressure: the reference state (N_REF molecules, full volume, 300 K) is DEFINED as 1 atm
 *     ideal, i.e. every drawn ball stands for [realPerBall] real N₂ molecules of a box this size.
 */
object GasScale {
    const val KB = 1.380649e-23
    const val AMU = 1.66053906660e-27
    const val M_N2 = 28.0134 * AMU
    const val ATM = 101325.0
    const val T_REF_K = 300.0
    const val V_RMS_300 = 0.40

    val modelPerKelvin = V_RMS_300 * V_RMS_300 / 3.0 / T_REF_K
    fun modelT(kelvin: Double) = kelvin * modelPerKelvin
    fun kelvin(modelT: Double) = modelT / modelPerKelvin

    fun realVrms(kelvin: Double) = sqrt(3 * KB * kelvin / M_N2)
    /** On-screen m/s -> real N₂ m/s. */
    val speedToReal get() = realVrms(T_REF_K) / V_RMS_300

    /** Model pressure that reads 1.00 atm: N_ref k T_ref / V_ref (ideal). */
    fun pRef(nRef: Int, vRef: Double) = nRef * modelT(T_REF_K) / vRef
    /** Real molecules per drawn ball if the drawn box (real size [vRefM3]) holds N₂ at 1 atm, 300 K. */
    fun realPerBall(nRef: Int, vRefM3: Double) = ATM * vRefM3 / (KB * T_REF_K) / nRef
}

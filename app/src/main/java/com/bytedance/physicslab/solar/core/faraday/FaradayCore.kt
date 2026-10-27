// FaradayCore — a bar magnet moving through a coil, pure Kotlin, SI units.
//
// AUDIT OF THE UNITY SOURCE (InductionDemo.cs + InductionCore.cs):
//   * FRAME-RATE BUG. The EMF was computed as  -N (Phi1 - Phi0) / Dt * (deltaTime / Dt)  with a
//     fixed Dt = 1/120 s. The finite difference must be divided by the real step deltaTime; this
//     divides by Dt and then MULTIPLIES by deltaTime/Dt, i.e. EMF_shown = EMF_true * (deltaTime/Dt)^2.
//     At the headset's 72 Hz the needle read 2.78x too high, at 90 Hz 1.78x, and only at exactly
//     120 Hz was it right — the same plunge gave a different "EMF" on every device.
//   * The coil was DRAWN as 5 rings spread over 20 cm, but the flux was computed for ONE loop at
//     its centre (times N): the long coil's flux linkage, which keeps rising while the magnet is
//     inside, was not modelled. Here every turn is integrated over the coil length.
//   * The flux was m/(d^2+R^2)^{3/2} in arbitrary units (right shape far away, by reciprocity), with
//     the magnet a point dipole even while inside the coil. Here the magnet is a uniformly
//     magnetised cylinder (its exact equivalent: a solenoid sheet current K = M = Br/mu0), and the
//     flux through each turn is an exact coaxial-loop mutual inductance (elliptic integrals).
//   * The needle was a massless pointer smoothed by a per-frame lerp (0.35/frame, so its lag also
//     depended on the frame rate). Here it is a damped torsion oscillator (period, damping ratio).
//   * The "current arrow" was a straight line drawn ALONG the coil axis — current in a coil
//     circulates around the axis; the demo now moves charge along the actual winding.
//   * The magnet had no N/S marking, so the Lenz direction could not be read off the scene.
//
// SIGN CONVENTION: the coil axis is +x. Flux linkage Lambda > 0 means field through the coil along
// +x. EMF > 0 drives current right-handed about +x (y toward z). EMF = -dLambda/dt (Lenz).

package com.bytedance.physicslab.solar.core.faraday

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sqrt

object Em {
    const val MU0 = 1.25663706212e-6        // T m / A
    const val RHO_CU = 1.68e-8              // ohm m at 20 C
}

/** Complete elliptic integrals by the arithmetic-geometric mean (A&S 17.6), modulus k. */
object Elliptic {
    fun K(k: Double): Double {
        var a = 1.0; var b = sqrt(1 - k * k)
        repeat(40) { if (abs(a - b) < 1e-15 * a) return PI / (2 * a); val an = (a + b) / 2; b = sqrt(a * b); a = an }
        return PI / (2 * a)
    }

    fun E(k: Double): Double {
        var a = 1.0; var b = sqrt(1 - k * k); var c = k
        var sum = c * c / 2; var pow = 0.5
        repeat(40) {
            if (abs(c) < 1e-15) return PI / (2 * a) * (1 - sum)
            val an = (a + b) / 2; c = (a - b) / 2; b = sqrt(a * b); a = an
            pow *= 2; sum += pow * c * c
        }
        return PI / (2 * a) * (1 - sum)
    }
}

object Coax {
    /** Mutual inductance of two coaxial circular loops, radii a and b, axial separation d (Maxwell). */
    fun mutual(a: Double, b: Double, d: Double): Double {
        val k2 = 4 * a * b / ((a + b) * (a + b) + d * d)
        val k = sqrt(k2)
        return Em.MU0 * sqrt(a * b) * ((2 / k - k) * Elliptic.K(k) - 2 / k * Elliptic.E(k))
    }

    /** Field (B_rho, B_z) of a loop of radius a carrying current i, at (rho, z) from its centre. */
    fun loopField(a: Double, i: Double, rho: Double, z: Double): DoubleArray {
        if (rho < 1e-9 * a) return doubleArrayOf(0.0, Em.MU0 * i * a * a / (2 * Math.pow(a * a + z * z, 1.5)))
        val q = (a + rho) * (a + rho) + z * z
        val k = sqrt(4 * a * rho / q)
        val kk = Elliptic.K(k); val ee = Elliptic.E(k)
        val den = (a - rho) * (a - rho) + z * z
        val c = Em.MU0 * i / (2 * PI * sqrt(q))
        val bz = c * (kk + (a * a - rho * rho - z * z) / den * ee)
        val br = c * z / rho * (-kk + (a * a + rho * rho + z * z) / den * ee)
        return doubleArrayOf(br, bz)
    }
}

/**
 * A uniformly magnetised cylinder (radius [a], length [len], remanence [br] tesla), moment along
 * +x. Exactly equivalent to a solenoid sheet of K = M = br/mu0 amperes per metre of length.
 */
class BarMagnet(val a: Double, val len: Double, val br: Double) {
    val magnetisation get() = br / Em.MU0
    /** Dipole moment m = M V (A m^2). */
    val moment get() = magnetisation * PI * a * a * len
    private val nq = 48     // Simpson intervals along the magnet

    /** Flux through a coaxial loop of radius r whose plane is z from the magnet centre (+x side +). */
    fun fluxThroughLoop(r: Double, z: Double): Double {
        val h = len / nq
        var s = 0.0
        for (j in 0..nq) {
            val zz = -len / 2 + j * h
            val w = if (j == 0 || j == nq) 1.0 else if (j % 2 == 1) 4.0 else 2.0
            s += w * Coax.mutual(a, r, z - zz)
        }
        return s * h / 3 * magnetisation
    }

    /** Field (B_rho, B_z) at (rho, z) relative to the magnet centre, z along the moment. */
    fun field(rho: Double, z: Double): DoubleArray {
        val h = len / nq
        var br = 0.0; var bz = 0.0
        for (j in 0..nq) {
            val zz = -len / 2 + j * h
            val w = if (j == 0 || j == nq) 1.0 else if (j % 2 == 1) 4.0 else 2.0
            val f = Coax.loopField(a, magnetisation * h / 3 * w, rho, z - zz)
            br += f[0]; bz += f[1]
        }
        return doubleArrayOf(br, bz)
    }

    /** Point-dipole flux through a loop, for the far-field check: mu0 m r^2 / (2 (r^2+z^2)^{3/2}). */
    fun dipoleFlux(r: Double, z: Double) = Em.MU0 * moment * r * r / (2 * Math.pow(r * r + z * z, 1.5))
}

/** A single-layer coil: [turns] over [len], radius [r], copper wire of diameter [wire]. */
class Coil(val r: Double, val len: Double, val turns: Int, val wire: Double = 0.25e-3) {
    val resistance get() = Em.RHO_CU * turns * 2 * PI * r / (PI * wire * wire / 4)
    /** Long-solenoid inductance with Nagaoka-ish end correction (Wheeler): good to ~1 %. */
    val inductance get() = Em.MU0 * turns * turns * PI * r * r / (len + 0.9 * r)
}

/**
 * Magnet + coil. [linkage] (Wb-turns) is tabulated once over magnet-centre positions x (relative
 * to the coil centre, along +x), so the per-frame cost is an interpolation.
 */
class Induction(val magnet: BarMagnet, val coil: Coil, val range: Double = 0.30, val n: Int = 401) {
    private val xs = DoubleArray(n) { -range + 2 * range * it / (n - 1) }
    private val lam: DoubleArray
    private val dlam: DoubleArray

    init {
        // flux linkage = sum over turns; turns spread uniformly over the coil length (32-pt Simpson)
        val per = DoubleArray(n)
        val m = 32
        for (i in 0 until n) {
            var s = 0.0
            for (j in 0..m) {
                val xc = -coil.len / 2 + coil.len * j / m
                val w = if (j == 0 || j == m) 1.0 else if (j % 2 == 1) 4.0 else 2.0
                s += w * magnet.fluxThroughLoop(coil.r, xc - xs[i])
            }
            per[i] = s / (3.0 * m)            // mean flux per turn
        }
        lam = DoubleArray(n) { per[it] }
        val h = xs[1] - xs[0]
        dlam = DoubleArray(n) { i ->
            when (i) { 0 -> (lam[1] - lam[0]) / h; n - 1 -> (lam[n - 1] - lam[n - 2]) / h; else -> (lam[i + 1] - lam[i - 1]) / (2 * h) }
        }
    }

    private fun interp(t: DoubleArray, x: Double): Double {
        val f = ((x + range) / (2 * range) * (n - 1)).coerceIn(0.0, (n - 1).toDouble())
        val i = f.toInt().coerceAtMost(n - 2); val w = f - i
        return t[i] + (t[i + 1] - t[i]) * w
    }

    /** Mean flux through one turn (Wb) for the magnet centre at x, moment sign [pole] (+1 = N toward +x). */
    fun fluxPerTurn(x: Double, pole: Int = 1) = pole * interp(lam, x)
    /** Total flux linkage N*Phi (Wb). */
    fun linkage(x: Double, pole: Int = 1) = coil.turns * fluxPerTurn(x, pole)
    fun dLinkageDx(x: Double, pole: Int = 1) = pole * coil.turns * interp(dlam, x)
    /** d(flux per turn)/dx (Wb/m) — the table is per turn, so any turn count can reuse it. */
    fun dFluxPerTurnDx(x: Double, pole: Int = 1) = pole * interp(dlam, x)
    /** EMF of [turns] turns wound like this coil: -N dPhi/dx v. */
    fun emfN(turns: Int, x: Double, v: Double, pole: Int = 1) = -turns * dFluxPerTurnDx(x, pole) * v
    /** EMF = -dLambda/dt = -(dLambda/dx) v. */
    fun emf(x: Double, v: Double, pole: Int = 1) = -dLinkageDx(x, pole) * v
    /** Force on the magnet from the coil current i: F = i dLambda/dx (always opposes v when i = emf/R). */
    fun forceOnMagnet(x: Double, i: Double, pole: Int = 1) = i * dLinkageDx(x, pole)
}

/**
 * A centre-zero moving-coil galvanometer: theta'' + 2 zeta w0 theta' + w0^2 theta = w0^2 k i,
 * k = fullScaleRad / fullScaleA. Mechanical stops at +-[stopRad] (a pegged needle bounces).
 * Integrated exactly for a current held constant over the step (no frame-rate dependence).
 */
class Galvanometer(val fullScaleA: Double, val fullScaleRad: Double, val period: Double = 0.9, val zeta: Double = 0.55,
                   val stopRad: Double = fullScaleRad * 1.12) {
    var theta = 0.0; private set
    var omega = 0.0; private set
    private val w0 = 2 * PI / period

    fun target(i: Double) = i / fullScaleA * fullScaleRad

    fun step(i: Double, dt: Double) {
        // exact solution of the underdamped oscillator about the static deflection th
        val th = target(i)
        val wd = w0 * sqrt(1 - zeta * zeta)
        val x0 = theta - th; val v0 = omega
        val e = exp(-zeta * w0 * dt)
        val c = kotlin.math.cos(wd * dt); val s = kotlin.math.sin(wd * dt)
        val b = (v0 + zeta * w0 * x0) / wd
        val x = e * (x0 * c + b * s)
        val v = e * (b * wd * c - x0 * wd * s - zeta * w0 * (x0 * c + b * s))
        theta = th + x; omega = v
        if (theta > stopRad) { theta = stopRad; omega = -0.3 * abs(omega) }
        if (theta < -stopRad) { theta = -stopRad; omega = 0.3 * abs(omega) }
    }

    fun reset() { theta = 0.0; omega = 0.0 }
}

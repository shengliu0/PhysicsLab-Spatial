// RutherfordCore — alpha scattering on a point nucleus (Rutherford 1911), pure Kotlin.
//
// The whole lesson is one length: the distance of closest approach in a head-on collision,
//     d = z Z e^2 / (4 pi eps0 E)          (5.5 MeV alpha on gold: 41.4 fm)
// Everything else follows from it:
//     deflection        theta = 2 atan( d / 2b )                    (b = impact parameter)
//     cross section     dsigma/dOmega = (d/4)^2 / sin^4(theta/2)
//     beyond theta      P(> theta) = n t pi (d/2)^2 cot^2(theta/2)  (thin foil, single scattering)
// so backscatter (theta > 90 deg) happens with probability n t pi (d/2)^2: for 1 um of gold at
// 5.5 MeV that is 7.9e-5, about one alpha in 12 600 — the "15-inch shell bouncing off tissue paper".
//
// The orbit is the exact repulsive Kepler problem (a hyperbola with the nucleus at the OUTER focus),
// given here in closed form with its time law, and cross-checked against a direct RK4 integration
// of Newton's equation (tests: energy and angular momentum conserved, same deflection).
//
// Thomson's plum pudding (1904) spreads the positive charge over the whole atom: a single atom can
// deflect an alpha by at most ~d / R_atom ~ 1e-4 rad, so through a foil the alpha only random-walks
// by ~1 deg (multiple scattering, Gaussian). The chance of > 90 deg is exp(-(90/sigma)^2 / 2) ~ 1e-1000.
// That contrast — not the formula — is what Geiger and Marsden's 1909 backscatter observation
// settled (Rutherford, Phil. Mag. 21, 669 (1911), sections 1-3).
//
// Simplifications (stated, not hidden): fixed nucleus (lab = CM frame; the recoil correction is
// m_alpha/M = 2 % for gold, 15 % for aluminium), no energy loss in the foil, point charges
// (valid while d > R_nucleus + R_alpha — aluminium at 8 MeV gets inside, see [Foil.contactFm]).

package com.bytedance.physicslab.solar.core.rutherford

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.cosh
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sinh
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

object Rutherford {
    /** e^2/(4 pi eps0) = alpha hbar c in MeV fm (CODATA 2018: hbar c = 197.3269804 MeV fm, 1/alpha = 137.035999084). */
    const val KE2_MEV_FM = 197.3269804 / 137.035999084
    const val Z_ALPHA = 2
    const val N_A = 6.02214076e23                 // mol^-1 (SI 2019, exact)
    const val A0_FM = 52917.721090                // Bohr radius, fm (CODATA 2018)
    const val R0_FM = 1.2                         // nuclear radius R = r0 A^(1/3) (Krane, Introductory Nuclear Physics, sec. 3.1)
    const val FOIL_T_M = 1.0e-6                   // the demo foil: 1 um (Geiger & Marsden used 0.2-0.9 um gold leaf)
    const val THETA_CUT = 5.0 * PI / 180          // below this the multiple-scattering core rules; above, single scattering

    /**
     * Foil metals. Densities and molar masses: CRC Handbook of Chemistry and Physics (97th ed.), 20 C.
     * n = rho N_A / M — gold: 19.32 g/cm^3, 196.967 g/mol -> 5.907e28 atoms/m^3.
     */
    class Foil(val symbol: String, val zh: String, val en: String, val z: Int, val massNumber: Int,
               val molarGPerMol: Double, val rhoGPerCm3: Double) {
        /** Atoms per m^3. */
        val n: Double = rhoGPerCm3 * 1e6 / molarGPerMol * N_A
        val nuclearRadiusFm: Double = R0_FM * cbrt(massNumber.toDouble())
        /** Centre distance at which alpha and nucleus touch (same r0 for both): below it the strong force acts. */
        val contactFm: Double = R0_FM * (cbrt(massNumber.toDouble()) + cbrt(4.0))
        /** Wigner-Seitz radius (3 / 4 pi n)^(1/3): the space one atom gets in the metal. */
        val atomRadiusFm: Double = Math.cbrt(3.0 / (4 * PI * n)) * 1e15
        /** Thomas-Fermi screening radius 0.8853 a0 Z^(-1/3): beyond it the nucleus is hidden by its electrons. */
        val screeningFm: Double = 0.8853 * A0_FM / cbrt(z.toDouble())
    }

    val FOILS = listOf(
        Foil("Au", "金", "Gold", 79, 197, 196.96657, 19.32),
        Foil("Ag", "银", "Silver", 47, 107, 107.8682, 10.49),
        Foil("Al", "铝", "Aluminium", 13, 27, 26.981539, 2.70),
    )

    /** Distance of closest approach (fm) for a head-on alpha of kinetic energy [eMeV] on charge [z]. */
    fun closestApproachFm(z: Int, eMeV: Double) = Z_ALPHA * z * KE2_MEV_FM / eMeV

    /** Deflection angle (rad) for impact parameter [b] (same unit as [d]); b = 0 is straight back. */
    fun deflection(b: Double, d: Double): Double = 2 * atan2(d, 2 * abs(b))

    /** The impact parameter that gives deflection [theta]: b = (d/2) cot(theta/2). */
    fun impactParameter(theta: Double, d: Double) = d / 2 / tan(theta / 2)

    /** Rutherford's differential cross section (units of d^2 per steradian). */
    fun dSigmaDOmega(theta: Double, d: Double): Double { val s = sin(theta / 2); return (d / 4) * (d / 4) / (s * s * s * s) }

    /** Areal density n t (atoms / m^2) of the demo foil. */
    fun arealDensity(f: Foil, tM: Double = FOIL_T_M) = f.n * tM

    /** Probability that one alpha is scattered beyond [theta] (single scattering): n t pi b(theta)^2. */
    fun fractionBeyond(theta: Double, dFm: Double, nt: Double): Double {
        if (theta <= 0.0) return 1.0
        val b = impactParameter(theta, dFm) * 1e-15
        return nt * PI * b * b
    }

    fun fractionBetween(t1: Double, t2: Double, dFm: Double, nt: Double) = fractionBeyond(t1, dFm, nt) - fractionBeyond(t2, dFm, nt)

    /**
     * Multiple-scattering width: rms PROJECTED angle (rad) after many small Coulomb deflections.
     * Small-angle Rutherford, theta ~ d/b, summed over impact parameters from b1 (where the alpha meets
     * on average one such nucleus: n t pi b1^2 = 1 — closer encounters are the single-scattering tail)
     * out to the screening radius a_TF:  <Theta^2> = 2 pi n t d^2 ln(a_TF / b1), sigma^2 = <Theta^2>/2
     * (the classical Bohr/Williams estimate, cf. Jackson, Classical Electrodynamics 3rd ed., sec. 13.7).
     * 1 um gold, 5.5 MeV: 1.27 deg. Geiger (1910) measured a most probable angle of 0.87 deg for
     * 4e-5 cm of gold (quoted by Rutherford 1911); this formula gives 0.67 deg there — same scale.
     */
    fun multipleScatteringSigma(f: Foil, dFm: Double, tM: Double = FOIL_T_M): Double {
        val nt = arealDensity(f, tM)
        val b1Fm = 1.0 / sqrt(PI * nt) * 1e15
        val lnArg = max(ln(f.screeningFm / b1Fm), 1.0)
        val d = dFm * 1e-15
        return sqrt(PI * nt * d * d * lnArg)
    }

    /** Thomson model: Gaussian (Rayleigh in space angle) multiple scattering only: log10 P(> theta). */
    fun thomsonLog10Beyond(theta: Double, sigma: Double) = -theta * theta / (2 * sigma * sigma) / ln(10.0)

    fun thomsonFractionBetween(t1: Double, t2: Double, sigma: Double) =
        exp(-t1 * t1 / (2 * sigma * sigma)) - exp(-t2 * t2 / (2 * sigma * sigma))

    /**
     * Deflection by ONE Thomson atom (uniform sphere of charge Ze, radius [rAtom]) for impact parameter
     * [b] < rAtom, impulse approximation: theta = d b sqrt(R^2 - b^2) / R^3 (all lengths in one unit).
     * Maximum d / 2R at b = R/sqrt2: for gold, 1.3e-4 rad = 0.007 deg.
     */
    fun thomsonAtomDeflection(b: Double, d: Double, rAtom: Double): Double {
        val bb = min(abs(b), rAtom)
        return d * bb * sqrt(rAtom * rAtom - bb * bb) / (rAtom * rAtom * rAtom)
    }
}

/**
 * The exact repulsive Coulomb orbit in units where d = 1 and the speed at infinity v_inf = 1
 * (so the semi-major axis is a = d/2 and the time unit is d / v_inf). The alpha comes in along +x
 * on the line y = +[b] and is pushed away (deflected toward +y) by the nucleus at the origin.
 *
 * Hyperbolic anomaly F (F < 0 incoming, F = 0 periapsis, F > 0 outgoing); perifocal coordinates
 *     X = -a (e + cosh F),  Y = b sinh F,  r = a (e cosh F + 1),  t = a (e sinh F + F)
 * with e = sqrt(1 + (b/a)^2) = 1 / sin(theta/2), rotated by -beta (cos beta = 1/e) so the incoming
 * asymptote is the line y = b. The asymptotes cross at (-a, b): the "kink" of the straight-line picture.
 */
class CoulombHyperbola(val b: Double) {
    val a = 0.5
    val e = sqrt(1 + (b / a) * (b / a))
    val theta = Rutherford.deflection(b, 1.0)
    val rMin = a * (e + 1)
    private val cb = 1 / e
    private val sb = b / (a * e)

    fun x(f: Double) = -a * (e + cosh(f)) * cb + b * sinh(f) * sb
    fun y(f: Double) = a * (e + cosh(f)) * sb + b * sinh(f) * cb
    fun r(f: Double) = a * (e * cosh(f) + 1)
    fun time(f: Double) = a * (e * sinh(f) + f)

    /** Velocity (v_inf = 1 units) at anomaly [f], written into [out] = (vx, vy). */
    fun velocity(f: Double, out: DoubleArray) {
        val dX = -a * sinh(f); val dY = b * cosh(f)
        val dt = a * (e * cosh(f) + 1)
        out[0] = (dX * cb + dY * sb) / dt; out[1] = (-dX * sb + dY * cb) / dt
    }

    /** Incoming-branch anomaly (F <= 0) where the alpha crosses the plane x = [xPlane] (x is monotonic there). */
    fun fAtX(xPlane: Double): Double {
        if (x(0.0) <= xPlane) return 0.0
        var lo = -1.0
        while (x(lo) > xPlane && lo > -80) lo *= 2
        var hi = 0.0
        repeat(80) { val m = (lo + hi) / 2; if (x(m) > xPlane) hi = m else lo = m }
        return (lo + hi) / 2
    }

    /** First outgoing anomaly (F >= 0) where [leave] becomes true (monotone after a coarse scan). */
    fun fLeaving(leave: (Double) -> Boolean): Double {
        var f0 = 0.0
        val step = 0.02
        while (!leave(f0 + step) && f0 < 40) f0 += step
        var lo = f0; var hi = f0 + step
        repeat(50) { val m = (lo + hi) / 2; if (leave(m)) hi = m else lo = m }
        return (lo + hi) / 2
    }
}

/** Direct numerical integration of the same orbit (RK4, step h = eta r / v), to check [CoulombHyperbola]. */
object CoulombOrbit {
    /** mu = a v_inf^2 with a = d/2: in d = 1, v_inf = 1 units the repulsion is r'' = 0.5 r / |r|^3. */
    private const val MU = 0.5

    class Result(val x: Double, val y: Double, val vx: Double, val vy: Double, val t: Double,
                 val rMin: Double, val maxRelEnergyErr: Double, val maxRelLErr: Double, val steps: Int) {
        /** Direction of motion (rad from +x). Far away this is the deflection angle. */
        val heading get() = atan2(vy, vx)
    }

    private fun acc(x: Double, y: Double, out: DoubleArray) {
        val r2 = x * x + y * y; val r = sqrt(r2); val k = MU / (r2 * r)
        out[0] = k * x; out[1] = k * y
    }

    private fun energy(x: Double, y: Double, vx: Double, vy: Double) = 0.5 * (vx * vx + vy * vy) + MU / sqrt(x * x + y * y)

    /**
     * Integrate from (x, y, vx, vy) until [stop] says so or [tEnd] is reached (exactly: the last step
     * is shortened). Tracks the closest approach and the worst relative drift of E and L.
     */
    fun propagate(x0: Double, y0: Double, vx0: Double, vy0: Double, tEnd: Double = Double.MAX_VALUE,
                  eta: Double = 1e-3, stop: (Double, Double, Double) -> Boolean = { _, _, _ -> false }): Result {
        var x = x0; var y = y0; var vx = vx0; var vy = vy0; var t = 0.0
        val e0 = energy(x, y, vx, vy); val l0 = x * vy - y * vx
        var dE = 0.0; var dL = 0.0; var rMin = sqrt(x * x + y * y); var n = 0
        val a1 = DoubleArray(2); val a2 = DoubleArray(2); val a3 = DoubleArray(2); val a4 = DoubleArray(2)
        while (t < tEnd && n < 5_000_000) {
            val r = sqrt(x * x + y * y); val v = sqrt(vx * vx + vy * vy)
            var h = eta * r / max(v, 1e-9)
            if (t + h > tEnd) h = tEnd - t
            acc(x, y, a1)
            val x2 = x + 0.5 * h * vx; val y2 = y + 0.5 * h * vy; val vx2 = vx + 0.5 * h * a1[0]; val vy2 = vy + 0.5 * h * a1[1]
            acc(x2, y2, a2)
            val x3 = x + 0.5 * h * vx2; val y3 = y + 0.5 * h * vy2; val vx3 = vx + 0.5 * h * a2[0]; val vy3 = vy + 0.5 * h * a2[1]
            acc(x3, y3, a3)
            val x4 = x + h * vx3; val y4 = y + h * vy3; val vx4 = vx + h * a3[0]; val vy4 = vy + h * a3[1]
            acc(x4, y4, a4)
            x += h / 6 * (vx + 2 * vx2 + 2 * vx3 + vx4); y += h / 6 * (vy + 2 * vy2 + 2 * vy3 + vy4)
            vx += h / 6 * (a1[0] + 2 * a2[0] + 2 * a3[0] + a4[0]); vy += h / 6 * (a1[1] + 2 * a2[1] + 2 * a3[1] + a4[1])
            t += h; n++
            rMin = min(rMin, sqrt(x * x + y * y))
            dE = max(dE, abs(energy(x, y, vx, vy) / e0 - 1)); dL = max(dL, abs((x * vy - y * vx) / l0 - 1))
            if (stop(x, y, t)) break
        }
        return Result(x, y, vx, vy, t, rMin, dE, dL, n)
    }

    /**
     * Fire an alpha from far away (distance [r0] in d units) along +x at impact parameter [b] with the
     * exact energy of a v_inf = 1 particle; integrate until it is [r0] away again. heading = deflection.
     */
    fun scatter(b: Double, r0: Double = 1e5, eta: Double = 1e-3): Result {
        val x0 = -sqrt(r0 * r0 - b * b)
        val v0 = sqrt(1 - 2 * MU / r0)
        return propagate(x0, b, v0, 0.0, eta = eta) { x, y, t -> t > r0 && x * x + y * y > r0 * r0 }
    }
}

/**
 * The Geiger-Marsden counter: fires alphas through the foil and histograms the scattering angle
 * (0.5 deg bins, folded: every azimuth counts — the ring of screen + microscope reads it).
 *
 * Per batch of n alphas (any n, fractional ok) it draws Poisson numbers instead of looping over every
 * alpha, so 10^5 alpha/s costs microseconds:
 *   Rutherford tail: N ~ Poisson(n P(> THETA_CUT)); each gets an impact parameter uniform over the
 *     disc b < b(THETA_CUT) (b = b_max sqrt(u)) and theta = 2 atan(d/2b) — the physical sampling,
 *     which the tests check reproduces 1/sin^4(theta/2) bin by bin;
 *   multiple-scattering core beyond the cut (both models): Rayleigh tail exp(-theta^2 / 2 sigma^2).
 * Alphas under the cut are only counted ([fired]); they are the bright straight-through spot.
 */
class AlphaCounter(private val seed: Int = 1911) {
    companion object {
        const val BINS = 360
        const val BIN_RAD = PI / BINS
    }

    val hist = LongArray(BINS)
    var fired = 0.0; private set
    var beyond90 = 0L; private set
    var events = 0L; private set
    private var rng = Random(seed)

    fun reset(reseed: Boolean = false) {
        hist.fill(0); fired = 0.0; beyond90 = 0; events = 0
        if (reseed) rng = Random(seed)
    }

    /** Fire [n] alphas. [onEvent] sees every alpha deflected beyond THETA_CUT (rad). */
    fun fire(n: Double, dFm: Double, nt: Double, sigma: Double, rutherford: Boolean, onEvent: ((Double) -> Unit)? = null) {
        if (n <= 0) return
        fired += n
        val cut = Rutherford.THETA_CUT
        if (rutherford) {
            val k = poisson(n * Rutherford.fractionBeyond(cut, dFm, nt))
            val bMax = Rutherford.impactParameter(cut, dFm)
            for (i in 0 until k) record(Rutherford.deflection(bMax * sqrt(1.0 - rng.nextDouble()), dFm), onEvent)
        }
        val pCore = exp(-cut * cut / (2 * sigma * sigma))
        if (pCore > 1e-300) {
            val k = poisson(n * pCore)
            for (i in 0 until k) record(sqrt(cut * cut - 2 * sigma * sigma * ln(1.0 - rng.nextDouble())), onEvent)
        }
    }

    private fun record(theta: Double, onEvent: ((Double) -> Unit)?) {
        val th = theta.coerceIn(0.0, PI)
        hist[min(BINS - 1, floor(th / BIN_RAD).toInt())]++
        events++
        if (th > PI / 2) beyond90++
        onEvent?.invoke(th)
    }

    /** Counts with theta in [t1Deg, t2Deg) — both edges are rounded to the 0.5 deg bin grid. */
    fun countBetween(t1Deg: Double, t2Deg: Double): Long {
        val i0 = (t1Deg * 2).roundToLong().toInt().coerceIn(0, BINS); val i1 = (t2Deg * 2).roundToLong().toInt().coerceIn(0, BINS)
        var s = 0L
        for (i in i0 until i1) s += hist[i]
        return s
    }

    /** A core (multiple-scattering) angle below the cut, for drawing the straight-through cluster. */
    fun coreAngle(sigma: Double): Double {
        val cut = Rutherford.THETA_CUT
        val pIn = 1 - exp(-cut * cut / (2 * sigma * sigma))
        return sqrt(-2 * sigma * sigma * ln(1 - rng.nextDouble() * pIn))
    }

    fun uniform() = rng.nextDouble()

    /** Poisson deviate: Knuth below mean 40, rounded normal above (error < 1 %, fine for counting). */
    fun poisson(mean: Double): Int {
        if (mean <= 0) return 0
        if (mean < 40) {
            val l = exp(-mean); var k = 0; var p = rng.nextDouble()
            while (p > l) { k++; p *= rng.nextDouble() }
            return k
        }
        val u1 = 1.0 - rng.nextDouble(); val u2 = rng.nextDouble()
        val g = sqrt(-2 * ln(u1)) * cos(2 * PI * u2)
        return max(0.0, mean + sqrt(mean) * g + 0.5).toInt()
    }
}

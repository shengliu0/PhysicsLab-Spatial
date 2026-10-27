// MicroBox — the molecule window: [units] NO₂ units (NO₂ = 1, N₂O₄ = 2) flying in a glass box, with
// 2NO₂ → N₂O₄ and N₂O₄ → 2NO₂ happening one molecule at a time. Pure Kotlin.
//
// WHY THE STATISTICS ARE EXACT. Reaction EVENTS are drawn from the chemical master equation with the
// Gillespie algorithm (exact stochastic simulation):
//     association   propensity  c_f · n(n−1)/2     (n = free NO₂)
//     dissociation  propensity  c_r · m            (m = N₂O₄, including pairs already committed)
// with c_f / (2 c_r) = κ = Kp·P0/N  (the macroscopic mass-action ratio m/n² of the flask, in this
// box's N units). Detailed balance then gives the stationary law π(m+1)/π(m) = κ n(n−1)/(m+1) — the
// canonical-ensemble distribution of an ideal reacting gas of N units — whose mean equals the flask's
// Kp equilibrium up to a finite-size term of ~0.1 % at N = 100 ([MicroTheory.canonicalAlpha]).
// So the window converges to the same composition as the flask it magnifies, at the same rate
// (c_r = k_f/Kp, the flask's own rate constants), whatever the box geometry or frame rate.
//
// WHAT YOU SEE. Molecules move ballistically (ideal gas: they do not interact except by reacting) and
// bounce off the walls. When an association fires, the two free NO₂ closest to each other are steered
// into a collision — rotating so their N atoms meet — and become one N₂O₄ in the real planar geometry
// (seamless: the dimer's halves sit exactly where the two NO₂ arrived). When a dissociation fires, a
// random N₂O₄ breaks its long, weak N–N bond and the halves fly apart along it. Both leave a flash.
// The lid can move (the syringe flask): same molecules, smaller box.

package com.bytedance.physicslab.solar.core.equilibrium

import java.util.Random
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Molecular structures (Å, degrees): NO₂ r(N–O) 1.197, ∠ONO 134.1 (microwave spectroscopy);
 * N₂O₄ planar D2h, r(N–N) 1.782, r(N–O) 1.190, ∠ONO 135.4 (gas electron diffraction, McClelland
 * et al., JACS 105, 3789, 1983). Frames: plane xy; NO₂ has N toward −x and both O toward +x, origin at
 * its centre of mass; N₂O₄ has its N–N bond along x, origin at its centre.
 */
object MolGeom {
    const val M_N = 14.007
    const val M_O = 15.999
    const val NO2_R = 1.197; const val NO2_ANGLE = 134.1
    const val N2O4_NN = 1.782; const val N2O4_R = 1.190; const val N2O4_ANGLE = 135.4

    /** NO₂ atoms (N, O, O) as xyz triples, Å, centre-of-mass frame. */
    fun no2Atoms(): DoubleArray {
        val h = Math.toRadians(NO2_ANGLE / 2)
        val ox = NO2_R * cos(h); val oy = NO2_R * sin(h)
        val com = 2 * M_O * ox / (M_N + 2 * M_O)
        return doubleArrayOf(-com, 0.0, 0.0, ox - com, oy, 0.0, ox - com, -oy, 0.0)
    }

    /** N₂O₄ atoms (N+, N−, O+, O+, O−, O−) as xyz triples, Å. */
    fun n2o4Atoms(): DoubleArray {
        val h = Math.toRadians(N2O4_ANGLE / 2)
        val n = N2O4_NN / 2; val ox = n + N2O4_R * cos(h); val oy = N2O4_R * sin(h)
        return doubleArrayOf(n, 0.0, 0.0, -n, 0.0, 0.0, ox, oy, 0.0, ox, -oy, 0.0, -ox, oy, 0.0, -ox, -oy, 0.0)
    }

    /** Distance of each NO₂ half's centre of mass from the N₂O₄ centre, Å (≈ 1.205). */
    val HALF_OFFSET: Double by lazy {
        val a = n2o4Atoms()
        (M_N * a[0] + M_O * a[6] + M_O * a[9]) / (M_N + 2 * M_O)
    }
}

/** Closed forms for the small (canonical) reacting system. */
object MicroTheory {
    /**
     * Exact stationary mean of α = n/N for N units with mass-action ratio κ (= c_f / 2c_r), from the
     * detailed-balance recursion π(m+1)/π(m) = κ n(n−1)/(m+1), n = N − 2m.
     */
    fun canonicalAlpha(units: Int, kappa: Double): Double {
        val mMax = units / 2
        val lw = DoubleArray(mMax + 1)
        for (m in 0 until mMax) {
            val n = units - 2 * m
            val r = kappa * n * (n - 1) / (m + 1)
            lw[m + 1] = if (r > 0) lw[m] + ln(r) else Double.NEGATIVE_INFINITY
        }
        val mx = lw.maxOrNull() ?: 0.0
        var z = 0.0; var s = 0.0
        for (m in 0..mMax) { val w = exp(lw[m] - mx); z += w; s += w * (units - 2 * m) }
        return s / z / units
    }
}

class MicroBox(val units: Int, seed: Long = 1884L) {
    /** Metres per Å for the drawn molecules (the demo uses the same value for its meshes). */
    var angstrom = 0.011
    /** Inner half-size of the box (m) and the lid height (box-local y, ≤ half). */
    var half = 0.115
    var top = 0.115; private set
    /** Centre margins from the walls for NO₂ and N₂O₄ (≈ their drawn radii). */
    var marginA = 0.020
    var marginB = 0.032
    /** rms speed of NO₂ at 298 K on screen (m/s); scales √T, N₂O₄ is √2 slower (twice the mass). */
    var vRms298 = 0.16

    private val rng = Random(seed)
    private var tK = 298.15
    private var sigA = vRms298 / sqrt(3.0)

    // ---------------------------------------------------------------- NO₂ slots
    val capA = units
    val aAlive = BooleanArray(capA)
    val ax = DoubleArray(capA * 3); val av = DoubleArray(capA * 3)
    val aq = DoubleArray(capA * 4); val aw = DoubleArray(capA * 3)
    private val partner = IntArray(capA) { -1 }
    private val steerLeft = DoubleArray(capA); private val steerTotal = DoubleArray(capA)
    private val q0 = DoubleArray(capA * 4); private val qT = DoubleArray(capA * 4)
    private val pairC = DoubleArray(capA * 3); private val pairV = DoubleArray(capA * 3); private val pairQ = DoubleArray(capA * 4)

    // ---------------------------------------------------------------- N₂O₄ slots
    val capB = units / 2
    val bAlive = BooleanArray(capB)
    val bx = DoubleArray(capB * 3); val bv = DoubleArray(capB * 3)
    val bq = DoubleArray(capB * 4); val bw = DoubleArray(capB * 3)

    /** Free (not yet committed) NO₂ = the chemistry's n. */
    var nFree = 0; private set
    /** NO₂ pairs steering toward a collision (already N₂O₄ for the chemistry). */
    var nPairs = 0; private set
    /** N₂O₄ drawn. */
    var nB = 0; private set
    /** The chemistry's counts: n(NO₂) and m(N₂O₄); n + 2m = units always. */
    val nChem get() = nFree
    val mChem get() = nB + nPairs
    /** NO₂ / N₂O₄ as drawn right now (pairs still look like two NO₂ until they meet). */
    val nDrawnNO2 get() = nFree + 2 * nPairs

    // ---------------------------------------------------------------- flashes
    val flashCap = 16
    val fx = DoubleArray(flashCap * 3)
    val fAge = DoubleArray(flashCap) { 1e9 }
    /** 0 = association (bond formed, exothermic), 1 = dissociation (bond broken, endothermic). */
    val fKind = IntArray(flashCap)
    private var fHead = 0
    val flashLife = 0.45

    // ---------------------------------------------------------------- statistics
    var simTime = 0.0; private set
    /** ∫ n(NO₂) dt (exact, piecewise between events) — time average = this / simTime. */
    var nTimeIntegral = 0.0; private set
    var assocEvents = 0L; private set
    var dissocEvents = 0L; private set
    /** Exponentially-smoothed event rates (events per simulated second, ~4 s memory). */
    var assocRate = 0.0; private set
    var dissocRate = 0.0; private set

    fun timeAverageAlpha() = if (simTime > 0) nTimeIntegral / simTime / units else nFree.toDouble() / units

    // ================================================================ setup

    /** Fresh box: composition α (NO₂ fraction of units), temperature [tKelvin], random positions. */
    fun reset(alpha: Double, tKelvin: Double, expectedRate: Double = 0.0) {
        aAlive.fill(false); bAlive.fill(false); partner.fill(-1)
        nFree = 0; nPairs = 0; nB = 0
        fAge.fill(1e9); fHead = 0
        simTime = 0.0; nTimeIntegral = 0.0; assocEvents = 0; dissocEvents = 0
        assocRate = expectedRate; dissocRate = expectedRate
        tK = tKelvin; sigA = vRms298 / sqrt(3.0) * sqrt(tK / 298.15)
        val m = Math.round(units * (1 - alpha.coerceIn(0.0, 1.0)) / 2).toInt().coerceIn(0, capB)
        val n = units - 2 * m
        repeat(n) { spawnA(randIn(marginA, 0), randIn(marginA, 1), randIn(marginA, 2), randomQ(), null) }
        repeat(m) { spawnB(randIn(marginB, 0), randIn(marginB, 1), randIn(marginB, 2), randomQ(), null) }
    }

    /** New gas temperature: speeds and spins rescale as √T. */
    fun setTemperature(tKelvin: Double) {
        if (abs(tKelvin - tK) < 1e-9) return
        val f = sqrt(tKelvin / tK)
        for (i in 0 until capA) if (aAlive[i] && partner[i] < 0) for (k in 0 until 3) { av[i * 3 + k] *= f; aw[i * 3 + k] *= f }
        for (i in 0 until capB) if (bAlive[i]) for (k in 0 until 3) { bv[i * 3 + k] *= f; bw[i * 3 + k] *= f }
        tK = tKelvin; sigA = vRms298 / sqrt(3.0) * sqrt(tK / 298.15)
    }

    /** Move the lid (box-local y). Molecules above it are pushed down. */
    fun setTop(y: Double) {
        top = y.coerceIn(-half + 2 * marginB + 0.01, half)
        for (i in 0 until capA) if (aAlive[i] && partner[i] < 0 && ax[i * 3 + 1] > top - marginA) { ax[i * 3 + 1] = top - marginA; av[i * 3 + 1] = -abs(av[i * 3 + 1]) }
        for (i in 0 until capB) if (bAlive[i] && bx[i * 3 + 1] > top - marginB) { bx[i * 3 + 1] = top - marginB; bv[i * 3 + 1] = -abs(bv[i * 3 + 1]) }
    }

    // ================================================================ time step

    /**
     * Advance [dt] simulated seconds. [kappa] = Kp·P0/N of the flask shown, [kr] = its dissociation rate
     * constant k_r (1/s), so c_r = kr and c_f = 2κ·kr.
     */
    fun step(dt: Double, kappa: Double, kr: Double) {
        if (dt <= 0.0) return
        move(dt)
        var left = dt
        var na = 0; var nd = 0
        while (true) {
            val n = nFree; val m = nB + nPairs
            val af = kr * kappa * n * (n - 1)          // c_f n(n−1)/2 with c_f = 2κ k_r
            val ar = kr * m
            val a0 = af + ar
            if (a0 <= 0.0) { nTimeIntegral += n * left; break }
            val tau = -ln(1.0 - rng.nextDouble()) / a0
            if (tau >= left) { nTimeIntegral += n * left; break }
            nTimeIntegral += n * tau; left -= tau
            if (rng.nextDouble() * a0 < af) { associate(); na++ } else { dissociate(); nd++ }
        }
        simTime += dt
        val keep = exp(-dt / 4.0)
        assocRate = assocRate * keep + na / 4.0
        dissocRate = dissocRate * keep + nd / 4.0
    }

    private fun move(dt: Double) {
        for (k in 0 until flashCap) fAge[k] += dt
        val loA = -half + marginA; val hiA = half - marginA; val topA = top - marginA
        for (i in 0 until capA) {
            if (!aAlive[i]) continue
            val p = i * 3
            ax[p] += av[p] * dt; ax[p + 1] += av[p + 1] * dt; ax[p + 2] += av[p + 2] * dt
            if (partner[i] >= 0) {
                steerLeft[i] -= dt
                val f = (1 - steerLeft[i] / steerTotal[i]).coerceIn(0.0, 1.0)
                slerp(q0, i * 4, qT, i * 4, f, aq, i * 4)
            } else {
                wall(ax, av, p, loA, hiA); wall(ax, av, p + 1, loA, topA); wall(ax, av, p + 2, loA, hiA)
                integrateQ(aq, i * 4, aw, p, dt)
            }
        }
        val loB = -half + marginB; val hiB = half - marginB; val topB = top - marginB
        for (i in 0 until capB) {
            if (!bAlive[i]) continue
            val p = i * 3
            bx[p] += bv[p] * dt; bx[p + 1] += bv[p + 1] * dt; bx[p + 2] += bv[p + 2] * dt
            wall(bx, bv, p, loB, hiB); wall(bx, bv, p + 1, loB, topB); wall(bx, bv, p + 2, loB, hiB)
            integrateQ(bq, i * 4, bw, p, dt)
        }
        // pairs that have met become N₂O₄
        for (i in 0 until capA) {
            val j = partner[i]
            if (!aAlive[i] || j < i || steerLeft[i] > 0.0) continue
            aAlive[i] = false; aAlive[j] = false; partner[i] = -1; partner[j] = -1
            nPairs--
            val c = i * 3
            val x = pairC[c].coerceIn(loB, hiB); val y = pairC[c + 1].coerceIn(loB, maxOf(loB, topB)); val z = pairC[c + 2].coerceIn(loB, hiB)
            val s = spawnB(x, y, z, doubleArrayOf(pairQ[i * 4], pairQ[i * 4 + 1], pairQ[i * 4 + 2], pairQ[i * 4 + 3]),
                doubleArrayOf(pairV[c], pairV[c + 1], pairV[c + 2]))
            if (s >= 0) flash(x, y, z, 0)
        }
    }

    private fun wall(x: DoubleArray, v: DoubleArray, k: Int, lo: Double, hi: Double) {
        if (hi <= lo) { x[k] = (lo + hi) / 2; return }
        if (x[k] > hi) { x[k] = 2 * hi - x[k]; v[k] = -abs(v[k]) }
        if (x[k] < lo) { x[k] = 2 * lo - x[k]; v[k] = abs(v[k]) }
        x[k] = x[k].coerceIn(lo, hi)
    }

    // ================================================================ events

    /** 2NO₂ → N₂O₄: the closest free pair is steered into a collision. */
    private fun associate() {
        var bi = -1; var bj = -1; var best = Double.MAX_VALUE
        for (i in 0 until capA) {
            if (!aAlive[i] || partner[i] >= 0) continue
            val xi = ax[i * 3]; val yi = ax[i * 3 + 1]; val zi = ax[i * 3 + 2]
            for (j in i + 1 until capA) {
                if (!aAlive[j] || partner[j] >= 0) continue
                val dx = ax[j * 3] - xi; val dy = ax[j * 3 + 1] - yi; val dz = ax[j * 3 + 2] - zi
                val d2 = dx * dx + dy * dy + dz * dz
                if (d2 < best) { best = d2; bi = i; bj = j }
            }
        }
        if (bi < 0) return
        assocEvents++
        val pi = bi * 3; val pj = bj * 3
        var ux = ax[pj] - ax[pi]; var uy = ax[pj + 1] - ax[pi + 1]; var uz = ax[pj + 2] - ax[pi + 2]
        val dist = sqrt(ux * ux + uy * uy + uz * uz)
        if (dist < 1e-9) { val r = randomUnit(); ux = r[0]; uy = r[1]; uz = r[2] } else { ux /= dist; uy /= dist; uz /= dist }
        // meeting point (inside the N₂O₄ box) and the dimer frame: x = bond axis, y = any perpendicular
        val loB = -half + marginB; val hiB = half - marginB
        val mx = ((ax[pi] + ax[pj]) / 2).coerceIn(loB, hiB)
        val my = ((ax[pi + 1] + ax[pj + 1]) / 2).coerceIn(loB, maxOf(loB, top - marginB))
        val mz = ((ax[pi + 2] + ax[pj + 2]) / 2).coerceIn(loB, hiB)
        val r = randomUnit()
        var px = r[1] * uz - r[2] * uy; var py = r[2] * ux - r[0] * uz; var pz = r[0] * uy - r[1] * ux
        var pl = sqrt(px * px + py * py + pz * pz)
        if (pl < 1e-6) { px = -uy; py = ux; pz = 0.0; pl = sqrt(px * px + py * py); if (pl < 1e-6) { px = 0.0; py = -uz; pz = uy; pl = sqrt(py * py + pz * pz) } }
        px /= pl; py /= pl; pz /= pl
        val qd = fromAxes(ux, uy, uz, px, py, pz)
        val off = MolGeom.HALF_OFFSET * angstrom
        val t = (dist / 0.25).coerceIn(0.15, 0.45)
        val vdx = (av[pi] + av[pj]) / 2; val vdy = (av[pi + 1] + av[pj + 1]) / 2; val vdz = (av[pi + 2] + av[pj + 2]) / 2
        for ((s, sign) in listOf(bi to -1.0, bj to 1.0)) {
            val p = s * 3
            val tx = mx + sign * ux * off; val ty = my + sign * uy * off; val tz = mz + sign * uz * off
            av[p] = (tx - ax[p]) / t; av[p + 1] = (ty - ax[p + 1]) / t; av[p + 2] = (tz - ax[p + 2]) / t
            steerLeft[s] = t; steerTotal[s] = t
            System.arraycopy(aq, s * 4, q0, s * 4, 4)
            // the −x half's O atoms point away from its partner: rotate the dimer frame by π about z
            val target = if (sign < 0) qMul(qd, doubleArrayOf(0.0, 0.0, 1.0, 0.0)) else qd
            if (dot4(q0, s * 4, target) < 0) for (k in 0 until 4) target[k] = -target[k]
            System.arraycopy(target, 0, qT, s * 4, 4)
            pairC[p] = mx; pairC[p + 1] = my; pairC[p + 2] = mz
            pairV[p] = vdx; pairV[p + 1] = vdy; pairV[p + 2] = vdz
            System.arraycopy(qd, 0, pairQ, s * 4, 4)
        }
        partner[bi] = bj; partner[bj] = bi
        nFree -= 2; nPairs++
    }

    /** N₂O₄ → 2NO₂: a random dimer (or a pair still on its way to colliding) comes apart. */
    private fun dissociate() {
        val m = nB + nPairs
        if (m <= 0) return
        dissocEvents++
        var r = rng.nextInt(m)
        if (r < nB) {
            for (k in 0 until capB) { if (!bAlive[k]) continue; if (r-- == 0) { split(k); return } }
        } else {
            r -= nB
            for (i in 0 until capA) {
                val j = partner[i]
                if (!aAlive[i] || j < i) continue
                if (r-- == 0) {                             // the committed pair is released again
                    for (s in intArrayOf(i, j)) { partner[s] = -1; maxwell(av, s * 3, sigA) }
                    nPairs--; nFree += 2
                    return
                }
            }
        }
    }

    private fun split(k: Int) {
        val p = k * 3
        val cx = bx[p]; val cy = bx[p + 1]; val cz = bx[p + 2]
        val q = doubleArrayOf(bq[k * 4], bq[k * 4 + 1], bq[k * 4 + 2], bq[k * 4 + 3])
        val axis = rotate(q, 1.0, 0.0, 0.0)
        val off = MolGeom.HALF_OFFSET * angstrom
        bAlive[k] = false; nB--
        // momentum is conserved; the separation velocity has the variance that makes both halves thermal
        val w = DoubleArray(3); maxwell(w, 0, sigA / sqrt(2.0))
        if (w[0] * axis[0] + w[1] * axis[1] + w[2] * axis[2] < 0) for (c in 0 until 3) w[c] = -w[c]
        val lo = -half + marginA; val hi = half - marginA
        for (sign in doubleArrayOf(1.0, -1.0)) {
            val x = (cx + sign * axis[0] * off).coerceIn(lo, hi)
            val y = (cy + sign * axis[1] * off).coerceIn(lo, maxOf(lo, top - marginA))
            val z = (cz + sign * axis[2] * off).coerceIn(lo, hi)
            val qh = if (sign > 0) q else qMul(q, doubleArrayOf(0.0, 0.0, 1.0, 0.0))
            spawnA(x, y, z, qh, doubleArrayOf(bv[p] + sign * w[0], bv[p + 1] + sign * w[1], bv[p + 2] + sign * w[2]))
        }
        flash(cx, cy, cz, 1)
    }

    // ================================================================ slots

    private fun spawnA(x: Double, y: Double, z: Double, q: DoubleArray, v: DoubleArray?): Int {
        val i = aAlive.indexOfFirst { !it }
        if (i < 0) return -1
        aAlive[i] = true; partner[i] = -1
        val p = i * 3
        ax[p] = x; ax[p + 1] = y; ax[p + 2] = z
        if (v != null) { av[p] = v[0]; av[p + 1] = v[1]; av[p + 2] = v[2] } else maxwell(av, p, sigA)
        System.arraycopy(q, 0, aq, i * 4, 4)
        maxwell(aw, p, 1.4 * sqrt(tK / 298.15))
        nFree++
        return i
    }

    private fun spawnB(x: Double, y: Double, z: Double, q: DoubleArray, v: DoubleArray?): Int {
        val i = bAlive.indexOfFirst { !it }
        if (i < 0) return -1
        bAlive[i] = true
        val p = i * 3
        bx[p] = x; bx[p + 1] = y; bx[p + 2] = z
        if (v != null) { bv[p] = v[0]; bv[p + 1] = v[1]; bv[p + 2] = v[2] } else maxwell(bv, p, sigA / sqrt(2.0))
        System.arraycopy(q, 0, bq, i * 4, 4)
        maxwell(bw, p, 0.8 * sqrt(tK / 298.15))
        nB++
        return i
    }

    private fun flash(x: Double, y: Double, z: Double, kind: Int) {
        val k = fHead; fHead = (fHead + 1) % flashCap
        fx[k * 3] = x; fx[k * 3 + 1] = y; fx[k * 3 + 2] = z; fAge[k] = 0.0; fKind[k] = kind
    }

    /** Is NO₂ slot [i] on its way to a collision? */
    fun isPending(i: Int) = partner[i] >= 0

    // ================================================================ random + quaternion helpers

    private fun randIn(margin: Double, axis: Int): Double {
        val lo = -half + margin
        val hi = if (axis == 1) maxOf(lo, top - margin) else half - margin
        return lo + (hi - lo) * rng.nextDouble()
    }

    private fun maxwell(v: DoubleArray, p: Int, sigma: Double) { for (k in 0 until 3) v[p + k] = rng.nextGaussian() * sigma }

    private fun randomUnit(): DoubleArray {
        while (true) {
            val x = rng.nextGaussian(); val y = rng.nextGaussian(); val z = rng.nextGaussian()
            val l = sqrt(x * x + y * y + z * z)
            if (l > 1e-9) return doubleArrayOf(x / l, y / l, z / l)
        }
    }

    private fun randomQ(): DoubleArray {
        while (true) {
            val q = DoubleArray(4) { rng.nextGaussian() }
            val l = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
            if (l > 1e-9) { for (k in 0 until 4) q[k] /= l; return q }
        }
    }

    companion object {
        /** Hamilton product a⊗b, quaternions as (x, y, z, w). */
        fun qMul(a: DoubleArray, b: DoubleArray) = doubleArrayOf(
            a[3] * b[0] + a[0] * b[3] + a[1] * b[2] - a[2] * b[1],
            a[3] * b[1] - a[0] * b[2] + a[1] * b[3] + a[2] * b[0],
            a[3] * b[2] + a[0] * b[1] - a[1] * b[0] + a[2] * b[3],
            a[3] * b[3] - a[0] * b[0] - a[1] * b[1] - a[2] * b[2])

        fun rotate(q: DoubleArray, x: Double, y: Double, z: Double): DoubleArray {
            val r = qMul(qMul(q, doubleArrayOf(x, y, z, 0.0)), doubleArrayOf(-q[0], -q[1], -q[2], q[3]))
            return doubleArrayOf(r[0], r[1], r[2])
        }

        /** Rotation whose local x maps to (ex) and local y to (ey) (orthonormal), z = x × y. */
        fun fromAxes(ex: Double, ey: Double, ez: Double, fx: Double, fy: Double, fz: Double): DoubleArray {
            val zx = ey * fz - ez * fy; val zy = ez * fx - ex * fz; val zz = ex * fy - ey * fx
            // rotation matrix columns: (ex,ey,ez), (fx,fy,fz), (zx,zy,zz)
            val m00 = ex; val m10 = ey; val m20 = ez; val m01 = fx; val m11 = fy; val m21 = fz; val m02 = zx; val m12 = zy; val m22 = zz
            val tr = m00 + m11 + m22
            val q = if (tr > 0) {
                val s = sqrt(tr + 1.0) * 2
                doubleArrayOf((m21 - m12) / s, (m02 - m20) / s, (m10 - m01) / s, 0.25 * s)
            } else if (m00 > m11 && m00 > m22) {
                val s = sqrt(1.0 + m00 - m11 - m22) * 2
                doubleArrayOf(0.25 * s, (m01 + m10) / s, (m02 + m20) / s, (m21 - m12) / s)
            } else if (m11 > m22) {
                val s = sqrt(1.0 + m11 - m00 - m22) * 2
                doubleArrayOf((m01 + m10) / s, 0.25 * s, (m12 + m21) / s, (m02 - m20) / s)
            } else {
                val s = sqrt(1.0 + m22 - m00 - m11) * 2
                doubleArrayOf((m02 + m20) / s, (m12 + m21) / s, 0.25 * s, (m10 - m01) / s)
            }
            val l = sqrt(q[0] * q[0] + q[1] * q[1] + q[2] * q[2] + q[3] * q[3])
            for (k in 0 until 4) q[k] /= l
            return q
        }

        private fun dot4(a: DoubleArray, o: Int, b: DoubleArray) = a[o] * b[0] + a[o + 1] * b[1] + a[o + 2] * b[2] + a[o + 3] * b[3]

        /** Spherical interpolation between quaternions a[ao] and b[bo] (same hemisphere), into out[oo]. */
        fun slerp(a: DoubleArray, ao: Int, b: DoubleArray, bo: Int, t: Double, out: DoubleArray, oo: Int) {
            var d = a[ao] * b[bo] + a[ao + 1] * b[bo + 1] + a[ao + 2] * b[bo + 2] + a[ao + 3] * b[bo + 3]
            val sb = if (d < 0) -1.0 else 1.0; d = abs(d)
            val wa: Double; val wb: Double
            if (d > 0.9995) { wa = 1 - t; wb = t * sb } else {
                val th = kotlin.math.acos(d.coerceIn(-1.0, 1.0)); val s = sin(th)
                wa = sin((1 - t) * th) / s; wb = sin(t * th) / s * sb
            }
            var l = 0.0
            for (k in 0 until 4) { out[oo + k] = wa * a[ao + k] + wb * b[bo + k]; l += out[oo + k] * out[oo + k] }
            l = sqrt(l); for (k in 0 until 4) out[oo + k] /= l
        }

        /** q ← exp(½ ω dt) ⊗ q (ω in world axes). */
        fun integrateQ(q: DoubleArray, o: Int, w: DoubleArray, wo: Int, dt: Double) {
            val wx = w[wo]; val wy = w[wo + 1]; val wz = w[wo + 2]
            val ang = sqrt(wx * wx + wy * wy + wz * wz) * dt
            if (ang < 1e-12) return
            val s = sin(ang / 2) / (ang / dt); val c = cos(ang / 2)
            val r = doubleArrayOf(wx * s, wy * s, wz * s, c)
            val n = qMul(r, doubleArrayOf(q[o], q[o + 1], q[o + 2], q[o + 3]))
            val l = sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2] + n[3] * n[3])
            for (k in 0 until 4) q[o + k] = n[k] / l
        }

    }
}

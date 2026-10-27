// VseprCore — valence-shell electron-pair repulsion as PHYSICS, not a lookup table (Gillespie & Nyholm, 1957).
//
// MODEL. Every electron domain (a bonding pair BP or a lone pair LP) is a point on a sphere around the
// central nucleus. A lone pair is held by ONE nucleus only, so its charge cloud sits closer to it: LP
// points live at radius rho = 0.80, BP points at 1. All pairs repel with the same steep law
//                   E = sum_{i<j} 1 / d_ij^3          (d = chord between the two points)
// and nothing else. "LP-LP > LP-BP > BP-BP" is not put in by hand: at the same angle, two inner points
// are closer together, so they push harder (at 90 deg: LP-LP 1.95x, LP-BP 1.38x a BP-BP pair).
//
// TWO NUMBERS, FITTED ON TWO MOLECULES. n = 3 and rho = 0.80 were chosen (scan n = 1..6, rho = 0.6..1)
// so that one model reproduces BOTH  NH3  H-N-H 106.7 deg (model 106.70)  and  H2O  H-O-H 104.5 deg
// (model 104.57)  [NIST CCCBDB experimental geometries]. Everything else is then a PREDICTION:
//   ClF3  F_ax-Cl-F_eq  model 86.6 deg   vs 87.5 deg  (microwave, D. F. Smith, J. Chem. Phys. 21, 609 (1953))
//   SF4   F_ax-S-F_ax   model 170.7 deg  vs 173.1 deg (microwave, Tolles & Gwinn, J. Chem. Phys. 36, 1119 (1962))
//   SF4   F_eq-S-F_eq   model 122.7 deg  vs 101.6 deg  <- a REAL FAILURE of every point-charge model (we scanned
//         separate exponents and weights too: the equatorial angle always opens). A real lone pair is not a
//         point: it flattens into the roomy equatorial plane. Shown as such in the demo, not hidden.
// The qualitative VSEPR rules all EMERGE as the global energy minimum: lone pairs go equatorial in a
// trigonal bipyramid (seesaw, T-shape, linear XeF2), two lone pairs on an octahedron go trans (square planar).
//
// DYNAMICS. Projected gradient descent on the sphere: each domain slides "downhill" along the surface,
// u_i <- normalize(u_i - h grad_i E), with an Armijo line search (energy can only go DOWN, every step) and
// a cap on the angular move per step (the live animation is smooth). Exact symmetric saddles are escaped
// with a tiny deterministic jitter (seeded, so every run is identical).
//
// LOCAL MINIMA. The landscape has false minima (lone pair axial in a bipyramid, cis lone pairs on an
// octahedron: 80 % of random starts end there for XeF4). A real molecule escapes them thermally. Here,
// when the user adds a domain, the newcomer is tried at 48 places on the sphere (biggest hole first) and the
// first one whose plain downhill flow reaches the ground state is used; removals try small kicks. The live
// animation is then the plain gradient flow from that start. If no start works, the flow settles in the
// false minimum and a lone pair swaps sites with a bond (a "pseudorotation" hop, accepted only if the energy
// after relaxing is lower). The ground state itself is found by relaxing every lone-pair labelling of the
// ideal polyhedron (<= 20 per composition) and is cross-checked against 100 random starts in the tests.

package com.bytedance.physicslab.solar.core.vsepr

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

object Vsepr {
    /** Repulsion exponent: E = sum 1/d^n. */
    const val EXPONENT = 3
    /** Lone-pair point radius / bonding-pair point radius. */
    const val LP_RADIUS = 0.80
    const val MAX_DOMAINS = 6
    /** Fewer than two bonds has no bond angle to show. */
    const val MIN_BONDS = 2

    /** Largest angular move of any domain in one flow step (the live animation's speed limit). */
    val CAP_RAD = Math.toRadians(2.5)
    /** Converged when every tangential force is below this: angles then sit within ~2e-4 deg of the minimum
     *  (Hessian eigenvalues here are 0.35..10, so the error is ~ g / lambda_min). Tighter is unresolvable:
     *  the energy change of a step drops below one ulp of E near g ~ 5e-8. */
    internal const val GRAD_TOL = 1e-6
    internal const val ARMIJO = 1e-4
    internal const val GROW = 1.6

    fun radius(lone: Boolean) = if (lone) LP_RADIUS else 1.0

    /** Total repulsion energy (model units) of [n] unit vectors [u] (xyz triples). */
    fun energy(u: DoubleArray, lone: BooleanArray, n: Int): Double {
        var e = 0.0
        for (i in 0 until n) {
            val ri = radius(lone[i])
            for (j in i + 1 until n) {
                val rj = radius(lone[j])
                val dx = ri * u[3 * i] - rj * u[3 * j]; val dy = ri * u[3 * i + 1] - rj * u[3 * j + 1]; val dz = ri * u[3 * i + 2] - rj * u[3 * j + 2]
                val d2 = dx * dx + dy * dy + dz * dz
                e += 1.0 / (d2 * sqrt(d2))
            }
        }
        return e
    }

    /** Energy, and dE/du_i projected onto each domain's tangent plane, written into [g]. */
    fun gradient(u: DoubleArray, lone: BooleanArray, n: Int, g: DoubleArray): Double {
        var e = 0.0
        for (k in 0 until 3 * n) g[k] = 0.0
        for (i in 0 until n) {
            val ri = radius(lone[i])
            for (j in i + 1 until n) {
                val rj = radius(lone[j])
                val dx = ri * u[3 * i] - rj * u[3 * j]; val dy = ri * u[3 * i + 1] - rj * u[3 * j + 1]; val dz = ri * u[3 * i + 2] - rj * u[3 * j + 2]
                val d2 = dx * dx + dy * dy + dz * dz
                val inv = 1.0 / (d2 * sqrt(d2))
                e += inv
                val f = -3.0 * inv / d2                       // dE/dr_i = f (r_i - r_j)
                g[3 * i] += ri * f * dx; g[3 * i + 1] += ri * f * dy; g[3 * i + 2] += ri * f * dz
                g[3 * j] -= rj * f * dx; g[3 * j + 1] -= rj * f * dy; g[3 * j + 2] -= rj * f * dz
            }
        }
        for (i in 0 until n) {
            val d = g[3 * i] * u[3 * i] + g[3 * i + 1] * u[3 * i + 1] + g[3 * i + 2] * u[3 * i + 2]
            g[3 * i] -= d * u[3 * i]; g[3 * i + 1] -= d * u[3 * i + 1]; g[3 * i + 2] -= d * u[3 * i + 2]
        }
        return e
    }

    /** Angle (deg) between domains i and j. */
    fun angleDeg(u: DoubleArray, i: Int, j: Int): Double {
        val c = u[3 * i] * u[3 * j] + u[3 * i + 1] * u[3 * j + 1] + u[3 * i + 2] * u[3 * j + 2]
        return Math.toDegrees(acos(c.coerceIn(-1.0, 1.0)))
    }

    /** Every bond-bond angle (deg), ascending. */
    fun bondAngles(u: DoubleArray, lone: BooleanArray, n: Int): DoubleArray {
        val out = ArrayList<Double>()
        for (i in 0 until n) for (j in i + 1 until n) if (!lone[i] && !lone[j]) out += angleDeg(u, i, j)
        return out.sorted().toDoubleArray()
    }

    // ------------------------------------------------------------------ ground states

    class Ground(val e1: Double, val e2: Double, val u: DoubleArray, val minima: DoubleArray) {
        /** Energy below which a downhill flow can only end in the ground state (it cannot climb to E2). */
        val acceptBelow get() = if (e2.isInfinite()) Double.POSITIVE_INFINITY else e1 + 0.5 * (e2 - e1)
    }

    private val groundCache = HashMap<Int, Ground>()
    private val idealCache = HashMap<Int, DoubleArray>()

    /** Lone-pair flags for a composition: bonds first, then lone pairs. */
    fun types(nB: Int, nL: Int) = BooleanArray(nB + nL) { it >= nB }

    /** The all-equal-domains polyhedron for [n] points (Thomson-like problem with 1/d^3). */
    fun ideal(n: Int): DoubleArray = synchronized(idealCache) {
        idealCache.getOrPut(n) {
            var best: Flow? = null
            for (s in 0 until 6) {
                val f = Flow(n, BooleanArray(n), randomStart(n, Random(9001 + 31 * s + n)))
                f.relax()
                if (best == null || f.energy < best.energy - 1e-12) best = f
            }
            best!!.u.copyOf()
        }
    }

    /**
     * Ground state of [nB] bonds + [nL] lone pairs: relax every lone-pair labelling of the ideal polyhedron.
     * Also returns the second-lowest minimum (E2), which makes "will this start reach the ground state?" cheap.
     */
    fun ground(nB: Int, nL: Int): Ground = synchronized(groundCache) {
        groundCache.getOrPut(nB * 8 + nL) {
            val n = nB + nL
            val lone = types(nB, nL)
            val base = ideal(n)
            val energies = ArrayList<Double>()
            var best: Flow? = null
            for (mask in 0 until (1 shl n)) {
                if (Integer.bitCount(mask) != nL) continue
                // put the lone pairs on the sites in [mask], the bonds on the others, in type order
                val start = DoubleArray(3 * n)
                var b = 0; var l = nB
                for (s in 0 until n) {
                    val dst = if ((mask shr s) and 1 == 1) l++ else b++
                    for (a in 0 until 3) start[3 * dst + a] = base[3 * s + a]
                }
                jitter(start, n, Math.toRadians(0.3), Random(77 + mask))
                val f = Flow(n, lone, start); f.relax()
                energies += f.energy
                if (best == null || f.energy < best.energy - 1e-12) best = f
            }
            val e1 = best!!.energy
            val distinct = distinctMinima(energies)
            val e2 = distinct.firstOrNull { it > e1 + MIN_GAP } ?: Double.POSITIVE_INFINITY
            Ground(e1, e2, best.u.copyOf(), distinct.toDoubleArray())
        }
    }

    internal const val MIN_GAP = 1e-7
    internal fun distinctMinima(e: List<Double>): List<Double> {
        val out = ArrayList<Double>()
        for (x in e.sorted()) if (out.isEmpty() || x > out.last() + MIN_GAP) out += x
        return out
    }

    /** Is [e] the ground-state energy (to convergence precision)? */
    fun isGround(e: Double, g: Ground) = e <= g.e1 + 1e-7 * (1 + abs(g.e1))

    // ------------------------------------------------------------------ settling with pseudorotation hops

    /** The lone-pair <-> bond site exchange that, after relaxing, lowers the energy most (null = none does). */
    fun bestSwap(u: DoubleArray, lone: BooleanArray, n: Int, e: Double): Pair<Int, Int>? {
        var best: Pair<Int, Int>? = null; var bestE = e - 1e-9
        for (i in 0 until n) for (j in 0 until n) {
            if (!lone[i] || lone[j]) continue
            val f = Flow(n, lone, swapStart(u, n, i, j)); f.relax()
            if (f.energy < bestE) { bestE = f.energy; best = i to j }
        }
        return best
    }

    /** [u] with domains i and j exchanging sites (plus the tiny deterministic jitter). */
    fun swapStart(u: DoubleArray, n: Int, i: Int, j: Int): DoubleArray {
        val v = u.copyOf(3 * n)
        for (a in 0 until 3) { v[3 * i + a] = u[3 * j + a]; v[3 * j + a] = u[3 * i + a] }
        jitter(v, n, Math.toRadians(0.3), Random(4242 + 7 * i + j))
        return v
    }

    /** Downhill flow, then pseudorotation hops while one lowers the energy: always ends in a minimum. */
    fun settle(n: Int, lone: BooleanArray, start: DoubleArray): Flow {
        var f = Flow(n, lone, start); f.relax()
        val nL = (0 until n).count { lone[it] }
        val g = ground(n - nL, nL)
        repeat(12) {
            if (isGround(f.energy, g)) return f
            val s = bestSwap(f.u, lone, n, f.energy) ?: return f
            f = Flow(n, lone, swapStart(f.u, n, s.first, s.second)); f.relax()
        }
        return f
    }

    /** Does the PLAIN downhill flow from [start] end in the ground state? (early exit once below E2) */
    fun reachesGround(n: Int, lone: BooleanArray, start: DoubleArray, g: Ground): Boolean {
        val f = Flow(n, lone, start)
        if (f.energy < g.acceptBelow) return true
        var k = 0
        while (k++ < 8000 && f.step()) if (f.energy < g.acceptBelow) return true
        return isGround(f.energy, g)
    }

    // ------------------------------------------------------------------ presentation frame

    /**
     * A rotation (row-major 3x3, rows = new x, y, z axes in old coordinates) that presents a settled molecule
     * the way a textbook draws it: lone pairs pointing UP (their sum, or the first one if they cancel), else
     * a bipyramid's axis / the first bond up (linear: the axis across); then the bonds spread across the line
     * of sight (+X), so the viewer looks at the shape, not along it. Where symmetry leaves a choice (which of
     * several equivalent bonds goes up or right), [upHint] / [xHint] (the current display axes, core frame) pick
     * the nearest one, so a change never spins the molecule further than it must.
     */
    fun canonicalRotation(u: DoubleArray, lone: BooleanArray, n: Int, upHint: DoubleArray? = null, xHint: DoubleArray? = null): DoubleArray {
        fun v(i: Int) = doubleArrayOf(u[3 * i], u[3 * i + 1], u[3 * i + 2])
        val nL = (0 until n).count { lone[it] }
        val bonds = (0 until n).filter { !lone[it] }
        var up: DoubleArray
        if (nL > 0) {
            val s = DoubleArray(3); for (i in 0 until n) if (lone[i]) for (a in 0 until 3) s[a] += u[3 * i + a]
            val lps = (0 until n).filter { lone[it] }
            up = if (len(s) > 0.3) s else v(if (upHint == null) lps[0] else lps.maxByOrNull { dot(v(it), upHint) }!!)
        } else up = when (bonds.size) {
            // linear: any axis across the bond will do; keep the current "up" as far as possible (idempotent)
            2 -> { val b = v(bonds[0]); val h = upHint ?: doubleArrayOf(0.0, 1.0, 0.0)
                val y = sub(h, scale(b, dot(h, b))); if (len(y) > 0.1) y else perp(b) }
            5 -> { val ax = bonds.filter { i -> bonds.count { j -> j != i && abs(dot(v(i), v(j))) < 0.3 } == 3 }.ifEmpty { bonds }
                v(if (upHint == null) ax[0] else ax.maxByOrNull { dot(v(it), upHint) }!!) }
            else -> v(if (upHint == null) bonds[0] else bonds.maxByOrNull { dot(v(it), upHint) }!!)
        }
        up = norm(up)
        // spread: principal axis of the bonds' components across [up]
        val e1 = norm(perp(up)); val e2 = cross(up, e1)
        var c11 = 0.0; var c22 = 0.0; var c12 = 0.0
        for (i in bonds) { val p = v(i); val h1 = dot(p, e1); val h2 = dot(p, e2); c11 += h1 * h1; c22 += h2 * h2; c12 += h1 * h2 }
        val tr = c11 + c22
        val aniso = if (tr > 1e-9) sqrt((c11 - c22) * (c11 - c22) + 4 * c12 * c12) / tr else 0.0
        var x: DoubleArray? = null
        if (aniso > 0.05) {
            val phi = 0.5 * atan2(2 * c12, c11 - c22)
            x = DoubleArray(3) { cos(phi) * e1[it] + sin(phi) * e2[it] }
        } else {
            val across = bonds.map { i -> val p = v(i); sub(p, scale(up, dot(p, up))) }.filter { len(it) > 0.3 }
            if (across.isNotEmpty()) x = norm(if (xHint == null) across[0] else across.maxByOrNull { dot(norm(it), xHint) }!!)
        }
        var xx = norm(x ?: e1)
        if (xHint != null && aniso > 0.05) { if (dot(xx, xHint) < 0) xx = scale(xx, -1.0) }
        else if (xHint == null) {
            // deterministic sign: the first bond with a real component along x points to +x
            for (i in bonds) { val d = dot(v(i), xx); if (abs(d) > 0.2) { if (d < 0) xx = scale(xx, -1.0); break } }
        }
        val z = cross(xx, up)
        return doubleArrayOf(xx[0], xx[1], xx[2], up[0], up[1], up[2], z[0], z[1], z[2])
    }

    /** out = R v for every domain (R row-major). */
    fun rotateAll(r: DoubleArray, u: DoubleArray, n: Int): DoubleArray {
        val out = DoubleArray(3 * n)
        for (i in 0 until n) {
            val x = u[3 * i]; val y = u[3 * i + 1]; val z = u[3 * i + 2]
            for (a in 0 until 3) out[3 * i + a] = r[3 * a] * x + r[3 * a + 1] * y + r[3 * a + 2] * z
        }
        return out
    }

    // ------------------------------------------------------------------ angle classes (what the arcs show)

    /** A family of equal bond angles: its value (deg, live mean), and the representative pair drawn as an arc. */
    class AngleClass(val deg: Double, val count: Int, val i: Int, val j: Int)

    /**
     * Bond angles grouped into classes (values within 1.2 deg), ascending; exact trans pairs (> 179.5 deg) are
     * dropped unless the molecule is linear. [pick] scores a pair (higher = better arc to draw).
     */
    fun angleClasses(u: DoubleArray, lone: BooleanArray, n: Int, pick: (Int, Int) -> Double = { _, _ -> 0.0 }): List<AngleClass> {
        val pairs = ArrayList<Triple<Int, Int, Double>>()
        for (i in 0 until n) for (j in i + 1 until n) if (!lone[i] && !lone[j]) pairs += Triple(i, j, angleDeg(u, i, j))
        pairs.sortBy { it.third }
        val groups = ArrayList<ArrayList<Triple<Int, Int, Double>>>()
        for (p in pairs) if (groups.isEmpty() || p.third > groups.last().last().third + 1.2) groups += arrayListOf(p) else groups.last() += p
        val keep = groups.filter { g -> g.first().third < 179.5 }.ifEmpty { groups }
        return keep.map { g ->
            val rep = g.maxByOrNull { pick(it.first, it.second) }!!
            AngleClass(g.sumOf { it.third } / g.size, g.size, rep.first, rep.second)
        }
    }

    // ------------------------------------------------------------------ shapes (naming follows AXmEn, the angles are computed)

    class Shape(val zh: String, val en: String)

    fun electronGeometry(n: Int) = when (n) {
        2 -> Shape("直线形", "linear"); 3 -> Shape("平面三角形", "trigonal planar"); 4 -> Shape("四面体", "tetrahedral")
        5 -> Shape("三角双锥", "trigonal bipyramidal"); 6 -> Shape("八面体", "octahedral"); else -> Shape("—", "—")
    }

    fun molecularShape(nB: Int, nL: Int): Shape = when (nB * 10 + nL) {
        20 -> Shape("直线形", "linear")
        30 -> Shape("平面三角形", "trigonal planar"); 21 -> Shape("V 形", "bent")
        40 -> Shape("正四面体", "tetrahedral"); 31 -> Shape("三角锥形", "trigonal pyramidal"); 22 -> Shape("V 形", "bent")
        50 -> Shape("三角双锥", "trigonal bipyramidal"); 41 -> Shape("跷跷板形", "seesaw")
        32 -> Shape("T 形", "T-shaped"); 23 -> Shape("直线形", "linear")
        60 -> Shape("正八面体", "octahedral"); 51 -> Shape("四方锥形", "square pyramidal")
        42 -> Shape("平面正方形", "square planar"); 33 -> Shape("T 形", "T-shaped"); 24 -> Shape("直线形", "linear")
        else -> Shape("—", "—")
    }

    /** "AX₃E₂" */
    fun formula(nB: Int, nL: Int): String = "AX" + sub(nB) + when (nL) { 0 -> ""; 1 -> "E"; else -> "E" + sub(nL) }
    fun sub(k: Int) = k.toString().map { "₀₁₂₃₄₅₆₇₈₉"[it - '0'] }.joinToString("")

    // ------------------------------------------------------------------ small vector maths

    internal fun dot(a: DoubleArray, b: DoubleArray) = a[0] * b[0] + a[1] * b[1] + a[2] * b[2]
    internal fun len(a: DoubleArray) = sqrt(dot(a, a))
    internal fun norm(a: DoubleArray): DoubleArray { val l = max(len(a), 1e-300); return doubleArrayOf(a[0] / l, a[1] / l, a[2] / l) }
    internal fun cross(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])
    internal fun sub(a: DoubleArray, b: DoubleArray) = doubleArrayOf(a[0] - b[0], a[1] - b[1], a[2] - b[2])
    internal fun scale(a: DoubleArray, s: Double) = doubleArrayOf(a[0] * s, a[1] * s, a[2] * s)
    internal fun perp(a: DoubleArray): DoubleArray = norm(if (abs(a[0]) < 0.9) cross(a, doubleArrayOf(1.0, 0.0, 0.0)) else cross(a, doubleArrayOf(0.0, 1.0, 0.0)))

    /** Uniformly random unit vectors (xyz triples). */
    fun randomStart(n: Int, rnd: Random): DoubleArray {
        val u = DoubleArray(3 * n)
        for (i in 0 until n) {
            val z = 2 * rnd.nextDouble() - 1; val phi = 2 * PI * rnd.nextDouble(); val r = sqrt(1 - z * z)
            u[3 * i] = r * cos(phi); u[3 * i + 1] = r * sin(phi); u[3 * i + 2] = z
        }
        return u
    }

    /** Tilt every vector by a random angle up to [rad] (seeded): breaks exact symmetric saddles. */
    fun jitter(u: DoubleArray, n: Int, rad: Double, rnd: Random) {
        for (i in 0 until n) {
            val p = doubleArrayOf(u[3 * i], u[3 * i + 1], u[3 * i + 2])
            val a = norm(perp(p)); val b = cross(p, a)
            val phi = 2 * PI * rnd.nextDouble(); val t = rad * rnd.nextDouble()
            val q = norm(DoubleArray(3) { p[it] * cos(t) + sin(t) * (cos(phi) * a[it] + sin(phi) * b[it]) })
            u[3 * i] = q[0]; u[3 * i + 1] = q[1]; u[3 * i + 2] = q[2]
        }
    }

    /** [n] points spread evenly over the sphere (Fibonacci lattice). */
    fun fibonacci(n: Int): DoubleArray {
        val out = DoubleArray(3 * n); val ga = PI * (3 - sqrt(5.0))
        for (k in 0 until n) {
            val y = 1 - 2 * (k + 0.5) / n; val r = sqrt(max(0.0, 1 - y * y)); val phi = k * ga
            out[3 * k] = r * cos(phi); out[3 * k + 1] = y; out[3 * k + 2] = r * sin(phi)
        }
        return out
    }

}

/**
 * Downhill flow of [n] domains on the unit sphere: projected gradient descent with an Armijo line search
 * (every accepted step LOWERS the energy) and an angular speed limit [Vsepr.CAP_RAD] per step.
 * Deterministic: same start, same trajectory.
 */
class Flow(val n: Int, lone: BooleanArray, start: DoubleArray) {
    val lone: BooleanArray = lone.copyOf(n)
    val u = DoubleArray(3 * n)
    private val g = DoubleArray(3 * n)
    private val t = DoubleArray(3 * n)
    private var eta = 0.02
    private var stall = 0
    var energy: Double; private set
    var steps = 0; private set
    var converged = false; private set
    /** Largest angle (rad) any domain moved in the last accepted step. */
    var lastMove = Double.MAX_VALUE; private set

    init {
        for (i in 0 until n) {
            val l = sqrt(start[3 * i] * start[3 * i] + start[3 * i + 1] * start[3 * i + 1] + start[3 * i + 2] * start[3 * i + 2])
            for (a in 0 until 3) u[3 * i + a] = start[3 * i + a] / l
        }
        energy = Vsepr.energy(u, this.lone, n)
        if (n < 2) converged = true
    }

    /** One downhill step; false once converged (gradient below tolerance or no decrease possible). */
    fun step(): Boolean {
        if (converged) return false
        val e0 = Vsepr.gradient(u, lone, n, g)
        var gm = 0.0; var g2 = 0.0
        for (i in 0 until n) {
            val s = g[3 * i] * g[3 * i] + g[3 * i + 1] * g[3 * i + 1] + g[3 * i + 2] * g[3 * i + 2]
            g2 += s; if (s > gm) gm = s
        }
        gm = sqrt(gm)
        if (gm < Vsepr.GRAD_TOL) { converged = true; lastMove = 0.0; energy = e0; return false }
        var h = minOf(eta * Vsepr.GROW, Vsepr.CAP_RAD / gm)
        for (tries in 0 until 60) {
            for (i in 0 until n) {
                val x = u[3 * i] - h * g[3 * i]; val y = u[3 * i + 1] - h * g[3 * i + 1]; val z = u[3 * i + 2] - h * g[3 * i + 2]
                val l = sqrt(x * x + y * y + z * z)
                t[3 * i] = x / l; t[3 * i + 1] = y / l; t[3 * i + 2] = z / l
            }
            val e1 = Vsepr.energy(t, lone, n)
            if (e1 <= e0 - Vsepr.ARMIJO * h * g2) {
                // energy resolution exhausted (no real decrease for a while): that is convergence too
                stall = if (e1 < e0) 0 else stall + 1
                if (stall > 8) { converged = true; lastMove = 0.0; energy = e0; return false }
                System.arraycopy(t, 0, u, 0, 3 * n)
                energy = e1; eta = h; steps++
                lastMove = kotlin.math.atan(h * gm)
                return true
            }
            h *= 0.5
        }
        converged = true; lastMove = 0.0; energy = e0
        return false
    }

    /** Run to convergence (or [maxSteps]). */
    fun relax(maxSteps: Int = 20000): Flow { var k = 0; while (k++ < maxSteps && step()) Unit; return this }
}

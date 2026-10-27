// VseprSim — the LIVE molecule: add / remove a domain, then watch it slide downhill into its shape.
//
// Every change picks a START (where the newcomer appears, or a small kick after a removal) whose plain
// gradient flow ends in the ground state, PREDICTS the end state by running that flow once off-screen, and
// then replays the very same deterministic flow one step at a time for the animation (bit-identical, so the
// prediction IS the end). The prediction also gives the presentation rotation (lone pairs up, bonds across the
// line of sight); the demo eases its display frame toward it while the molecule moves, so nothing jumps.
//
// Pure Kotlin, no Android: tested in core/vsepr/VseprCoreTest.

package com.bytedance.physicslab.solar.core.vsepr

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sqrt
import kotlin.random.Random

class VseprSim {
    enum class Phase { NUDGE, FLOW, SWAP, SETTLED }

    var n = 0; private set
    val lone = BooleanArray(Vsepr.MAX_DOMAINS)
    /** Live domain directions (xyz triples, core frame). */
    val u = DoubleArray(3 * Vsepr.MAX_DOMAINS)
    /** Where the current change will end (same frame as [u]). */
    val target = DoubleArray(3 * Vsepr.MAX_DOMAINS)
    /** Presentation rotation (row-major) of [target]: the display frame the demo eases toward. */
    val presentRotation = doubleArrayOf(1.0, 0.0, 0.0, 0.0, 1.0, 0.0, 0.0, 0.0, 1.0)
    var phase = Phase.SETTLED; private set
    /** Pseudorotation hops the live flow needed (0 when the chosen start was good — the normal case). */
    var hops = 0; private set
    /** Changes since construction (the demo uses it to know a new prediction exists). */
    var revision = 0; private set

    private var flow: Flow? = null
    private val from = DoubleArray(3 * Vsepr.MAX_DOMAINS)
    private val to = DoubleArray(3 * Vsepr.MAX_DOMAINS)
    private var animK = 0
    private var animN = 0
    private var seedCounter = 0

    val nBonds get() = (0 until n).count { !lone[it] }
    val nLone get() = (0 until n).count { lone[it] }
    val ground get() = Vsepr.ground(nBonds, nLone)
    val energy get() = flow?.energy ?: Vsepr.energy(u, lone, n)
    val settled get() = phase == Phase.SETTLED
    /** Moving less than ~0.02 deg per step and already in the ground-state basin: safe to finish at once. */
    val visuallySettled get() = phase == Phase.FLOW && (flow?.let { it.lastMove < 3.5e-4 && it.energy < ground.acceptBelow } ?: true)
    private fun loneArr() = lone.copyOf(n)

    /** A fresh molecule of [nB] bonds + [nL] lone pairs blooming out of a tight bud (seeded, so repeatable). */
    fun reset(nB: Int, nL: Int, variant: Int = 0) {
        require(nB in Vsepr.MIN_BONDS..Vsepr.MAX_DOMAINS && nL >= 0 && nB + nL <= Vsepr.MAX_DOMAINS)
        n = nB + nL; seedCounter = 0                  // a preset starts a fresh, repeatable history
        for (i in 0 until Vsepr.MAX_DOMAINS) lone[i] = i >= nB && i < n
        val g = Vsepr.ground(nB, nL)
        var start: DoubleArray? = null
        for (s in 0 until 64) {
            val c = bud(n, Random(1957 + 101 * variant + 13 * s + 7 * n + nL))
            if (Vsepr.reachesGround(n, loneArr(), c, g)) { start = c; break }
        }
        commit(start ?: bud(n, Random(1957 + 101 * variant)))
        animN = 0; phase = Phase.FLOW
    }

    /** Add a bonding pair or a lone pair; returns its index, or -1 if the shell is full. */
    fun add(isLone: Boolean): Int {
        if (n >= Vsepr.MAX_DOMAINS) return -1
        val k = n
        lone[k] = isLone; n++
        val g = ground
        val lo = loneArr()
        // candidate spots for the newcomer: 48 points, biggest hole (lowest insertion energy) first
        val fib = Vsepr.fibonacci(48)
        val order = (0 until 48).sortedBy { c -> insertionEnergy(fib, c, k) }
        var chosen: DoubleArray? = null
        for (c in order) {
            val s = u.copyOf(3 * n)
            for (a in 0 until 3) s[3 * k + a] = fib[3 * c + a]
            Vsepr.jitter(s, n, Math.toRadians(0.2), Random(31 * c + 5 * n + seedCounter))
            if (Vsepr.reachesGround(n, lo, s, g)) { chosen = s; break }
        }
        if (chosen == null) {
            chosen = u.copyOf(3 * n); for (a in 0 until 3) chosen[3 * k + a] = fib[3 * order[0] + a]
            Vsepr.jitter(chosen, n, Math.toRadians(0.2), Random(seedCounter))
        }
        commit(chosen)
        animN = 0; phase = Phase.FLOW
        return k
    }

    /** Remove domain [k] (refused if it would leave fewer than two bonds). */
    fun remove(k: Int): Boolean {
        if (k !in 0 until n) return false
        if (!lone[k] && nBonds <= Vsepr.MIN_BONDS) return false
        for (i in k until n - 1) { lone[i] = lone[i + 1]; for (a in 0 until 3) u[3 * i + a] = u[3 * (i + 1) + a] }
        n--; lone[n] = false
        val g = ground
        val lo = loneArr()
        val here = u.copyOf(3 * n)
        var chosen: DoubleArray? = null
        var kickDeg = 0.0
        loop@ for (deg in doubleArrayOf(0.2, 4.0, 8.0, 15.0, 25.0, 40.0)) for (s in 0 until 3) {
            val c = here.copyOf(); Vsepr.jitter(c, n, Math.toRadians(deg), Random(911 + 17 * s + 3 * n + seedCounter))
            if (Vsepr.reachesGround(n, lo, c, g)) { chosen = c; kickDeg = deg; break@loop }
        }
        if (chosen == null) { chosen = here.copyOf(); Vsepr.jitter(chosen, n, Math.toRadians(0.2), Random(seedCounter)); kickDeg = 0.2 }
        commit(chosen)
        if (kickDeg > 1.0) {
            // the kick is animated (a short nudge), never a jump: from where the domains are to the kicked start
            System.arraycopy(here, 0, from, 0, 3 * n); System.arraycopy(chosen, 0, to, 0, 3 * n)
            System.arraycopy(here, 0, u, 0, 3 * n)
            animK = 0; animN = 12; phase = Phase.NUDGE
        } else { animN = 0; phase = Phase.FLOW }
        return true
    }

    /** Fix [start] as the beginning of the live flow; predict the end and its presentation rotation. */
    private fun commit(start: DoubleArray) {
        seedCounter++; revision++
        val lo = loneArr()
        val end = Vsepr.settle(n, lo, start)
        System.arraycopy(Vsepr.canonicalRotation(end.u, lo, n), 0, presentRotation, 0, 9)
        System.arraycopy(end.u, 0, target, 0, 3 * n)
        System.arraycopy(start, 0, u, 0, 3 * n)
        flow = Flow(n, lo, start)
    }

    /** One animation step (nudge frame, swap frame, or one downhill step). */
    fun advance() {
        when (phase) {
            Phase.SETTLED -> return
            Phase.NUDGE, Phase.SWAP -> {
                animK++
                val f = smooth(animK.toDouble() / animN)
                for (i in 0 until n) {
                    var x = from[3 * i] + (to[3 * i] - from[3 * i]) * f; var y = from[3 * i + 1] + (to[3 * i + 1] - from[3 * i + 1]) * f
                    var z = from[3 * i + 2] + (to[3 * i + 2] - from[3 * i + 2]) * f
                    var l = sqrt(x * x + y * y + z * z)
                    if (l < 1e-6) { x = from[3 * i]; y = from[3 * i + 1]; z = from[3 * i + 2]; l = 1.0 }  // antipodal: hold
                    u[3 * i] = x / l; u[3 * i + 1] = y / l; u[3 * i + 2] = z / l
                }
                if (animK >= animN) { System.arraycopy(to, 0, u, 0, 3 * n); flow = Flow(n, loneArr(), to.copyOf(3 * n)); phase = Phase.FLOW }
            }
            Phase.FLOW -> {
                val f = flow ?: return
                f.step()
                System.arraycopy(f.u, 0, u, 0, 3 * n)
                if (f.converged) {
                    val g = ground
                    val hop = if (Vsepr.isGround(f.energy, g)) null else Vsepr.bestSwap(f.u, loneArr(), n, f.energy)
                    if (hop == null) { phase = Phase.SETTLED; return }
                    hops++
                    System.arraycopy(f.u, 0, from, 0, 3 * n)
                    System.arraycopy(Vsepr.swapStart(f.u, n, hop.first, hop.second), 0, to, 0, 3 * n)
                    animK = 0; animN = 36; phase = Phase.SWAP
                }
            }
        }
    }

    /** Finish the current downhill run at once (the motion is already invisible). */
    fun polish() {
        val f = flow ?: return
        if (phase != Phase.FLOW) return
        var k = 0
        while (k++ < 20000 && !f.converged) advance()
        if (phase == Phase.FLOW) advance()
    }

    /** Run everything to the end (review renders, tests). */
    fun finish() { var k = 0; while (k++ < 100000 && phase != Phase.SETTLED) advance() }

    private fun insertionEnergy(fib: DoubleArray, c: Int, k: Int): Double {
        var e = 0.0; val rk = Vsepr.radius(lone[k])
        for (i in 0 until k) {
            val ri = Vsepr.radius(lone[i])
            val dx = rk * fib[3 * c] - ri * u[3 * i]; val dy = rk * fib[3 * c + 1] - ri * u[3 * i + 1]; val dz = rk * fib[3 * c + 2] - ri * u[3 * i + 2]
            val d2 = dx * dx + dy * dy + dz * dz
            e += 1.0 / (d2 * sqrt(d2))
        }
        return e
    }

    companion object {
        /** A tight bud: every domain within ~28 deg of +Y, seeded. */
        fun bud(n: Int, rnd: Random): DoubleArray {
            val u = DoubleArray(3 * n)
            for (i in 0 until n) {
                val t = Math.toRadians(6.0 + 22.0 * rnd.nextDouble()); val phi = 2 * PI * rnd.nextDouble()
                u[3 * i] = kotlin.math.sin(t) * cos(phi); u[3 * i + 1] = cos(t); u[3 * i + 2] = kotlin.math.sin(t) * kotlin.math.sin(phi)
            }
            return u
        }
        fun smooth(x: Double): Double { val t = x.coerceIn(0.0, 1.0); return t * t * (3 - 2 * t) }
    }
}

/** The preset shelf: real molecules, their AXmEn class and measured bond angles. */
object VseprData {
    /** How to read a measured angle off the computed geometry. */
    enum class Stat { MEAN, MIN, MAX, OTHER }   // OTHER = bonds left after removing the widest pair (SF4 / PCl5 equatorial)

    class Angle(val labelZh: String, val labelEn: String, val exp: Double, val stat: Stat)

    class Molecule(
        val formula: String, val central: String, val ligand: String, val nB: Int, val nL: Int,
        val doubleBond: Boolean, val angles: List<Angle>, val sourceZh: String, val sourceEn: String,
    )

    // Experimental gas-phase geometries. NIST CCCBDB = Computational Chemistry Comparison and Benchmark
    // DataBase, experimental section (cccbdb.nist.gov). SF4: Tolles & Gwinn, J. Chem. Phys. 36, 1119 (1962).
    // ClF3: D. F. Smith, J. Chem. Phys. 21, 609 (1953). PCl5/SF6/XeF2/XeF4/CO2/BF3/CH4: point-group symmetry
    // (D3h / Oh / D-inf-h / D4h / D-inf-h / D3h / Td) confirmed by electron diffraction or spectroscopy.
    val presets = listOf(
        Molecule("CO₂", "C", "O", 2, 0, true, listOf(Angle("O–C–O", "O–C–O", 180.0, Stat.MAX)), "NIST CCCBDB", "NIST CCCBDB"),
        Molecule("BF₃", "B", "F", 3, 0, false, listOf(Angle("F–B–F", "F–B–F", 120.0, Stat.MEAN)), "NIST CCCBDB", "NIST CCCBDB"),
        Molecule("CH₄", "C", "H", 4, 0, false, listOf(Angle("H–C–H", "H–C–H", 109.47, Stat.MEAN)), "NIST CCCBDB", "NIST CCCBDB"),
        Molecule("NH₃", "N", "H", 3, 1, false, listOf(Angle("H–N–H", "H–N–H", 106.7, Stat.MEAN)), "NIST CCCBDB", "NIST CCCBDB"),
        Molecule("H₂O", "O", "H", 2, 2, false, listOf(Angle("H–O–H", "H–O–H", 104.5, Stat.MEAN)), "NIST CCCBDB", "NIST CCCBDB"),
        Molecule("PCl₅", "P", "Cl", 5, 0, false, listOf(Angle("轴–赤", "ax–eq", 90.0, Stat.MIN), Angle("赤–赤", "eq–eq", 120.0, Stat.OTHER)),
            "电子衍射", "electron diffraction"),
        Molecule("SF₄", "S", "F", 4, 1, false, listOf(Angle("轴–轴", "ax–ax", 173.1, Stat.MAX), Angle("赤–赤", "eq–eq", 101.6, Stat.OTHER)),
            "微波谱 1962", "microwave 1962"),
        Molecule("ClF₃", "Cl", "F", 3, 2, false, listOf(Angle("轴–赤", "ax–eq", 87.5, Stat.MIN)), "微波谱 1953", "microwave 1953"),
        Molecule("XeF₂", "Xe", "F", 2, 3, false, listOf(Angle("F–Xe–F", "F–Xe–F", 180.0, Stat.MAX)), "NIST CCCBDB", "NIST CCCBDB"),
        Molecule("SF₆", "S", "F", 6, 0, false, listOf(Angle("F–S–F", "F–S–F", 90.0, Stat.MIN)), "NIST CCCBDB", "NIST CCCBDB"),
        Molecule("XeF₄", "Xe", "F", 4, 2, false, listOf(Angle("F–Xe–F", "F–Xe–F", 90.0, Stat.MIN)), "NIST CCCBDB", "NIST CCCBDB"),
    )

    /** The preset of this AXmEn class (the molecule the readout compares with), if any. */
    fun reference(nB: Int, nL: Int) = presets.firstOrNull { it.nB == nB && it.nL == nL }

    /** Evaluate [s] on the computed geometry. */
    fun measure(s: Stat, u: DoubleArray, lone: BooleanArray, n: Int): Double {
        val a = Vsepr.bondAngles(u, lone, n)
        if (a.isEmpty()) return 0.0
        return when (s) {
            Stat.MEAN -> a.average()
            Stat.MIN -> a.first()
            Stat.MAX -> a.last()
            Stat.OTHER -> {
                // drop the two bonds of the widest pair; average the angles among the rest
                var bi = -1; var bj = -1; var best = -1.0
                for (i in 0 until n) for (j in i + 1 until n) if (!lone[i] && !lone[j]) {
                    val d = Vsepr.angleDeg(u, i, j); if (d > best) { best = d; bi = i; bj = j }
                }
                val rest = (0 until n).filter { !lone[it] && it != bi && it != bj }
                val v = ArrayList<Double>()
                for (x in rest.indices) for (y in x + 1 until rest.size) v += Vsepr.angleDeg(u, rest[x], rest[y])
                if (v.isEmpty()) 0.0 else v.average()
            }
        }
    }
}

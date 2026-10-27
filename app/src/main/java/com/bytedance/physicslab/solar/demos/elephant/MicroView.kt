// MicroView — the molecular picture inside a floating glass cube: H2O2 molecules drift, and the
// catalyst runs its two-step cycle in front of the viewer:
//     (1) H2O2 + I-  -> H2O + IO-        the catalyst TAKES an O atom      (slow, rate-determining)
//     (2) H2O2 + IO- -> H2O + O2 + I-    it GIVES it back as O2            (fast)
// Catalase does the same with its iron (Fe -> Fe=O "compound I" -> Fe), MnO2 on a surface site.
// Atoms are conserved and never teleport: a reaction only re-assigns atoms to new molecules and
// every atom eases to its new place, so you SEE the O hop onto the iodide and leave with the next
// H2O2's O as O2 (which rises out of the cube), while the iodide comes back unchanged.
// Illustrative timing (real turnovers are micro- to nanoseconds); the cycle speeds up with the
// macroscopic rate, H2O2 thins out as the cylinder's is used up, and the catalyst stays.

package com.bytedance.physicslab.solar.demos.elephant

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.core.catalysis.Catalyst
import com.bytedance.physicslab.solar.lab.spec.Blend
import com.bytedance.physicslab.solar.lab.spec.Mat
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.Rgb
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

class MicroView(private val half: Float) {
    private val NO = 16; private val NA = 32; private val MAXM = 20
    private val R_O = 0.0122f; private val R_H = 0.0080f; private val R_I = 0.024f

    // atoms: 0..15 = O, 16..31 = H
    private lateinit var atomNodes: Array<Node>
    private val disp = FloatArray(NA * 3); private val tgt = FloatArray(NA * 3)
    private val owner = IntArray(NA)                 // molecule id, -1 = reservoir, -2-s = held by site s
    // molecules
    private val kind = IntArray(MAXM)                // 0 none, 1 H2O2, 2 H2O, 3 O2
    private val mst = IntArray(MAXM)                 // 0 free, 1 docking, 2 leaving
    private val mc = FloatArray(MAXM * 3); private val mv = FloatArray(MAXM * 3)
    private val yaw = FloatArray(MAXM); private val pitch = FloatArray(MAXM); private val spin = FloatArray(MAXM)
    private val ma = IntArray(MAXM * 4); private val mn = IntArray(MAXM)
    private val dockFrom = FloatArray(MAXM * 3)
    // sites
    private class Site(val pos: P3, val dir: P3, val bond: Float) {
        var state = 0; var t = 0f; var bound = -1; var m = -1; var pulse = 0f
    }
    private var sites: List<Site> = emptyList()
    private var siteCat: Catalyst? = null

    // catalyst visuals
    private lateinit var iodides: Array<Node>
    private lateinit var glows: Array<Node>
    private lateinit var enzyme: Node
    private lateinit var heme: Node
    private lateinit var surface: Node
    private lateinit var surfSites: Array<Node>
    private var rnd = Random(7)
    private val glowMats = Array(6) { k -> Mat.Unlit(Rgb(0.75f, 0.45f, 1.0f), alpha = 0.10f + 0.07f * k, blend = Blend.ADD) }

    /** Completed cycles since the last reset (each = one O2 from this cube). */
    var cycles = 0; private set

    fun build(group: Node, sphere: MeshData, unitBox: MeshData) {
        val oMat = Mat.Pbr(Rgb(0.92f, 0.20f, 0.16f), metallic = 0f, roughness = 0.30f, emissive = Rgb(0.20f, 0.03f, 0.02f))
        val hMat = Mat.Pbr(Rgb(0.95f, 0.96f, 0.98f), metallic = 0f, roughness = 0.30f, emissive = Rgb(0.10f, 0.10f, 0.11f))
        atomNodes = Array(NA) { k -> group.add("MAtom$k", sphere, if (k < NO) oMat else hMat).also { it.uniformScale(if (k < NO) R_O else R_H); it.visible = false } }
        val iMat = Mat.Pbr(Rgb(0.58f, 0.32f, 0.92f), metallic = 0f, roughness = 0.25f, emissive = Rgb(0.16f, 0.06f, 0.26f))
        iodides = Array(2) { k -> group.add("MIodide$k", sphere, iMat).also { it.uniformScale(R_I); it.visible = false } }
        glows = Array(2) { k -> group.add("MIodGlow$k", sphere, glowMats[0]).also { it.visible = false } }
        // catalase: a lumpy protein (green) with its iron-heme active site (orange)
        enzyme = group.add(Node("MEnzyme")).also { it.visible = false }
        val pMat = Mat.Pbr(Rgb(0.30f, 0.72f, 0.45f), metallic = 0f, roughness = 0.55f, emissive = Rgb(0.03f, 0.08f, 0.04f))
        val blob = listOf(P3(0f, -0.025f, 0f) to 0.036f, P3(-0.035f, -0.01f, -0.01f) to 0.027f, P3(0.034f, -0.012f, -0.008f) to 0.028f,
            P3(-0.016f, 0.012f, -0.02f) to 0.026f, P3(0.018f, 0.010f, -0.022f) to 0.025f, P3(0f, -0.05f, 0.012f) to 0.024f, P3(-0.022f, -0.042f, 0.02f) to 0.022f)
        for ((k, b) in blob.withIndex()) enzyme.add("MProt$k", sphere, pMat, b.first).uniformScale(b.second)
        heme = enzyme.add("MHeme", sphere, Mat.Pbr(Rgb(1.0f, 0.45f, 0.15f), roughness = 0.3f, emissive = Rgb(0.55f, 0.18f, 0.04f)), P3(0f, 0.004f, 0.026f))
        heme.uniformScale(0.011f)
        // MnO2: a dark crystal surface with two active sites
        surface = group.add("MSurface", unitBox, Mat.Pbr(Rgb(0.20f, 0.17f, 0.15f), metallic = 0.4f, roughness = 0.45f), P3(0f, -0.098f, 0f))
            .also { it.scale = P3(0.21f, 0.018f, 0.17f); it.visible = false }
        val mnMat = Mat.Pbr(Rgb(0.45f, 0.38f, 0.55f), metallic = 0.3f, roughness = 0.35f, emissive = Rgb(0.08f, 0.05f, 0.10f))
        surfSites = Array(2) { k -> group.add("MMnSite$k", sphere, mnMat).also { it.uniformScale(0.012f); it.visible = false } }
    }

    fun reset(seed: Int) {
        rnd = Random(seed)
        kind.fill(0); mst.fill(0); mn.fill(0); owner.fill(-1); cycles = 0
        sites = emptyList(); siteCat = null
        for (n in iodides) n.visible = false
        for (n in glows) n.visible = false
        enzyme.visible = false; surface.visible = false; for (n in surfSites) n.visible = false
        repeat(6) { spawnH2O2(inside = true) }
        for (k in 0 until NA) atomNodes[k].visible = owner[k] != -1
        place(0f, snap = true)
    }

    /**
     * [mixed]: catalyst is in; [left] fraction of H2O2 remaining; [rate] -d[H2O2]/dt (M/s) of the
     * cylinder; [active] fraction of the catalyst still working (catalase can denature).
     */
    fun update(dt: Float, cat: Catalyst, mixed: Boolean, left: Float, rate: Double, active: Float) {
        val want = if (mixed && cat != Catalyst.NONE) cat else null
        if (want != siteCat) setupSites(want)

        // population: H2O2 in view follows the cylinder's remaining fraction
        val target = if (!mixed) 6 else kotlin.math.ceil(6 * left.coerceIn(0f, 1f) - 0.02f).toInt()
        var have = 0; for (m in 0 until MAXM) if (kind[m] == 1 && mst[m] != 2) have++
        if (have < target && dt > 0f && rnd.nextFloat() < 4f * dt) spawnH2O2(inside = false)
        if (have > target + 1) {                                   // used up in the cylinder: let one drift out
            var far = -1; var fd = -1f
            for (m in 0 until MAXM) if (kind[m] == 1 && mst[m] == 0) { val d = abs(mc[m * 3]) + abs(mc[m * 3 + 1]); if (d > fd) { fd = d; far = m } }
            if (far >= 0) { mst[far] = 2; val l = sqrt(mc[far * 3] * mc[far * 3] + mc[far * 3 + 2] * mc[far * 3 + 2]).coerceAtLeast(1e-3f); mv[far * 3] = mc[far * 3] / l * 0.07f; mv[far * 3 + 1] = 0f; mv[far * 3 + 2] = mc[far * 3 + 2] / l * 0.07f }
        }

        // catalytic cycles: faster when the cylinder reacts faster (log scale), stopped when dead
        val f = if (want == null || rate <= 0.0 || active < 0.05f) 0f
                else ((0.35 + 0.30 * log10(1 + rate / 0.002)).coerceIn(0.35, 1.2) * active).toFloat()
        for ((s, site) in sites.withIndex()) runSite(s, site, dt, f)

        // free molecules: Brownian drift in the cube; leaving ones fly out
        for (m in 0 until MAXM) {
            if (kind[m] == 0 || mst[m] == 1) continue
            val o = m * 3
            if (mst[m] == 0) {
                for (a in 0 until 3) { mv[o + a] += (rnd.nextFloat() - 0.5f) * 0.25f * sqrt(maxOf(dt, 0f)); mv[o + a] *= exp(-0.6f * dt) }
                val sp = sqrt(mv[o] * mv[o] + mv[o + 1] * mv[o + 1] + mv[o + 2] * mv[o + 2])
                if (sp > 0.045f) for (a in 0 until 3) mv[o + a] *= 0.045f / sp
            }
            for (a in 0 until 3) mc[o + a] += mv[o + a] * dt
            yaw[m] += spin[m] * dt; pitch[m] += spin[m] * 0.6f * dt
            val lim = half - 0.024f
            if (mst[m] == 0) {
                val yLo = if (siteCat == Catalyst.MNO2) -0.07f else -lim
                for (a in 0 until 3) {
                    val lo = if (a == 1) yLo else -lim
                    if (mc[o + a] < lo) { mc[o + a] = lo; mv[o + a] = abs(mv[o + a]) }
                    if (mc[o + a] > lim) { mc[o + a] = lim; mv[o + a] = -abs(mv[o + a]) }
                }
            } else if (abs(mc[o]) > half + 0.01f || abs(mc[o + 1]) > half + 0.01f || abs(mc[o + 2]) > half + 0.01f) release(m)
        }

        // catalyst visuals
        for ((s, site) in sites.withIndex()) {
            site.pulse = maxOf(0f, site.pulse - dt * 2.2f)
            if (siteCat == Catalyst.KI && s < 2) {
                glows[s].visible = true
                glows[s].uniformScale(R_I * (1.35f + 0.6f * site.pulse))
                glows[s].setMat(glowMats[minOf(5, (site.pulse * 6).toInt())])
            }
        }
        place(dt, snap = false)
    }

    // ---------------------------------------------------------------- sites

    private fun setupSites(c: Catalyst?) {
        // whatever a previous catalyst held goes back to the reservoir
        for (s in sites) { if (s.bound >= 0) { owner[s.bound] = -1 }; if (s.m >= 0 && mst[s.m] == 1) mst[s.m] = 0 }
        siteCat = c
        for (n in iodides) n.visible = false
        for (n in glows) n.visible = false
        enzyme.visible = false; surface.visible = false; for (n in surfSites) n.visible = false
        sites = when (c) {
            Catalyst.KI -> listOf(Site(P3(-0.045f, -0.02f, 0.0f), nrm(0.35f, 0.55f, 0.75f), R_I + R_O - 0.004f),
                                  Site(P3(0.048f, 0.03f, -0.015f), nrm(-0.40f, 0.45f, 0.80f), R_I + R_O - 0.004f))
            Catalyst.CATALASE -> listOf(Site(P3(0f, 0.004f, 0.026f), nrm(0f, 0.55f, 0.84f), 0.017f))
            Catalyst.MNO2 -> listOf(Site(P3(-0.05f, -0.083f, 0.02f), nrm(0.1f, 1f, 0.35f), 0.018f),
                                    Site(P3(0.05f, -0.083f, -0.01f), nrm(-0.1f, 1f, 0.35f), 0.018f))
            else -> emptyList()
        }
        when (c) {
            Catalyst.KI -> for (k in 0 until 2) { iodides[k].visible = true; iodides[k].pos = sites[k].pos; glows[k].pos = sites[k].pos }
            Catalyst.CATALASE -> enzyme.visible = true
            Catalyst.MNO2 -> { surface.visible = true; for (k in 0 until 2) { surfSites[k].visible = true; surfSites[k].pos = sites[k].pos } }
            else -> {}
        }
        for ((k, s) in sites.withIndex()) { s.state = 0; s.t = -0.5f * k }   // staggered
    }

    // fractions of one cycle: approach 1, react 1, hold (IO-), approach 2, react 2, rest
    private val PH = floatArrayOf(0f, 0.30f, 0.10f, 0.10f, 0.22f, 0.10f, 0.18f)

    private fun runSite(s: Int, site: Site, dt: Float, f: Float) {
        if (f <= 0f && site.bound < 0) {
            // stopped (no H2O2 left, catalyst dead) with nothing held: let a half-docked molecule go.
            // A site that holds an O always finishes its cycle, so the catalyst ends as it began.
            if (site.state == 1) {
                val m = site.m; if (m >= 0 && mst[m] == 1) mst[m] = 0
                site.m = -1; site.state = 0; site.t = 0f
            }
            if (site.state == 0 || site.state == 1) return
        }
        val rate = maxOf(f, 0.35f)
        site.t += dt * rate
        when (site.state) {
            0 -> if (site.t >= 0f) {                                 // pick the nearest free H2O2
                val m = nearestFree(site); if (m >= 0) { dock(m, site); site.state = 1; site.t = 0f } else site.t = 0f
            }
            1 -> { dockMove(site); if (site.t >= PH[1]) { react1(s, site); site.state = 2; site.t = 0f } }
            2 -> if (site.t >= PH[2]) { site.state = 3; site.t = 0f }
            3 -> if (site.t >= PH[3]) {
                // the catalyst holds an O: it always gets its partner H2O2 (so it ends as it began)
                val m = nearestFree(site)
                if (m >= 0) { dock(m, site); site.state = 4; site.t = 0f } else { spawnH2O2(inside = false); site.t = PH[3] }
            }
            4 -> { dockMove(site); if (site.t >= PH[4]) { react2(site); site.state = 5; site.t = 0f } }
            5 -> if (site.t >= PH[5]) { site.state = 6; site.t = 0f }
            6 -> if (site.t >= PH[6]) { site.state = 0; site.t = 0f }
        }
    }

    private fun nearestFree(site: Site): Int {
        var best = -1; var bd = 1e9f
        for (m in 0 until MAXM) if (kind[m] == 1 && mst[m] == 0) {
            val dx = mc[m * 3] - site.pos.x; val dy = mc[m * 3 + 1] - site.pos.y; val dz = mc[m * 3 + 2] - site.pos.z
            val d = dx * dx + dy * dy + dz * dz
            if (d < bd) { bd = d; best = m }
        }
        return best
    }

    private fun dock(m: Int, site: Site) {
        mst[m] = 1; site.m = m
        for (a in 0 until 3) dockFrom[m * 3 + a] = mc[m * 3 + a]
    }

    private fun dockMove(site: Site) {
        val m = site.m; if (m < 0) return
        val k = smooth((site.t / maxOf(1e-3f, if (site.state == 1) PH[1] else PH[4])).coerceIn(0f, 1f))
        val d = site.bond + 0.024f
        val tx = site.pos.x + site.dir.x * d; val ty = site.pos.y + site.dir.y * d; val tz = site.pos.z + site.dir.z * d
        mc[m * 3] = dockFrom[m * 3] + (tx - dockFrom[m * 3]) * k
        mc[m * 3 + 1] = dockFrom[m * 3 + 1] + (ty - dockFrom[m * 3 + 1]) * k
        mc[m * 3 + 2] = dockFrom[m * 3 + 2] + (tz - dockFrom[m * 3 + 2]) * k
    }

    /** H2O2 + catalyst -> H2O + catalyst=O : the O nearer the site hops over, its H joins the other O. */
    private fun react1(s: Int, site: Site) {
        val m = site.m; site.m = -1; if (m < 0 || kind[m] != 1) return
        val o1 = ma[m * 4]; val o2 = ma[m * 4 + 1]; val h1 = ma[m * 4 + 2]; val h2 = ma[m * 4 + 3]
        val near = if (dist2(o1, site) < dist2(o2, site)) o1 else o2
        val far = if (near == o1) o2 else o1
        site.bound = near; owner[near] = -2 - s
        makeLeaving(m, 2, intArrayOf(far, h1, h2), site, 0.06f)
    }

    /** H2O2 + catalyst=O -> H2O + O2 + catalyst : the held O leaves with one O of the new H2O2. */
    private fun react2(site: Site) {
        val m = site.m; site.m = -1; if (m < 0 || kind[m] != 1) return
        val o1 = ma[m * 4]; val o2 = ma[m * 4 + 1]; val h1 = ma[m * 4 + 2]; val h2 = ma[m * 4 + 3]
        val near = if (dist2(o1, site) < dist2(o2, site)) o1 else o2
        val far = if (near == o1) o2 else o1
        val held = site.bound; site.bound = -1
        makeLeaving(m, 2, intArrayOf(far, h1, h2), site, 0.06f)
        if (held >= 0) {
            val q = freeSlot()
            if (q >= 0) {
                kind[q] = 3; mst[q] = 2; mn[q] = 2; ma[q * 4] = held; ma[q * 4 + 1] = near
                owner[held] = q; owner[near] = q
                for (a in 0 until 3) mc[q * 3 + a] = (disp[held * 3 + a] + disp[near * 3 + a]) / 2
                mv[q * 3] = (rnd.nextFloat() - 0.5f) * 0.02f; mv[q * 3 + 1] = 0.085f; mv[q * 3 + 2] = 0.02f
                yaw[q] = rnd.nextFloat() * 6.28f; pitch[q] = 0f; spin[q] = 1.5f
            } else { owner[held] = -1; owner[near] = -1 }
        }
        site.pulse = 1f
        cycles++
    }

    /** Molecule [m] becomes a product of [k] with [atoms] and drifts away from the site. */
    private fun makeLeaving(m: Int, k: Int, atoms: IntArray, site: Site, speed: Float) {
        kind[m] = k; mst[m] = 2; mn[m] = atoms.size
        for ((j, a) in atoms.withIndex()) { ma[m * 4 + j] = a; owner[a] = m }
        for (a in 0 until 3) mc[m * 3 + a] = disp[atoms[0] * 3 + a]
        var dx = mc[m * 3] - site.pos.x; var dy = mc[m * 3 + 1] - site.pos.y; var dz = mc[m * 3 + 2] - site.pos.z
        val l = sqrt(dx * dx + dy * dy + dz * dz).coerceAtLeast(1e-4f); dx /= l; dy /= l; dz /= l
        mv[m * 3] = dx * speed + 0.03f * (if (site.pos.x < 0) -1f else 1f); mv[m * 3 + 1] = dy * speed * 0.5f; mv[m * 3 + 2] = dz * speed
    }

    private fun release(m: Int) {
        for (j in 0 until mn[m]) owner[ma[m * 4 + j]] = -1
        kind[m] = 0; mst[m] = 0; mn[m] = 0
    }

    private fun freeSlot(): Int { for (m in 0 until MAXM) if (kind[m] == 0) return m; return -1 }

    /** A new H2O2 from the reservoir: inside the cube (reset) or entering through a side face. */
    private fun spawnH2O2(inside: Boolean) {
        val os = (0 until NO).filter { owner[it] == -1 }; val hs = (NO until NA).filter { owner[it] == -1 }
        if (os.size < 2 || hs.size < 2) return
        val m = freeSlot(); if (m < 0) return
        kind[m] = 1; mst[m] = 0; mn[m] = 4
        val atoms = intArrayOf(os[0], os[1], hs[0], hs[1])
        for (j in 0 until 4) { ma[m * 4 + j] = atoms[j]; owner[atoms[j]] = m }
        val lim = half - 0.03f
        if (inside) {
            for (a in 0 until 3) mc[m * 3 + a] = (rnd.nextFloat() * 2 - 1) * lim
            for (a in 0 until 3) mv[m * 3 + a] = (rnd.nextFloat() - 0.5f) * 0.04f
        } else {
            val side = if (rnd.nextBoolean()) 1f else -1f
            mc[m * 3] = side * lim; mc[m * 3 + 1] = (rnd.nextFloat() * 2 - 1) * lim * 0.7f; mc[m * 3 + 2] = (rnd.nextFloat() * 2 - 1) * lim
            mv[m * 3] = -side * 0.04f; mv[m * 3 + 1] = 0f; mv[m * 3 + 2] = 0f
        }
        yaw[m] = rnd.nextFloat() * 6.28f; pitch[m] = rnd.nextFloat() * 6.28f; spin[m] = (rnd.nextFloat() - 0.5f) * 1.6f
        // appear at once where they are (no flying in from the last place these atoms were)
        computeTargets(m); for (j in 0 until 4) { val a = atoms[j]; for (c in 0 until 3) disp[a * 3 + c] = tgt[a * 3 + c] }
    }

    // ---------------------------------------------------------------- placement

    private val T_H2O2 = floatArrayOf(-0.0099f, 0f, 0f, 0.0099f, 0f, 0f, -0.0157f, 0.0099f, 0f, 0.0157f, 0f, 0.0099f)
    private val T_H2O = floatArrayOf(0f, 0f, 0f, -0.0093f, 0.0072f, 0f, 0.0093f, 0.0072f, 0f)
    private val T_O2 = floatArrayOf(-0.0086f, 0f, 0f, 0.0086f, 0f, 0f)

    private fun computeTargets(m: Int) {
        val t = when (kind[m]) { 1 -> T_H2O2; 2 -> T_H2O; else -> T_O2 }
        val cy = cos(yaw[m]); val sy = sin(yaw[m]); val cp = cos(pitch[m]); val sp = sin(pitch[m])
        for (j in 0 until mn[m]) {
            val x = t[j * 3]; val y0 = t[j * 3 + 1]; val z0 = t[j * 3 + 2]
            val y = y0 * cp - z0 * sp; val z = y0 * sp + z0 * cp          // pitch about x
            val xr = x * cy + z * sy; val zr = -x * sy + z * cy            // yaw about y
            val a = ma[m * 4 + j]
            tgt[a * 3] = mc[m * 3] + xr; tgt[a * 3 + 1] = mc[m * 3 + 1] + y; tgt[a * 3 + 2] = mc[m * 3 + 2] + zr
        }
    }

    private fun place(dt: Float, snap: Boolean) {
        for (m in 0 until MAXM) if (kind[m] != 0) computeTargets(m)
        for ((s, site) in sites.withIndex()) if (site.bound >= 0) {
            val a = site.bound
            tgt[a * 3] = site.pos.x + site.dir.x * site.bond; tgt[a * 3 + 1] = site.pos.y + site.dir.y * site.bond; tgt[a * 3 + 2] = site.pos.z + site.dir.z * site.bond
            if (owner[a] != -2 - s) owner[a] = -2 - s
        }
        val k = if (snap) 1f else 1f - exp(-9f * dt)
        for (a in 0 until NA) {
            val on = owner[a] != -1
            atomNodes[a].visible = on
            if (!on) continue
            for (c in 0 until 3) disp[a * 3 + c] += (tgt[a * 3 + c] - disp[a * 3 + c]) * k
            atomNodes[a].pos = P3(disp[a * 3], disp[a * 3 + 1], disp[a * 3 + 2])
        }
    }

    private fun dist2(a: Int, s: Site): Float {
        val dx = disp[a * 3] - s.pos.x; val dy = disp[a * 3 + 1] - s.pos.y; val dz = disp[a * 3 + 2] - s.pos.z
        return dx * dx + dy * dy + dz * dz
    }

    private fun smooth(t: Float) = t * t * (3 - 2 * t)
    private fun nrm(x: Float, y: Float, z: Float): P3 { val l = sqrt(x * x + y * y + z * z); return P3(x / l, y / l, z / l) }

    /** Tests/readout: atoms are conserved (every atom is in a molecule, at a site, or in the reservoir). */
    val heldBySites get() = sites.count { it.bound >= 0 }
}

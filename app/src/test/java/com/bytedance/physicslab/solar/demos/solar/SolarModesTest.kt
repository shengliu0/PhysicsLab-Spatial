package com.bytedance.physicslab.solar.demos.solar

import com.bytedance.physicslab.solar.core.P3
import com.bytedance.physicslab.solar.lab.Lang
import com.bytedance.physicslab.solar.lab.spec.Cam
import com.bytedance.physicslab.solar.lab.spec.Node
import com.bytedance.physicslab.solar.lab.spec.SoftRender
import com.bytedance.physicslab.solar.lab.tr
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * The four solar views, end to end through the real demo:
 *  - every pinch target of every mode answers with a note anchored inside the volume, in both languages;
 *  - the static views cost nothing per frame (no mesh uploads), the close-up only tiny edits;
 *  - every view keeps its content inside the volume rules;
 *  - review renders WITH the floating labels and the panel's footprint drawn in (SoftRender skips labels)
 *    -> build/review/solar_modes/<variant>_<lang>.png
 */
class SolarModesTest {
    private val cache = File("build/review-cache").apply { mkdirs() }
    private val d = SolarDemo
    private val cjk = Regex("[\\u4e00-\\u9fff]")

    @After fun back() = Lang.set(false)

    private fun fresh() { d.runHeadless(cache, 0.5f) }

    @Test
    fun everyTargetInEveryModeAnswers() {
        val bad = ArrayList<String>(); var n = 0
        for (en in listOf(false, true)) {
            Lang.set(en)
            for (variant in listOf("closeup", "lunar", "solar", "toscale", "sizes1", "sizes2", "sizes3")) {
                fresh(); d.ensureHalos()
                d.reviewVariants()[variant]!!.invoke(); repeat(10) { d.update(1f / 60f) }
                d.ensureHalos()
                for (t in d.tapTargets) {
                    // the variant again before each tap: taps change the mode (eclipse, overview, next stage)
                    d.reviewVariants()[variant]!!.invoke(); repeat(3) { d.update(1f / 60f) }
                    val node = d.root!!.find(t.node)
                    if (node == null) { bad += "$variant/${t.node}: no such node"; continue }
                    if (node.tapRadius <= 0f) bad += "$variant/${t.node}: not pinchable (tapRadius 0)"
                    if (!d.isTarget(t.node)) bad += "$variant/${t.node}: isTarget false"
                    val r = worldRadius(node)
                    if (r < 0.045f) bad += "$variant/${t.node}: gaze radius %.3f m < 0.045".format(r)
                    d.onTap(t.node); d.update(1f / 60f)
                    val msg = d.tapMessage(t.node) ?: ("✓ " + tr(t.zh, t.en))
                    val at = d.anchorOf(t.node)
                    if (en && cjk.containsMatchIn(msg)) bad += "$variant/${t.node}: CJK in EN note '$msg'"
                    if (at == null || at.any { !it.isFinite() } || abs(at[0]) > 1.05f || at[1] !in -0.6f..0.7f || abs(at[2]) > 1.05f)
                        bad += "$variant/${t.node}: note anchor ${at?.joinToString { "%.2f".format(it) }}"
                    if (!en) println("TAP $variant/${t.node} -> \"$msg\"")
                    n++
                }
                // readout: exactly 3 lines, no NaN, right language
                val lines = d.readoutText.lines()
                if (lines.size != 3) bad += "$variant: readout has ${lines.size} lines"
                if ("NaN" in d.readoutText) bad += "$variant: NaN in readout"
                if (en && cjk.containsMatchIn(d.readoutText)) bad += "$variant: CJK in EN readout"
                if (en) d.labels.filter { cjk.containsMatchIn(it.text) }.forEach { bad += "$variant: CJK in EN label '${it.text}'" }
                if (d.labels.size > 6) bad += "$variant: ${d.labels.size} labels"
            }
        }
        println("SOLAR-MODES taps checked: $n; problems: ${if (bad.isEmpty()) "none" else bad.joinToString("\n")}")
        assertTrue(bad.joinToString("\n"), bad.isEmpty())
        assertTrue(n > 40)
    }

    private fun worldRadius(n: Node): Float {
        val a = SoftRender.worldOf(n)
        val v = a.mul(n.tapRadius, 0f, 0f)
        return sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2])
    }

    @Test
    fun staticViewsAreFreeAndTheCloseUpIsCheap() {
        fresh()
        // (case, variant, speed level): at rest the static views and a paused eclipse must upload nothing;
        // playing an eclipse may re-upload only the two small disc lenses (49 vertices each)
        val cases = listOf(Triple("toscale", "toscale", -1), Triple("sizes1", "sizes1", -1), Triple("sizes3", "sizes3", -1),
            Triple("closeup-fast", "closeup", 3), Triple("lunar-paused", "lunar", 0), Triple("lunar-playing", "lunar", 2),
            Triple("solar-playing", "solar", 2))
        val perFrame = LinkedHashMap<String, Double>()
        for ((name, variant, rate) in cases) {
            d.reviewVariants()[variant]!!.invoke(); repeat(150) { d.update(1f / 60f) }    // settle (stage animations, lock blend)
            if (rate >= 0) { d.rateIdx = rate; d.applyRate() }
            repeat(5) { d.update(1f / 60f) }
            d.takeMeshEdits()
            val t0 = System.nanoTime()
            repeat(240) { d.update(1f / 60f) }
            val ms = (System.nanoTime() - t0) / 1e6 / 240
            val edits = d.takeMeshEdits()
            perFrame[name] = edits / 240.0
            println("PERF %-14s %.2f mesh uploads/frame (<= %d vertices), update %.3f ms/frame (JVM)".format(name, edits / 240.0, (edits / 240.0 * 49).toInt() + if (edits > 0) 1 else 0, ms))
        }
        for (k in listOf("toscale", "sizes1", "sizes3", "lunar-paused")) assertEquals("$k must not upload meshes", 0.0, perFrame[k]!!, 0.0)
        // free running at 20 days/s crosses the 2025-09-07 eclipse: the disc lens updates only while the Moon is in the shadow
        assertTrue("free close-up uploads ${perFrame["closeup-fast"]}/frame", perFrame["closeup-fast"]!! <= 0.05)
        assertTrue("lunar playback uploads ${perFrame["lunar-playing"]}/frame", perFrame["lunar-playing"]!! <= 2.0)
    }

    @Test
    fun everyViewStaysInsideTheVolume() {
        fresh()
        val bad = ArrayList<String>()
        for (variant in listOf("closeup", "lunar", "solar", "solar2027", "toscale", "sizes2", "sizes3")) {
            d.reviewVariants()[variant]!!.invoke(); repeat(150) { d.update(1f / 60f) }
            // halos excluded (they are added lazily on device); the Sun's corona glow is decoration
            val (lo, hi) = boundsOf(d.root!!) { !it.name.startsWith("Halo_") && !it.name.contains("Corona") }
            println("BOUNDS %-9s x %.2f..%.2f  y %.2f..%.2f  z %.2f..%.2f".format(variant, lo.x, hi.x, lo.y, hi.y, lo.z, hi.z))
            if (lo.x < -0.9f || hi.x > 0.9f || lo.z < -0.6f || hi.z > 0.6f || lo.y < -0.27f || hi.y > 0.45f) {
                val who = ArrayList<String>()
                SoftRender.forEachMesh(d.root!!) { n, w ->
                    if (n.name.startsWith("Halo_") || n.name.contains("Corona")) return@forEachMesh
                    val m = n.mesh!!
                    for (k in 0 until m.vertexCount) {
                        val q = w.apply(m.pos[k * 3], m.pos[k * 3 + 1], m.pos[k * 3 + 2])
                        if (q[0] < -0.9f || q[0] > 0.9f || q[2] < -0.6f || q[2] > 0.6f || q[1] < -0.27f || q[1] > 0.45f) { who += n.name; break }
                    }
                }
                bad += "$variant out of the volume rules: ${who.distinct()}"
            }
        }
        // sizes stage 1: the Sun is meant to overflow at the back (a wall of fire) — only check the front
        d.reviewVariants()["sizes1"]!!.invoke(); repeat(150) { d.update(1f / 60f) }
        val (lo1, hi1) = boundsOf(d.root!!) { !it.name.startsWith("Halo_") && !it.name.startsWith("SZ_Sun") }
        println("BOUNDS sizes1 (without the Sun) x %.2f..%.2f  y %.2f..%.2f  z %.2f..%.2f".format(lo1.x, hi1.x, lo1.y, hi1.y, lo1.z, hi1.z))
        if (lo1.x < -0.9f || hi1.x > 0.9f || hi1.z > 0.6f || hi1.y > 0.45f) bad += "sizes1 out of the volume rules"
        assertTrue(bad.joinToString(), bad.isEmpty())
    }

    private fun boundsOf(root: Node, keep: (Node) -> Boolean): Pair<P3, P3> {
        val lo = floatArrayOf(9f, 9f, 9f); val hi = floatArrayOf(-9f, -9f, -9f)
        SoftRender.forEachMesh(root) { n, w ->
            var p: Node? = n; var ok = true
            while (p != null) { if (!keep(p)) { ok = false; break }; p = p.parent }
            if (!ok) return@forEachMesh
            val m = n.mesh!!
            for (k in 0 until m.vertexCount) {
                val q = w.apply(m.pos[k * 3], m.pos[k * 3 + 1], m.pos[k * 3 + 2])
                for (a in 0..2) { lo[a] = minOf(lo[a], q[a]); hi[a] = maxOf(hi[a], q[a]) }
            }
        }
        return P3(lo[0], lo[1], lo[2]) to P3(hi[0], hi[1], hi[2])
    }

    // ---------------------------------------------------------------- review renders with labels

    @Test
    fun reviewRendersWithLabels() {
        if (System.getProperty("review.only") != "solar") return     // opt-in: -Dreview.only=solar
        val out = File("build/review/solar_modes").apply { mkdirs() }
        val cam = Cam(P3(0f, 0.10f, 2.5f), P3(0f, 0.02f, 0f), 55f, "viewer")
        for (en in listOf(false, true)) {
            Lang.set(en)
            fresh()
            for ((name, apply) in d.reviewVariants()) {
                if (name == "comets" || name == "jupiter") continue
                apply(); repeat(60) { d.update(1f / 60f) }
                d.root!!.walk { if (it.name.startsWith("Halo_")) it.visible = false }
                val img = SoftRender.render(d.root!!, cam, 1280, 960, 2)
                // the panel's footprint (front, y -0.56 .. -0.16 at z 0.92) and the top bar (y 0.46 .. 0.56)
                fun shade(x0: Float, y0: Float, x1: Float, y1: Float) {
                    val a = proj(cam, x0, y0, 0.92f); val b = proj(cam, x1, y1, 0.92f)
                    for (y in a[1].coerceIn(0, 959)..b[1].coerceIn(0, 959)) for (x in a[0].coerceIn(0, 1279)..b[0].coerceIn(0, 1279)) {
                        val p = img.px[y * 1280 + x]
                        val r = ((p shr 16) and 255) * 0.6 + 120 * 0.4; val gg = ((p shr 8) and 255) * 0.6 + 140 * 0.4; val bb = (p and 255) * 0.6 + 170 * 0.4
                        img.px[y * 1280 + x] = (0xFF shl 24) or (r.toInt() shl 16) or (gg.toInt() shl 8) or bb.toInt()
                    }
                }
                shade(-0.62f, -0.16f, 0.62f, -0.56f); shade(-0.62f, 0.56f, 0.62f, 0.46f)
                val tag = "${name}_${if (en) "en" else "zh"}"
                SoftRender.writePng(img, File(out, "$tag.png"))
                // labels + readout as a sidecar; tools draw the text (no AWT on the Android unit-test classpath)
                val sb = StringBuilder()
                for (l in d.labels) {
                    val p = proj(cam, l.pos.x, l.pos.y, l.pos.z)
                    val dist = sqrt((l.pos.x - cam.eye.x) * (l.pos.x - cam.eye.x) + (l.pos.y - cam.eye.y) * (l.pos.y - cam.eye.y) + (l.pos.z - cam.eye.z) * (l.pos.z - cam.eye.z))
                    sb.append("L\t${p[0]}\t${p[1]}\t$dist\t${l.text.replace("\n", "\\n")}\n")
                }
                for (line in d.readoutText.lines()) sb.append("R\t0\t0\t0\t$line\n")
                File(out, "$tag.tsv").writeText(sb.toString())
            }
        }
    }

    private fun proj(c: Cam, x: Float, y: Float, z: Float): IntArray {
        val f = norm(floatArrayOf(c.target.x - c.eye.x, c.target.y - c.eye.y, c.target.z - c.eye.z))
        val r = norm(floatArrayOf(-f[2], 0f, f[0]))      // f x up
        val u = floatArrayOf(r[1] * f[2] - r[2] * f[1], r[2] * f[0] - r[0] * f[2], r[0] * f[1] - r[1] * f[0])
        val d = floatArrayOf(x - c.eye.x, y - c.eye.y, z - c.eye.z)
        val vx = d[0] * r[0] + d[1] * r[1] + d[2] * r[2]; val vy = d[0] * u[0] + d[1] * u[1] + d[2] * u[2]; val vz = d[0] * f[0] + d[1] * f[1] + d[2] * f[2]
        val th = tan(Math.toRadians(c.fovDeg / 2.0)).toFloat(); val asp = 1280f / 960f
        return intArrayOf(((vx / vz / th / asp * 0.5f + 0.5f) * 1280).toInt(), ((-vy / vz / th * 0.5f + 0.5f) * 960).toInt())
    }

    private fun norm(v: FloatArray): FloatArray { val l = sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]); return floatArrayOf(v[0] / l, v[1] / l, v[2] / l) }
}

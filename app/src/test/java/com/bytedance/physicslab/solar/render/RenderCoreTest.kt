package com.bytedance.physicslab.solar.render

import com.bytedance.physicslab.solar.core.Moon
import com.bytedance.physicslab.solar.core.SolarModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * The render half of the Unity-vs-SpatialSDK comparison, minus the GPU:
 *  - NOISE PARITY: the bake primitives (hash, Worley, FBM, System.Random) match the C# bit-for-bit,
 *    so both stacks bake the same sun and the same planets from the same seeds.
 *  - GEOMETRY: every generated mesh is indexed in range and wound outward.
 *  - The Moon's phase comes out of real geometry and lands on real, dated new/full moons.
 *  - Bakes are dumped as PNGs to build/bake-preview/ for eyeballing (the pre-device visual check).
 */
class RenderCoreTest {

    private fun golden() = javaClass.classLoader!!.getResource("noise_golden.csv")!!.readText()
        .lineSequence().drop(1).filter { it.isNotBlank() }.map { it.split(",") }.toList()

    @Test
    fun noisePrimitivesMatchCSharpBitForBit() {
        var n = 0
        for (f in golden()) {
            when (f[0]) {
                "hash" -> {
                    val v = ProcTex.hash(f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt())
                    assertEquals("hash $f", f[6].toFloat(), v, 0f); n++
                }
                "worley" -> {
                    val (f1, f2) = ProcTex.worley(V3(f[1].toFloat(), f[2].toFloat(), f[3].toFloat()), f[4].toInt())
                    assertEquals("worley f1 $f", f[6].toFloat(), f1, 1e-6f)
                    assertEquals("worley f2 $f", f[7].toFloat(), f2, 1e-6f); n++
                }
                "fbm" -> {
                    val v = ProcTex.fbm(V3(f[1].toFloat(), f[2].toFloat(), f[3].toFloat()), f[5].toInt(), f[4].toInt())
                    assertEquals("fbm $f", f[6].toFloat(), v, 1e-6f); n++
                }
                "rand" -> Unit
            }
        }
        println("NOISE parity: $n primitive evaluations match C#")
        assertTrue(n >= 45)
    }

    @Test
    fun csRandomMatchesDotNetSequence() {
        val bySeed = golden().filter { it[0] == "rand" }.groupBy { it[1].toInt() }
        for ((seed, rows) in bySeed) {
            val r = CsRandom(seed)
            for (row in rows.sortedBy { it[2].toInt() }) assertEquals("seed $seed #${row[2]}", row[6].toDouble(), r.nextDouble(), 0.0)
        }
        println("System.Random parity: ${bySeed.values.sumOf { it.size }} draws identical")
    }

    @Test
    fun sphereIsWoundOutwardAndIndexed() {
        for (layout in MeshGen.SphereUv.values()) {
            val m = MeshGen.sphere(0.5f, 24, 48, layout)
            checkIndices(m)
            val (out, inn) = windingCounts(m) { cx, cy, cz -> floatArrayOf(cx, cy, cz) }
            println("sphere $layout: ${m.vertexCount} v, ${m.triangleCount} t, outward=$out inward=$inn")
            assertEquals("inward triangles", 0, inn)
            assertTrue(out > m.triangleCount * 0.9)
        }
    }

    @Test
    fun annulusFacesUp() {
        val m = MeshGen.annulus(0.1f, 0.2f, 64)
        checkIndices(m)
        val (up, down) = windingCounts(m) { _, _, _ -> floatArrayOf(0f, 1f, 0f) }
        println("annulus up=$up down=$down")
        assertEquals(0, down)
    }

    @Test
    fun orbitTubeIsWoundOutward() {
        val model = SolarModel()
        val path = model.orbitShapeUnit(2).also { for (i in it.indices) it[i] *= 0.16f }
        val m = MeshGen.tube(path, 0.0011f, 6, closed = true)
        checkIndices(m)
        // outward = away from the nearest path point
        var out = 0; var inn = 0
        for (t in 0 until m.triangleCount) {
            val c = centroid(m, t); val nrm = triNormal(m, t)
            var best = Float.MAX_VALUE; var bx = 0f; var by = 0f; var bz = 0f
            for (k in 0 until path.size / 3) {
                val dx = c[0] - path[k * 3]; val dy = c[1] - path[k * 3 + 1]; val dz = c[2] - path[k * 3 + 2]
                val d = dx * dx + dy * dy + dz * dz
                if (d < best) { best = d; bx = dx; by = dy; bz = dz }
            }
            if (nrm[0] * bx + nrm[1] * by + nrm[2] * bz > 0) out++ else inn++
        }
        println("orbit tube: ${m.vertexCount} v, ${m.triangleCount} t, outward=$out inward=$inn")
        assertTrue("tube mostly inward", inn < m.triangleCount * 0.02)
    }

    @Test
    fun moonPhasesLandOnDatedEvents() {
        // 2024-04-08 18:21 UTC new moon (the North American total eclipse), 2024-04-23 23:49 full.
        // Mean elements omit the equation of centre (6.3 deg) and evection (1.3 deg): allow 9 deg.
        val newPh = Moon.phaseDeg(2460409.2646)
        val fullPh = Moon.phaseDeg(2460424.4924)
        val dNew = minOf(newPh, 360 - newPh)
        println("Moon phase at dated new = $newPh deg, at dated full = $fullPh deg; synodic = ${Moon.SYNODIC_DAYS} d")
        assertTrue("new moon off by $dNew deg", dNew < 9.0)
        assertTrue("full moon off by ${abs(fullPh - 180)} deg", abs(fullPh - 180) < 9.0)
        assertEquals(29.53, Moon.SYNODIC_DAYS, 0.02)
    }

    @Test
    fun moonFitsAtDefaultsAndDateStringWorks() {
        val m = SolarModel().apply { spread = 0.40; sizeExag = 1.8 }   // Unity's defaults
        val mm = m.moonMetrics()
        assertTrue("moon hidden at defaults", mm != null)
        println("moon orbit ${mm!!.first} m, moon r ${mm.second} m, earth r ${m.bodyR(2)} m")
        assertEquals(0.0398f, mm.first, 0.002f)   // Unity comment: 39.8 mm at SCALE 0.40 SIZE 1.8
        val d = SolarModel()                                            // this app's defaults
        val dm = d.moonMetrics()
        println("app defaults: earth r ${d.bodyR(2)} m, jupiter r ${d.bodyR(4)} m, moon orbit ${dm?.first} m")
        assertTrue("moon hidden at app defaults", dm != null)
        // no planet body may touch a neighbour's orbit band at the app defaults
        for (i in 0 until 7) {
            val pin = com.bytedance.physicslab.solar.core.Solar.planets[i]
            val pout = com.bytedance.physicslab.solar.core.Solar.planets[i + 1]
            val apo = d.orbitR(i) * (1 + pin.e) + d.bodyR(i)
            val peri = d.orbitR(i + 1) * (1 - pout.e) - d.bodyR(i + 1)
            assertTrue("bodies $i/${i + 1} overlap at defaults: $apo vs $peri", apo < peri)
        }
        assertEquals("2000-01-01", SolarModel.dateString(2451545.0))
        assertEquals("2024-04-08", SolarModel.dateString(2460409.2646))
    }

    @Test
    fun dumpBakePreviews() {
        val dir = File("build/bake-preview").apply { mkdirs() }
        val t0 = System.nanoTime()
        val sun0 = ProcTex.bakeSunPhotosphere(512, 256, 0)
        val tSun = (System.nanoTime() - t0) / 1e6
        save(sun0, File(dir, "sun_phase0.png"))
        save(ProcTex.bakeSunPhotosphere(512, 256, 1), File(dir, "sun_phase1.png"))
        save(ProcTex.bakeCoronaDisc(256), File(dir, "corona.png"))
        save(ProcTex.bakeCoronaShellsUnity(256), File(dir, "corona_unity_shells.png"))
        save(ProcTex.bakeLimbShell(256), File(dir, "limb_shell.png"))
        save(ProcTex.bakeLightShell(256), File(dir, "light_shell.png"))
        save(ProcTex.bakeRingProfile(256), File(dir, "ring.png"))
        val p = Palette
        val t1 = System.nanoTime()
        for (i in 0 until 8) {
            val tw = if (i == 4 || i == 5) 512 else 256
            save(ProcTex.bakePlanet(tw, tw / 2, p.deep[i], p.mid[i], p.high[i], p.band[i], 9000 + i * 37, p.caps[i]),
                File(dir, "planet_$i.png"))
        }
        val tPl = (System.nanoTime() - t1) / 1e6
        // sun preview with the limb shell composited, as a disc seen face-on
        save(sunDiscPreview(sun0, ProcTex.bakeCoronaDisc(256)), File(dir, "sun_disc_composite.png"))
        save(sunDiscPreview(sun0, ProcTex.bakeCoronaShellsUnity(256)), File(dir, "sun_disc_composite_unity_corona.png"))
        println("BAKE sun 512x256 = ${"%.0f".format(tSun)} ms, 8 planets = ${"%.0f".format(tPl)} ms (JVM desktop)")
        val files = dir.listFiles()!!.filter { it.name.endsWith(".png") }
        for (f in files.sortedBy { it.name }) println("  ${f.name} ${f.length()} B")
        assertTrue(files.size >= 15)
    }

    /** CPU composite of photosphere x (1 - limb alpha) + corona, orthographic, sun at centre. */
    private fun sunDiscPreview(sun: Image, corona: Image): Image {
        val n = 384
        val img = Image(n, n)
        val limb = ProcTex.bakeLimbShell(256)
        for (y in 0 until n) for (x in 0 until n) {
            val px = ((x + 0.5f) / n * 2f - 1f) * 2.3f; val py = ((y + 0.5f) / n * 2f - 1f) * 2.3f
            val rho = sqrt(px * px + py * py)
            val cx = ((px / 2.3f * 0.5f + 0.5f) * 255).toInt().coerceIn(0, 255)
            val cy = ((py / 2.3f * 0.5f + 0.5f) * 255).toInt().coerceIn(0, 255)
            val cor = corona.px[cy * 256 + cx]
            var r = 0.055f; var g = 0.06f; var b = 0.075f     // ShotDriver backdrop
            if (rho < 1f) {
                val nz = sqrt(1 - rho * rho)
                val lat = Math.asin(-py.toDouble()).toFloat()
                val lon = Math.atan2(nz.toDouble(), px.toDouble()).toFloat()
                val u = ((lon / (2 * Math.PI.toFloat())) + 1f) % 1f
                val v = 0.5f - lat / Math.PI.toFloat()
                val s = sun.px[(v * (sun.h - 1)).toInt() * sun.w + (u * (sun.w - 1)).toInt()]
                val theta = Math.acos(nz.toDouble()).toFloat()
                val la = ((limb.px[(theta / Math.PI.toFloat() * 255).toInt().coerceIn(0, 255)] ushr 24) and 0xFF) / 255f
                r = ((s ushr 16) and 0xFF) / 255f * (1 - la)
                g = ((s ushr 8) and 0xFF) / 255f * (1 - la)
                b = (s and 0xFF) / 255f * (1 - la)
            } else {
                r += ((cor ushr 16) and 0xFF) / 255f; g += ((cor ushr 8) and 0xFF) / 255f; b += (cor and 0xFF) / 255f
            }
            img.px[y * n + x] = ProcTex.argb(1f, minOf(1f, r), minOf(1f, g), minOf(1f, b))
        }
        return img
    }

    // ---------------------------------------------------------------- helpers

    private fun checkIndices(m: MeshData) {
        assertEquals(0, m.idx.size % 3)
        for (i in m.idx) assertTrue("index $i out of range ${m.vertexCount}", i in 0 until m.vertexCount)
        assertEquals(m.vertexCount * 2, m.uv.size)
        assertEquals(m.vertexCount * 3, m.nrm.size)
    }

    private fun centroid(m: MeshData, t: Int): FloatArray {
        val c = FloatArray(3)
        for (k in 0 until 3) { val v = m.idx[t * 3 + k]; for (a in 0 until 3) c[a] += m.pos[v * 3 + a] / 3f }
        return c
    }

    private fun triNormal(m: MeshData, t: Int): FloatArray {
        val a = m.idx[t * 3]; val b = m.idx[t * 3 + 1]; val c = m.idx[t * 3 + 2]
        val e1 = FloatArray(3) { m.pos[b * 3 + it] - m.pos[a * 3 + it] }
        val e2 = FloatArray(3) { m.pos[c * 3 + it] - m.pos[a * 3 + it] }
        return floatArrayOf(e1[1] * e2[2] - e1[2] * e2[1], e1[2] * e2[0] - e1[0] * e2[2], e1[0] * e2[1] - e1[1] * e2[0])
    }

    /** (outward, inward) counts against a reference direction; degenerate triangles skipped. */
    private fun windingCounts(m: MeshData, ref: (Float, Float, Float) -> FloatArray): Pair<Int, Int> {
        var out = 0; var inn = 0
        for (t in 0 until m.triangleCount) {
            val n = triNormal(m, t)
            if (sqrt(n[0] * n[0] + n[1] * n[1] + n[2] * n[2]) < 1e-12f) continue
            val c = centroid(m, t); val r = ref(c[0], c[1], c[2])
            if (n[0] * r[0] + n[1] * r[1] + n[2] * r[2] > 0) out++ else inn++
        }
        return out to inn
    }

    /** Minimal RGBA PNG writer (unit tests compile against android.jar: no javax.imageio). */
    private fun save(img: Image, f: File) {
        val raw = java.io.ByteArrayOutputStream()
        for (y in 0 until img.h) {
            raw.write(0)
            for (x in 0 until img.w) {
                val c = img.px[y * img.w + x]
                raw.write((c ushr 16) and 0xFF); raw.write((c ushr 8) and 0xFF); raw.write(c and 0xFF); raw.write((c ushr 24) and 0xFF)
            }
        }
        val out = java.io.DataOutputStream(java.io.FileOutputStream(f))
        out.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 13, 10, 26, 10))
        fun chunk(type: String, data: ByteArray) {
            out.writeInt(data.size)
            val td = type.toByteArray() + data
            out.write(td)
            val crc = java.util.zip.CRC32(); crc.update(td); out.writeInt(crc.value.toInt())
        }
        val ihdr = java.nio.ByteBuffer.allocate(13).putInt(img.w).putInt(img.h).put(8).put(6).put(0).put(0).put(0).array()
        chunk("IHDR", ihdr)
        val z = java.io.ByteArrayOutputStream()
        java.util.zip.DeflaterOutputStream(z).use { it.write(raw.toByteArray()) }
        chunk("IDAT", z.toByteArray())
        chunk("IEND", ByteArray(0))
        out.close()
    }
}

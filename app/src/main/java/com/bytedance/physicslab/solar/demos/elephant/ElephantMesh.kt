// Small mesh/texture helpers for the elephant-toothpaste bench (kept in this package on purpose):
// static parts are MERGED into one mesh per object (node budget: the foam pools need the nodes),
// chart curves are flat ribbons (2 vertices per point — cheap to re-upload), a unit quad + a soft
// radial sprite for steam, and two tiny textures for the foam inside the cylinder.
// Same conventions as render/MeshGen: plain arrays, CCW outward winding, no SDK types.

package com.bytedance.physicslab.solar.demos.elephant

import com.bytedance.physicslab.solar.render.Image
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.ProcTex
import com.bytedance.physicslab.solar.render.Rgb
import com.bytedance.physicslab.solar.render.V3
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object ElephantMesh {

    /** One placed copy of a mesh: scaled per axis, then translated. */
    class Part(val mesh: MeshData, val tx: Float, val ty: Float, val tz: Float, val sx: Float = 1f, val sy: Float = 1f, val sz: Float = 1f)

    /** Concatenate parts into one mesh (normals renormalised for the per-axis scale). */
    fun merge(parts: List<Part>): MeshData {
        var nv = 0; var ni = 0
        for (p in parts) { nv += p.mesh.vertexCount; ni += p.mesh.idx.size }
        val pos = FloatArray(nv * 3); val nrm = FloatArray(nv * 3); val uv = FloatArray(nv * 2); val idx = IntArray(ni)
        var v0 = 0; var i0 = 0
        for (p in parts) {
            val m = p.mesh
            for (k in 0 until m.vertexCount) {
                val o = (v0 + k) * 3
                pos[o] = m.pos[k * 3] * p.sx + p.tx; pos[o + 1] = m.pos[k * 3 + 1] * p.sy + p.ty; pos[o + 2] = m.pos[k * 3 + 2] * p.sz + p.tz
                var nx = m.nrm[k * 3] / p.sx; var ny = m.nrm[k * 3 + 1] / p.sy; var nz = m.nrm[k * 3 + 2] / p.sz
                val l = sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(1e-12f); nx /= l; ny /= l; nz /= l
                nrm[o] = nx; nrm[o + 1] = ny; nrm[o + 2] = nz
                uv[(v0 + k) * 2] = m.uv[k * 2]; uv[(v0 + k) * 2 + 1] = m.uv[k * 2 + 1]
            }
            for (k in m.idx.indices) idx[i0 + k] = m.idx[k] + v0
            v0 += m.vertexCount; i0 += m.idx.size
        }
        return MeshData(pos, nrm, uv, idx)
    }

    /** Open glass tube (side wall only) along +Y from 0 to [h]. */
    fun openTube(r: Float, h: Float, seg: Int) = MeshGen.tube(floatArrayOf(0f, 0f, 0f, 0f, h, 0f), r, seg, false)

    /** A ring (closed tube) of radius [r] in the XZ plane at height [y]. */
    fun ring(r: Float, y: Float, wire: Float, seg: Int): MeshData {
        val p = FloatArray(seg * 3) { k -> val a = 2.0 * Math.PI * (k / 3) / seg; when (k % 3) { 0 -> (r * cos(a)).toFloat(); 1 -> y; else -> (r * sin(a)).toFloat() } }
        return MeshGen.tube(p, wire, 8, true)
    }

    // ---------------------------------------------------------------- ribbons (chart curves)

    /** Flat strip of [n] points in a z = const plane: 2 vertices per point, normal +Z. */
    fun ribbon(n: Int): MeshData {
        val pos = FloatArray(n * 2 * 3); val nrm = FloatArray(n * 2 * 3); val uv = FloatArray(n * 2 * 2)
        for (k in 0 until n * 2) { nrm[k * 3 + 2] = 1f; uv[k * 2] = (k / 2).toFloat() / (n - 1); uv[k * 2 + 1] = (k % 2).toFloat() }
        val idx = IntArray((n - 1) * 6); var t = 0
        for (i in 0 until n - 1) {
            val a = i * 2; val b = a + 2
            idx[t++] = a; idx[t++] = b; idx[t++] = a + 1
            idx[t++] = a + 1; idx[t++] = b; idx[t++] = b + 1
        }
        return MeshData(pos, nrm, uv, idx)
    }

    /** Rewrite a ribbon in place along (xs, ys) at depth [z], half width [hw]. */
    fun ribbonInto(md: MeshData, xs: FloatArray, ys: FloatArray, z: Float, hw: Float) {
        val n = xs.size
        var px = 0f; var py = 1f                               // last good normal (kept across collapsed points)
        for (i in 0 until n) {
            val i0 = maxOf(0, i - 1); val i1 = minOf(n - 1, i + 1)
            val tx = xs[i1] - xs[i0]; val ty = ys[i1] - ys[i0]
            val l = sqrt(tx * tx + ty * ty)
            if (l > 1e-6f) { px = -ty / l; py = tx / l }
            val o = i * 6
            md.pos[o] = xs[i] - px * hw; md.pos[o + 1] = ys[i] - py * hw; md.pos[o + 2] = z
            md.pos[o + 3] = xs[i] + px * hw; md.pos[o + 4] = ys[i] + py * hw; md.pos[o + 5] = z
        }
    }

    // ---------------------------------------------------------------- sprites and textures

    /** Unit quad in the XY plane facing +Z (billboards). */
    fun quad(): MeshData = MeshData(
        floatArrayOf(-0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
        intArrayOf(0, 1, 2, 0, 2, 3))

    /** Soft round puff (white, alpha falls off smoothly): steam wisps. */
    fun puff(n: Int = 48): Image {
        val img = Image(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val r = sqrt(dx * dx + dy * dy)
            val wisp = 0.75f + 0.25f * ProcTex.fbm(V3(dx * 2.2f, dy * 2.2f, 0.7f), 3, 9)
            val a = (ProcTex.smooth01(1f - r) * wisp).coerceIn(0f, 1f)
            img.px[y * n + x] = ProcTex.argb(a * a, 1f, 1f, 1f)
        }
        return img
    }

    /**
     * Foam inside the cylinder, wrapped around its side (u = angle): bubbly white cells, and with
     * [stripes] the food-colour bands dripped down the inner wall (vertical stripes along the column).
     */
    fun foamTex(stripes: List<Rgb>?, w: Int = 96, h: Int = 32, fu: Float = 14f, fv: Float = 5f): Image {
        val img = Image(w, h)
        for (y in 0 until h) for (x in 0 until w) {
            val u = (x + 0.5f) / w; val v = (y + 0.5f) / h
            val (f1, f2) = ProcTex.worley(V3(u * fu, v * fv, 0.3f), 21)
            val cell = ProcTex.smooth01((f2 - f1) * 4.5f)                      // bright cell interiors, dark films
            var c = Rgb(0.80f + 0.20f * cell, 0.80f + 0.20f * cell, 0.80f + 0.20f * cell)
            if (stripes != null) {
                val k = (u * stripes.size).toInt().coerceIn(0, stripes.size - 1)
                val edge = ProcTex.smooth01(minOf(u * stripes.size - k, k + 1 - u * stripes.size) * 6f)
                c = ProcTex.okLerp(c, Rgb(stripes[k].r * (0.85f + 0.15f * cell), stripes[k].g * (0.85f + 0.15f * cell), stripes[k].b * (0.85f + 0.15f * cell)), edge)
            }
            img.px[y * w + x] = ProcTex.argb(1f, c.r, c.g, c.b)
        }
        return img
    }
}

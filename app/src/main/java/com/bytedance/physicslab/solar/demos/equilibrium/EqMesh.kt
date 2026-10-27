// Geometry and textures for the equilibrium bench: ball molecules built from the published
// structures (core/equilibrium/MolGeom), one mesh per species so a molecule is ONE node (atoms are
// coloured through constant UVs into a 4-texel CPK strip), a soft sprite for flashes and steam.

package com.bytedance.physicslab.solar.demos.equilibrium

import com.bytedance.physicslab.solar.core.equilibrium.MolGeom
import com.bytedance.physicslab.solar.render.Image
import com.bytedance.physicslab.solar.render.MeshData
import com.bytedance.physicslab.solar.render.MeshGen
import com.bytedance.physicslab.solar.render.ProcTex
import kotlin.math.exp

internal object EqMesh {
    /** Texel centres of [atomTex]: N blue, O red, bond grey. */
    const val U_N = 0.125f
    const val U_O = 0.375f
    const val U_BOND = 0.625f

    /** 4 x 2 CPK strip (sRGB): nitrogen blue, oxygen red, the N–N bond pale gold (makes N₂O₄ read at a glance), white. */
    fun atomTex(): Image {
        val img = Image(4, 2)
        val c = intArrayOf(
            ProcTex.argb(1f, 0.26f, 0.46f, 1.00f), ProcTex.argb(1f, 0.95f, 0.22f, 0.16f),
            ProcTex.argb(1f, 1.00f, 0.93f, 0.62f), ProcTex.argb(1f, 1f, 1f, 1f))
        for (y in 0 until 2) for (x in 0 until 4) img.px[y * 4 + x] = c[x]
        return img
    }

    /** Merge spheres (atoms, xyz in Å, scaled by [s] m/Å) with constant UVs into one mesh. */
    private fun merge(parts: List<MeshData>): MeshData {
        val nv = parts.sumOf { it.vertexCount }; val ni = parts.sumOf { it.idx.size }
        val pos = FloatArray(nv * 3); val nrm = FloatArray(nv * 3); val uv = FloatArray(nv * 2); val idx = IntArray(ni)
        var v = 0; var t = 0
        for (p in parts) {
            System.arraycopy(p.pos, 0, pos, v * 3, p.pos.size); System.arraycopy(p.nrm, 0, nrm, v * 3, p.nrm.size)
            System.arraycopy(p.uv, 0, uv, v * 2, p.uv.size)
            for (k in p.idx.indices) idx[t + k] = p.idx[k] + v
            v += p.vertexCount; t += p.idx.size
        }
        return MeshData(pos, nrm, uv, idx)
    }

    private fun atom(x: Float, y: Float, z: Float, r: Float, u: Float): MeshData {
        val m = MeshGen.sphere(r, 7, 12, MeshGen.SphereUv.EQUIRECT)
        for (k in 0 until m.vertexCount) { m.pos[k * 3] += x; m.pos[k * 3 + 1] += y; m.pos[k * 3 + 2] += z; m.uv[k * 2] = u; m.uv[k * 2 + 1] = 0.5f }
        return m
    }

    const val R_N = 0.0128f
    const val R_O = 0.0122f

    /** NO₂ (N toward −x, O's toward +x, centre of mass at the origin), [s] metres per Å. */
    fun no2(s: Double): MeshData {
        val a = MolGeom.no2Atoms()
        return merge(listOf(atom((a[0] * s).toFloat(), (a[1] * s).toFloat(), 0f, R_N, U_N),
            atom((a[3] * s).toFloat(), (a[4] * s).toFloat(), 0f, R_O, U_O),
            atom((a[6] * s).toFloat(), (a[7] * s).toFloat(), 0f, R_O, U_O)))
    }

    /** N₂O₄ (planar, N–N along x) with its long weak N–N bond drawn as a thin grey stick. */
    fun n2o4(s: Double): MeshData {
        val a = MolGeom.n2o4Atoms()
        val parts = ArrayList<MeshData>()
        for (k in 0 until 6) parts += atom((a[k * 3] * s).toFloat(), (a[k * 3 + 1] * s).toFloat(), 0f, if (k < 2) R_N else R_O, if (k < 2) U_N else U_O)
        val len = (a[0] - a[3]).toFloat() * s.toFloat()
        val stick = MeshGen.cylinder(0.0074f, len + 0.004f, 12)
        // cylinder along +Y -> along +X: (x, y) -> (y, -x)
        for (k in 0 until stick.vertexCount) {
            val x = stick.pos[k * 3]; val y = stick.pos[k * 3 + 1]; stick.pos[k * 3] = y; stick.pos[k * 3 + 1] = -x
            val nx = stick.nrm[k * 3]; val ny = stick.nrm[k * 3 + 1]; stick.nrm[k * 3] = ny; stick.nrm[k * 3 + 1] = -nx
            stick.uv[k * 2] = U_BOND; stick.uv[k * 2 + 1] = 0.5f
        }
        parts += stick
        return merge(parts)
    }

    /** Soft round sprite (white, gaussian alpha) for reaction flashes and steam. */
    fun blob(n: Int = 64, sharp: Float = 3.2f): Image {
        val img = Image(n, n)
        for (y in 0 until n) for (x in 0 until n) {
            val dx = (x + 0.5f) / n * 2 - 1; val dy = (y + 0.5f) / n * 2 - 1
            val r2 = dx * dx + dy * dy
            val a = if (r2 >= 1f) 0f else exp(-sharp * r2) * (1 - r2)
            img.px[y * n + x] = ProcTex.argb(a, 1f, 1f, 1f)
        }
        return img
    }

    /** Unit quad in the XY plane facing +Z (billboards). */
    fun quad(): MeshData = MeshData(
        floatArrayOf(-0.5f, -0.5f, 0f, 0.5f, -0.5f, 0f, 0.5f, 0.5f, 0f, -0.5f, 0.5f, 0f),
        floatArrayOf(0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f, 0f, 0f, 1f),
        floatArrayOf(0f, 1f, 1f, 1f, 1f, 0f, 0f, 0f),
        intArrayOf(0, 1, 2, 0, 2, 3))

    /** Open vertical glass tube of radius [r] from y0 to y1 (no caps). */
    fun openTube(r: Float, y0: Float, y1: Float, sides: Int = 40) = MeshGen.tube(floatArrayOf(0f, y0, 0f, 0f, y1, 0f), r, sides, false)

    /** linear -> sRGB (one channel). */
    fun srgb(v: Double): Float {
        val c = v.coerceIn(0.0, 1.0)
        return (if (c <= 0.0031308) 12.92 * c else 1.055 * Math.pow(c, 1 / 2.4) - 0.055).toFloat()
    }
}

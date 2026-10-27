package com.bytedance.physicslab.solar.core.flame

import com.bytedance.physicslab.solar.demos.flame.FlameDemo
import com.bytedance.physicslab.solar.lab.Lang
import com.bytedance.physicslab.solar.lab.spec.Mat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The experiment as a user drives it: dip, look, raise the glass, turn the gas, play the mystery round. */
class FlameDemoFlowTest {
    private val cjk = Regex("[\\u4e00-\\u9fff]")
    private fun run(s: Float) = repeat((s * 60).toInt()) { FlameDemo.update(1f / 60f) }
    private fun plume() = FlameDemo.root!!.find("Plume0")!!
    private fun tap(n: String): String { FlameDemo.onTap(n); FlameDemo.update(1f / 60f); return FlameDemo.tapMessage(n) ?: "" }

    @After fun back() = Lang.set(false)

    @Test
    fun dipCobaltGasAndMystery() {
        Lang.set(false)
        // bake cost: an empty cache (first launch) vs a second visit (BakeCache hit)
        val tmp = java.nio.file.Files.createTempDirectory("flamebake").toFile()
        val t0 = System.nanoTime(); FlameDemo.buildSpec(tmp)
        val t1 = System.nanoTime(); FlameDemo.buildSpec(tmp)
        println("FLOW buildSpec: empty cache %.0f ms, cached %.0f ms".format((t1 - t0) / 1e6, (System.nanoTime() - t1) / 1e6))
        tmp.deleteRecursively()
        FlameDemo.runHeadless(File("build/review-cache").apply { mkdirs() }, 2f)
        assertTrue(FlameDemo.readoutText, FlameDemo.readoutText.contains("670.8"))      // opens on LiCl
        // device budget: node count, visible nodes, no per-frame mesh edits (none are ever made)
        var all = 0; var vis = 0
        fun walk(n: com.bytedance.physicslab.solar.lab.spec.Node, on: Boolean) { all++; val v = on && n.visible; if (v) vis++; n.children.forEach { walk(it, v) } }
        walk(FlameDemo.root!!, true)
        println("FLOW nodes: $all total, $vis visible")
        assertTrue(vis < 600)

        // dip CuCl2, then LiCl: the loop travels to the dish, dips, comes back -> the flame takes the salt's colour
        val cu = tap("Dish6"); run(4.5f)
        val cc = (plume().mat as Mat.Unlit).color
        println("FLOW dip CuCl2 -> \"$cu\", plume colour $cc")
        assertTrue("CuCl2 plume should be blue-green: $cc", cc.g > 0.9f && cc.b > 0.6f && cc.r < 0.1f)
        val msg = tap("Dish2")
        println("FLOW dip LiCl -> \"$msg\"")
        assertTrue(msg.contains("670.8"))
        run(0.5f)
        assertFalse("the loop is still on its way", FlameDemo.readoutText.contains("670.8"))
        run(4f)
        println("FLOW readout after LiCl:\n${FlameDemo.readoutText}")
        assertTrue(FlameDemo.readoutText.contains("670.8") && FlameDemo.readoutText.contains("1.848 eV"))
        val c = (plume().mat as Mat.Unlit).color
        assertTrue("LiCl plume should be red: $c", c.r > 0.9f && c.g < 0.1f && plume().visible)

        // cobalt glass up: the rig travels in front of the flame and the readout explains the filter
        tap("Cobalt"); run(1.5f)
        assertTrue(FlameDemo.cobaltUp)
        val rig = FlameDemo.root!!.find("CobaltRig")!!.pos
        assertEquals(-0.12f, rig.x, 0.01f); assertTrue(rig.z > 0.15f)
        assertTrue(FlameDemo.readoutText.lines()[2].contains("钴玻璃"))
        tap("Cobalt"); run(1.5f)

        // gas off: no flame, no colour; on again: the salt is still on the loop
        tap("GasTap"); run(1f)
        assertFalse(plume().visible); assertTrue(FlameDemo.readoutText.startsWith("本生灯已关"))
        tap("GasTap"); run(1.5f)
        assertTrue(plume().visible)

        // the mystery round: first unknown is NaCl + KCl
        val m0 = tap("DishQ"); run(4f)
        assertTrue(FlameDemo.mysteryOn); assertTrue(m0.isNotEmpty())
        assertTrue(FlameDemo.readoutText, FlameDemo.readoutText.startsWith("未知样品"))
        assertTrue(FlameDemo.tapTargets.any { it.node == "Card0" })
        assertFalse("the unknown's name must not be shown", FlameDemo.readoutText.lines()[0].contains("钠"))
        tap("Cobalt"); run(1.5f)
        println("FLOW mystery through cobalt glass:\n${FlameDemo.readoutText}")
        assertFalse("the glass line must not name the unknown", FlameDemo.readoutText.contains("钠") || FlameDemo.readoutText.contains("钾"))
        tap("Cobalt"); run(1.5f)
        val wrong = tap("Card2")
        val right1 = tap("Card0")
        assertFalse(FlameDemo.solved)
        val right2 = tap("Card1")
        println("FLOW mystery: \"$wrong\" / \"$right1\" / \"$right2\"\n${FlameDemo.readoutText}")
        assertTrue(wrong.startsWith("✗") && right1.startsWith("✓") && right2.startsWith("✓"))
        assertTrue(FlameDemo.solved); assertTrue(FlameDemo.readoutText.lines()[1].contains("Na + K"))

        // English: every note and the readout are free of Chinese
        Lang.set(true); FlameDemo.update(1f / 60f)
        for (n in listOf("Dish0", "Dish6", "DishQ", "GasTap", "Cobalt", "Card3")) {
            val t = FlameDemo.tapMessage(n); assertNotNull(t); assertFalse("$n: $t", cjk.containsMatchIn(t!!))
        }
        assertFalse(FlameDemo.readoutText, cjk.containsMatchIn(FlameDemo.readoutText))
        FlameDemo.labels.forEach { assertFalse(it.text, cjk.containsMatchIn(it.text)) }
    }
}

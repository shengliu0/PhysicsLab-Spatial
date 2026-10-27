package com.bytedance.physicslab.solar.lab

import com.bytedance.physicslab.solar.demos.brachistochrone.BrachistochroneDemo
import com.bytedance.physicslab.solar.demos.buoyancy.BuoyancyDemo
import com.bytedance.physicslab.solar.demos.cyclotron.CyclotronDemo
import com.bytedance.physicslab.solar.demos.expansion.ExpansionDemo
import com.bytedance.physicslab.solar.demos.faraday.FaradayDemo
import com.bytedance.physicslab.solar.demos.gas.GasDemo
import com.bytedance.physicslab.solar.demos.pendulum.PendulumDemo
import com.bytedance.physicslab.solar.demos.photoelectric.PhotoelectricDemo
import com.bytedance.physicslab.solar.demos.prism.PrismDemo
import com.bytedance.physicslab.solar.demos.solar.SolarDemo
import com.bytedance.physicslab.solar.demos.young.YoungDemo

/** Subjects = the catalogue's tabs. */
enum class Subject(private val zh: String, private val en: String) {
    MECH("力学", "Mechanics"), OPTICS("光学", "Optics"), HEAT("热学", "Heat"), EM("电磁", "E&M"), MODERN("近代", "Modern");  // i18n-ok (zh/en pairs)
    val label: String get() = tr(zh, en)
}

/**
 * The catalogue. Order inside a subject = order on the cards. Adding a demo = one line here, plus a
 * thumbnail (render it with LabReviewTest, then `python3 tools/make_thumbs.py`).
 */
object DemoRegistry {
    val catalog: List<Pair<Demo, Subject>> = listOf(
        SolarDemo to Subject.MECH,            // 太阳系 · 开普勒定律      Kepler 1609
        PendulumDemo to Subject.MECH,         // 伽利略单摆               Galileo 1602
        BrachistochroneDemo to Subject.MECH,  // 最速降线                 Bernoulli 1696
        BuoyancyDemo to Subject.MECH,         // 阿基米德浮力             Archimedes
        YoungDemo to Subject.OPTICS,          // 杨氏双缝干涉             Young 1801
        PrismDemo to Subject.OPTICS,          // 牛顿棱镜色散             Newton 1666
        GasDemo to Subject.HEAT,              // 气体分子运动论           Boltzmann 1872
        ExpansionDemo to Subject.HEAT,        // 热胀冷缩 · 铜球与环      's Gravesande 1720
        CyclotronDemo to Subject.EM,          // 回旋加速器 · 洛伦兹力    Lawrence 1932
        FaradayDemo to Subject.EM,            // 法拉第电磁感应           Faraday 1831
        PhotoelectricDemo to Subject.MODERN,  // 光电效应                 Einstein 1905
    )
    val all: List<Demo> = catalog.map { it.first }

    /** English titles for the catalogue / top bar (each demo's own `title` is the Chinese one). */
    private val TITLE_EN = mapOf(
        "solar" to "Solar System · Kepler's Laws",
        "pendulum" to "Galileo's Pendulum",
        "brachistochrone" to "Brachistochrone",
        "buoyancy" to "Archimedes' Buoyancy",
        "young" to "Young's Double Slit",
        "prism" to "Newton's Prism",
        "gas" to "Kinetic Theory of Gases",
        "expansion" to "Thermal Expansion · Ball & Ring",
        "cyclotron" to "Cyclotron · Lorentz Force",
        "faraday" to "Faraday's Induction",
        "photoelectric" to "Photoelectric Effect",
    )
    fun titleOf(d: Demo): String = tr(d.title, TITLE_EN[d.id] ?: d.title)
    /** Catalogue-card title: one line in a 225 dp card, so English drops its " · subtitle". */
    fun cardTitleOf(d: Demo): String = tr(d.title, (CARD_EN[d.id] ?: TITLE_EN[d.id] ?: d.title).substringBefore(" · "))
    private val CARD_EN = mapOf("gas" to "Kinetic Theory", "buoyancy" to "Buoyancy", "faraday" to "Faraday Induction")
    fun subjectOf(id: String) = catalog.firstOrNull { it.first.id == id }?.second
}

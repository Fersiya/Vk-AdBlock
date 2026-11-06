package com.vkadblock.data

import android.content.Context

/**
 * Менеджер списка блокировки рекламных доменов.
 * Содержит все известные рекламные домены VK / Mail.ru / MyTarget.
 */
class BlocklistManager(context: Context) {

    private val prefs = context.getSharedPreferences("blocklist_prefs", Context.MODE_PRIVATE)
    private val customDomains = mutableSetOf<String>()
    private val disabledDomains = mutableSetOf<String>()

    /**
     * Встроенный список рекламных доменов VK
     */
    private val builtInDomains = setOf(
        // === Рекламная сеть VK ===
        "ads.vk.com",
        "ad.vk.com",
        "api.ad.vk.com",
        "pads.vk.com",

        // === Mail.ru рекламная сеть ===
        "ad.mail.ru",
        "ads.mail.ru",
        "r.mail.ru",
        "rs.mail.ru",
        "rg.mail.ru",
        "c.mail.ru",
        "pixel.mail.ru",

        // === Mail.ru трекеры ===
        "top.mail.ru",
        "top-fwz1.mail.ru",
        "rating.mail.ru",
        "stat.radar.imgsmail.ru",
        "radar.imgsmail.ru",
        "b.radar.imgsmail.ru",
        "ad.imgsmail.ru",

        // === MyTarget (рекламная платформа VK) ===
        "target.my.com",
        "target-sandbox.my.com",
        "ad.target.my.com",
        "jsn.target.my.com",
        "jscdn.target.my.com",
        "r.target.my.com",

        // === VK DMP (Data Management Platform) ===
        "dmp.mail.ru",
        "dmp.one.mail.ru",
        "sync.1dmp.io",

        // === VK RTB (Real-Time Bidding) ===
        "rtb.mail.ru",
        "bidder.mail.ru",

        // === VK Retargeting пиксель ===
        "vk.com/rtrg",

        // === Счётчики / Аналитика ===
        "counter.yadro.ru",
        "an.yandex.ru",
        "informer.yandex.ru",
        "adfox.yandex.ru",
        "matchid.adfox.yandex.ru",
        "banners.adfox.ru",

        // === Внешние рекламные сети (используются VK) ===
        "ssp.rambler.ru",
        "exchange.buzzoola.com",
        "serving.buzzoola.com",
        "score.addapptr.com",
        "smi2.ru",
        "img.smi2.ru",
        "js.smi2.ru",
        "reklama.mirtesen.ru",

        // === Трекеры мобильных приложений ===
        "track.mytracker.com",
        "tracker.my.com",
        "mytopf.com",
        "vistat.mobi",
        "t.myvisualiq.net",

        // === Прочие рекламные / аналитические домены VK ===
        "push.vk.com",
        "st.top.mail.ru",
        "sentry.my.com",

        // === Appsflyer (трекинг установок) ===
        "impression.appsflyer.com",
        "app.appsflyer.com",
        "onelink.me",
        "click.onelink.me",

        // === Adjust (мобильный трекинг) ===
        "app.adjust.com",
        "view.adjust.com"
    )

    init {
        loadCustomDomains()
        loadDisabledDomains()
    }

    /**
     * Проверяет, заблокирован ли домен.
     * Поддерживает wildcard: если заблокирован "mail.ru",
     * то будет заблокирован и "ad.mail.ru"
     */
    fun isBlocked(domain: String): Boolean {
        val d = domain.lowercase().trim()
        if (d.isEmpty()) return false
        if (d in disabledDomains) return false

        // Прямое совпадение
        if (d in builtInDomains || d in customDomains) return true

        // Wildcard: проверяем родительские домены
        val parts = d.split(".")
        for (i in 1 until parts.size) {
            val parent = parts.subList(i, parts.size).joinToString(".")
            if (parent in builtInDomains || parent in customDomains) {
                // Не блокируем основные домены VK
                if (parent in WHITELIST) continue
                return true
            }
        }

        return false
    }

    fun addCustomDomain(domain: String) {
        customDomains.add(domain.lowercase().trim())
        saveCustomDomains()
    }

    fun removeCustomDomain(domain: String) {
        customDomains.remove(domain.lowercase().trim())
        saveCustomDomains()
    }

    fun getBlockedDomains(): Set<String> = builtInDomains + customDomains - disabledDomains

    fun getBuiltInCount(): Int = builtInDomains.size

    fun getCustomDomains(): Set<String> = customDomains.toSet()

    private fun saveCustomDomains() {
        prefs.edit().putStringSet("custom_domains", customDomains).apply()
    }

    private fun loadCustomDomains() {
        customDomains.addAll(prefs.getStringSet("custom_domains", emptySet()) ?: emptySet())
    }

    private fun saveDisabledDomains() {
        prefs.edit().putStringSet("disabled_domains", disabledDomains).apply()
    }

    private fun loadDisabledDomains() {
        disabledDomains.addAll(prefs.getStringSet("disabled_domains", emptySet()) ?: emptySet())
    }

    companion object {
        /** Домены, которые нельзя блокировать (основной функционал VK) */
        private val WHITELIST = setOf(
            "vk.com",
            "vk.ru",
            "vk.me",
            "m.vk.com",
            "api.vk.com",
            "oauth.vk.com",
            "login.vk.com",
            "id.vk.com",
            "st.vk.com",
            "sun1-1.userapi.com",
            "pp.userapi.com",
            "vk.userapi.com",
            "mail.ru",
            "e.mail.ru",
            "my.com"
        )
    }
}

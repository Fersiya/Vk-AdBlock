package com.vkadblock.data

import android.content.Context

/**
 * Менеджер статистики — считает заблокированные и пропущенные DNS-запросы.
 */
class StatsManager(context: Context) {

    private val prefs = context.getSharedPreferences("stats_prefs", Context.MODE_PRIVATE)

    @Volatile
    private var blockedCount: Long = 0

    @Volatile
    private var allowedCount: Long = 0

    init {
        blockedCount = prefs.getLong("blocked_count", 0)
        allowedCount = prefs.getLong("allowed_count", 0)
    }

    fun incrementBlocked() {
        blockedCount++
        if (blockedCount % 10 == 0L) save() // Сохраняем каждые 10 запросов
    }

    fun incrementAllowed() {
        allowedCount++
    }

    fun getBlockedCount(): Long = blockedCount

    fun getAllowedCount(): Long = allowedCount

    fun getTotalCount(): Long = blockedCount + allowedCount

    fun resetStats() {
        blockedCount = 0
        allowedCount = 0
        save()
    }

    fun save() {
        prefs.edit()
            .putLong("blocked_count", blockedCount)
            .putLong("allowed_count", allowedCount)
            .apply()
    }
}

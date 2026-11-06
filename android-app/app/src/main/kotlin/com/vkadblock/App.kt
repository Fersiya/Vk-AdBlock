package com.vkadblock

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

class App : Application() {

    companion object {
        const val VPN_NOTIFICATION_CHANNEL = "vpn_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VPN_NOTIFICATION_CHANNEL,
                "VPN Защита",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Уведомление о работе VPN-блокировщика рекламы"
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }
}

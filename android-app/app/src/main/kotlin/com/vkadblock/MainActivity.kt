package com.vkadblock

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.card.MaterialCardView
import com.google.android.material.materialswitch.MaterialSwitch
import com.vkadblock.data.BlocklistManager
import com.vkadblock.data.StatsManager
import com.vkadblock.vpn.AdBlockVpnService

class MainActivity : AppCompatActivity() {

    private lateinit var switchProtection: MaterialSwitch
    private lateinit var tvStatus: TextView
    private lateinit var tvBlocked: TextView
    private lateinit var tvDomains: TextView
    private lateinit var tvDescription: TextView
    private lateinit var cardStatus: MaterialCardView
    private lateinit var btnReset: Button

    private lateinit var statsManager: StatsManager
    private lateinit var blocklistManager: BlocklistManager
    private val handler = Handler(Looper.getMainLooper())
    private var statsUpdater: Runnable? = null

    private val vpnPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            startVpnService()
        } else {
            switchProtection.isChecked = false
            Toast.makeText(this, "Для работы нужно разрешение VPN", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statsManager = StatsManager(this)
        blocklistManager = BlocklistManager(this)

        initViews()
        requestNotificationPermission()
        updateUI()
        startStatsUpdater()
    }

    private fun initViews() {
        switchProtection = findViewById(R.id.switchProtection)
        tvStatus = findViewById(R.id.tvStatus)
        tvBlocked = findViewById(R.id.tvBlocked)
        tvDomains = findViewById(R.id.tvDomains)
        tvDescription = findViewById(R.id.tvDescription)
        cardStatus = findViewById(R.id.cardStatus)
        btnReset = findViewById(R.id.btnReset)

        switchProtection.isChecked = AdBlockVpnService.isRunning

        switchProtection.setOnCheckedChangeListener { _, isChecked ->
            if (isChecked) {
                requestVpnPermission()
            } else {
                stopVpnService()
            }
        }

        btnReset.setOnClickListener {
            statsManager.resetStats()
            updateStats()
            Toast.makeText(this, "Статистика сброшена", Toast.LENGTH_SHORT).show()
        }

        tvDomains.text = "Доменов в списке: ${blocklistManager.getBuiltInCount()}"
    }

    private fun requestVpnPermission() {
        val intent = VpnService.prepare(this)
        if (intent != null) {
            vpnPermissionLauncher.launch(intent)
        } else {
            startVpnService()
        }
    }

    private fun startVpnService() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_START
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        updateUI()
    }

    private fun stopVpnService() {
        val intent = Intent(this, AdBlockVpnService::class.java).apply {
            action = AdBlockVpnService.ACTION_STOP
        }
        startService(intent)
        statsManager.save()
        // Даём время на остановку
        handler.postDelayed({ updateUI() }, 500)
    }

    private fun updateUI() {
        val isActive = AdBlockVpnService.isRunning
        switchProtection.isChecked = isActive

        if (isActive) {
            tvStatus.text = "🛡️ Защита активна"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_active))
            tvDescription.text = "VPN-фильтр перехватывает DNS-запросы и блокирует рекламные домены ВКонтакте"
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_active))
        } else {
            tvStatus.text = "⛔ Защита отключена"
            tvStatus.setTextColor(ContextCompat.getColor(this, R.color.status_inactive))
            tvDescription.text = "Включите защиту для блокировки рекламы в приложении ВКонтакте"
            cardStatus.setCardBackgroundColor(ContextCompat.getColor(this, R.color.card_inactive))
        }

        updateStats()
    }

    private fun updateStats() {
        tvBlocked.text = formatNumber(statsManager.getBlockedCount())
    }

    private fun formatNumber(n: Long): String {
        return when {
            n >= 1_000_000 -> String.format("%.1fM", n / 1_000_000.0)
            n >= 1_000 -> String.format("%.1fK", n / 1_000.0)
            else -> n.toString()
        }
    }

    private fun startStatsUpdater() {
        statsUpdater = object : Runnable {
            override fun run() {
                if (AdBlockVpnService.isRunning) {
                    updateStats()
                }
                // Обновляем состояние переключателя если VPN был отключён извне
                if (switchProtection.isChecked != AdBlockVpnService.isRunning) {
                    updateUI()
                }
                handler.postDelayed(this, 2000)
            }
        }
        handler.post(statsUpdater!!)
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    100
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        updateUI()
    }

    override fun onDestroy() {
        statsUpdater?.let { handler.removeCallbacks(it) }
        super.onDestroy()
    }
}

/**
 * Ресивер для автозапуска после перезагрузки
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            val prefs = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            if (prefs.getBoolean("auto_start", false)) {
                val vpnIntent = Intent(context, AdBlockVpnService::class.java).apply {
                    action = AdBlockVpnService.ACTION_START
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(vpnIntent)
                } else {
                    context.startService(vpnIntent)
                }
            }
        }
    }
}

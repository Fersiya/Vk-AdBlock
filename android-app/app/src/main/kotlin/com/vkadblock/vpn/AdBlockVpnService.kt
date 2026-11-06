package com.vkadblock.vpn

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.core.app.NotificationCompat
import com.vkadblock.App
import com.vkadblock.MainActivity
import com.vkadblock.R
import com.vkadblock.data.BlocklistManager
import com.vkadblock.data.StatsManager
import kotlinx.coroutines.*
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer

/**
 * Локальный VPN-сервис, перехватывающий DNS-запросы
 * и блокирующий рекламные домены ВКонтакте.
 *
 * Принцип работы:
 * 1. Создаём TUN-интерфейс через VpnService.Builder
 * 2. Весь DNS-трафик направляется через TUN
 * 3. Читаем IP-пакеты из TUN, парсим DNS-запросы
 * 4. Для заблокированных доменов — возвращаем 0.0.0.0
 * 5. Для остальных — пересылаем на реальный DNS-сервер
 */
class AdBlockVpnService : VpnService() {

    companion object {
        private const val TAG = "AdBlockVPN"
        private const val VPN_ADDRESS = "10.0.0.2"
        private const val VPN_DNS = "10.0.0.1"
        private const val VPN_ROUTE = "10.0.0.0"
        private const val UPSTREAM_DNS = "8.8.8.8"
        private const val UPSTREAM_DNS_2 = "8.8.4.4"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.vkadblock.vpn.START"
        const val ACTION_STOP = "com.vkadblock.vpn.STOP"

        @Volatile
        var isRunning = false
            private set
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var blocklistManager: BlocklistManager
    private lateinit var statsManager: StatsManager

    @Volatile
    private var running = false

    override fun onCreate() {
        super.onCreate()
        blocklistManager = BlocklistManager(this)
        statsManager = StatsManager(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopVpn()
                return START_NOT_STICKY
            }
            else -> {
                startVpn()
                return START_STICKY
            }
        }
    }

    private fun startVpn() {
        if (running) return

        startForeground(NOTIFICATION_ID, buildNotification())

        try {
            vpnInterface = establishVpn()
            if (vpnInterface == null) {
                Log.e(TAG, "Не удалось создать VPN-интерфейс")
                stopSelf()
                return
            }

            running = true
            isRunning = true

            scope.launch { runTunnel() }

            Log.i(TAG, "VPN запущен")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка запуска VPN", e)
            stopSelf()
        }
    }

    private fun establishVpn(): ParcelFileDescriptor? {
        return Builder()
            .setSession("VK Ad Blocker")
            .addAddress(VPN_ADDRESS, 32)
            .addDnsServer(VPN_DNS)
            .addRoute(VPN_DNS, 32)     // Перехватываем только DNS-трафик к нашему виртуальному DNS
            .setBlocking(true)
            .setMtu(1500)
            .also { builder ->
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }
            }
            .establish()
    }

    /**
     * Основной цикл: читаем пакеты из TUN, обрабатываем DNS-запросы
     */
    private suspend fun runTunnel() {
        val fd = vpnInterface?.fileDescriptor ?: return
        val input = FileInputStream(fd)
        val output = FileOutputStream(fd)
        val buffer = ByteArray(4096)

        while (running) {
            try {
                val length = input.read(buffer)
                if (length <= 0) {
                    delay(10)
                    continue
                }

                val packet = buffer.copyOf(length)
                scope.launch {
                    processPacket(packet, output)
                }
            } catch (e: Exception) {
                if (running) {
                    Log.e(TAG, "Ошибка чтения из TUN", e)
                    delay(100)
                }
            }
        }
    }

    /**
     * Обрабатывает IP-пакет из TUN
     */
    private suspend fun processPacket(packet: ByteArray, output: FileOutputStream) {
        try {
            // Проверяем, что это IPv4
            val version = (packet[0].toInt() shr 4) and 0xF
            if (version != 4) return

            // Проверяем протокол (17 = UDP)
            val protocol = packet[9].toInt() and 0xFF
            if (protocol != 17) return

            // Парсим IP-заголовок
            val ipHeaderLength = (packet[0].toInt() and 0xF) * 4
            val srcIp = ByteArray(4).also { System.arraycopy(packet, 12, it, 0, 4) }
            val dstIp = ByteArray(4).also { System.arraycopy(packet, 16, it, 0, 4) }

            // Парсим UDP-заголовок
            val udpOffset = ipHeaderLength
            val srcPort = ((packet[udpOffset].toInt() and 0xFF) shl 8) or
                    (packet[udpOffset + 1].toInt() and 0xFF)
            val dstPort = ((packet[udpOffset + 2].toInt() and 0xFF) shl 8) or
                    (packet[udpOffset + 3].toInt() and 0xFF)

            // Обрабатываем только DNS (порт 53)
            if (dstPort != 53) return

            // Извлекаем DNS-данные
            val dnsOffset = udpOffset + 8
            val dnsLength = packet.size - dnsOffset
            if (dnsLength <= 12) return

            val dnsData = ByteArray(dnsLength)
            System.arraycopy(packet, dnsOffset, dnsData, 0, dnsLength)

            // Парсим DNS-запрос
            val domain = DnsPacket.extractDomain(dnsData) ?: return
            val queryType = DnsPacket.extractQueryType(dnsData)

            Log.d(TAG, "DNS запрос: $domain (тип: $queryType)")

            val responseData: ByteArray

            if (blocklistManager.isBlocked(domain)) {
                // Заблокировано — возвращаем 0.0.0.0
                Log.i(TAG, "⛔ Заблокирован: $domain")
                responseData = DnsPacket.buildBlockedResponse(dnsData, queryType)
                statsManager.incrementBlocked()
            } else {
                // Разрешено — пересылаем на upstream DNS
                responseData = forwardDnsQuery(dnsData) ?: return
                statsManager.incrementAllowed()
            }

            // Собираем ответный IP-пакет
            val responsePacket = buildResponsePacket(
                srcIp = dstIp,
                dstIp = srcIp,
                srcPort = dstPort,
                dstPort = srcPort,
                dnsResponse = responseData
            )

            synchronized(output) {
                output.write(responsePacket)
                output.flush()
            }

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка обработки пакета", e)
        }
    }

    /**
     * Пересылает DNS-запрос на upstream-сервер
     */
    private fun forwardDnsQuery(query: ByteArray): ByteArray? {
        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            protect(socket) // Важно! Исключаем сокет из VPN, чтобы избежать петли

            val address = InetAddress.getByName(UPSTREAM_DNS)
            val requestPacket = DatagramPacket(query, query.size, address, 53)
            socket.soTimeout = 5000
            socket.send(requestPacket)

            val responseBuffer = ByteArray(4096)
            val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
            socket.receive(responsePacket)

            return responseBuffer.copyOf(responsePacket.length)
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка пересылки DNS", e)
            // Пробуем второй DNS
            try {
                socket?.close()
                socket = DatagramSocket()
                protect(socket)

                val address = InetAddress.getByName(UPSTREAM_DNS_2)
                val requestPacket = DatagramPacket(query, query.size, address, 53)
                socket.soTimeout = 5000
                socket.send(requestPacket)

                val responseBuffer = ByteArray(4096)
                val responsePacket = DatagramPacket(responseBuffer, responseBuffer.size)
                socket.receive(responsePacket)

                return responseBuffer.copyOf(responsePacket.length)
            } catch (e2: Exception) {
                Log.e(TAG, "Ошибка пересылки DNS (резервный)", e2)
                return null
            }
        } finally {
            socket?.close()
        }
    }

    /**
     * Собирает ответный IP+UDP пакет из DNS-данных
     */
    private fun buildResponsePacket(
        srcIp: ByteArray,
        dstIp: ByteArray,
        srcPort: Int,
        dstPort: Int,
        dnsResponse: ByteArray
    ): ByteArray {
        val ipHeaderLen = 20
        val udpHeaderLen = 8
        val totalLen = ipHeaderLen + udpHeaderLen + dnsResponse.size

        val buf = ByteBuffer.allocate(totalLen)

        // === IP Header ===
        buf.put((0x45).toByte())           // Version (4) + IHL (5)
        buf.put(0.toByte())                 // DSCP/ECN
        buf.putShort(totalLen.toShort())    // Total length
        buf.putShort(0.toShort())           // Identification
        buf.putShort(0x4000.toShort())      // Flags (Don't Fragment)
        buf.put(64.toByte())                // TTL
        buf.put(17.toByte())                // Protocol (UDP)
        buf.putShort(0.toShort())           // Checksum (will calculate)
        buf.put(srcIp)                      // Source IP
        buf.put(dstIp)                      // Destination IP

        // Вычисляем контрольную сумму IP-заголовка
        val ipChecksum = calculateChecksum(buf.array(), 0, ipHeaderLen)
        buf.putShort(10, ipChecksum.toShort())

        // === UDP Header ===
        buf.putShort(srcPort.toShort())
        buf.putShort(dstPort.toShort())
        buf.putShort((udpHeaderLen + dnsResponse.size).toShort())
        buf.putShort(0.toShort()) // UDP checksum (optional for IPv4)

        // === DNS Data ===
        buf.put(dnsResponse)

        return buf.array()
    }

    /**
     * Вычисляет Internet Checksum (RFC 1071)
     */
    private fun calculateChecksum(data: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        var remaining = length

        while (remaining > 1) {
            sum += ((data[i].toInt() and 0xFF) shl 8) or (data[i + 1].toInt() and 0xFF)
            i += 2
            remaining -= 2
        }

        if (remaining == 1) {
            sum += (data[i].toInt() and 0xFF) shl 8
        }

        while (sum shr 16 != 0L) {
            sum = (sum and 0xFFFF) + (sum shr 16)
        }

        return (sum.inv() and 0xFFFF).toInt()
    }

    private fun stopVpn() {
        running = false
        isRunning = false
        scope.cancel()
        vpnInterface?.close()
        vpnInterface = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        Log.i(TAG, "VPN остановлен")
    }

    private fun buildNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, AdBlockVpnService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 1, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, App.VPN_NOTIFICATION_CHANNEL)
            .setContentTitle("VK Ad Blocker")
            .setContentText("🛡️ Защита от рекламы активна")
            .setSmallIcon(R.drawable.ic_shield)
            .setContentIntent(pendingIntent)
            .addAction(R.drawable.ic_shield, "Выключить", stopPendingIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        stopVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopVpn()
        super.onRevoke()
    }
}

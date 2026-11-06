package com.vkadblock.vpn

import java.nio.ByteBuffer

/**
 * Утилиты для парсинга и создания DNS-пакетов.
 *
 * Структура DNS-пакета (RFC 1035):
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                      ID                         |  2 байта
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |QR| Opcode |AA|TC|RD|RA|  Z  |      RCODE       |  2 байта (Flags)
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                    QDCOUNT                       |  2 байта
 * |                    ANCOUNT                       |  2 байта
 * |                    NSCOUNT                       |  2 байта
 * |                    ARCOUNT                       |  2 байта
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 * |                   QUESTIONS...                   |
 * |                   ANSWERS...                     |
 * +--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+--+
 */
object DnsPacket {

    private const val TYPE_A = 1       // IPv4 адрес
    private const val TYPE_AAAA = 28   // IPv6 адрес
    private const val TYPE_CNAME = 5

    /**
     * Извлекает доменное имя из DNS-запроса
     */
    fun extractDomain(dnsData: ByteArray): String? {
        if (dnsData.size < 12) return null

        try {
            // Пропускаем заголовок (12 байт), начинаем читать Question section
            var offset = 12
            val domainParts = mutableListOf<String>()

            while (offset < dnsData.size) {
                val labelLength = dnsData[offset].toInt() and 0xFF
                if (labelLength == 0) break // Конец доменного имени

                // Проверка на сжатие (pointer)
                if (labelLength and 0xC0 == 0xC0) break

                offset++
                if (offset + labelLength > dnsData.size) return null

                val label = String(dnsData, offset, labelLength, Charsets.US_ASCII)
                domainParts.add(label)
                offset += labelLength
            }

            return if (domainParts.isNotEmpty()) {
                domainParts.joinToString(".").lowercase()
            } else null
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Извлекает тип запроса (A, AAAA, и т.д.)
     */
    fun extractQueryType(dnsData: ByteArray): Int {
        if (dnsData.size < 12) return TYPE_A

        try {
            var offset = 12

            // Пропускаем доменное имя
            while (offset < dnsData.size) {
                val labelLength = dnsData[offset].toInt() and 0xFF
                if (labelLength == 0) {
                    offset++ // Пропускаем нулевой терминатор
                    break
                }
                if (labelLength and 0xC0 == 0xC0) {
                    offset += 2
                    break
                }
                offset += labelLength + 1
            }

            // Теперь offset указывает на QTYPE (2 байта)
            if (offset + 2 <= dnsData.size) {
                return ((dnsData[offset].toInt() and 0xFF) shl 8) or
                        (dnsData[offset + 1].toInt() and 0xFF)
            }
        } catch (e: Exception) {
            // Используем значение по умолчанию
        }

        return TYPE_A
    }

    /**
     * Создаёт DNS-ответ для заблокированного домена.
     * Для A-запросов возвращает 0.0.0.0
     * Для AAAA-запросов возвращает ::
     */
    fun buildBlockedResponse(queryData: ByteArray, queryType: Int): ByteArray {
        if (queryData.size < 12) return queryData

        // Находим конец секции вопросов (Question section)
        var questionEnd = 12
        while (questionEnd < queryData.size) {
            val labelLength = queryData[questionEnd].toInt() and 0xFF
            if (labelLength == 0) {
                questionEnd += 5 // null + QTYPE (2) + QCLASS (2)
                break
            }
            if (labelLength and 0xC0 == 0xC0) {
                questionEnd += 6 // pointer (2) + QTYPE (2) + QCLASS (2)
                break
            }
            questionEnd += labelLength + 1
        }

        // Размер ответа зависит от типа запроса
        val rdataLength = when (queryType) {
            TYPE_AAAA -> 16  // IPv6: 16 байт нулей (::)
            else -> 4        // IPv4: 4 байта нулей (0.0.0.0)
        }

        // Ответная запись (Answer RR):
        // Name pointer (2) + Type (2) + Class (2) + TTL (4) + RDLength (2) + RData (4 или 16)
        val answerLength = 2 + 2 + 2 + 4 + 2 + rdataLength
        val responseSize = questionEnd + answerLength

        val response = ByteBuffer.allocate(responseSize)

        // Копируем заголовок и вопрос
        response.put(queryData, 0, minOf(questionEnd, queryData.size))

        // Позиционируемся на заголовок для модификации
        response.position(0)

        // Оставляем ID как есть (первые 2 байта)
        response.position(2)

        // Флаги: QR=1 (ответ), AA=1, RD=1, RA=1, RCODE=0 (No Error)
        response.putShort(0x8580.toShort())

        // QDCOUNT = 1
        response.putShort(1.toShort())
        // ANCOUNT = 1
        response.putShort(1.toShort())
        // NSCOUNT = 0
        response.putShort(0.toShort())
        // ARCOUNT = 0
        response.putShort(0.toShort())

        // Переходим к ответу
        response.position(questionEnd)

        // Answer section
        // Name: pointer to domain in question (offset 12)
        response.putShort(0xC00C.toInt().toShort())

        // Type
        response.putShort(queryType.toShort())

        // Class: IN (1)
        response.putShort(1.toShort())

        // TTL: 300 секунд (5 минут)
        response.putInt(300)

        // RDLength
        response.putShort(rdataLength.toShort())

        // RData: нули (0.0.0.0 для A или :: для AAAA)
        for (i in 0 until rdataLength) {
            response.put(0.toByte())
        }

        return response.array()
    }
}

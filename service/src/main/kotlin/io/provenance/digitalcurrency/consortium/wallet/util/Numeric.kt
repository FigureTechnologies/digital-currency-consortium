package io.provenance.digitalcurrency.consortium.wallet.util

import kotlin.experimental.and

/**
 * Message codec functions.
 *
 * Implementation as per https://github.com/ethereum/wiki/wiki/JSON-RPC#hex-value-encoding
 */
object Numeric {
    private fun cleanHexPrefix(input: String): String {
        return if (containsHexPrefix(input)) {
            input.substring(2)
        } else {
            input
        }
    }

    private fun containsHexPrefix(input: String): Boolean {
        return (input.isNotEmpty() && input.length > 1 && input[0] == '0' && input[1] == 'x')
    }

    fun hexStringToByteArray(input: String): ByteArray {
        val cleanInput = cleanHexPrefix(input)
        val len = cleanInput.length
        if (len == 0) {
            return byteArrayOf()
        }
        val data: ByteArray
        val startIdx: Int
        if (len % 2 != 0) {
            data = ByteArray(len / 2 + 1)
            data[0] = Character.digit(cleanInput[0], 16).toByte()
            startIdx = 1
        } else {
            data = ByteArray(len / 2)
            startIdx = 0
        }
        var i = startIdx
        while (i < len) {
            data[(i + 1) / 2] = ((Character.digit(cleanInput[i], 16) shl 4) + Character.digit(cleanInput[i + 1], 16)).toByte()
            i += 2
        }
        return data
    }

    @JvmOverloads
    fun toHexString(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size,
        withPrefix: Boolean = true
    ): String {
        val stringBuilder = StringBuilder()
        if (withPrefix) {
            stringBuilder.append("0x")
        }
        for (i in offset until offset + length) {
            stringBuilder.append(String.format("%02x", input[i] and 0xFF.toByte()))
        }
        return stringBuilder.toString()
    }
}

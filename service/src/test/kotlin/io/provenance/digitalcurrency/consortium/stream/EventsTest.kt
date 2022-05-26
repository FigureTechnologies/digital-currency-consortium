package io.provenance.digitalcurrency.consortium.stream

import io.provenance.eventstream.stream.models.Event
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import java.util.Base64

class EventsTest {

    private val base64Encoder = Base64.getEncoder()
    private val base64Decoder = Base64.getDecoder()

    private fun String.encodeString() = base64Encoder.encodeToString(toByteArray())
    private fun String?.decodeString() = String(base64Decoder.decode(this ?: ""))

    @Test
    fun `empty split attributes`() {
        val input = emptyList<Event>()

        Assertions.assertEquals(listOf<MutableList<Attribute>>(mutableListOf()), input.splitAttributes())
    }

    @Test
    fun `simple split attributes`() {
        val input = listOf(
            Event(key = "dupe".encodeString(), value = "1".encodeString()),
            Event(key = "dupe".encodeString(), value = "2".encodeString()),
            Event(key = "dupe".encodeString(), value = "3".encodeString()),
        )

        Assertions.assertEquals(
            input.map { listOf(it.key.decodeString() to it.value.decodeString()) },
            input.splitAttributes(),
        )
    }

    @Test
    fun `marker example split attributes`() {
        val input = listOf(
            Event(key = "amount".encodeString(), value = "100".encodeString()),
            Event(key = "denom".encodeString(), value = "usdf.c".encodeString()),
            Event(key = "from_address".encodeString(), value = "tp1".encodeString()),
            Event(key = "to_address".encodeString(), value = "tp2".encodeString()),
            Event(key = "amount".encodeString(), value = "10000".encodeString()),
            Event(key = "denom".encodeString(), value = "usdf.c".encodeString()),
            Event(key = "from_address".encodeString(), value = "tp3".encodeString()),
            Event(key = "admin_address".encodeString(), value = "tpadmin".encodeString()),
            Event(key = "amount".encodeString(), value = "10000000".encodeString()),
            Event(key = "denom".encodeString(), value = "stonk.cs".encodeString()),
            Event(key = "from_address".encodeString(), value = "tp5".encodeString()),
            Event(key = "to_address".encodeString(), value = "tp2".encodeString()),
            Event(key = "admin_address".encodeString(), value = "tpadmin".encodeString()),
        )

        Assertions.assertEquals(
            listOf(
                mutableListOf(
                    Pair("amount", "100"),
                    Pair("denom", "usdf.c"),
                    Pair("from_address", "tp1"),
                    Pair("to_address", "tp2"),
                ),
                mutableListOf(
                    Pair("amount", "10000"),
                    Pair("denom", "usdf.c"),
                    Pair("from_address", "tp3"),
                    Pair("admin_address", "tpadmin"),
                ),
                mutableListOf(
                    Pair("amount", "10000000"),
                    Pair("denom", "stonk.cs"),
                    Pair("from_address", "tp5"),
                    Pair("to_address", "tp2"),
                    Pair("admin_address", "tpadmin"),
                ),
            ),
            input.splitAttributes(),
        )
    }
}

package io.provenance.digitalcurrency.consortium.stream

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class EventsTest {

    @Test
    fun `empty split attributes`() {
        val input = emptyList<Attribute>()

        Assertions.assertEquals(listOf<MutableList<Attribute>>(mutableListOf()), input.splitAttributes())
    }

    @Test
    fun `simple split attributes`() {
        val input = listOf(
            Attribute(key = "dupe".toByteArray(), value = "1".toByteArray()),
            Attribute(key = "dupe".toByteArray(), value = "2".toByteArray()),
            Attribute(key = "dupe".toByteArray(), value = "3".toByteArray()),
        )

        Assertions.assertEquals(
            input.map { listOf(it) },
            input.splitAttributes(),
        )
    }

    @Test
    fun `marker example split attributes`() {
        val input = listOf(
            Attribute(key = "amount".toByteArray(), value = "100".toByteArray()),
            Attribute(key = "denom".toByteArray(), value = "usdf.c".toByteArray()),
            Attribute(key = "from_address".toByteArray(), value = "tp1".toByteArray()),
            Attribute(key = "to_address".toByteArray(), value = "tp2".toByteArray()),
            Attribute(key = "amount".toByteArray(), value = "10000".toByteArray()),
            Attribute(key = "denom".toByteArray(), value = "usdf.c".toByteArray()),
            Attribute(key = "from_address".toByteArray(), value = "tp3".toByteArray()),
            Attribute(key = "admin_address".toByteArray(), value = "tpadmin".toByteArray()),
            Attribute(key = "amount".toByteArray(), value = "10000000".toByteArray()),
            Attribute(key = "denom".toByteArray(), value = "stonk.cs".toByteArray()),
            Attribute(key = "from_address".toByteArray(), value = "tp5".toByteArray()),
            Attribute(key = "to_address".toByteArray(), value = "tp2".toByteArray()),
            Attribute(key = "admin_address".toByteArray(), value = "tpadmin".toByteArray()),
        )

        Assertions.assertEquals(
            listOf(
                mutableListOf(
                    Attribute(key = "amount".toByteArray(), value = "100".toByteArray()),
                    Attribute(key = "denom".toByteArray(), value = "usdf.c".toByteArray()),
                    Attribute(key = "from_address".toByteArray(), value = "tp1".toByteArray()),
                    Attribute(key = "to_address".toByteArray(), value = "tp2".toByteArray()),
                ),
                mutableListOf(
                    Attribute(key = "amount".toByteArray(), value = "10000".toByteArray()),
                    Attribute(key = "denom".toByteArray(), value = "usdf.c".toByteArray()),
                    Attribute(key = "from_address".toByteArray(), value = "tp3".toByteArray()),
                    Attribute(key = "admin_address".toByteArray(), value = "tpadmin".toByteArray()),
                ),
                mutableListOf(
                    Attribute(key = "amount".toByteArray(), value = "10000000".toByteArray()),
                    Attribute(key = "denom".toByteArray(), value = "stonk.cs".toByteArray()),
                    Attribute(key = "from_address".toByteArray(), value = "tp5".toByteArray()),
                    Attribute(key = "to_address".toByteArray(), value = "tp2".toByteArray()),
                    Attribute(key = "admin_address".toByteArray(), value = "tpadmin".toByteArray()),
                ),
            ),
            input.splitAttributes(),
        )
    }
}

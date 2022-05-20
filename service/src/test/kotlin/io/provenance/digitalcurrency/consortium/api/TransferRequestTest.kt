package io.provenance.digitalcurrency.consortium.api

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.util.UUID

class TransferRequestTest {

    @Test
    fun `valid address returns true`() {
        val request = TransferRequest(
            uuid = UUID.randomUUID(),
            blockchainAddress = "tp1a9xvl9gfljsdnanmn9rj38e2mcselp3r8q0qvg",
            bankAccountUuid = null,
            amount = BigDecimal.ONE
        )

        assertTrue(request.hasValidAddress(), "Invalid address:${request.blockchainAddress}")
    }

    @Test
    fun `no address returns true`() {
        val request = TransferRequest(
            uuid = UUID.randomUUID(),
            blockchainAddress = null,
            bankAccountUuid = UUID.randomUUID(),
            amount = BigDecimal.ONE
        )

        assertTrue(request.hasValidAddress(), "Invalid address:${request.blockchainAddress}")
    }

    @Test
    fun `invalid address returns false`() {
        val request = TransferRequest(
            uuid = UUID.randomUUID(),
            blockchainAddress = "tp1gj4j5evfx0xpcs0rke6ydty0hwr4v4dss2vpac",
            bankAccountUuid = null,
            amount = BigDecimal.ONE
        )

        assertFalse(request.hasValidAddress(), "Valid address:${request.blockchainAddress}")
    }
}

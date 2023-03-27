package io.provenance.digitalcurrency.consortium.api

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.UUID

class RegisterAddressRequestTest {

    private val validAddresses = listOf(
        "tp1a9xvl9gfljsdnanmn9rj38e2mcselp3r8q0qvg",
        "tp1fs6uycs4zau8fgg8uz5fqaj3z8ts9xxy3rmdk4",
        "tp1umtv5uywmtfarauwruc6mj644tljt6a72t8puk",
        "tp1gj4j5evfx0xpcs0rke6ydty0hwr4v4dss2vpah",
        "tp1mk0ftdt2a0ds7zaw09aajf33cp07r35p2f39hx",
        "tp1dpjv3mea489qfvu66zvhc9778fxuk9rl44syfd",
        "tp18wv7cn9vgwn0fxs7qzuhc0c40a8h4cyq9xz3ve",
        "tp1ag7nmuqfjqcsjmdma9vl3s5a4h3w55jaj7mjt2",
        "tp1tcvup43amqzxx3hkkrgcarq7p4lvyr3djxnvlv",
        "tp1zp9tqmaq6d27xugl6wllj0j95c8g9v7j0zq8xl",
        "tp1xzsqw9x9natcec49z2nzf4ejx4kfdkcj7cqg7f",
        "tp1uww82kjss6zts8pc88ads4rfukp3a0p9pppg26",
        "tp1jpzpm09axhrkz2se3ylf2jnflrx4lgmgzavrtp",
        "tp1s90aed8q4y54xgvgsfajn9wf465sa3p7cks879",
        "tp1rj8vll7nmy0h7mr3lfsz8w8x3ptj97wkt4lp34",
        "tp1gswttywdwhzdmmt6wqj8uwh8t9095w4ggyqchx",
        "tp155jdkerdgm4cy6rxhur2fxlfuamsfryzze3aza",
        "tp1z2z978fpqejfmem7w4f436kc7w9ug859gdcztd",
        "tp1y0ytlkkulpzcrn9uhczy835wtxhfz70mtkf3p7",
        "tp19wh59n5dnjq8e8fyuhzlaygv0vcxl0z760nw0s",
        "tp19qnxg0237yltcd39ngctg88uu7v6t9l2nx4033",
        "tp1hrt44802wg5ee5dwz9t74jy66dvnapqsxnqv87",
        "tp1je96qf5aff0y7lf5a3vj6jz8azvmgpfm46vaxl",
        "pb1fed5ymhr8nwaf54x6envudl9lug73uw8jyqays",
    )

    @Test
    fun `valid address returns true`() {
        validAddresses.forEach { address ->
            val request = RegisterAddressRequest(
                bankAccountUuid = UUID.randomUUID(),
                blockchainAddress = address,
            )

            assertTrue(request.hasValidAddress(), "Invalid address:$address")
        }
    }

    private val invalidAddresses = listOf(
        "tp1gj4j5evfx0xpcs0rke6ydty0hwr4v4dss2vpac",
        "tp1mk0ftdt2a0ds7zaw09aajf33cp07r35p2f39ha",
        "tp1gj4j5evfx0xpcs0rke6ydty0hwr4v4dss2vpab",
        "pb1fed5ymhr8nwaf54x6envudl9lug73uw8jyqayt",
    )

    @Test
    fun `invalid address returns false`() {
        invalidAddresses.forEach { address ->
            val request = RegisterAddressRequest(
                bankAccountUuid = UUID.randomUUID(),
                blockchainAddress = address,
            )

            assertFalse(request.hasValidAddress(), "Valid address:$address")
        }
    }
}

package th.ac.bodin2.electives.utils

import java.math.BigInteger
import kotlin.test.*

class IPTest {
    @Test
    fun `parsing IPv4 works`() {
        val ip = IP.parse("192.168.1.1") as IP.V4
        assertEquals(32, ip.bits)
        assertEquals(0xc0a80101L, ip.value)
    }

    @Test
    fun `parsing IPv4 works with spaces around`() {
        val ip = IP.parse("  10.0.0.1  ") as IP.V4
        assertEquals(0x0a000001L, ip.value)
    }

    @Test
    fun `parsing IPv6 works`() {
        val ip = IP.parse("2001:db8::1") as IP.V6
        assertEquals(128, ip.bits)
        assertTrue(ip.value > BigInteger.ZERO)
    }

    @Test
    fun `parsing IPv6 zero works`() {
        val ip = IP.parse("::") as IP.V6
        assertEquals(BigInteger.ZERO, ip.value)
    }

    @Test
    fun `parsing invalid IP fails`() {
        assertFails {
            IP.parse("invalid")
        }
    }

    @Test
    fun `parsing an empty string fails`() {
        assertFailsWith<IllegalArgumentException> {
            IP.parse("")
        }
    }
}

class CIDRTest {
    @Test
    fun `parsing IPv4 works`() {
        val cidr = CIDR.parse("192.168.1.0/24")
        assertEquals(24, cidr.prefix)
        val net = cidr.net as IP.V4
        assertEquals(0xc0a80100L, net.value)
        assertEquals(24, cidr.prefix)
    }

    @Test
    fun `IPv4 is normalized`() {
        val cidr = CIDR.parse("192.168.1.15/24")
        val net = cidr.net as IP.V4
        assertEquals(0xc0a80100L, net.value)
        assertEquals(24, cidr.prefix)
    }

    @Test
    fun `IPv4 zero prefix works`() {
        val cidr = CIDR.parse("10.0.0.1/0")
        assertEquals(0, cidr.prefix)
        val net = cidr.net as IP.V4
        assertEquals(0L, net.value)
    }

    @Test
    fun `parsing IPv6 works`() {
        val cidr = CIDR.parse("2001:db8::/128")
        assertEquals(128, cidr.prefix)
        assertTrue(cidr.net is IP.V6)
    }

    @Test
    fun `IPv6 zero prefix works`() {
        val cidr = CIDR.parse("2001:db8::1/0")
        assertEquals(0, cidr.prefix)
        val net = cidr.net as IP.V6
        assertEquals(BigInteger.ZERO, net.value)
    }

    @Test
    fun `CIDR with space around works`() {
        val cidr = CIDR.parse("  192.168.1.0/24  ")
        assertEquals(24, cidr.prefix)
    }

    @Test
    fun `parsing an empty string fails`() {
        assertFailsWith<IllegalArgumentException> {
            CIDR.parse("")
        }
    }

    @Test
    fun `parsing without prefix fails`() {
        assertFailsWith<IllegalArgumentException> {
            CIDR.parse("192.168.1.0")
        }
    }

    @Test
    fun `parsing with invalid prefix fails`() {
        assertFailsWith<NumberFormatException> {
            CIDR.parse("192.168.1.0/abc")
        }
    }

    @Test
    fun `parsing with large prefix fails`() {
        assertFailsWith<IllegalArgumentException> {
            CIDR.parse("192.168.1.0/33")
        }
    }

    @Test
    fun `parsing with negative prefix fails`() {
        assertFailsWith<IllegalArgumentException> {
            CIDR.parse("192.168.1.0/-1")
        }
    }

    @Test
    fun `IPv4 matches inside range`() {
        val cidr = CIDR.parse("192.168.1.0/24")
        val ip = IP.parse("192.168.1.100")
        assertTrue(cidr.matches(ip))
    }

    @Test
    fun `IPv4 doesn't match outside range`() {
        val cidr = CIDR.parse("192.168.1.0/24")
        val ip = IP.parse("192.168.2.1")
        assertFalse(cidr.matches(ip))
    }

    @Test
    fun `IPv4 matches exact`() {
        val cidr = CIDR.parse("192.168.1.1/32")
        val ip = IP.parse("192.168.1.1")
        assertTrue(cidr.matches(ip))
    }

    @Test
    fun `IPv4 zero prefix matches`() {
        val cidr = CIDR.parse("0.0.0.0/0")
        val ip = IP.parse("192.168.1.1")
        assertTrue(cidr.matches(ip))
    }

    @Test
    fun `IPv6 matches in range`() {
        val cidr = CIDR.parse("2001:db8::/32")
        val ip = IP.parse("2001:db8::1")
        assertTrue(cidr.matches(ip))
    }

    @Test
    fun `IPv6 doesn't match outside range`() {
        val cidr = CIDR.parse("2001:db8::/32")
        val ip = IP.parse("2001:db9::1")
        assertFalse(cidr.matches(ip))
    }

    @Test
    fun `IPv6 zero prefix matches`() {
        val cidr = CIDR.parse("::/0")
        val ip = IP.parse("2001:db8::1")
        assertTrue(cidr.matches(ip))
    }

    @Test
    fun `different protocol versions don't match`() {
        val cidr = CIDR.parse("192.168.1.0/24")
        val ip = IP.parse("2001:db8::1")
        assertFalse(cidr.matches(ip))
    }

    @Test
    fun `contains operator works IP`() {
        val cidr = CIDR.parse("10.0.0.0/8")
        val ip = IP.parse("10.1.2.3")
        assertTrue(ip in cidr)
    }

    @Test
    fun `contains operator works with string`() {
        val cidr = CIDR.parse("10.0.0.0/8")
        assertTrue("10.1.2.3" in cidr)
        assertFalse("11.1.2.3" in cidr)
    }

    @Test
    fun `list contains operator works with IP`() {
        val list = listOf(
            CIDR.parse("192.168.0.0/16"),
            CIDR.parse("10.0.0.0/8")
        )
        val ip = IP.parse("192.168.1.1")
        assertTrue(ip in list)
    }

    @Test
    fun `list contains operator works with string`() {
        val list = listOf(
            CIDR.parse("192.168.0.0/16"),
            CIDR.parse("10.0.0.0/8")
        )
        assertTrue("10.5.10.15" in list)
        assertFalse("172.16.0.1" in list)
    }

    @Test
    fun `empty list contains operator doesn't match`() {
        val list = emptyList<CIDR>()
        assertFalse(IP.parse("192.168.1.1") in list)
        assertFalse("192.168.1.1" in list)
    }
}
package th.ac.bodin2.electives.utils

import java.math.BigInteger
import kotlin.test.*

class IPTest {
    @Test
    fun testParseIPv4() {
        val ip = IP.parse("192.168.1.1") as IP.V4
        assertEquals(32, ip.bits)
        assertEquals(0xc0a80101L, ip.value)
    }

    @Test
    fun testParseIPv4WithLeadingTrailingSpaces() {
        val ip = IP.parse("  10.0.0.1  ") as IP.V4
        assertEquals(0x0a000001L, ip.value)
    }

    @Test
    fun testParseIPv4Localhost() {
        val ip = IP.parse("127.0.0.1") as IP.V4
        assertEquals(0x7f000001L, ip.value)
    }

    @Test
    fun testParseIPv4Zero() {
        val ip = IP.parse("0.0.0.0") as IP.V4
        assertEquals(0L, ip.value)
    }

    @Test
    fun testParseIPv4Max() {
        val ip = IP.parse("255.255.255.255") as IP.V4
        assertEquals(0xffffffffL, ip.value)
    }

    @Test
    fun testParseIPv6() {
        val ip = IP.parse("2001:db8::1") as IP.V6
        assertEquals(128, ip.bits)
        assertTrue(ip.value > BigInteger.ZERO)
    }

    @Test
    fun testParseIPv6Localhost() {
        val ip = IP.parse("::1") as IP.V6
        assertEquals(BigInteger.ONE, ip.value)
    }

    @Test
    fun testParseIPv6Zero() {
        val ip = IP.parse("::") as IP.V6
        assertEquals(BigInteger.ZERO, ip.value)
    }

    @Test
    fun testParseInvalidIP() {
        assertFails {
            IP.parse("invalid")
        }
    }

    @Test
    fun testParseEmptyIP() {
        assertFailsWith<IllegalArgumentException> {
            IP.parse("")
        }
    }
}

class CIDRTest {
    @Test
    fun testParseCIDRv4() {
        val cidr = CIDR.parse("192.168.1.0/24")
        assertEquals(24, cidr.prefix)
        val net = cidr.net as IP.V4
        assertEquals(0xc0a80100L, net.value)
    }

    @Test
    fun testParseCIDRv4WithNormalization() {
        val cidr = CIDR.parse("192.168.1.15/24")
        val net = cidr.net as IP.V4
        assertEquals(0xc0a80100L, net.value)
        assertEquals(24, cidr.prefix)
    }

    @Test
    fun testParseCIDRv4PrefixZero() {
        val cidr = CIDR.parse("10.0.0.1/0")
        assertEquals(0, cidr.prefix)
        val net = cidr.net as IP.V4
        assertEquals(0L, net.value)
    }

    @Test
    fun testParseCIDRv4Prefix32() {
        val cidr = CIDR.parse("192.168.1.1/32")
        assertEquals(32, cidr.prefix)
        val net = cidr.net as IP.V4
        assertEquals(0xc0a80101L, net.value)
    }

    @Test
    fun testParseCIDRv6() {
        val cidr = CIDR.parse("2001:db8::/32")
        assertEquals(32, cidr.prefix)
        assertTrue(cidr.net is IP.V6)
    }

    @Test
    fun testParseCIDRv6PrefixZero() {
        val cidr = CIDR.parse("2001:db8::1/0")
        assertEquals(0, cidr.prefix)
        val net = cidr.net as IP.V6
        assertEquals(BigInteger.ZERO, net.value)
    }

    @Test
    fun testParseCIDRv6Prefix128() {
        val cidr = CIDR.parse("::1/128")
        assertEquals(128, cidr.prefix)
        val net = cidr.net as IP.V6
        assertEquals(BigInteger.ONE, net.value)
    }

    @Test
    fun testParseCIDRWithSpaces() {
        val cidr = CIDR.parse("  192.168.1.0/24  ")
        assertEquals(24, cidr.prefix)
    }

    @Test
    fun testParseCIDREmpty() {
        assertFailsWith<IllegalArgumentException> {
            CIDR.parse("")
        }
    }

    @Test
    fun testParseCIDRMissingPrefix() {
        assertFailsWith<IllegalArgumentException> {
            CIDR.parse("192.168.1.0")
        }
    }

    @Test
    fun testParseCIDRInvalidPrefix() {
        assertFailsWith<NumberFormatException> {
            CIDR.parse("192.168.1.0/abc")
        }
    }

    @Test
    fun testParseCIDRPrefixTooLarge() {
        assertFailsWith<IllegalArgumentException> {
            CIDR.parse("192.168.1.0/33")
        }
    }

    @Test
    fun testParseCIDRPrefixNegative() {
        assertFailsWith<IllegalArgumentException> {
            CIDR.parse("192.168.1.0/-1")
        }
    }

    @Test
    fun testMatchesIPv4InRange() {
        val cidr = CIDR.parse("192.168.1.0/24")
        val ip = IP.parse("192.168.1.100")
        assertTrue(cidr.matches(ip))
    }

    @Test
    fun testMatchesIPv4OutOfRange() {
        val cidr = CIDR.parse("192.168.1.0/24")
        val ip = IP.parse("192.168.2.1")
        assertFalse(cidr.matches(ip))
    }

    @Test
    fun testMatchesIPv4ExactMatch() {
        val cidr = CIDR.parse("192.168.1.1/32")
        val ip = IP.parse("192.168.1.1")
        assertTrue(cidr.matches(ip))
    }

    @Test
    fun testMatchesIPv4PrefixZero() {
        val cidr = CIDR.parse("0.0.0.0/0")
        val ip = IP.parse("192.168.1.1")
        assertTrue(cidr.matches(ip))
    }

    @Test
    fun testMatchesIPv6InRange() {
        val cidr = CIDR.parse("2001:db8::/32")
        val ip = IP.parse("2001:db8::1")
        assertTrue(cidr.matches(ip))
    }

    @Test
    fun testMatchesIPv6OutOfRange() {
        val cidr = CIDR.parse("2001:db8::/32")
        val ip = IP.parse("2001:db9::1")
        assertFalse(cidr.matches(ip))
    }

    @Test
    fun testMatchesIPv6PrefixZero() {
        val cidr = CIDR.parse("::/0")
        val ip = IP.parse("2001:db8::1")
        assertTrue(cidr.matches(ip))
    }

    @Test
    fun testMatchesDifferentIPVersions() {
        val cidr = CIDR.parse("192.168.1.0/24")
        val ip = IP.parse("2001:db8::1")
        assertFalse(cidr.matches(ip))
    }

    @Test
    fun testContainsOperatorWithIPObject() {
        val cidr = CIDR.parse("10.0.0.0/8")
        val ip = IP.parse("10.1.2.3")
        assertTrue(ip in cidr)
    }

    @Test
    fun testContainsOperatorWithString() {
        val cidr = CIDR.parse("10.0.0.0/8")
        assertTrue("10.1.2.3" in cidr)
        assertFalse("11.1.2.3" in cidr)
    }

    @Test
    fun testListContainsOperatorWithIP() {
        val list = listOf(
            CIDR.parse("192.168.0.0/16"),
            CIDR.parse("10.0.0.0/8")
        )
        val ip = IP.parse("192.168.1.1")
        assertTrue(ip in list)
    }

    @Test
    fun testListContainsOperatorWithString() {
        val list = listOf(
            CIDR.parse("192.168.0.0/16"),
            CIDR.parse("10.0.0.0/8")
        )
        assertTrue("10.5.10.15" in list)
        assertFalse("172.16.0.1" in list)
    }

    @Test
    fun testListContainsOperatorEmpty() {
        val list = emptyList<CIDR>()
        assertFalse(IP.parse("192.168.1.1") in list)
        assertFalse("192.168.1.1" in list)
    }
}
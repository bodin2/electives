package th.ac.bodin2.electives.utils

import java.math.BigInteger
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress

sealed class IP(val bits: Int) {
    class V4(val value: Long) : IP(32)
    class V6(val value: BigInteger) : IP(128)

    companion object {
        fun parse(ip: String): IP {
            if (ip.isBlank()) throw IllegalArgumentException("IP must not be blank")

            return when (val address = InetAddress.getByName(ip.trim())) {
                is Inet4Address -> {
                    val b = address.address
                    val v =
                        ((b[0].toLong() and 0xff) shl 24) or
                                ((b[1].toLong() and 0xff) shl 16) or
                                ((b[2].toLong() and 0xff) shl 8) or
                                (b[3].toLong() and 0xff)
                    V4(v)
                }

                is Inet6Address -> V6(BigInteger(1, address.address))
                else -> throw IllegalArgumentException("Unknown InetAddress type: ${address::class.java}")
            }
        }
    }
}

data class CIDR(val net: IP, val prefix: Int) {
    companion object {
        fun parse(cidr: String): CIDR {
            val s = cidr.trim()
            require(s.isNotEmpty()) { "Empty CIDR entry" }

            val parts = s.split("/", limit = 2)
            require(parts.size == 2) { "CIDR must be like 'ip/prefix': $s" }

            val ip = IP.parse(parts[0])
            val prefix = parts[1].toInt()
            require(prefix in 0..ip.bits) { "Invalid prefix $prefix for ${ip.bits}-bit IP: $s" }

            val normalizedNet: IP = when (ip) {
                is IP.V4 -> {
                    val mask =
                        if (prefix == 0) 0L
                        else (0xffff_ffffL shl (32 - prefix)) and 0xffff_ffffL
                    IP.V4(ip.value and mask)
                }
                is IP.V6 -> {
                    val hostBits = 128 - prefix
                    val mask =
                        if (prefix == 0) BigInteger.ZERO
                        else BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
                            .shiftRight(hostBits)
                            .shiftLeft(hostBits)
                    IP.V6(ip.value.and(mask))
                }
            }

            return CIDR(normalizedNet, prefix)
        }
    }

    fun matches(ip: IP): Boolean {
        if (ip.bits != net.bits) return false

        return when (ip) {
            is IP.V4 -> {
                val mask =
                    if (prefix == 0) 0L
                    else (0xffff_ffffL shl (32 - prefix)) and 0xffff_ffffL
                val net = (net as IP.V4).value
                (ip.value and mask) == net
            }

            is IP.V6 -> {
                val hostBits = 128 - prefix
                val mask =
                    if (prefix == 0) BigInteger.ZERO
                    else BigInteger.ONE.shiftLeft(128).subtract(BigInteger.ONE)
                        .shiftRight(hostBits)
                        .shiftLeft(hostBits)
                val net = (net as IP.V6).value
                ip.value.and(mask) == net
            }
        }
    }

    operator fun contains(ip: String) = contains(IP.parse(ip))

    operator fun contains(ip: IP): Boolean {
        return matches(ip)
    }
}

operator fun List<CIDR>.contains(ip: IP): Boolean = any { ip in it }
operator fun List<CIDR>.contains(ip: String): Boolean = any { ip in it }
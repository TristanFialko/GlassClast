package dev.imirror.receiver.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * LoggerTest — verifies log scrubbing behavior.
 *
 * The scrub function must redact:
 * - Hex strings >= 32 chars (crypto keys)
 * - MAC addresses (xx:xx:xx:xx:xx:xx)
 * - The exact current secret value (pairing PIN)
 *
 * It must NOT redact:
 * - 4-digit numbers (ports, seq numbers) — false positive risk
 * - Other numbers that could look like PINs
 */
class LoggerTest {

    @Test
    fun `scrub redacts 32-char hex string`() {
        val input = "Key: abcdef1234567890abcdef1234567890 end"
        val result = Logger.scrub(input)
        assertEquals("Key: <HEX[32]> end", result)
    }

    @Test
    fun `scrub redacts 64-char hex string`() {
        val input = "Key: abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890 end"
        val result = Logger.scrub(input)
        assertEquals("Key: <HEX[64]> end", result)
    }

    @Test
    fun `scrub does NOT redact 31-char hex string`() {
        val input = "Short: abcdef1234567890abcdef123456789 end"
        val result = Logger.scrub(input)
        assertEquals(input, result)
    }

    @Test
    fun `scrub redacts MAC address`() {
        val input = "Device MAC: aa:bb:cc:dd:ee:ff connected"
        val result = Logger.scrub(input)
        assertEquals("Device MAC: <MAC> connected", result)
    }

    @Test
    fun `scrub redacts MAC address uppercase`() {
        val input = "Device MAC: AA:BB:CC:DD:EE:FF connected"
        val result = Logger.scrub(input)
        assertEquals("Device MAC: <MAC> connected", result)
    }

    @Test
    fun `scrub redacts secret PIN when set`() {
        Logger.secret = "1234"
        val input = "Pairing PIN: 1234 shown on screen"
        val result = Logger.scrub(input)
        assertEquals("Pairing PIN: <PIN> shown on screen", result)
        Logger.secret = null
    }

    @Test
    fun `scrub does NOT redact 4-digit number without secret`() {
        Logger.secret = null
        val input = "Port: 7000 opened"
        val result = Logger.scrub(input)
        assertEquals(input, result)
    }

    @Test
    fun `scrub does NOT redact 4-digit number that is not the secret`() {
        Logger.secret = "5678"
        val input = "Port: 7000 opened, seq: 1234"
        val result = Logger.scrub(input)
        assertEquals(input, result)
        Logger.secret = null
    }

    @Test
    fun `scrub redacts multiple secrets in one line`() {
        Logger.secret = "9999"
        val input = "PIN 9999, key abcdef1234567890abcdef1234567890, MAC aa:bb:cc:dd:ee:ff"
        val result = Logger.scrub(input)
        assertEquals("PIN <PIN>, key <HEX[32]>, MAC <MAC>", result)
        Logger.secret = null
    }

    @Test
    fun `scrub handles empty secret`() {
        Logger.secret = ""
        val input = "Key: abcdef1234567890abcdef1234567890"
        val result = Logger.scrub(input)
        assertEquals("Key: <HEX[32]>", result)
        Logger.secret = null
    }

    @Test
    fun `scrub handles null secret`() {
        Logger.secret = null
        val input = "Key: abcdef1234567890abcdef1234567890"
        val result = Logger.scrub(input)
        assertEquals("Key: <HEX[32]>", result)
    }
}
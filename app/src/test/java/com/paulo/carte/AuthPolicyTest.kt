package com.paulo.carte

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthPolicyTest {
    @Test
    fun strongPassword_requiresLettersDigitsAndSymbols() {
        assertFalse(AuthPolicy.isStrongPassword("abcdef12"))
        assertFalse(AuthPolicy.isStrongPassword("12345678@"))
        assertFalse(AuthPolicy.isStrongPassword("Abcdefgh"))
        assertTrue(AuthPolicy.isStrongPassword("Paulo2026@"))
    }

    @Test
    fun lockout_startsAfterFiveFailedAttempts() {
        assertFalse(AuthPolicy.shouldLock(4))
        assertTrue(AuthPolicy.shouldLock(5))
    }
}

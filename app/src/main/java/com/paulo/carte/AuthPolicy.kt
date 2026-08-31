package com.paulo.carte

object AuthPolicy {
    fun isStrongPassword(password: String): Boolean {
        if (password.length < 8) return false
        val hasLetter = password.any { it.isLetter() }
        val hasDigit = password.any { it.isDigit() }
        val hasSymbol = password.any { !it.isLetterOrDigit() && !it.isWhitespace() }
        return hasLetter && hasDigit && hasSymbol
    }

    fun shouldLock(failedAttempts: Int): Boolean = failedAttempts >= 5

    const val LOCKOUT_MS: Long = 30_000L
}

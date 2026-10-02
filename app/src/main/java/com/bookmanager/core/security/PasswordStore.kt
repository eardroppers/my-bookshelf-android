package com.bookmanager.core.security

import android.content.Context
import java.security.MessageDigest

/**
 * Stores a local app-open password hash on device.
 */
class PasswordStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("app_lock", Context.MODE_PRIVATE)

    /**
     * Returns true when the user has enabled an app-open password.
     */
    fun hasPassword(): Boolean = preferences.getString(KEY_HASH, null) != null

    /**
     * Returns true when the user dismissed first-run password setup.
     */
    fun isSetupSkipped(): Boolean = preferences.getBoolean(KEY_SETUP_SKIPPED, false)

    /**
     * Returns true after the first-run notice has been acknowledged.
     */
    fun isIntroAccepted(): Boolean = preferences.getBoolean(KEY_INTRO_ACCEPTED, false)

    /**
     * Marks the first-run notice as acknowledged.
     */
    fun acceptIntro() {
        preferences.edit().putBoolean(KEY_INTRO_ACCEPTED, true).apply()
    }

    /**
     * Saves or replaces the app-open password.
     */
    fun setPassword(password: String) {
        preferences.edit()
            .putString(KEY_HASH, hash(password))
            .putBoolean(KEY_SETUP_SKIPPED, true)
            .apply()
    }

    /**
     * Dismisses first-run password setup.
     */
    fun skipSetup() {
        preferences.edit().putBoolean(KEY_SETUP_SKIPPED, true).apply()
    }

    /**
     * Checks a user-entered password.
     */
    fun verify(password: String): Boolean = preferences.getString(KEY_HASH, null) == hash(password)

    /**
     * Removes the app-open password.
     */
    fun clearPassword() {
        preferences.edit().remove(KEY_HASH).apply()
    }

    private fun hash(password: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(("bookmanager:$password").toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val KEY_HASH = "password_hash"
        private const val KEY_SETUP_SKIPPED = "setup_skipped"
        private const val KEY_INTRO_ACCEPTED = "intro_accepted"
    }
}

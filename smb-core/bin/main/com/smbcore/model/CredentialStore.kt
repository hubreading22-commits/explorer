package com.smbcore.model

interface CredentialStore {
    fun save(credentials: Credentials)
    fun get(): Credentials?
    fun clear()
    // markActive() was discussed for time-based expiry, but we switched to cold-boot wipe.
    // So we don't strictly need markActive(). I'll omit it to keep the interface clean.
}

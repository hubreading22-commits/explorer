package com.collegefiles.app.config

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.smbcore.model.Credentials
import com.smbcore.model.CredentialStore
import java.io.File

class AndroidCredentialStore(private val context: Context) : CredentialStore {

    private val prefsName = "secure_smb_prefs"

    private fun getPrefs(): android.content.SharedPreferences? {
        return try {
            val masterKey = MasterKey.Builder(context)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()

            EncryptedSharedPreferences.create(
                context,
                prefsName,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            // Catches GeneralSecurityException (Keystore invalidation) and IOException
            // Delete the corrupted preferences file safely
            try {
                val prefsFile = File(context.applicationInfo.dataDir, "shared_prefs/$prefsName.xml")
                if (prefsFile.exists()) prefsFile.delete()
            } catch (_: Exception) {}
            null
        }
    }

    override fun save(credentials: Credentials) {
        val prefs = getPrefs() ?: return
        prefs.edit()
            .putString("username", credentials.username)
            .putString("password", String(credentials.password))
            .putString("domain", credentials.domain)
            .commit()
    }

    override fun get(): Credentials? {
        val prefs = getPrefs() ?: return null
        val username = prefs.getString("username", null) ?: return null
        val passwordStr = prefs.getString("password", null) ?: return null
        val domain = prefs.getString("domain", null) ?: return null

        return Credentials(
            username = username,
            password = passwordStr.toCharArray(),
            domain = domain
        )
    }

    override fun clear() {
        val prefs = getPrefs()
        if (prefs != null) {
            prefs.edit().clear().commit()
        } else {
            // If getPrefs failed, the file is already deleted or inaccessible.
        }
    }
}

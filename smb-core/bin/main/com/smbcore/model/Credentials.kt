package com.smbcore.model

data class Credentials(
    val username: String,
    val password: CharArray,
    val domain: String = ""
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as Credentials

        if (username != other.username) return false
        if (!password.contentEquals(other.password)) return false
        if (domain != other.domain) return false

        return true
    }

    override fun hashCode(): Int {
        var result = username.hashCode()
        result = 31 * result + password.contentHashCode()
        result = 31 * result + domain.hashCode()
        return result
    }
}

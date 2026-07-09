package com.collegefiles.app.ui.shares

import com.smbcore.model.Share

data class SharesState(
    val isLoading: Boolean = true,
    val shares: List<Share> = emptyList(),
    val error: String? = null,
    val isConnected: Boolean = true,
    val sessionExpired: Boolean = false
)

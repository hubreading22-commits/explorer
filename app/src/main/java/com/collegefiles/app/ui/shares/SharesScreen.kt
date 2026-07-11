package com.collegefiles.app.ui.shares

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.smbcore.model.Share

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SharesScreen(
    viewModel: SharesViewModel,
    onNavigateToShare: (String) -> Unit,
    onSessionExpired: () -> Unit
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    LaunchedEffect(state.sessionExpired) {
        if (state.sessionExpired) {
            onSessionExpired()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Files") },
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(end = 16.dp)
                    ) {
                        Text(
                            text = if (state.isConnected) "🟢 Connected" else "🔴 Offline",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        IconButton(onClick = { viewModel.refresh() }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        var showLogoutDialog by remember { mutableStateOf(false) }
                        if (showLogoutDialog) {
                            AlertDialog(
                                onDismissRequest = { showLogoutDialog = false },
                                title = { Text("Logout") },
                                text = { Text("Are you sure you want to logout?") },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            showLogoutDialog = false
                                            onSessionExpired() // Treating manual logout same as session expiry
                                        },
                                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) { Text("Logout") }
                                },
                                dismissButton = {
                                    TextButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
                                }
                            )
                        }
                        FilledTonalButton(
                            onClick = { showLogoutDialog = true },
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                            )
                        ) {
                            Icon(Icons.Default.ExitToApp, contentDescription = "Logout", modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Logout")
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when {
                state.isLoading -> {
                    SkeletonLoadingList()
                }
                state.error != null -> {
                    ErrorStateView(
                        error = state.error!!,
                        onRetry = { viewModel.refresh() }
                    )
                }
                state.shares.isEmpty() -> {
                    EmptyStateView()
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(state.shares) { share ->
                            ShareCard(share = share, onClick = { onNavigateToShare(share.name) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ShareCard(share: Share, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Folder,
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(24.dp))
            Column {
                Text(
                    text = share.name,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        }
    }
}

@Composable
fun SkeletonLoadingList() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        repeat(5) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(96.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
fun ErrorStateView(error: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onRetry) {
            Text("Retry")
        }
    }
}

@Composable
fun EmptyStateView() {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "No folders are available for your account.",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Please contact your administrator if you believe this is incorrect.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

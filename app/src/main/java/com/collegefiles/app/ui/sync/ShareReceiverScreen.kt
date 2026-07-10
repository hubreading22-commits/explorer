package com.collegefiles.app.ui.sync

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ShareReceiverDialogLayer(
    viewModel: ShareReceiverViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()

    when (val currentState = state) {
        is ShareReceiverState.Conflict -> {
            AlertDialog(
                onDismissRequest = { viewModel.onCancel(currentState.nextUris) },
                title = { Text("Original Document Found") },
                text = {
                    Column {
                        Text("We found the original location for this file:")
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "${currentState.session.shareName} / ${currentState.session.remotePath}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(16.dp))
                        Text("Would you like to replace the existing file, or keep both?")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { viewModel.onReplace(currentState.uri, currentState.session, currentState.nextUris) }) {
                        Text("Replace")
                    }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { viewModel.onKeepBoth(currentState.uri, currentState.session, currentState.nextUris) }) {
                            Text("Keep Both")
                        }
                        TextButton(onClick = { viewModel.onCancel(currentState.nextUris) }) {
                            Text("Cancel")
                        }
                    }
                }
            )
        }
        is ShareReceiverState.NoMatch -> {
            AlertDialog(
                onDismissRequest = { viewModel.onCancel(currentState.nextUris) },
                title = { Text("Save Document") },
                text = {
                    Column {
                        Text("We couldn't determine the original location for ${currentState.fileName}.")
                        Spacer(Modifier.height(8.dp))
                        Text("Please use the Folder Picker to select a destination.")
                    }
                },
                confirmButton = {
                    TextButton(onClick = { 
                        viewModel.onManualUpload(currentState.uri, currentState.fileName, currentState.nextUris)
                    }) {
                        Text("Choose Folder")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.onCancel(currentState.nextUris) }) {
                        Text("Cancel")
                    }
                }
            )
        }
        is ShareReceiverState.Done -> {
            LaunchedEffect(Unit) {
                viewModel.dismiss()
            }
        }
        else -> {
            // Idle or Resolving, no UI needed
        }
    }
}

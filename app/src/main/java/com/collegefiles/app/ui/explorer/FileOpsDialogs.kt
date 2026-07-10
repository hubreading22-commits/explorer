package com.collegefiles.app.ui.explorer

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.smbcore.model.FileItem

@Composable
fun RenameDialog(
    item: FileItem,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newName by remember { mutableStateOf(item.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename") },
        text = {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (newName.isNotBlank() && newName != item.name) onConfirm(newName) },
                enabled = newName.isNotBlank() && newName != item.name
            ) { Text("Rename") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun DeleteConfirmationDialog(
    item: FileItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete") },
        text = {
            Column {
                Text("Are you sure you want to delete:")
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (item.isDirectory) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "This will delete the folder and all its contents.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun CreateFolderDialog(
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            OutlinedTextField(
                value = folderName,
                onValueChange = { folderName = it },
                label = { Text("Folder name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (folderName.isNotBlank()) onConfirm(folderName) },
                enabled = folderName.isNotBlank()
            ) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileActionSheet(
    item: FileItem,
    canRename: Boolean,
    canDelete: Boolean,
    onOpen: () -> Unit,
    onCopy: () -> Unit,
    onMove: () -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    onProperties: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.padding(bottom = 24.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Divider()
            Spacer(modifier = Modifier.height(8.dp))

            ListItem(
                headlineContent = { Text("Open") },
                modifier = Modifier.clickableAction(onOpen)
            )
            if (!item.isDirectory) {
                ListItem(
                    headlineContent = { Text("Copy") },
                    modifier = Modifier.clickableAction(onCopy)
                )
                ListItem(
                    headlineContent = { Text("Move") },
                    modifier = Modifier.clickableAction(onMove)
                )
            }
            if (canRename) {
                ListItem(
                    headlineContent = { Text("Rename") },
                    modifier = Modifier.clickableAction(onRename)
                )
            }
            if (canDelete) {
                ListItem(
                    headlineContent = {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    },
                    modifier = Modifier.clickableAction(onDelete)
                )
            }
            ListItem(
                headlineContent = { Text("Properties") },
                modifier = Modifier.clickableAction(onProperties)
            )
        }
    }
}

// Small helper to apply clickable modifier cleanly
private fun Modifier.clickableAction(action: () -> Unit) =
    this.then(Modifier.fillMaxWidth()).wrapContentHeight().clickable { action() }

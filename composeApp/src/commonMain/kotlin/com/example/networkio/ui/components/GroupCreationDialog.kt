package com.example.networkio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@Composable
fun GroupCreationDialog(
    onlineUsers: List<String>,
    currentUser: String,
    onDismiss: () -> Unit,
    onCreateGroup: (name: String, members: List<String>) -> Unit
) {
    var groupName by remember { mutableStateOf("") }
    // Re-initialize selection map whenever online users list changes
    var selectedUsers by remember(onlineUsers) {
        mutableStateOf(onlineUsers.associateWith { false }.toMutableMap())
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Group") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = groupName,
                    onValueChange = { groupName = it; error = null },
                    label = { Text("Group name") },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Text("Select members:", style = MaterialTheme.typography.bodyMedium)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    onlineUsers.forEach { user ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(user)
                            Checkbox(
                                checked = selectedUsers[user] == true,
                                onCheckedChange = { checked ->
                                    selectedUsers[user] = checked
                                }
                            )
                        }
                    }
                    if (onlineUsers.isEmpty()) {
                        Text("No other users online right now. You can still create a group and add members later.", style = MaterialTheme.typography.bodySmall)
                    }
                }
                if (error != null) {
                    Text(error!!, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                val members = selectedUsers.filterValues { it }.keys.toList()
                when {
                    groupName.isBlank() -> error = "Group name cannot be empty"
                    else -> onCreateGroup(groupName.trim(), members)
                }
            }) { Text("Create") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

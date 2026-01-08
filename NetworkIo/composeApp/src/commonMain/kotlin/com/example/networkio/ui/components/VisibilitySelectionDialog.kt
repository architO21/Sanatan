package com.example.networkio.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun VisibilitySelectionDialog(
    groupMembers: List<String>,
    currentUser: String,
    onDismiss: () -> Unit,
    onConfirm: (selectedMembers: List<String>?) -> Unit
) {
    val selectedMembers = remember { mutableStateMapOf<String, Boolean>() }
    var sendToAll by remember { mutableStateOf(true) }
    
    // Initialize with all members except current user
    LaunchedEffect(groupMembers) {
        groupMembers.forEach { member ->
            if (member != currentUser && selectedMembers[member] == null) {
                selectedMembers[member] = true // Default: visible to all
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Message Visibility") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Who should see this message?",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                // Option: Send to all
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Everyone in group", style = MaterialTheme.typography.bodyLarge)
                    RadioButton(
                        selected = sendToAll,
                        onClick = { 
                            sendToAll = true
                            // Select all members
                            groupMembers.forEach { member ->
                                if (member != currentUser) {
                                    selectedMembers[member] = true
                                }
                            }
                        }
                    )
                }
                
                HorizontalDivider()
                
                // Option: Select specific members
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Select specific members", style = MaterialTheme.typography.bodyLarge)
                    RadioButton(
                        selected = !sendToAll,
                        onClick = { sendToAll = false }
                    )
                }
                
                if (!sendToAll) {
                    Column(
                        modifier = Modifier.padding(start = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        groupMembers.filter { it != currentUser }.forEach { member ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(member)
                                Checkbox(
                                    checked = selectedMembers[member] == true,
                                    onCheckedChange = { checked ->
                                        selectedMembers[member] = checked
                                    }
                                )
                            }
                        }
                    }
                    
                    if (selectedMembers.values.none { it }) {
                        Text(
                            "⚠️ No members selected - message won't be sent to anyone",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val result = if (sendToAll) {
                        null // null means visible to all
                    } else {
                        selectedMembers.filterValues { it }.keys.toList()
                    }
                    onConfirm(result)
                }
            ) {
                Text("Confirm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

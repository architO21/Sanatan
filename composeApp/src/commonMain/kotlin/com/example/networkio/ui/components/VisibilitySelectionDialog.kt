package com.example.networkio.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Dialog that lets the user pick which group members should see the next message.
 *
 * - [members] should be the group member usernames (including [currentUser]).
 * - The dialog only shows entries excluding [currentUser].
 * - On confirm, returns the selected usernames.
 */
@Composable
fun VisibilitySelectionDialog(
    members: List<String>,
    currentUser: String,
    initialSelected: List<String> = emptyList(),
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    var selected by remember {
        mutableStateOf<MutableSet<String>>(initialSelected.toMutableSet())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select recipients") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                val visibleMembers = members.filter { it != currentUser }
                if (visibleMembers.isEmpty()) {
                    Text("No other members in this group.")
                } else {
                    visibleMembers.forEach { member ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(member)
                            Checkbox(
                                checked = selected.contains(member),
                                onCheckedChange = { checked ->
                                    val next = selected.toMutableSet()
                                    if (checked) next.add(member) else next.remove(member)
                                    selected = next
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selected.toList()) }) {
                Text("Send")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

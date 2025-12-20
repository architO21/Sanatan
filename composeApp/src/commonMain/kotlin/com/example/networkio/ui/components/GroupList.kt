package com.example.networkio.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun GroupList(
    groups: List<GroupItem>,
    selectedGroupId: String?,
    onSelect: (String) -> Unit,
    onCreateGroup: () -> Unit
) {
    Column(modifier = Modifier.fillMaxHeight()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Groups", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onCreateGroup) { Text("New Group") }
        }
        if (groups.isEmpty()) {
            Text(
                text = "No groups yet",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                groups.forEach { group ->
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                            .clickable { onSelect(group.id) },
                        color = if (group.id == selectedGroupId) 
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surface
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(group.name, style = MaterialTheme.typography.bodyLarge)
                            Text(
                                "Members: ${group.members.size}",
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                }
            }
        }
    }
}

data class GroupItem(val id: String, val name: String, val members: List<String>)

package com.example.networkio.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog

@Composable
fun UsernameInputDialog(onUsernameSet: (String) -> Unit) {
    var username by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    
    Dialog(onDismissRequest = { /* Cannot dismiss */ }) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Welcome to Chat",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Text(
                    text = "Enter your username to start chatting",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                
                OutlinedTextField(
                    value = username,
                    onValueChange = { 
                        username = it
                        error = null
                    },
                    label = { Text("Username") },
                    placeholder = { Text("Enter your name") },
                    singleLine = true,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth()
                )
                
                Button(
                    onClick = {
                        when {
                            username.isBlank() -> error = "Username cannot be empty"
                            username.length < 3 -> error = "Username must be at least 3 characters"
                            username.length > 20 -> error = "Username must be less than 20 characters"
                            !username.matches(Regex("^[a-zA-Z0-9_]+$")) -> error = "Username can only contain letters, numbers, and underscores"
                            else -> onUsernameSet(username.trim())
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Join Chat")
                }
            }
        }
    }
}

package com.example.networkio.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.networkio.network.Message
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageBubble(
    message: Message,
    isCurrentUser: Boolean,
    currentUsername: String = message.from
) {
    var showVisibilityDialog by remember { mutableStateOf(false) }
    
    val alignment = if (isCurrentUser) Alignment.CenterEnd else Alignment.CenterStart
    
    // Modern gradient colors for sent messages
    val sentGradient = listOf(
        Color(0xFF6B4CE8), // Purple
        Color(0xFF8B5FE8)  // Lighter purple
    )
    
    // Soft colors for received messages
    val receivedColor = Color(0xFFF0F0F5)
    
    val textColor = if (isCurrentUser) Color.White else Color(0xFF1A1A1A)
    
    // Check if this is a selective visibility message
    val visibleToList: List<String>? = message.visibleTo
    val isSelectiveMessage = message.sendTo.startsWith("group:") && 
                            visibleToList != null && 
                            visibleToList.isNotEmpty()
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = alignment
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .shadow(
                    elevation = 2.dp,
                    shape = RoundedCornerShape(
                        topStart = 20.dp,
                        topEnd = 20.dp,
                        bottomStart = if (isCurrentUser) 20.dp else 6.dp,
                        bottomEnd = if (isCurrentUser) 6.dp else 20.dp
                    ),
                    clip = false
                ),
            shape = RoundedCornerShape(
                topStart = 20.dp,
                topEnd = 20.dp,
                bottomStart = if (isCurrentUser) 20.dp else 6.dp,
                bottomEnd = if (isCurrentUser) 6.dp else 20.dp
            ),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier.then(
                    if (isCurrentUser) {
                        Modifier.background(
                            brush = Brush.linearGradient(sentGradient)
                        )
                    } else {
                        Modifier.background(receivedColor)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(
                        horizontal = 16.dp,
                        vertical = 12.dp
                    )
                ) {
                    // Show sender name for messages from others
                    if (!isCurrentUser) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = message.from,
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold
                                ),
                                color = Color(0xFF6B4CE8),
                                modifier = Modifier.weight(1f)
                            )
                            // Info button for selective messages
                            if (isSelectiveMessage) {
                                IconButton(
                                    onClick = { showVisibilityDialog = true },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Text(
                                        text = "ⓘ",
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = Color(0xFF6B4CE8).copy(alpha = 0.8f)
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    } else if (isSelectiveMessage) {
                        // For current user's selective messages, show info button aligned right
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            IconButton(
                                onClick = { showVisibilityDialog = true },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Text(
                                    text = "ⓘ",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                    
                    Text(
                        text = message.content,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.3
                        ),
                        color = textColor
                    )
                    
                    Spacer(modifier = Modifier.height(6.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            text = formatTimestamp(message.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = textColor.copy(alpha = 0.65f)
                        )
                    }
                }
            }
        }
    }
    
    // Show visibility info dialog when info button is clicked
    if (showVisibilityDialog && isSelectiveMessage && visibleToList != null) {
        MessageVisibilityInfoDialog(
            visibleToList = visibleToList,
            currentUser = currentUsername,
            onDismiss = { showVisibilityDialog = false }
        )
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    return sdf.format(Date(timestamp))
}

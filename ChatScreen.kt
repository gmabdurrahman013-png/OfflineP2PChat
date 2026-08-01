package com.example.offlinep2p.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image as ImageIcon
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.offlinep2p.data.ChatMessage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    peerName: String,
    messages: List<ChatMessage>,
    onSendText: (String) -> Unit,
    onSendImage: (Uri) -> Unit,
    onBack: () -> Unit
) {
    var input by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? -> uri?.let(onSendImage) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            scope.launch { listState.animateScrollToItem(messages.size - 1) }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(peerName) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("\u2190") }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { imagePicker.launch("image/*") }) {
                        Icon(ImageIcon, contentDescription = "Send image")
                    }
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Message") }
                    )
                    IconButton(onClick = {
                        if (input.isNotBlank()) {
                            onSendText(input.trim())
                            input = ""
                        }
                    }) {
                        Icon(Icons.Filled.Send, contentDescription = "Send")
                    }
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.padding(padding).fillMaxSize().padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(messages) { msg -> MessageBubble(msg) }
            item { Spacer(Modifier.height(8.dp)) }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage) {
    val alignment = if (msg.isMine) Alignment.CenterEnd else Alignment.CenterStart
    val bubbleColor = if (msg.isMine) MaterialTheme.colorScheme.primaryContainer
                       else MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = alignment) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.padding(vertical = 2.dp)
        ) {
            Box(modifier = Modifier.padding(10.dp)) {
                if (msg.isImage && msg.imagePath != null) {
                    AsyncImage(
                        model = msg.imagePath,
                        contentDescription = "Shared image",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(200.dp).clip(RoundedCornerShape(8.dp))
                    )
                } else {
                    Text(msg.textContent ?: "")
                }
            }
        }
    }
}

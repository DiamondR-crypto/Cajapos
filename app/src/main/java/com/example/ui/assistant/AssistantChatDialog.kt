package com.example.ui.assistant

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.PosViewModel
import com.example.utils.SpeechToTextManager
import com.example.utils.TextToSpeechManager
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantChatDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    val messages by viewModel.assistantMessages.collectAsStateWithLifecycle()
    val isThinking by viewModel.isAssistantThinking.collectAsStateWithLifecycle()
    val pendingAction by viewModel.pendingAssistantAction.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val selectedModel by viewModel.geminiSelectedModel.collectAsStateWithLifecycle()
    val customApiKey by viewModel.geminiCustomApiKey.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var rmsLevel by remember { mutableStateOf(0f) }
    var showCsvImportDialog by remember { mutableStateOf(false) }

    val sttManager = remember { SpeechToTextManager(context) }
    val ttsManager = remember { TextToSpeechManager(context) }

    // TTS disposal
    DisposableEffect(Unit) {
        onDispose {
            sttManager.stopListening()
            ttsManager.shutdown()
        }
    }

    // TTS response observer
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            val lastMsg = messages.last()
            if (lastMsg.sender == AssistantSender.ASSISTANT) {
                ttsManager.speak(lastMsg.text)
            }
        }
    }

    // Scroll to bottom when new messages arrive
    LaunchedEffect(messages.size, isThinking) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun startInAppListening() {
        isListening = true
        sttManager.startListening(
            onResults = { text ->
                isListening = false
                viewModel.sendAssistantMessage(text)
            },
            onError = { isListening = false },
            onRmsChanged = { rmsLevel = it }
        )
    }

    // Speech to text launcher
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            startInAppListening()
        } else {
            Toast.makeText(context, "Permiso de micrófono necesario para hablar con el asistente.", Toast.LENGTH_SHORT).show()
        }
    }

    fun launchVoiceRecognition() {
        if (isListening) {
            sttManager.stopListening()
            isListening = false
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startInAppListening()
        } else {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val modelLabel = if (customApiKey.isNotBlank()) "Gemini $selectedModel (De Pago)" else "Gemini 3.5 Flash (Nivel Gratuito)"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.65f))
                .imePadding()
                .padding(12.dp),
            contentAlignment = Alignment.Center
        ) {
            val isWide = maxWidth > 650.dp
            val dialogWidth = if (isWide) 620.dp else maxWidth
            val dialogHeight = if (maxHeight > 750.dp) 700.dp else maxHeight

            Card(
                modifier = Modifier
                    .width(dialogWidth)
                    .height(dialogHeight),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
                border = BorderStroke(1.dp, Color(0xFF334155)),
                elevation = CardDefaults.cardElevation(16.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color(0xFF1E1B4B), Color(0xFF1E293B))
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            listOf(Color(0xFF6366F1), Color(0xFF3B82F6))
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Asistente Conversacional POS",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = Color(0xFF3B82F6).copy(alpha = 0.25f)
                                    ) {
                                        Text(
                                            text = currentUser?.role ?: "ADMIN",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFF93C5FD)
                                        )
                                    }
                                    Text(
                                        text = "• $modelLabel",
                                        fontSize = 11.sp,
                                        color = Color(0xFF94A3B8)
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.clearAssistantChat() },
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF94A3B8))
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Reiniciar Chat")
                            }
                            IconButton(
                                onClick = onDismiss,
                                colors = IconButtonDefaults.iconButtonColors(contentColor = Color(0xFF94A3B8))
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // Message List Area
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFF090D16))
                    ) {
                        LazyColumn(
                            state = listState,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                AssistantBubbleItem(
                                    message = msg,
                                    onConfirm = { viewModel.confirmPendingAction() },
                                    onCancel = { viewModel.cancelPendingAction() },
                                    onSelectDisambiguation = { item ->
                                        viewModel.selectDisambiguatedProduct(item)
                                    }
                                )
                            }

                            if (isThinking) {
                                item {
                                    AssistantThinkingBubble()
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color(0xFF1E293B))

                    // Quick Suggestion Chips
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        item {
                            SuggestionChip(
                                text = "📁 Cargar Inventario CSV",
                                onClick = { showCsvImportDialog = true }
                            )
                        }
                        item {
                            SuggestionChip(
                                text = "💵 Tasa BCV",
                                onClick = { viewModel.sendAssistantMessage("¿Cuál es la tasa oficial del BCV hoy?") }
                            )
                        }
                        item {
                            SuggestionChip(
                                text = "📈 Ventas de Hoy",
                                onClick = { viewModel.sendAssistantMessage("Resumen de ventas de hoy") }
                            )
                        }
                        item {
                            SuggestionChip(
                                text = "⚠️ Bajo Stock",
                                onClick = { viewModel.sendAssistantMessage("¿Cuáles productos tienen bajo stock?") }
                            )
                        }
                        item {
                            SuggestionChip(
                                text = "➕ Nuevo Producto",
                                onClick = { inputText = "Agrega un producto  con  unidades a precio de costo  y precio de venta " }
                            )
                        }
                        item {
                            SuggestionChip(
                                text = "📦 Añadir Existencias",
                                onClick = { inputText = "Añade  existencias a " }
                            )
                        }
                        item {
                            SuggestionChip(
                                text = "🧾 Factura Proveedor",
                                onClick = { inputText = "Registrar factura de proveedor  número  por monto " }
                            )
                        }
                    }

                    // Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F172A))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Botón para Cargar Inventario CSV
                        IconButton(
                            onClick = { showCsvImportDialog = true },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF064E3B)),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = Color(0xFF34D399)
                            )
                        ) {
                            Icon(
                                Icons.Filled.UploadFile,
                                contentDescription = "Cargar Inventario CSV",
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Mic Button
                        val infiniteTransition = rememberInfiniteTransition(label = "mic_anim")
                        val scale by infiniteTransition.animateFloat(
                            initialValue = 1f,
                            targetValue = if (isListening) 1.25f else 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "scale"
                        )
                        val micPulseAlpha by infiniteTransition.animateFloat(
                            initialValue = 0.3f,
                            targetValue = if (isListening) 0.8f else 0.3f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(500, easing = LinearEasing),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "alpha"
                        )

                        IconButton(
                            onClick = { launchVoiceRecognition() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isListening) Color(0xFF0EA5E9).copy(alpha = micPulseAlpha) else Color(0xFF1E293B)
                                )
                                .then(
                                    if (isListening) Modifier.graphicsLayer {
                                        scaleX = scale
                                        scaleY = scale
                                    } else Modifier
                                ),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (isListening) Color.White else Color(0xFF38BDF8)
                            )
                        ) {
                            Icon(
                                if (isListening) Icons.Filled.MicNone else Icons.Filled.Mic,
                                contentDescription = "Dictar por Voz"
                            )
                        }

                        // Text Field
                        TextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    "Escribe tu consulta o solicitud...",
                                    fontSize = 13.sp,
                                    color = Color(0xFF64748B)
                                )
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 44.dp),
                            shape = RoundedCornerShape(22.dp),
                            keyboardOptions = KeyboardOptions(
                                imeAction = ImeAction.Send,
                                capitalization = KeyboardCapitalization.Sentences
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = {
                                    if (inputText.isNotBlank()) {
                                        val textToSend = inputText.trim()
                                        inputText = ""
                                        viewModel.sendAssistantMessage(textToSend)
                                    }
                                }
                            ),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = Color(0xFF1E293B),
                                unfocusedContainerColor = Color(0xFF1E293B),
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White,
                                cursorColor = Color(0xFF38BDF8)
                            ),
                            maxLines = 3
                        )

                        // Send Button
                        val hasText = inputText.isNotBlank()
                        IconButton(
                            onClick = {
                                if (hasText) {
                                    val textToSend = inputText.trim()
                                    inputText = ""
                                    viewModel.sendAssistantMessage(textToSend)
                                }
                            },
                            enabled = hasText,
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(
                                    if (hasText) Color(0xFF3B82F6) else Color(0xFF1E293B)
                                ),
                            colors = IconButtonDefaults.iconButtonColors(
                                contentColor = if (hasText) Color.White else Color(0xFF64748B)
                            )
                        ) {
                            Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Enviar")
                        }
                    }
                }
            }
        }

        if (showCsvImportDialog) {
            com.example.ui.inventory.CsvInventoryImportDialog(
                viewModel = viewModel,
                onDismiss = { showCsvImportDialog = false },
                onImportCompleted = { resultMsg ->
                    viewModel.notifyAssistantCsvImportCompleted(resultMsg)
                }
            )
        }
    }
}

@Composable
private fun AssistantBubbleItem(
    message: AssistantChatMessage,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onSelectDisambiguation: (DisambiguationItem) -> Unit
) {
    val isUser = message.sender == AssistantSender.USER
    val timeStr = remember(message.timestamp) {
        SimpleDateFormat("hh:mm a", Locale.getDefault()).format(Date(message.timestamp))
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!isUser) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF312E81)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = Color(0xFF818CF8),
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 480.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            Surface(
                shape = RoundedCornerShape(
                    topStart = 16.dp,
                    topEnd = 16.dp,
                    bottomStart = if (isUser) 16.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 16.dp
                ),
                color = if (isUser) Color(0xFF2563EB) else Color(0xFF1E293B),
                border = if (!isUser) BorderStroke(1.dp, Color(0xFF334155)) else null
            ) {
                Column(
                    modifier = Modifier.padding(12.dp)
                ) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp
                    )

                    // If message contains pending action awaiting confirmation
                    if (message.pendingAction != null && !message.isConfirmedOrResolved) {
                        Spacer(Modifier.height(10.dp))
                        when (val action = message.pendingAction) {
                            is PendingAssistantAction.SelectDisambiguation -> {
                                DisambiguationCards(
                                    candidates = action.candidates,
                                    onSelect = onSelectDisambiguation
                                )
                            }
                            else -> {
                                ConfirmationButtonsCard(
                                    onConfirm = onConfirm,
                                    onCancel = onCancel
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(2.dp))
            Text(
                text = timeStr,
                fontSize = 10.sp,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        if (isUser) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E293B)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Person,
                    contentDescription = null,
                    tint = Color(0xFF93C5FD),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ConfirmationButtonsCard(
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFF475569))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Filled.HelpOutline,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "¿Deseas confirmar esta operación?",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color(0xFFF1F5F9)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Sí, Confirmar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFEF4444)),
                    border = BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.5f))
                ) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("No, Cancelar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun DisambiguationCards(
    candidates: List<DisambiguationItem>,
    onSelect: (DisambiguationItem) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            "Toca una opción o responde con el nombre:",
            fontSize = 11.sp,
            color = Color(0xFF94A3B8),
            fontWeight = FontWeight.SemiBold
        )
        candidates.forEachIndexed { index, item ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(item) },
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F172A),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.4f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            "${index + 1}. ${item.title}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.5.sp,
                            color = Color.White
                        )
                        Text(
                            item.subtitle,
                            fontSize = 11.sp,
                            color = Color(0xFF38BDF8)
                        )
                    }
                    Icon(
                        Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AssistantThinkingBubble() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(Color(0xFF312E81)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.AutoAwesome,
                contentDescription = null,
                tint = Color(0xFF818CF8),
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(8.dp))
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = Color(0xFF1E293B).copy(alpha = alpha),
            border = BorderStroke(1.dp, Color(0xFF334155))
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(14.dp),
                    color = Color(0xFF38BDF8),
                    strokeWidth = 2.dp
                )
                Text(
                    "El asistente está analizando tu solicitud...",
                    fontSize = 12.sp,
                    color = Color(0xFF94A3B8)
                )
            }
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E293B),
        border = BorderStroke(1.dp, Color(0xFF334155))
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFFE2E8F0)
        )
    }
}

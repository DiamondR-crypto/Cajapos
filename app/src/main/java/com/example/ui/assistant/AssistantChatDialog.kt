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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.Quote
import com.example.data.local.QuoteItem
import com.example.ui.PosViewModel
import com.example.ui.components.QuoteTicketDialog
import com.example.utils.GeminiLiveStatus
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
    onNavigateToRoute: (String) -> Unit = {},
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
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val isMinProfitMarginEnabled by viewModel.isMinProfitMarginEnabled.collectAsStateWithLifecycle()
    val minProfitMarginPercent by viewModel.minProfitMarginPercent.collectAsStateWithLifecycle()
    val allQuotes by viewModel.allQuotes.collectAsStateWithLifecycle()

    // Gemini Live state
    val isGeminiLiveActive by viewModel.isGeminiLiveActive.collectAsStateWithLifecycle()
    val geminiLiveStatus by viewModel.geminiLiveStatus.collectAsStateWithLifecycle()
    val geminiLiveStatusText by viewModel.geminiLiveStatusText.collectAsStateWithLifecycle()
    val isGeminiLiveMuted by viewModel.isGeminiLiveMuted.collectAsStateWithLifecycle()
    val liveUserTranscript by viewModel.liveUserTranscript.collectAsStateWithLifecycle()
    val liveModelTranscript by viewModel.liveModelTranscript.collectAsStateWithLifecycle()
    val liveAudioLevel by viewModel.liveAudioLevel.collectAsStateWithLifecycle()
    val navigationRequest by viewModel.assistantNavigationRequest.collectAsStateWithLifecycle()

    var inputText by remember { mutableStateOf("") }
    var isListening by remember { mutableStateOf(false) }
    var partialSpeechText by remember { mutableStateOf("") }
    var rmsLevel by remember { mutableStateOf(0f) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var editingProductEntry by remember { mutableStateOf<Pair<String, PendingAssistantAction.CreateProductAction>?>(null) }
    var editingQuoteEntry by remember { mutableStateOf<Pair<String, PendingAssistantAction.CreateQuoteAction>?>(null) }
    var selectedQuoteForTicket by remember { mutableStateOf<Quote?>(null) }

    val sttManager = remember { SpeechToTextManager(context) }
    val ttsManager = remember { TextToSpeechManager(context) }

    var pendingAudioAction by remember { mutableStateOf<String?>(null) } // "STT" or "LIVE"

    // Navigation bridge from Assistant -> MainApp
    LaunchedEffect(navigationRequest) {
        val route = navigationRequest
        if (!route.isNullOrBlank()) {
            viewModel.consumeAssistantNavigationRequest()
            onNavigateToRoute(route)
            onDismiss()
        }
    }

    // TTS & Live disposal
    DisposableEffect(Unit) {
        onDispose {
            sttManager.stopListening()
            ttsManager.shutdown()
            viewModel.stopGeminiLive()
        }
    }

    // Stop TTS immediately when user starts speaking (barge-in)
    LaunchedEffect(liveUserTranscript, isListening) {
        if (liveUserTranscript.isNotBlank() || isListening) {
            ttsManager.stop()
        }
    }

    // Normal text mode is text-only (no audio TTS response)

    // Scroll to bottom when new messages or live transcripts arrive
    LaunchedEffect(messages.size, isThinking, liveUserTranscript, liveModelTranscript) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    fun startInAppListening() {
        ttsManager.stop()
        if (isGeminiLiveActive) {
            viewModel.stopGeminiLive()
        }
        isListening = true
        partialSpeechText = ""
        sttManager.startListening(
            continuous = false,
            silenceTimeoutMs = 3500L,
            onBeginning = { ttsManager.stop() },
            onPartialResults = { partial -> partialSpeechText = partial },
            onResults = { text ->
                isListening = false
                partialSpeechText = ""
                viewModel.sendAssistantMessage(text)
            },
            onError = {
                isListening = false
                partialSpeechText = ""
            },
            onRmsChanged = { rmsLevel = it }
        )
    }

    // Speech & Live audio permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            when (pendingAudioAction) {
                "LIVE" -> {
                    ttsManager.stop()
                    sttManager.stopListening()
                    isListening = false
                    viewModel.toggleGeminiLive(context)
                }
                else -> startInAppListening()
            }
        } else {
            Toast.makeText(context, "Permiso de micrófono necesario para hablar con el asistente.", Toast.LENGTH_SHORT).show()
        }
        pendingAudioAction = null
    }

    fun launchVoiceRecognition() {
        if (isListening) {
            sttManager.stopListening()
            isListening = false
            partialSpeechText = ""
            return
        }

        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startInAppListening()
        } else {
            pendingAudioAction = "STT"
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun handleToggleGeminiLive() {
        if (isGeminiLiveActive) {
            viewModel.stopGeminiLive()
            return
        }
        ttsManager.stop()
        sttManager.stopListening()
        isListening = false
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            viewModel.toggleGeminiLive(context)
        } else {
            pendingAudioAction = "LIVE"
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
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(
                                        Brush.linearGradient(
                                            if (isGeminiLiveActive) {
                                                listOf(Color(0xFF10B981), Color(0xFF06B6D4))
                                            } else {
                                                listOf(Color(0xFF6366F1), Color(0xFF3B82F6))
                                            }
                                        )
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isGeminiLiveActive) Icons.Filled.GraphicEq else Icons.Filled.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Column {
                                Text(
                                    "Asistente POS IA",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1
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
                                        text = if (isGeminiLiveActive) "• En Vivo" else "• $modelLabel",
                                        fontSize = 11.sp,
                                        color = if (isGeminiLiveActive) Color(0xFF34D399) else Color(0xFF94A3B8),
                                        maxLines = 1
                                    )
                                }
                            }
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            // Gemini Live Toggle Pill
                            Surface(
                                onClick = { handleToggleGeminiLive() },
                                modifier = Modifier
                                    .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
                                    .testTag("assistant_gemini_live_toggle"),
                                shape = RoundedCornerShape(24.dp),
                                color = if (isGeminiLiveActive) Color(0xFF059669) else Color(0xFF1E293B),
                                border = BorderStroke(
                                    1.dp,
                                    if (isGeminiLiveActive) Color(0xFF34D399) else Color(0xFF6366F1)
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                                ) {
                                    Icon(
                                        if (isGeminiLiveActive) Icons.Filled.GraphicEq else Icons.Filled.Podcasts,
                                        contentDescription = "Activar o desactivar Gemini Live",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "Gemini Live",
                                        fontSize = 11.5.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

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

                    // Gemini Live Active Banner (Waveform, Status, Mute & Live Transcripts)
                    AnimatedVisibility(visible = isGeminiLiveActive) {
                        GeminiLiveBanner(
                            status = geminiLiveStatus,
                            statusText = geminiLiveStatusText,
                            isMuted = isGeminiLiveMuted,
                            audioLevel = liveAudioLevel,
                            liveUserTranscript = liveUserTranscript,
                            liveModelTranscript = liveModelTranscript,
                            onToggleMute = { viewModel.toggleGeminiLiveMute() },
                            onStopLive = { viewModel.stopGeminiLive() }
                        )
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
                                    },
                                    onEditCreateProduct = { productAction ->
                                        editingProductEntry = msg.id to productAction
                                    },
                                    onToggleProductTaxExempt = { productAction, isExempt ->
                                        viewModel.updatePendingCreateProductAction(
                                            msg.id,
                                            productAction.copy(isTaxExempt = isExempt)
                                        )
                                    },
                                    onEditCreateQuote = { quoteAction ->
                                        editingQuoteEntry = msg.id to quoteAction
                                    },
                                    onOpenCreatedQuoteTicket = { quoteId ->
                                        val found = allQuotes.find { it.id == quoteId }
                                        if (found != null) {
                                            selectedQuoteForTicket = found
                                        } else {
                                            Toast.makeText(context, "Cargando presupuesto...", Toast.LENGTH_SHORT).show()
                                        }
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

                    // Partial STT live dictation preview when using normal mic
                    AnimatedVisibility(visible = isListening && partialSpeechText.isNotBlank()) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            color = Color(0xFF0C4A6E)
                        ) {
                            Text(
                                text = "🎙️ Escuchando: \"$partialSpeechText\"",
                                color = Color(0xFFE0F2FE),
                                fontSize = 12.sp,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                            )
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
                                text = "📋 Crear Presupuesto",
                                onClick = { inputText = "Crear presupuesto para  con " }
                            )
                        }
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

        editingProductEntry?.let { (messageId, productAction) ->
            EditPendingProductDialog(
                initialAction = productAction,
                bcvRate = bcvRate,
                configuredMinProfitPercent = if (isMinProfitMarginEnabled && minProfitMarginPercent > 0.0) minProfitMarginPercent else null,
                onDismiss = { editingProductEntry = null },
                onSave = { updated ->
                    viewModel.updatePendingCreateProductAction(messageId, updated)
                    editingProductEntry = null
                }
            )
        }

        editingQuoteEntry?.let { (messageId, quoteAction) ->
            EditPendingQuoteDialog(
                initialAction = quoteAction,
                bcvRate = bcvRate,
                onDismiss = { editingQuoteEntry = null },
                onSave = { updated ->
                    viewModel.updatePendingCreateQuoteAction(messageId, updated)
                    editingQuoteEntry = null
                }
            )
        }

        selectedQuoteForTicket?.let { quote ->
            val quoteItems by viewModel.getItemsForQuote(quote.id).collectAsStateWithLifecycle(initialValue = emptyList())
            if (quoteItems.isNotEmpty()) {
                QuoteTicketDialog(
                    quote = quote,
                    items = quoteItems,
                    viewModel = viewModel,
                    onConvertToSale = {
                        viewModel.loadQuoteToBillingCart(quote) {
                            selectedQuoteForTicket = null
                            onNavigateToRoute("pos")
                            onDismiss()
                        }
                    },
                    onDismiss = { selectedQuoteForTicket = null }
                )
            }
        }
    }
}

@Composable
private fun GeminiLiveBanner(
    status: GeminiLiveStatus,
    statusText: String,
    isMuted: Boolean,
    audioLevel: Float,
    liveUserTranscript: String,
    liveModelTranscript: String,
    onToggleMute: () -> Unit,
    onStopLive: () -> Unit
) {
    val bgColor = when (status) {
        GeminiLiveStatus.MODEL_SPEAKING -> Color(0xFF1E1B4B)
        GeminiLiveStatus.PROCESSING_TOOL -> Color(0xFF312E81)
        GeminiLiveStatus.FALLBACK_CONTINUOUS -> Color(0xFF064E3B)
        GeminiLiveStatus.CONNECTING -> Color(0xFF1E293B)
        else -> Color(0xFF064E3B)
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Live animated level bars
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.height(22.dp)
                ) {
                    val multipliers = listOf(0.5f, 0.9f, 1.0f, 0.7f, 0.45f)
                    multipliers.forEach { mult ->
                        val barFraction = if (isMuted) 0.2f else (0.25f + audioLevel * 0.75f * mult).coerceIn(0.2f, 1f)
                        Box(
                            modifier = Modifier
                                .width(3.dp)
                                .fillMaxHeight(barFraction)
                                .clip(RoundedCornerShape(2.dp))
                                .background(
                                    if (isMuted) Color(0xFF94A3B8)
                                    else if (status == GeminiLiveStatus.MODEL_SPEAKING) Color(0xFFA5B4FC)
                                    else Color(0xFF34D399)
                                )
                        )
                    }
                }

                Column {
                    Text(
                        text = if (status == GeminiLiveStatus.FALLBACK_CONTINUOUS) "Voz Continua Activa (Sin cortes)"
                        else "Gemini Live • Tiempo Real",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Text(
                        text = statusText.ifBlank { "Habla con naturalidad o haz correcciones al vuelo..." },
                        color = Color(0xFFCBD5E1),
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                IconButton(
                    onClick = onToggleMute,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(if (isMuted) Color(0xFFB91C1C) else Color(0xFF0F172A).copy(alpha = 0.45f))
                        .testTag("assistant_live_mute_button")
                ) {
                    Icon(
                        if (isMuted) Icons.Filled.MicOff else Icons.Filled.Mic,
                        contentDescription = if (isMuted) "Reactivar micrófono" else "Silenciar micrófono",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
                IconButton(
                    onClick = onStopLive,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF7F1D1D))
                        .testTag("assistant_live_stop_button")
                ) {
                    Icon(
                        Icons.Filled.CallEnd,
                        contentDescription = "Finalizar sesión Live",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (liveUserTranscript.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF0F172A).copy(alpha = 0.55f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Tú: $liveUserTranscript",
                    color = Color(0xFF93C5FD),
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
        if (liveModelTranscript.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Color(0xFF1E1B4B).copy(alpha = 0.7f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Gemini: $liveModelTranscript",
                    color = Color(0xFFE0E7FF),
                    fontSize = 11.5.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
private fun AssistantBubbleItem(
    message: AssistantChatMessage,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onSelectDisambiguation: (DisambiguationItem) -> Unit,
    onEditCreateProduct: (PendingAssistantAction.CreateProductAction) -> Unit = {},
    onToggleProductTaxExempt: (PendingAssistantAction.CreateProductAction, Boolean) -> Unit = { _, _ -> },
    onEditCreateQuote: (PendingAssistantAction.CreateQuoteAction) -> Unit = {},
    onOpenCreatedQuoteTicket: (Int) -> Unit = {}
) {
    val isUser = message.sender == AssistantSender.USER
    val isSystem = message.sender == AssistantSender.SYSTEM
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
                    .background(if (isSystem) Color(0xFF065F46) else Color(0xFF312E81)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isSystem) Icons.Filled.Podcasts else Icons.Filled.SmartToy,
                    contentDescription = null,
                    tint = if (isSystem) Color(0xFF6EE7B7) else Color(0xFF818CF8),
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
                color = when {
                    isUser -> Color(0xFF2563EB)
                    isSystem -> Color(0xFF064E3B)
                    else -> Color(0xFF1E293B)
                },
                border = if (!isUser) BorderStroke(1.dp, if (isSystem) Color(0xFF10B981) else Color(0xFF334155)) else null
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
                            is PendingAssistantAction.CreateProductAction -> {
                                CreateProductConfirmationCard(
                                    action = action,
                                    onConfirm = onConfirm,
                                    onCancel = onCancel,
                                    onModifyClick = { onEditCreateProduct(action) },
                                    onToggleTaxExempt = { exempt -> onToggleProductTaxExempt(action, exempt) }
                                )
                            }
                            is PendingAssistantAction.CreateQuoteAction -> {
                                CreateQuoteConfirmationCard(
                                    action = action,
                                    onConfirm = onConfirm,
                                    onCancel = onCancel,
                                    onModifyClick = { onEditCreateQuote(action) }
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

                    // If a quote was generated, provide instant Ticket & WhatsApp button
                    val quoteId = message.createdQuoteId
                    if (quoteId != null) {
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { onOpenCreatedQuoteTicket(quoteId) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("assistant_quote_whatsapp_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981))
                        ) {
                            Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Ver Ticket / Enviar por WhatsApp", fontWeight = FontWeight.Bold, fontSize = 12.5.sp)
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
private fun CreateProductConfirmationCard(
    action: PendingAssistantAction.CreateProductAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onModifyClick: () -> Unit,
    onToggleTaxExempt: (Boolean) -> Unit
) {
    var verifyChecked by remember(action) { mutableStateOf(action.priceUsd > 0.0 && action.name.isNotBlank()) }
    val canConfirm = verifyChecked && action.name.isNotBlank() && action.priceUsd > 0.0

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
            // Verification checkboxes row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1E293B).copy(alpha = 0.7f))
                    .clickable { verifyChecked = !verifyChecked }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = verifyChecked,
                    onCheckedChange = { verifyChecked = it },
                    modifier = Modifier.testTag("assistant_verify_product_checkbox"),
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF10B981),
                        uncheckedColor = Color(0xFF94A3B8)
                    )
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Verificado para guardar en inventario",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        color = Color(0xFFF1F5F9)
                    )
                    if (action.priceUsd <= 0.0) {
                        Text(
                            "Falta definir el precio de venta",
                            fontSize = 10.5.sp,
                            color = Color(0xFFFBBF24)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onToggleTaxExempt(!action.isTaxExempt) }
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = action.isTaxExempt,
                    onCheckedChange = onToggleTaxExempt,
                    modifier = Modifier.testTag("assistant_tax_exempt_checkbox"),
                    colors = CheckboxDefaults.colors(
                        checkedColor = Color(0xFF38BDF8),
                        uncheckedColor = Color(0xFF94A3B8)
                    )
                )
                Text(
                    "Producto exento de IVA",
                    fontSize = 12.sp,
                    color = Color(0xFFCBD5E1)
                )
            }

            // Button to Modify / Add More Info (Code, Profit %, Wholesale Price, etc.)
            OutlinedButton(
                onClick = onModifyClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assistant_modify_product_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.65f))
            ) {
                Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Modificar / Agregar Info (Código, % Ganancia, Mayor)", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirm,
                    enabled = canConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assistant_confirm_product_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF059669),
                        disabledContainerColor = Color(0xFF334155)
                    )
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
private fun EditPendingProductDialog(
    initialAction: PendingAssistantAction.CreateProductAction,
    bcvRate: Double,
    configuredMinProfitPercent: Double?,
    onDismiss: () -> Unit,
    onSave: (PendingAssistantAction.CreateProductAction) -> Unit
) {
    var name by remember { mutableStateOf(initialAction.name) }
    var code by remember { mutableStateOf(initialAction.code) }
    var category by remember { mutableStateOf(initialAction.category) }
    var stockStr by remember {
        mutableStateOf(
            if (initialAction.stock % 1.0 == 0.0) initialAction.stock.toInt().toString()
            else initialAction.stock.toString()
        )
    }
    var costStr by remember {
        mutableStateOf(if (initialAction.costPriceUsd > 0.0) String.format(Locale.US, "%.2f", initialAction.costPriceUsd) else "")
    }
    var profitPctStr by remember {
        val initialPct = if (initialAction.profitPercentage > 0.0) initialAction.profitPercentage
        else if (initialAction.costPriceUsd > 0.0 && initialAction.priceUsd > 0.0) ((initialAction.priceUsd - initialAction.costPriceUsd) / initialAction.costPriceUsd) * 100.0
        else 0.0
        mutableStateOf(if (initialPct > 0.0) String.format(Locale.US, "%.1f", initialPct) else "")
    }
    var priceStr by remember {
        mutableStateOf(if (initialAction.priceUsd > 0.0) String.format(Locale.US, "%.2f", initialAction.priceUsd) else "")
    }
    var wholesaleStr by remember {
        mutableStateOf(if (initialAction.wholesalePriceUsd > 0.0) String.format(Locale.US, "%.2f", initialAction.wholesalePriceUsd) else "")
    }
    var isRecipe by remember { mutableStateOf(initialAction.isRecipe) }
    var isTaxExempt by remember { mutableStateOf(initialAction.isTaxExempt) }
    var errorMsg by remember { mutableStateOf("") }

    val costVal = costStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val saleVal = priceStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val wholesaleVal = wholesaleStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val profitPctVal = profitPctStr.replace(",", ".").toDoubleOrNull() ?: 0.0

    fun applyProfitPercent(pct: Double) {
        profitPctStr = String.format(Locale.US, "%.1f", pct)
        if (costVal > 0.0) {
            val newPrice = costVal * (1.0 + pct / 100.0)
            priceStr = String.format(Locale.US, "%.2f", newPrice)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.EditNote, contentDescription = null, tint = Color(0xFF38BDF8))
                        Text(
                            "Modificar / Completar Producto",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
                    }
                }

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color(0xFF38BDF8),
                    unfocusedBorderColor = Color(0xFF475569),
                    focusedLabelColor = Color(0xFF38BDF8),
                    unfocusedLabelColor = Color(0xFF94A3B8)
                )

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it; errorMsg = "" },
                    label = { Text("Nombre del Producto *") },
                    leadingIcon = { Icon(Icons.Filled.ShoppingBag, contentDescription = null, tint = Color(0xFF94A3B8)) },
                    singleLine = true,
                    colors = fieldColors,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("assistant_edit_name")
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Código / SKU") },
                        placeholder = { Text("Automático") },
                        leadingIcon = { Icon(Icons.Filled.QrCode, contentDescription = null, tint = Color(0xFF94A3B8)) },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f).testTag("assistant_edit_code")
                    )
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Categoría") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!isRecipe) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = stockStr,
                            onValueChange = { stockStr = it },
                            label = { Text("Existencias") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = fieldColors,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = costStr,
                            onValueChange = { newCostStr ->
                                costStr = newCostStr
                                val c = newCostStr.replace(",", ".").toDoubleOrNull() ?: 0.0
                                val p = profitPctStr.replace(",", ".").toDoubleOrNull() ?: 0.0
                                if (c > 0.0 && p > 0.0) {
                                    priceStr = String.format(Locale.US, "%.2f", c * (1.0 + p / 100.0))
                                } else if (c > 0.0 && configuredMinProfitPercent != null && priceStr.isBlank()) {
                                    profitPctStr = String.format(Locale.US, "%.1f", configuredMinProfitPercent)
                                    priceStr = String.format(Locale.US, "%.2f", c * (1.0 + configuredMinProfitPercent / 100.0))
                                }
                            },
                            label = { Text("Precio Costo ($)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = fieldColors,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("assistant_edit_cost")
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = profitPctStr,
                            onValueChange = { newPct ->
                                profitPctStr = newPct
                                val pct = newPct.replace(",", ".").toDoubleOrNull()
                                if (pct != null && costVal > 0.0) {
                                    priceStr = String.format(Locale.US, "%.2f", costVal * (1.0 + pct / 100.0))
                                    errorMsg = ""
                                }
                            },
                            label = { Text("% Ganancia") },
                            placeholder = { Text("Ej. 30") },
                            trailingIcon = { Text("%", color = Color(0xFF94A3B8), fontWeight = FontWeight.Bold) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = fieldColors,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("assistant_edit_profit_pct")
                        )
                        OutlinedTextField(
                            value = priceStr,
                            onValueChange = { newSale ->
                                priceStr = newSale
                                errorMsg = ""
                                val s = newSale.replace(",", ".").toDoubleOrNull()
                                if (s != null && costVal > 0.0) {
                                    val calcPct = ((s - costVal) / costVal) * 100.0
                                    profitPctStr = String.format(Locale.US, "%.1f", calcPct)
                                }
                            },
                            label = { Text("Precio Venta ($) *") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            colors = fieldColors,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f).testTag("assistant_edit_sale_price")
                        )
                    }

                    // Quick Margin Chips
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (configuredMinProfitPercent != null && configuredMinProfitPercent > 0.0) {
                            item {
                                FilterChip(
                                    selected = kotlin.math.abs(profitPctVal - configuredMinProfitPercent) < 0.1,
                                    onClick = { applyProfitPercent(configuredMinProfitPercent) },
                                    enabled = costVal > 0.0,
                                    label = { Text("+${configuredMinProfitPercent.toInt()}% (Mín.)", fontSize = 11.sp) }
                                )
                            }
                        }
                        items(listOf(20, 30, 40, 50, 100)) { pct ->
                            FilterChip(
                                selected = kotlin.math.abs(profitPctVal - pct) < 0.1,
                                onClick = { applyProfitPercent(pct.toDouble()) },
                                enabled = costVal > 0.0,
                                label = { Text("+$pct%", fontSize = 11.sp) }
                            )
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = priceStr,
                        onValueChange = { priceStr = it; errorMsg = "" },
                        label = { Text("Precio Venta ($) *") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth().testTag("assistant_edit_sale_price")
                    )
                }

                // Wholesale Price Field
                OutlinedTextField(
                    value = wholesaleStr,
                    onValueChange = { wholesaleStr = it },
                    label = { Text("Precio al Mayor ($) (Opcional)") },
                    placeholder = { Text("Ej. 10.50") },
                    leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null, tint = Color(0xFF94A3B8)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    colors = fieldColors,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().testTag("assistant_edit_wholesale_price")
                )

                // Live summary preview
                if (saleVal > 0.0) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                "Venta: \$${String.format(Locale.US, "%.2f", saleVal)} • Bs. ${String.format(Locale.US, "%.2f", saleVal * bcvRate)}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF34D399)
                            )
                            if (!isRecipe && costVal > 0.0) {
                                val unitGain = saleVal - costVal
                                Text(
                                    "Ganancia unitaria: +\$${String.format(Locale.US, "%.2f", unitGain)} (${String.format(Locale.US, "%.1f", profitPctVal)}%)",
                                    fontSize = 11.sp,
                                    color = Color(0xFF93C5FD)
                                )
                            }
                            if (wholesaleVal > 0.0) {
                                Text(
                                    "Al Mayor: \$${String.format(Locale.US, "%.2f", wholesaleVal)} • Bs. ${String.format(Locale.US, "%.2f", wholesaleVal * bcvRate)}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF38BDF8)
                                )
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isTaxExempt = !isTaxExempt }
                    ) {
                        Checkbox(checked = isTaxExempt, onCheckedChange = { isTaxExempt = it })
                        Text("Exento IVA", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { isRecipe = !isRecipe }
                    ) {
                        Checkbox(checked = isRecipe, onCheckedChange = { isRecipe = it })
                        Text("Receta/Servicio", fontSize = 12.sp, color = Color(0xFFE2E8F0))
                    }
                }

                if (errorMsg.isNotBlank()) {
                    Text(errorMsg, color = Color(0xFFF87171), fontSize = 12.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Cancelar", color = Color(0xFF94A3B8))
                    }
                    Button(
                        onClick = {
                            val cleanName = name.trim()
                            if (cleanName.isBlank()) {
                                errorMsg = "El nombre es obligatorio."
                                return@Button
                            }
                            if (saleVal <= 0.0) {
                                errorMsg = "Ingresa un precio de venta mayor a 0."
                                return@Button
                            }
                            val finalStock = if (isRecipe) 0.0 else (stockStr.replace(",", ".").toDoubleOrNull() ?: 0.0)
                            val finalCost = if (isRecipe) 0.0 else costVal
                            val finalPct = if (!isRecipe && finalCost > 0.0) ((saleVal - finalCost) / finalCost) * 100.0 else profitPctVal
                            onSave(
                                initialAction.copy(
                                    name = cleanName,
                                    code = code.trim(),
                                    category = category.trim().ifBlank { "General" },
                                    stock = finalStock,
                                    costPriceUsd = finalCost,
                                    priceUsd = saleVal,
                                    isRecipe = isRecipe,
                                    isTaxExempt = isTaxExempt,
                                    profitPercentage = finalPct,
                                    wholesalePriceUsd = wholesaleVal,
                                    autoCalculatedWithMinMargin = false
                                )
                            )
                        },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("assistant_save_edited_product_button"),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Aplicar Cambios", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateQuoteConfirmationCard(
    action: PendingAssistantAction.CreateQuoteAction,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    onModifyClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F172A)),
        border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.RequestQuote, contentDescription = null, tint = Color(0xFF38BDF8), modifier = Modifier.size(18.dp))
                Text(
                    "Presupuesto para ${action.customerName} (${action.items.size} ítems)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
            }

            OutlinedButton(
                onClick = onModifyClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("assistant_quote_modify_button"),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF38BDF8)),
                border = BorderStroke(1.dp, Color(0xFF38BDF8).copy(alpha = 0.65f))
            ) {
                Icon(Icons.Filled.EditNote, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Modificar Cliente / Validez / Descuento", fontSize = 11.5.sp, fontWeight = FontWeight.Bold)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onConfirm,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("assistant_quote_confirm_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                ) {
                    Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Generar Ticket", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                    Text("Cancelar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun EditPendingQuoteDialog(
    initialAction: PendingAssistantAction.CreateQuoteAction,
    bcvRate: Double,
    onDismiss: () -> Unit,
    onSave: (PendingAssistantAction.CreateQuoteAction) -> Unit
) {
    var customerName by remember { mutableStateOf(initialAction.customerName) }
    var customerDocument by remember { mutableStateOf(initialAction.customerDocument) }
    var customerPhone by remember { mutableStateOf(initialAction.customerPhone) }
    var validityStr by remember { mutableStateOf(initialAction.validityDays.toString()) }
    var discountStr by remember {
        mutableStateOf(if (initialAction.discountUsd > 0.0) String.format(Locale.US, "%.2f", initialAction.discountUsd) else "")
    }
    var notes by remember { mutableStateOf(initialAction.notesOrConditions) }
    var items by remember { mutableStateOf(initialAction.items) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedTextColor = Color.White,
        unfocusedTextColor = Color.White,
        focusedBorderColor = Color(0xFF38BDF8),
        unfocusedBorderColor = Color(0xFF475569),
        focusedLabelColor = Color(0xFF38BDF8),
        unfocusedLabelColor = Color(0xFF94A3B8)
    )

    val liveTotals = remember(items, discountStr, bcvRate) {
        val disc = discountStr.replace(",", ".").toDoubleOrNull() ?: 0.0
        ConversationalAssistantEngine.calculateQuoteTotals(items, disc, bcvRate)
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = Color(0xFF0F172A),
            border = BorderStroke(1.dp, Color(0xFF334155)),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(18.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Editar Presupuesto",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar", tint = Color(0xFF94A3B8))
                    }
                }

                OutlinedTextField(
                    value = customerName,
                    onValueChange = { customerName = it },
                    label = { Text("Cliente") },
                    singleLine = true,
                    colors = fieldColors,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customerDocument,
                        onValueChange = { customerDocument = it },
                        label = { Text("Cédula / RIF") },
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = customerPhone,
                        onValueChange = { customerPhone = it },
                        label = { Text("WhatsApp / Tel.") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = validityStr,
                        onValueChange = { validityStr = it },
                        label = { Text("Validez (días)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = discountStr,
                        onValueChange = { discountStr = it },
                        label = { Text("Descuento ($)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        colors = fieldColors,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                Text("Productos incluidos:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF93C5FD))
                items.forEachIndexed { idx, item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color(0xFF1E293B),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(item.productName, color = Color.White, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
                                Text("$${"%.2f".format(Locale.US, item.priceUsd)} c/u", color = Color(0xFF94A3B8), fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        val nextQty = (item.quantity - 1.0).coerceAtLeast(1.0)
                                        items = items.toMutableList().apply { set(idx, item.copy(quantity = nextQty)) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Remove, contentDescription = "Restar", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                Text(
                                    text = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else item.quantity.toString(),
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                                IconButton(
                                    onClick = {
                                        items = items.toMutableList().apply { set(idx, item.copy(quantity = item.quantity + 1.0)) }
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = "Sumar", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                                if (items.size > 1) {
                                    IconButton(
                                        onClick = {
                                            items = items.toMutableList().apply { removeAt(idx) }
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Quitar", tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                                    }
                                }
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notas / Condiciones") },
                    colors = fieldColors,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = Color(0xFF064E3B),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Total Estimado: $${"%.2f".format(Locale.US, liveTotals.totalUsd)} USD • Bs. ${"%.2f".format(Locale.US, liveTotals.totalBs)}",
                        color = Color(0xFF6EE7B7),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Cancelar", color = Color(0xFF94A3B8))
                    }
                    Button(
                        onClick = {
                            onSave(
                                initialAction.copy(
                                    customerName = customerName.trim().ifBlank { "Consumidor Final" },
                                    customerDocument = customerDocument.trim().ifBlank { "V-00000000" },
                                    customerPhone = customerPhone.trim(),
                                    items = items,
                                    validityDays = (validityStr.toIntOrNull() ?: 7).coerceIn(1, 365),
                                    discountUsd = (discountStr.replace(",", ".").toDoubleOrNull() ?: 0.0).coerceAtLeast(0.0),
                                    notesOrConditions = notes.trim()
                                )
                            )
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB))
                    ) {
                        Text("Aplicar Cambios", fontWeight = FontWeight.Bold)
                    }
                }
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

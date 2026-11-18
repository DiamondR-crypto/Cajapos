package com.example.ui.components

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DeliveryNote
import com.example.data.local.DeliveryNoteItem
import com.example.ui.PosViewModel

@Composable
fun DeliveryNoteDialog(
    note: DeliveryNote,
    items: List<DeliveryNoteItem>,
    viewModel: PosViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    if (items.isEmpty()) return

    val bitmap = remember(note, items) {
        TicketUtils.generateDeliveryNoteBitmap(context, viewModel.prefs, note, items)
    }

    val currentPrinterConnectionType by viewModel.printerConnectionType.collectAsStateWithLifecycle()
    val currentPrinterPaperWidth by viewModel.printerPaperWidth.collectAsStateWithLifecycle()
    val printerMac by viewModel.printerMacAddress.collectAsStateWithLifecycle()
    val printerName by viewModel.printerName.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()

    val customerPhone = remember(note, allCustomers) {
        val matchedCustomer = allCustomers.find {
            (it.documentId.isNotBlank() && it.documentId.equals(note.customerDocument, ignoreCase = true)) ||
            (it.name.isNotBlank() && it.name.equals(note.customerName, ignoreCase = true))
        }
        matchedCustomer?.phone
    }
    val isPrinterConfigured = viewModel.isPrinterConfigured()
    var isPrinting by remember { mutableStateOf(false) }
    var printStatusText by remember { mutableStateOf<String?>(null) }
    var showScanDialog by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 480.dp)
                    .fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.background
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Toolbar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                Icons.Filled.LocalShipping,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column {
                                Text(
                                    "Nota de Entrega ${note.noteNumber}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                if (isPrinterConfigured) {
                                    val connLabel = when (currentPrinterConnectionType) {
                                        "USB" -> "USB OTG"
                                        "BLUETOOTH_CLASSIC" -> "BT Clásico"
                                        else -> "BLE"
                                    }
                                    Text(
                                        "Impresora ($connLabel • $currentPrinterPaperWidth): $printerName",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF059669),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                        }
                    }

                    HorizontalDivider()

                    // Ticket / Document Preview
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .background(Color(0xFFE2E8F0))
                            .padding(16.dp),
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = "Nota de Entrega",
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        )
                    }

                    HorizontalDivider()

                    // Actions
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Thermal Print Button
                        if (isPrinterConfigured) {
                            Button(
                                onClick = {
                                    isPrinting = true
                                    printStatusText = "Conectando con $printerName..."
                                    viewModel.printDeliveryNoteTicket(
                                        context = context,
                                        note = note,
                                        items = items,
                                        onStatus = { printStatusText = it },
                                        onResult = { success, msg ->
                                            isPrinting = false
                                            printStatusText = msg
                                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                        }
                                    )
                                },
                                enabled = !isPrinting,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669))
                            ) {
                                if (isPrinting) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                                    Spacer(Modifier.width(8.dp))
                                    Text(printStatusText ?: "Imprimiendo...", fontSize = 14.sp)
                                } else {
                                    Icon(Icons.Filled.Print, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text("Imprimir Nota ($printerName)", fontWeight = FontWeight.Bold)
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = { showScanDialog = true },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD97706))
                            ) {
                                Icon(Icons.Filled.Print, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Vincular Impresora Térmica (BT / USB)", fontWeight = FontWeight.SemiBold)
                            }
                        }

                        if (printStatusText != null && printerMac.isNotBlank()) {
                            Text(
                                text = printStatusText ?: "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.CenterHorizontally)
                            )
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    val file = TicketUtils.saveDeliveryNoteBitmapToFile(context, bitmap, note.id)
                                    if (file != null) {
                                        TicketUtils.shareFile(context, file, "image/png")
                                    }
                                },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Compartir")
                            }

                            Button(
                                onClick = {
                                    val file = TicketUtils.saveDeliveryNoteBitmapToFile(context, bitmap, note.id)
                                    if (file != null) {
                                        TicketUtils.shareWhatsAppFile(
                                            context = context,
                                            file = file,
                                            phone = customerPhone,
                                            captionText = "Nota de Entrega N° #${note.id} - ${viewModel.prefs.storeName}"
                                        )
                                    }
                                },
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("WhatsApp")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showScanDialog) {
        PrinterScanDialog(
            viewModel = viewModel,
            onDismiss = { showScanDialog = false }
        )
    }
}

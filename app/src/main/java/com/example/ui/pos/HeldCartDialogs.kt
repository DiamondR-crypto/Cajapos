package com.example.ui.pos

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DeliveryNote
import com.example.data.local.HeldSale
import com.example.data.local.HeldSaleItem
import com.example.ui.CartItem
import com.example.ui.PosViewModel
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun HoldCartDialog(
    cart: List<CartItem>,
    customerName: String,
    customerDocument: String,
    onDismiss: () -> Unit,
    onConfirm: (note: String) -> Unit
) {
    var note by remember {
        mutableStateOf(
            if (customerName.isNotBlank() && customerName != "Cliente General") {
                "Cliente: $customerName"
            } else {
                "Factura #${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
            }
        )
    }

    val totalItems = cart.sumOf { it.quantity }
    val totalEstimatedUsd = cart.sumOf { it.product.priceUsd * it.quantity }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.PauseCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Guardar Factura en Espera", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Esta opción deja la factura en pausa para que puedas seguir cobrando a otros clientes.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Artículos:", style = MaterialTheme.typography.labelMedium)
                            Text("$totalItems producto(s)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
                        }
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Total Estimado:", style = MaterialTheme.typography.labelMedium)
                            Text("\$${String.format("%.2f", totalEstimatedUsd)} USD", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Nota de Referencia o Nombre *") },
                    placeholder = { Text("Ej. Mesa 3, Juan Pérez, etc.") },
                    leadingIcon = { Icon(Icons.Filled.BookmarkBorder, contentDescription = null) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(note) },
                shape = RoundedCornerShape(8.dp)
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Guardar en Espera")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

@Composable
fun HeldSalesListDialog(
    viewModel: PosViewModel,
    onDismiss: () -> Unit,
    onRestore: (HeldSale) -> Unit
) {
    val heldSales by viewModel.allHeldSales.collectAsStateWithLifecycle()
    var selectedHeldSale by remember { mutableStateOf<HeldSale?>(null) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .widthIn(max = 600.dp),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.PauseCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Text("Facturas en Espera", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Text("${heldSales.size} factura(s) en pausa", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                if (heldSales.isEmpty()) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                Icons.Filled.Inbox,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                            )
                            Spacer(Modifier.height(8.dp))
                            Text("No hay facturas en espera", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text("Cuando un cliente pague después, guárdala aquí para seguir facturando.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(heldSales) { heldSale ->
                            HeldSaleItemCard(
                                heldSale = heldSale,
                                viewModel = viewModel,
                                onRestoreClick = {
                                    onRestore(heldSale)
                                    onDismiss()
                                },
                                onDeleteClick = {
                                    viewModel.deleteHeldSale(heldSale.id)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeldSaleItemCard(
    heldSale: HeldSale,
    viewModel: PosViewModel,
    onRestoreClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val itemsFlow = remember(heldSale.id) { viewModel.getItemsForHeldSale(heldSale.id) }
    val items by itemsFlow.collectAsStateWithLifecycle(emptyList())
    var expanded by remember { mutableStateOf(false) }
    val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        heldSale.note.ifBlank { "Factura en Espera" },
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${heldSale.customerName} (${heldSale.customerDocument})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        sdf.format(Date(heldSale.timestamp)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "\$${String.format("%.2f", heldSale.totalEstimatedUsd)} USD",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "${heldSale.itemCount} producto(s)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Expand / Items preview
            if (items.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (expanded) "Ocultar detalle" else "Ver ${items.size} producto(s)",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }

                        if (expanded) {
                            Spacer(Modifier.height(4.dp))
                            items.forEach { itm ->
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "${itm.quantity}x ${itm.productName}",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        "\$${String.format("%.2f", itm.priceUsd * itm.quantity)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onDeleteClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Descartar",
                        tint = MaterialTheme.colorScheme.error.copy(alpha = 0.8f),
                        modifier = Modifier.size(18.dp)
                    )
                }

                Spacer(Modifier.width(8.dp))

                Button(
                    onClick = onRestoreClick,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.ShoppingCartCheckout, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Cargar al Carrito", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun CreateDeliveryNoteDialog(
    cart: List<CartItem>,
    bcvRate: Double,
    customerName: String,
    customerDocument: String,
    onDismiss: () -> Unit,
    onConfirm: (customerName: String, customerDoc: String, recipientName: String, reason: String) -> Unit
) {
    var custName by remember { mutableStateOf(if (customerName.isNotBlank() && customerName != "Cliente General") customerName else "") }
    var custDoc by remember { mutableStateOf(if (customerDocument.isNotBlank() && customerDocument != "V-00000000") customerDocument else "") }
    var recipientName by remember { mutableStateOf("") }
    var reason by remember { mutableStateOf("Despacho de mercancía sin factura fiscal") }

    val totalItems = cart.sumOf { it.quantity }
    val totalUsd = cart.sumOf { it.product.priceUsd * it.quantity }
    val totalBs = totalUsd * bcvRate

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Filled.LocalShipping,
                contentDescription = null,
                tint = Color(0xFF0284C7),
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Emitir Nota de Entrega", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Permite entregar o despachar productos sin emitir factura fiscal. Descuenta automáticamente el inventario físico.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF0284C7).copy(alpha = 0.1f),
                    border = BorderStroke(1.dp, Color(0xFF0284C7).copy(alpha = 0.25f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Total $totalItems producto(s)", style = MaterialTheme.typography.labelSmall)
                            Text("Valor Ref: \$${String.format("%.2f", totalUsd)} USD", fontWeight = FontWeight.Bold, color = Color(0xFF0284C7))
                        }
                        Text("Bs. ${String.format("%.2f", totalBs)}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                    }
                }

                OutlinedTextField(
                    value = custName,
                    onValueChange = { custName = it },
                    label = { Text("Nombre o Razón Social Cliente *") },
                    placeholder = { Text("Ej. Distribuidora Los Andes") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = custDoc,
                    onValueChange = { custDoc = it },
                    label = { Text("Cédula / RIF *") },
                    placeholder = { Text("Ej. J-12345678-9") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = recipientName,
                    onValueChange = { recipientName = it },
                    label = { Text("Nombre de quien recibe (Opcional)") },
                    placeholder = { Text("Persona que firma la recepción") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Motivo u Observación") },
                    placeholder = { Text("Ej. Despacho directo, Muestra, Consignación") },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalName = if (custName.isBlank()) "Cliente General" else custName.trim()
                    val finalDoc = if (custDoc.isBlank()) "V-00000000" else custDoc.trim()
                    val finalRecip = if (recipientName.isBlank()) finalName else recipientName.trim()
                    onConfirm(finalName, finalDoc, finalRecip, reason.trim())
                },
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7))
            ) {
                Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Generar y Despachar")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

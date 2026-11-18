package com.example.ui.invoices

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.DeliveryNote
import com.example.data.local.HeldSale
import com.example.data.local.Sale
import com.example.ui.PosViewModel
import com.example.ui.components.DebtsAndRemindersDialog
import com.example.ui.components.DeliveryNoteDialog
import com.example.ui.components.TicketDialog
import com.example.ui.pos.HeldSaleItemCard
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun InvoicesScreen(viewModel: PosViewModel) {
    val sales by viewModel.allSales.collectAsStateWithLifecycle()
    val deliveryNotes by viewModel.allDeliveryNotes.collectAsStateWithLifecycle()
    val heldSales by viewModel.allHeldSales.collectAsStateWithLifecycle()
    val creditSales by viewModel.allCreditSales.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.allPurchaseInvoices.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) } // 0: Facturas, 1: Notas de Entrega, 2: Facturas en Espera, 3: Créditos
    var selectedSale by remember { mutableStateOf<Sale?>(null) }
    var selectedDeliveryNote by remember { mutableStateOf<DeliveryNote?>(null) }
    var noteToCancel by remember { mutableStateOf<DeliveryNote?>(null) }
    var showDebtsDialog by remember { mutableStateOf(false) }
    var debtsInitialTab by remember { mutableStateOf(0) }

    val now = System.currentTimeMillis()
    val dayStart = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    val dayEnd = dayStart + 24 * 60 * 60 * 1000L - 1L

    val pendingCustomerCredits = creditSales.filter { !it.isCreditPaid }
    val dueTodayCreditCount = pendingCustomerCredits.count { it.creditDueDate in dayStart..dayEnd }
    val overdueCreditCount = pendingCustomerCredits.count { it.creditDueDate in 1 until dayStart }

    val pendingSupplierInvoices = purchaseInvoices.filter { !it.isPaid }
    val dueTodayInvoiceCount = pendingSupplierInvoices.count { it.dueDate in dayStart..dayEnd }
    val overdueInvoiceCount = pendingSupplierInvoices.count { it.dueDate in 1 until dayStart }

    Scaffold(containerColor = Color.Transparent) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Reminders Banner if any due today or overdue
            if (dueTodayCreditCount > 0 || overdueCreditCount > 0 || dueTodayInvoiceCount > 0 || overdueInvoiceCount > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .clickable {
                            debtsInitialTab = if (dueTodayCreditCount > 0 || overdueCreditCount > 0) 0 else 1
                            showDebtsDialog = true
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.NotificationsActive, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                            Column {
                                Text(
                                    "Recordatorio de Vencimientos:",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF991B1B)
                                )
                                val remindersList = mutableListOf<String>()
                                if (dueTodayCreditCount > 0) remindersList.add("$dueTodayCreditCount cobro(s) de clientes vencen hoy")
                                if (overdueCreditCount > 0) remindersList.add("$overdueCreditCount cobro(s) atrasados")
                                if (dueTodayInvoiceCount > 0) remindersList.add("$dueTodayInvoiceCount pago(s) a proveedores vencen hoy")
                                if (overdueInvoiceCount > 0) remindersList.add("$overdueInvoiceCount pago(s) a proveedores vencidos")

                                Text(
                                    remindersList.joinToString(" • "),
                                    fontSize = 11.5.sp,
                                    color = Color(0xFFB91C1C)
                                )
                            }
                        }
                        Button(
                            onClick = {
                                debtsInitialTab = if (dueTodayCreditCount > 0 || overdueCreditCount > 0) 0 else 1
                                showDebtsDialog = true
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Ver Deudas", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Top Navigation Tabs (Scrollable for responsive screen widths)
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
            ) {
                ScrollableTabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    edgePadding = 8.dp,
                    indicator = { tabPositions ->
                        if (selectedTab < tabPositions.size) {
                            TabRowDefaults.SecondaryIndicator(
                                Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Facturas (${sales.size})", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("Notas (${deliveryNotes.size})", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 2,
                        onClick = { selectedTab = 2 },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.PauseCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text("En Espera (${heldSales.size})", fontWeight = if (selectedTab == 2) FontWeight.Bold else FontWeight.Normal, fontSize = 12.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == 3,
                        onClick = {
                            debtsInitialTab = 0
                            showDebtsDialog = true
                        },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                                Text("Créditos/Deudas (${pendingCustomerCredits.size})", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFD97706))
                            }
                        }
                    )
                }
            }

            when (selectedTab) {
                0 -> {
                    // TAB: FACTURAS EMITIDAS
                    if (sales.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.ReceiptLong, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                                Spacer(Modifier.height(8.dp))
                                Text("No hay facturas registradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 340.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(sales) { sale ->
                                val paidBs = sale.paymentMobileBs + sale.paymentCardBs
                                val paidUsd = sale.paymentCashUsd
                                val isPaidInBs = (paidUsd <= 0.001)
                                val isPaidInUsd = (paidBs <= 0.001 && paidUsd > 0.001)
                                val rate = if (sale.rateBcv > 0.001) sale.rateBcv else 1.0

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedSale = sale },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text("Factura #${sale.id}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                                                    Surface(
                                                        shape = RoundedCornerShape(4.dp),
                                                        color = when {
                                                            isPaidInBs -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                            isPaidInUsd -> Color(0xFF10B981).copy(alpha = 0.15f)
                                                            else -> Color(0xFFF59E0B).copy(alpha = 0.15f)
                                                        }
                                                    ) {
                                                        Text(
                                                            text = when {
                                                                isPaidInBs -> "Bolívares"
                                                                isPaidInUsd -> if (sale.igtfUsd > 0.001) "USD + IGTF" else "USD Divisas"
                                                                else -> "Mixto"
                                                            },
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold,
                                                            color = when {
                                                                isPaidInBs -> MaterialTheme.colorScheme.onPrimaryContainer
                                                                isPaidInUsd -> Color(0xFF047857)
                                                                else -> Color(0xFFB45309)
                                                            },
                                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                        )
                                                    }
                                                }
                                                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                                Text(sdf.format(Date(sale.timestamp)), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                Text("${sale.customerName} (${sale.customerDocument})", style = MaterialTheme.typography.bodyMedium)
                                            }
                                            Column(horizontalAlignment = Alignment.End) {
                                                if (isPaidInBs) {
                                                    Text(
                                                        "Bs. ${String.format("%.2f", sale.totalBs)}",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else if (isPaidInUsd) {
                                                    Text(
                                                        "\$${String.format("%.2f", sale.totalUsd)} USD",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                } else {
                                                    Text(
                                                        "Bs. ${String.format("%.2f", sale.totalBs)}",
                                                        fontWeight = FontWeight.Bold,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                    Text(
                                                        "(\$${String.format("%.2f", sale.totalUsd)} USD)",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }

                                        Spacer(modifier = Modifier.height(8.dp))
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
                                        Spacer(modifier = Modifier.height(6.dp))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                                        ) {
                                            if (isPaidInBs) {
                                                val baseTaxableBs = sale.baseTaxableUsd * rate
                                                val baseExemptBs = sale.baseExemptUsd * rate
                                                val discountBs = sale.discountUsd * rate
                                                val ivaBs = sale.ivaUsd * rate

                                                Text(
                                                    "Base: Bs. ${String.format("%.2f", baseTaxableBs)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (sale.discountUsd > 0.001) {
                                                    Text(
                                                        "Desc: -Bs. ${String.format("%.2f", discountBs)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFFE11D48),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                if (baseExemptBs > 0.001) {
                                                    Text(
                                                        "Exento: Bs. ${String.format("%.2f", baseExemptBs)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF047857),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                Text(
                                                    "IVA (16%): Bs. ${String.format("%.2f", ivaBs)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                            } else {
                                                Text(
                                                    "Base: \$${String.format("%.2f", sale.baseTaxableUsd)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                                if (sale.discountUsd > 0.001) {
                                                    Text(
                                                        "Desc: -\$${String.format("%.2f", sale.discountUsd)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFFE11D48),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                                if (sale.baseExemptUsd > 0.001) {
                                                    Text(
                                                        "Exento: \$${String.format("%.2f", sale.baseExemptUsd)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF047857),
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                                Text(
                                                    "IVA: \$${String.format("%.2f", sale.ivaUsd)}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.SemiBold
                                                )
                                                if (sale.igtfUsd > 0.001) {
                                                    Text(
                                                        "IGTF: \$${String.format("%.2f", sale.igtfUsd)}",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFFB45309),
                                                        fontWeight = FontWeight.SemiBold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                1 -> {
                    // TAB: NOTAS DE ENTREGA
                    if (deliveryNotes.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.LocalShipping, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                                Spacer(Modifier.height(8.dp))
                                Text("No hay notas de entrega registradas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Puedes emitir notas de entrega desde el Punto de Venta para despachar mercancía sin facturar.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 340.dp),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(deliveryNotes) { note ->
                                val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
                                val isCancelled = note.status == "ANULADO"

                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { selectedDeliveryNote = note },
                                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                    shape = RoundedCornerShape(14.dp),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                                ) {
                                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Text(note.noteNumber, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF0284C7))
                                                Surface(
                                                    shape = RoundedCornerShape(4.dp),
                                                    color = when (note.status) {
                                                        "ENTREGADO" -> Color(0xFF059669).copy(alpha = 0.15f)
                                                        "FACTURADO" -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                                        else -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                                                    }
                                                ) {
                                                    Text(
                                                        text = note.status,
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = when (note.status) {
                                                            "ENTREGADO" -> Color(0xFF059669)
                                                            "FACTURADO" -> MaterialTheme.colorScheme.primary
                                                            else -> MaterialTheme.colorScheme.error
                                                        },
                                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                                    )
                                                }
                                            }

                                            Column(horizontalAlignment = Alignment.End) {
                                                Text(
                                                    "\$${String.format("%.2f", note.totalEstimatedUsd)} USD",
                                                    fontWeight = FontWeight.Bold,
                                                    style = MaterialTheme.typography.titleMedium,
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                                Text(
                                                    "Bs. ${String.format("%.2f", note.totalEstimatedBs)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Text("${note.customerName} (${note.customerDocument})", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                        if (note.recipientName.isNotBlank() && note.recipientName != note.customerName) {
                                            Text("Recibe: ${note.recipientName}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                        }
                                        if (note.reasonOrObservation.isNotBlank()) {
                                            Text("Motivo: ${note.reasonOrObservation}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
                                        }
                                        Text(sdf.format(Date(note.timestamp)), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))

                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            TextButton(
                                                onClick = { selectedDeliveryNote = note },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Icon(Icons.Filled.Visibility, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(4.dp))
                                                Text("Ver Comprobante", fontSize = 12.sp)
                                            }

                                            if (!isCancelled) {
                                                TextButton(
                                                    onClick = { noteToCancel = note },
                                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Icon(Icons.Filled.Cancel, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    Spacer(Modifier.width(4.dp))
                                                    Text("Anular", fontSize = 12.sp)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                2 -> {
                    // TAB: FACTURAS EN ESPERA
                    if (heldSales.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Filled.PauseCircle, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f))
                                Spacer(Modifier.height(8.dp))
                                Text("No hay facturas en espera.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Puedes poner facturas en pausa desde la caja mientras el cliente realiza el pago.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f))
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(heldSales) { heldSale ->
                                HeldSaleItemCard(
                                    heldSale = heldSale,
                                    viewModel = viewModel,
                                    onRestoreClick = {
                                        viewModel.restoreHeldCart(heldSale.id) {
                                            // Handled
                                        }
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

    selectedSale?.let { sale ->
        val items by viewModel.getItemsForSale(sale.id).collectAsStateWithLifecycle(initialValue = emptyList())
        TicketDialog(
            sale = sale,
            items = items,
            viewModel = viewModel,
            onDismiss = { selectedSale = null }
        )
    }

    selectedDeliveryNote?.let { note ->
        val items by viewModel.getItemsForDeliveryNote(note.id).collectAsStateWithLifecycle(initialValue = emptyList())
        DeliveryNoteDialog(
            note = note,
            items = items,
            viewModel = viewModel,
            onDismiss = { selectedDeliveryNote = null }
        )
    }

    noteToCancel?.let { note ->
        AlertDialog(
            onDismissRequest = { noteToCancel = null },
            icon = { Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("¿Anular Nota de Entrega?", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { noteToCancel = null }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            },
            text = {
                Text("Al anular la nota ${note.noteNumber}, los productos entregados se reintegrarán al stock del inventario automáticamente.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.cancelDeliveryNote(note.id)
                        noteToCancel = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sí, Anular y Devolver Stock")
                }
            },
            dismissButton = {
                TextButton(onClick = { noteToCancel = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showDebtsDialog) {
        DebtsAndRemindersDialog(
            viewModel = viewModel,
            initialTab = debtsInitialTab,
            onDismiss = { showDebtsDialog = false }
        )
    }
}

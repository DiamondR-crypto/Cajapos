package com.example.ui.summary

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.Expense
import com.example.data.local.PurchaseInvoice
import com.example.data.local.Sale
import com.example.ui.PosViewModel
import com.example.ui.components.TicketUtils
import com.example.utils.AccountingLedgerCsvExporter
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SummaryScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val sales by viewModel.allSales.collectAsStateWithLifecycle()
    val expenses by viewModel.allExpenses.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.allPurchaseInvoices.collectAsStateWithLifecycle()
    val suppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    var selectedTab by remember { mutableStateOf(0) } // 0: Resumen General, 1: Resumen Detallado, 2: Retenciones y Entradas
    var showClosureDialog by remember { mutableStateOf(false) }

    val todaySdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
    val timeSdf = SimpleDateFormat("HH:mm", Locale.getDefault())
    val todayDateString = todaySdf.format(Date())

    // Today's collections
    val todaySales = sales.filter { todaySdf.format(Date(it.timestamp)) == todayDateString }
    val todayExpenses = expenses.filter { todaySdf.format(Date(it.timestamp)) == todayDateString }
    val todayPurchaseInvoices = purchaseInvoices.filter { todaySdf.format(Date(it.timestamp)) == todayDateString }

    val todaySaleIds = todaySales.map { it.id }
    val todayItems by viewModel.getItemsForSales(todaySaleIds).collectAsStateWithLifecycle(initialValue = emptyList())

    val effectiveRate = if (bcvRate > 0.001) bcvRate else 1.0

    // Calculations for today's sales
    val totalSalesUsd = todaySales.sumOf { it.totalUsd }
    val totalSalesBs = todaySales.sumOf { it.totalBs }

    val totalCashUsd = todaySales.sumOf { it.paymentCashUsd }
    val totalMobileBs = todaySales.sumOf { it.paymentMobileBs }
    val totalCardBs = todaySales.sumOf { it.paymentCardBs }
    val totalCreditUsd = todaySales.sumOf { it.paymentCreditUsd }

    val countCashUsd = todaySales.count { it.paymentCashUsd > 0.001 }
    val countMobileBs = todaySales.count { it.paymentMobileBs > 0.001 }
    val countCardBs = todaySales.count { it.paymentCardBs > 0.001 }
    val countCreditUsd = todaySales.count { it.paymentCreditUsd > 0.001 }

    val totalBaseTaxableUsd = todaySales.sumOf { it.baseTaxableUsd }
    val totalBaseExemptUsd = todaySales.sumOf { it.baseExemptUsd }
    val totalDiscountUsd = todaySales.sumOf { it.discountUsd }
    val totalIvaUsd = todaySales.sumOf { it.ivaUsd }
    val totalIgtfUsd = todaySales.sumOf { it.igtfUsd }

    // Today's expenses calculations
    val todayExpensesUsd = todayExpenses.sumOf { it.amountUsd }
    val todayExpensesBs = todayExpensesUsd * effectiveRate
    val netBalanceUsd = totalSalesUsd - todayExpensesUsd
    val netBalanceBs = netBalanceUsd * effectiveRate

    // Today's withholdings calculations
    val clientWithholdingsToday = todaySales.filter { it.retencionIvaUsd > 0.001 || it.retencionIslrUsd > 0.001 }
    val totalClientRetentionUsdToday = clientWithholdingsToday.sumOf { it.retencionIvaUsd + it.retencionIslrUsd }

    val supplierWithholdingsToday = todayPurchaseInvoices.filter { it.retencionIvaUsd > 0.001 || it.retencionIslrUsd > 0.001 }
    val totalSupplierRetentionUsdToday = supplierWithholdingsToday.sumOf { it.retencionIvaUsd + it.retencionIslrUsd }

    val totalInvoicesEnteredUsdToday = todayPurchaseInvoices.sumOf { it.totalAmount }

    fun triggerExportPdf() {
        val cashierName = currentUser?.username ?: "Cajero"
        val file = DailyClosurePdfGenerator.generateDailyClosurePdf(
            context = context,
            prefs = viewModel.prefs,
            cashierName = cashierName,
            sales = todaySales,
            items = todayItems,
            closureDate = Date()
        )
        if (file != null) {
            TicketUtils.shareFile(context, file, "application/pdf")
        } else {
            Toast.makeText(context, "Error al generar el PDF del cierre diario", Toast.LENGTH_SHORT).show()
        }
    }

    fun triggerExportCsv() {
        val file = AccountingLedgerCsvExporter.exportLedgerToCsv(
            context = context,
            prefs = viewModel.prefs,
            sales = todaySales,
            deliveryNotes = sales.mapNotNull { null }, // Will filter from viewModel if needed or pass empty
            expenses = todayExpenses,
            purchaseInvoices = todayPurchaseInvoices,
            periodName = "Libro Contable Cierre Diario - $todayDateString"
        )
        if (file != null) {
            TicketUtils.shareFile(context, file, "text/csv")
            Toast.makeText(context, "Libro contable CSV generado exitosamente en Bolívares (Bs.)", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "Error al generar el Libro Contable CSV", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            Column(
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ExtendedFloatingActionButton(
                    onClick = { triggerExportCsv() },
                    icon = { Icon(Icons.Filled.TableChart, contentDescription = null) },
                    text = { Text("Exportar Libro Contable CSV (Bs.)") },
                    containerColor = Color(0xFF059669),
                    contentColor = Color.White
                )
                ExtendedFloatingActionButton(
                    onClick = { triggerExportPdf() },
                    icon = { Icon(Icons.Filled.PictureAsPdf, contentDescription = null) },
                    text = { Text("Exportar Cierre PDF") },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "Panel de Resumen Diario",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Jornada: $todayDateString • Tasa BCV: Bs. ${String.format(Locale.US, "%.2f", effectiveRate)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Text(
                        text = "${currentUser?.role ?: "CAJERO"}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Navigation Tabs (Resumen General, Resumen Detallado, Retenciones y Facturas)
            PrimaryTabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                modifier = Modifier.fillMaxWidth()
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("1. Resumen General", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Filled.Analytics, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("2. Resumen Detallado", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Filled.ListAlt, contentDescription = null) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text("3. Retenciones y Entradas", fontWeight = FontWeight.Bold, fontSize = 12.sp) },
                    icon = { Icon(Icons.Filled.Policy, contentDescription = null) }
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // TAB CONTENT
            when (selectedTab) {
                0 -> GeneralSummaryTab(
                    totalSalesUsd = totalSalesUsd,
                    totalSalesBs = totalSalesBs,
                    salesCount = todaySales.size,
                    todayExpensesUsd = todayExpensesUsd,
                    todayExpensesBs = todayExpensesBs,
                    expensesCount = todayExpenses.size,
                    netBalanceUsd = netBalanceUsd,
                    netBalanceBs = netBalanceBs,
                    totalCashUsd = totalCashUsd,
                    countCashUsd = countCashUsd,
                    totalMobileBs = totalMobileBs,
                    countMobileBs = countMobileBs,
                    totalCardBs = totalCardBs,
                    countCardBs = countCardBs,
                    totalCreditUsd = totalCreditUsd,
                    countCreditUsd = countCreditUsd,
                    totalBaseTaxableUsd = totalBaseTaxableUsd,
                    totalBaseExemptUsd = totalBaseExemptUsd,
                    totalDiscountUsd = totalDiscountUsd,
                    totalIvaUsd = totalIvaUsd,
                    totalIgtfUsd = totalIgtfUsd,
                    effectiveRate = effectiveRate,
                    isLandscape = isLandscape,
                    onOpenClosure = { showClosureDialog = true },
                    onExportPdf = { triggerExportPdf() },
                    onExportCsv = { triggerExportCsv() }
                )

                1 -> DetailedSummaryTab(
                    todaySales = todaySales,
                    todayItems = todayItems,
                    todayExpenses = todayExpenses,
                    effectiveRate = effectiveRate,
                    timeSdf = timeSdf,
                    onOpenClosure = { showClosureDialog = true }
                )

                2 -> RetentionsAndInvoicesTab(
                    clientWithholdingsToday = clientWithholdingsToday,
                    supplierWithholdingsToday = supplierWithholdingsToday,
                    todayPurchaseInvoices = todayPurchaseInvoices,
                    suppliers = suppliers,
                    totalClientRetentionUsdToday = totalClientRetentionUsdToday,
                    totalSupplierRetentionUsdToday = totalSupplierRetentionUsdToday,
                    totalInvoicesEnteredUsdToday = totalInvoicesEnteredUsdToday,
                    effectiveRate = effectiveRate,
                    timeSdf = timeSdf
                )
            }
        }
    }

    if (showClosureDialog) {
        CashRegisterClosureDialog(
            sales = todaySales,
            items = todayItems,
            rateBcv = effectiveRate,
            onExportPdf = { triggerExportPdf() },
            onDismiss = { showClosureDialog = false }
        )
    }
}

// ==================== TAB 1: RESUMEN GENERAL ====================
@Composable
fun GeneralSummaryTab(
    totalSalesUsd: Double,
    totalSalesBs: Double,
    salesCount: Int,
    todayExpensesUsd: Double,
    todayExpensesBs: Double,
    expensesCount: Int,
    netBalanceUsd: Double,
    netBalanceBs: Double,
    totalCashUsd: Double,
    countCashUsd: Int,
    totalMobileBs: Double,
    countMobileBs: Int,
    totalCardBs: Double,
    countCardBs: Int,
    totalCreditUsd: Double,
    countCreditUsd: Int,
    totalBaseTaxableUsd: Double,
    totalBaseExemptUsd: Double,
    totalDiscountUsd: Double,
    totalIvaUsd: Double,
    totalIgtfUsd: Double,
    effectiveRate: Double,
    isLandscape: Boolean,
    onOpenClosure: () -> Unit,
    onExportPdf: () -> Unit,
    onExportCsv: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // High-Level Flow Balance Cards (Ingresos vs Egresos / Salidas vs Balance Neto)
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Flujo General de Caja del Día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Ingresos por Ventas
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFECFDF5)),
                        border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.TrendingUp, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(16.dp))
                                Text("Ingresos (Ventas)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF065F46))
                            }
                            Text("\$${String.format(Locale.US, "%.2f", totalSalesUsd)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF047857))
                            Text("Bs. ${String.format(Locale.US, "%.2f", totalSalesBs)} • $salesCount fac.", fontSize = 10.sp, color = Color(0xFF059669))
                        }
                    }

                    // Salidas / Gastos
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                        border = BorderStroke(1.dp, Color(0xFFFECACA)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.TrendingDown, contentDescription = null, tint = Color(0xFFB91C1C), modifier = Modifier.size(16.dp))
                                Text("Salidas (Gastos)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                            }
                            Text("-\$${String.format(Locale.US, "%.2f", todayExpensesUsd)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFFDC2626))
                            Text("-Bs. ${String.format(Locale.US, "%.2f", todayExpensesBs)} • $expensesCount reg.", fontSize = 10.sp, color = Color(0xFFB91C1C))
                        }
                    }

                    // Balance Neto
                    Card(
                        modifier = Modifier.weight(1f),
                        colors = CardDefaults.cardColors(containerColor = if (netBalanceUsd >= 0) Color(0xFFEFF6FF) else Color(0xFFFFF1F2)),
                        border = BorderStroke(1.dp, if (netBalanceUsd >= 0) Color(0xFFBFDBFE) else Color(0xFFFECDD3)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                Icon(Icons.Filled.AccountBalanceWallet, contentDescription = null, tint = if (netBalanceUsd >= 0) Color(0xFF1D4ED8) else Color(0xFFE11D48), modifier = Modifier.size(16.dp))
                                Text("Balance Neto", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = if (netBalanceUsd >= 0) Color(0xFF1E40AF) else Color(0xFFBE123C))
                            }
                            Text("\$${String.format(Locale.US, "%.2f", netBalanceUsd)}", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = if (netBalanceUsd >= 0) Color(0xFF1D4ED8) else Color(0xFFE11D48))
                            Text("Bs. ${String.format(Locale.US, "%.2f", netBalanceBs)}", fontSize = 10.sp, color = if (netBalanceUsd >= 0) Color(0xFF2563EB) else Color(0xFFE11D48))
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    OutlinedButton(
                        onClick = onOpenClosure,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Arqueo Cierre", fontSize = 11.sp)
                    }

                    Button(
                        onClick = onExportCsv,
                        modifier = Modifier.weight(1.2f),
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.TableChart, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Libro CSV (Bs.)", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = onExportPdf,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Cierre PDF", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Ingresos por Método de Pago
        Text("Ingresos por Método de Pago", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)

        if (isLandscape) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SummaryCard(title = "Efectivo Divisas ($ USD)", amount = "$${String.format(Locale.US, "%.2f", totalCashUsd)}", subAmount = "Bs. ${String.format(Locale.US, "%.2f", totalCashUsd * effectiveRate)}", count = countCashUsd, icon = Icons.Filled.AttachMoney)
                }
                Box(modifier = Modifier.weight(1f)) {
                    SummaryCard(title = "Pago Móvil (Bs)", amount = "Bs. ${String.format(Locale.US, "%.2f", totalMobileBs)}", subAmount = "$${String.format(Locale.US, "%.2f", totalMobileBs / effectiveRate)} USD", count = countMobileBs, icon = Icons.Filled.PhoneAndroid)
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    SummaryCard(title = "Punto / Débito (Bs)", amount = "Bs. ${String.format(Locale.US, "%.2f", totalCardBs)}", subAmount = "$${String.format(Locale.US, "%.2f", totalCardBs / effectiveRate)} USD", count = countCardBs, icon = Icons.Filled.CreditCard)
                }
                Box(modifier = Modifier.weight(1f)) {
                    SummaryCard(title = "Ventas a Crédito ($ USD)", amount = "$${String.format(Locale.US, "%.2f", totalCreditUsd)}", subAmount = "Bs. ${String.format(Locale.US, "%.2f", totalCreditUsd * effectiveRate)}", count = countCreditUsd, icon = Icons.Filled.CreditScore)
                }
            }
        } else {
            SummaryCard(title = "Efectivo Divisas ($ USD)", amount = "$${String.format(Locale.US, "%.2f", totalCashUsd)}", subAmount = "Bs. ${String.format(Locale.US, "%.2f", totalCashUsd * effectiveRate)}", count = countCashUsd, icon = Icons.Filled.AttachMoney)
            SummaryCard(title = "Pago Móvil (Bs)", amount = "Bs. ${String.format(Locale.US, "%.2f", totalMobileBs)}", subAmount = "$${String.format(Locale.US, "%.2f", totalMobileBs / effectiveRate)} USD", count = countMobileBs, icon = Icons.Filled.PhoneAndroid)
            SummaryCard(title = "Punto de Venta / Débito (Bs)", amount = "Bs. ${String.format(Locale.US, "%.2f", totalCardBs)}", subAmount = "$${String.format(Locale.US, "%.2f", totalCardBs / effectiveRate)} USD", count = countCardBs, icon = Icons.Filled.CreditCard)
            SummaryCard(title = "Ventas a Crédito ($ USD)", amount = "$${String.format(Locale.US, "%.2f", totalCreditUsd)}", subAmount = "Bs. ${String.format(Locale.US, "%.2f", totalCreditUsd * effectiveRate)}", count = countCreditUsd, icon = Icons.Filled.CreditScore)
        }

        // Resumen Fiscal SENIAT Card
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Filled.AccountBalance, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
                    Text("Resumen Fiscal e Impuestos Recaudados Hoy", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Base Imponible Gravable (IVA 16%):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("\$${String.format(Locale.US, "%.2f", totalBaseTaxableUsd)} USD", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall)
                }

                if (totalBaseExemptUsd > 0.001) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Monto Exento (IVA 0%):", style = MaterialTheme.typography.bodySmall, color = Color(0xFF047857))
                        Text("\$${String.format(Locale.US, "%.2f", totalBaseExemptUsd)} USD", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodySmall, color = Color(0xFF047857))
                    }
                }

                if (totalDiscountUsd > 0.001) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Descuentos Otorgados (pre-IVA):", style = MaterialTheme.typography.bodySmall, color = Color(0xFFE11D48))
                        Text("-\$${String.format(Locale.US, "%.2f", totalDiscountUsd)} USD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = Color(0xFFE11D48))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total IVA Recaudado (16%):", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    Text("\$${String.format(Locale.US, "%.2f", totalIvaUsd)} USD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }

                if (totalIgtfUsd > 0.001) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total IGTF Divisas Recaudado (3%):", style = MaterialTheme.typography.bodySmall, color = Color(0xFFB45309))
                        Text("+\$${String.format(Locale.US, "%.2f", totalIgtfUsd)} USD", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall, color = Color(0xFFB45309))
                    }
                }
            }
        }
    }
}

// ==================== TAB 2: RESUMEN DETALLADO ====================
@Composable
fun DetailedSummaryTab(
    todaySales: List<Sale>,
    todayItems: List<com.example.data.local.SaleItem>,
    todayExpenses: List<Expense>,
    effectiveRate: Double,
    timeSdf: SimpleDateFormat,
    onOpenClosure: () -> Unit
) {
    // Group products sold today
    data class ProductRanking(
        val name: String,
        val isTaxExempt: Boolean,
        val quantity: Double,
        val totalUsd: Double
    )

    val topProducts = remember(todayItems) {
        todayItems.groupBy { it.productName }.map { (name, pItems) ->
            val isExempt = pItems.any { it.isTaxExempt }
            val qty = pItems.sumOf { it.quantity }
            val sumUsd = pItems.sumOf { it.priceUsd * it.quantity }
            ProductRanking(name, isExempt, qty, sumUsd)
        }.sortedByDescending { it.quantity }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Top Products Ranking Card
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Leaderboard, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("Productos Más Vendidos Hoy (${topProducts.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                    Text("${todayItems.sumOf { it.quantity }} un. totales", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                if (topProducts.isEmpty()) {
                    Text("No hay artículos registrados hoy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    topProducts.take(10).forEachIndexed { index, item ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text("#${index + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                                }
                                Text(item.name, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                if (item.isTaxExempt) {
                                    Surface(shape = RoundedCornerShape(4.dp), color = Color(0xFFDCFCE7)) {
                                        Text("EXENTO", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534), modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp))
                                    }
                                }
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("${item.quantity} un.", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                                Text("\$${String.format(Locale.US, "%.2f", item.totalUsd)} USD", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }

        // Detailed Sales Invoices Section
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Receipt, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Text("Facturas Emitidas Hoy (${todaySales.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                if (todaySales.isEmpty()) {
                    Text("No se han emitido facturas de venta hoy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    todaySales.forEach { sale ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Factura #${sale.id} • ${timeSdf.format(Date(sale.timestamp))}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Cliente: ${sale.customerName} (${sale.customerDocument})", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("\$${String.format(Locale.US, "%.2f", sale.totalUsd)} USD", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                                Text("Bs. ${String.format(Locale.US, "%.2f", sale.totalBs)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        }

        // Detailed Outgoings & Expenses Section
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.TrendingDown, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                        Text("Salidas y Gastos Registrados Hoy (${todayExpenses.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                if (todayExpenses.isEmpty()) {
                    Text("No hay gastos operativos registrados hoy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    todayExpenses.forEach { exp ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("[${exp.category}] ${exp.description.ifBlank { "Gasto de la jornada" }}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Registrado por: ${exp.registeredBy.ifBlank { "Usuario" }} • Hora: ${timeSdf.format(Date(exp.timestamp))}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("-\$${String.format(Locale.US, "%.2f", exp.amountUsd)} USD", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), style = MaterialTheme.typography.bodyMedium)
                                Text("-Bs. ${String.format(Locale.US, "%.2f", exp.amountBs)}", style = MaterialTheme.typography.labelSmall, color = Color(0xFFB91C1C))
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}

// ==================== TAB 3: RETENCIONES Y ENTRADAS (SENIAT / PROVEEDORES) ====================
@Composable
fun RetentionsAndInvoicesTab(
    clientWithholdingsToday: List<Sale>,
    supplierWithholdingsToday: List<PurchaseInvoice>,
    todayPurchaseInvoices: List<PurchaseInvoice>,
    suppliers: List<com.example.data.local.Supplier>,
    totalClientRetentionUsdToday: Double,
    totalSupplierRetentionUsdToday: Double,
    totalInvoicesEnteredUsdToday: Double,
    effectiveRate: Double,
    timeSdf: SimpleDateFormat
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 80.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Retention Metrics Cards
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                border = BorderStroke(1.dp, Color(0xFFBFDBFE)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Retenciones de Clientes", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1E40AF))
                    Text("\$${String.format(Locale.US, "%.2f", totalClientRetentionUsdToday)} USD", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF1E3A8A))
                    Text("Bs. ${String.format(Locale.US, "%.2f", totalClientRetentionUsdToday * effectiveRate)}", fontSize = 10.sp, color = Color(0xFF2563EB))
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                border = BorderStroke(1.dp, Color(0xFFFECACA)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Retenciones a Proveedores", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B))
                    Text("\$${String.format(Locale.US, "%.2f", totalSupplierRetentionUsdToday)} USD", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF7F1D1D))
                    Text("Bs. ${String.format(Locale.US, "%.2f", totalSupplierRetentionUsdToday * effectiveRate)}", fontSize = 10.sp, color = Color(0xFFDC2626))
                }
            }

            Card(
                modifier = Modifier.weight(1f),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                border = BorderStroke(1.dp, Color(0xFFBBF7D0)),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Facturas Ingresadas", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF166534))
                    Text("\$${String.format(Locale.US, "%.2f", totalInvoicesEnteredUsdToday)} USD", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF14532D))
                    Text("Bs. ${String.format(Locale.US, "%.2f", totalInvoicesEnteredUsdToday * effectiveRate)}", fontSize = 10.sp, color = Color(0xFF16A34A))
                }
            }
        }

        // Section 1: Retenciones Dejadas por Clientes
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CallReceived, contentDescription = null, tint = Color(0xFF1D4ED8), modifier = Modifier.size(20.dp))
                        Text("Retenciones de Clientes del Día (${clientWithholdingsToday.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                if (clientWithholdingsToday.isEmpty()) {
                    Text("No se generaron retenciones de clientes hoy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    clientWithholdingsToday.forEach { sale ->
                        val retTotal = sale.retencionIvaUsd + sale.retencionIslrUsd
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(sale.customerName, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("RIF: ${sale.customerDocument} • Factura #${sale.id}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Ret. IVA: \$${String.format(Locale.US, "%.2f", sale.retencionIvaUsd)} • Ret. ISLR: \$${String.format(Locale.US, "%.2f", sale.retencionIslrUsd)}", fontSize = 10.sp, color = Color(0xFF1D4ED8))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("\$${String.format(Locale.US, "%.2f", retTotal)} USD", fontWeight = FontWeight.Bold, color = Color(0xFF1D4ED8), style = MaterialTheme.typography.bodyMedium)
                                Text(if (sale.withholdingVoucherNumber.isNotBlank()) "Voucher #${sale.withholdingVoucherNumber}" else "Sin voucher", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        }

        // Section 2: Retenciones a Proveedores Generadas por Mí
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.CallMade, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(20.dp))
                        Text("Retenciones a Proveedores del Día (${supplierWithholdingsToday.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                if (supplierWithholdingsToday.isEmpty()) {
                    Text("No se practicaron retenciones a proveedores hoy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    supplierWithholdingsToday.forEach { inv ->
                        val supplier = suppliers.find { it.id == inv.supplierId }
                        val retTotal = inv.retencionIvaUsd + inv.retencionIslrUsd
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(supplier?.name ?: "Proveedor #${inv.supplierId}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                Text("Factura Compra #${inv.invoiceNumber}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                Text("Ret. IVA: \$${String.format(Locale.US, "%.2f", inv.retencionIvaUsd)} • Ret. ISLR: \$${String.format(Locale.US, "%.2f", inv.retencionIslrUsd)}", fontSize = 10.sp, color = Color(0xFFB91C1C))
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("\$${String.format(Locale.US, "%.2f", retTotal)} USD", fontWeight = FontWeight.Bold, color = Color(0xFFDC2626), style = MaterialTheme.typography.bodyMedium)
                                Text("Comprobante: ${inv.withholdingVoucherNumber.ifBlank { "Sin N°" }}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        }

        // Section 3: Facturas de Compras / Proveedores Ingresadas Hoy
        Card(
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = Color(0xFF166534), modifier = Modifier.size(20.dp))
                        Text("Facturas de Compras Ingresadas Hoy (${todayPurchaseInvoices.size})", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))

                if (todayPurchaseInvoices.isEmpty()) {
                    Text("No se registraron facturas de compras hoy.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    todayPurchaseInvoices.forEach { inv ->
                        val supplier = suppliers.find { it.id == inv.supplierId }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${supplier?.name ?: "Proveedor #${inv.supplierId}"} - Factura #${inv.invoiceNumber}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodySmall)
                                if (inv.description.isNotBlank()) {
                                    Text("Nota: ${inv.description}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text("Ingresado: ${timeSdf.format(Date(inv.timestamp))}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("\$${String.format(Locale.US, "%.2f", inv.totalAmount)} USD", fontWeight = FontWeight.Bold, color = Color(0xFF166534), style = MaterialTheme.typography.bodyMedium)
                                Text("Bs. ${String.format(Locale.US, "%.2f", inv.totalAmount * effectiveRate)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.1f))
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(
    title: String,
    amount: String,
    subAmount: String,
    count: Int,
    icon: ImageVector,
    modifier: Modifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp, vertical = 4.dp)
) {
    Card(
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        modifier = Modifier.size(32.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                        }
                    }
                    Text(title, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                }

                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        "$count fac.",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Column {
                Text(amount, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                Text("Equiv: $subAmount", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

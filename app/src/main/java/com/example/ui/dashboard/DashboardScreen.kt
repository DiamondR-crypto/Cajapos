package com.example.ui.dashboard

import android.content.Intent
import android.widget.Toast
import androidx.core.content.FileProvider
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.local.Product
import com.example.data.local.PurchaseInvoice
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import com.example.ui.PosViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val allSales by viewModel.allSales.collectAsStateWithLifecycle()
    val allSaleItems by viewModel.allSaleItems.collectAsStateWithLifecycle()
    val allProducts by viewModel.allProducts.collectAsStateWithLifecycle()
    val allPurchaseInvoices by viewModel.allPurchaseInvoices.collectAsStateWithLifecycle()
    val allCustomers by viewModel.allCustomers.collectAsStateWithLifecycle()
    val prefs = viewModel.prefs
    val bcvRate by viewModel.bcvRate.collectAsStateWithLifecycle()

    // Selected Month Calendar state
    var calendarState by remember { mutableStateOf(Calendar.getInstance()) }

    val monthFormat = remember { SimpleDateFormat("MMMM yyyy", Locale("es", "ES")) }
    val monthTitle = remember(calendarState) { monthFormat.format(calendarState.time).replaceFirstChar { it.uppercase() } }

    // Start & End Timestamps of Selected Month
    val monthStartTimestamp = remember(calendarState) {
        val cal = calendarState.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.timeInMillis
    }

    val monthEndTimestamp = remember(calendarState) {
        val cal = calendarState.clone() as Calendar
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        cal.timeInMillis
    }

    val daysInMonth = remember(calendarState) {
        calendarState.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    // Filter sales for the selected month
    val monthSales = remember(allSales, monthStartTimestamp, monthEndTimestamp) {
        allSales.filter { it.timestamp in monthStartTimestamp..monthEndTimestamp }
    }

    val monthSaleIds = remember(monthSales) { monthSales.map { it.id }.toSet() }

    val monthSaleItems = remember(allSaleItems, monthSaleIds) {
        allSaleItems.filter { monthSaleIds.contains(it.saleId) }
    }

    val monthPurchaseInvoices = remember(allPurchaseInvoices, monthStartTimestamp, monthEndTimestamp) {
        allPurchaseInvoices.filter { it.timestamp in monthStartTimestamp..monthEndTimestamp }
    }

    // Key Financial Metrics
    val totalRevenueUsd = remember(monthSales) { monthSales.sumOf { it.totalUsd } }
    val totalRevenueBs = remember(monthSales, bcvRate) {
        monthSales.sumOf { it.totalBs.let { bs -> if (bs > 0.001) bs else it.totalUsd * bcvRate } }
    }

    // Cost of Goods Sold (COGS) & Profit Margin
    val productMap = remember(allProducts) { allProducts.associateBy { it.id } }

    val totalCogsUsd = remember(monthSaleItems, productMap) {
        monthSaleItems.sumOf { item ->
            val product = productMap[item.productId]
            val cost = product?.costPriceUsd ?: 0.0
            cost * item.quantity
        }
    }

    val grossProfitUsd = remember(totalRevenueUsd, totalCogsUsd) { totalRevenueUsd - totalCogsUsd }
    val grossProfitMarginPct = remember(totalRevenueUsd, grossProfitUsd) {
        if (totalRevenueUsd > 0.001) (grossProfitUsd / totalRevenueUsd) * 100.0 else 0.0
    }

    // Purchase Invoices & Merchandise Inflow
    val purchaseInvoicesCount = remember(monthPurchaseInvoices) { monthPurchaseInvoices.size }
    val totalPurchasesSpentUsd = remember(monthPurchaseInvoices) { monthPurchaseInvoices.sumOf { it.totalAmount } }

    val merchandiseInflowUnits = remember(monthPurchaseInvoices) {
        // Approximate inflow or count of stock added via purchase invoices
        monthPurchaseInvoices.sumOf { inv -> if (inv.description.contains("unid", ignoreCase = true)) 10.0 else 1.0 }
    }

    // Current Inventory Status
    val totalAvailableStockUnits = remember(allProducts) {
        allProducts.filter { !it.isCombo && !it.isRecipe }.sumOf { it.stock }
    }

    val unitProductsCount = remember(allProducts) {
        allProducts.count { !it.isCombo && !it.isRecipe }
    }

    val recipeComboCount = remember(allProducts) {
        allProducts.count { it.isCombo || it.isRecipe }
    }

    // Daily Sales Breakdown for Bar Chart (1..daysInMonth)
    val dailySalesData = remember(monthSales, daysInMonth, calendarState) {
        val dailyMap = FloatArray(daysInMonth + 1)
        val cal = Calendar.getInstance()
        for (sale in monthSales) {
            cal.timeInMillis = sale.timestamp
            val day = cal.get(Calendar.DAY_OF_MONTH)
            if (day in 1..daysInMonth) {
                dailyMap[day] += sale.totalUsd.toFloat()
            }
        }
        dailyMap
    }

    // Top Selling Products
    val topSellingProducts = remember(monthSaleItems) {
        monthSaleItems.groupBy { it.productName }
            .map { (name, items) ->
                DashboardProductRank(
                    productName = name,
                    quantitySold = items.sumOf { it.quantity },
                    totalRevenueUsd = items.sumOf { it.priceUsd * it.quantity }
                )
            }
            .sortedByDescending { it.quantitySold }
            .take(8)
    }

    // Frequent Customers Ranking
    val topFrequentCustomers = remember(monthSales) {
        monthSales.filter { it.customerName.isNotBlank() && !it.customerName.contains("Consumidor", ignoreCase = true) }
            .groupBy { if (it.customerDocument.isNotBlank()) it.customerDocument else it.customerName }
            .map { (_, custSales) ->
                val sample = custSales.first()
                DashboardCustomerRank(
                    customerName = sample.customerName,
                    document = sample.customerDocument,
                    transactionCount = custSales.size,
                    totalSpentUsd = custSales.sumOf { it.totalUsd }
                )
            }
            .sortedByDescending { it.transactionCount }
            .take(6)
    }

    // Payment Methods Breakdown
    val paymentCashUsd = remember(monthSales) { monthSales.sumOf { it.paymentCashUsd } }
    val paymentMobileBs = remember(monthSales) { monthSales.sumOf { it.paymentMobileBs } }
    val paymentCardBs = remember(monthSales) { monthSales.sumOf { it.paymentCardBs } }
    val paymentCreditUsd = remember(monthSales) { monthSales.sumOf { it.paymentCreditUsd } }

    val paymentBreakdown = remember(paymentCashUsd, paymentMobileBs, paymentCardBs, paymentCreditUsd, bcvRate) {
        mapOf(
            "Efectivo USD" to paymentCashUsd,
            "Pago Móvil" to (if (bcvRate > 0) paymentMobileBs / bcvRate else 0.0),
            "Punto de Venta" to (if (bcvRate > 0) paymentCardBs / bcvRate else 0.0),
            "Ventas a Crédito" to paymentCreditUsd
        )
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // --- MONTH SELECTOR HEADER ---
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val newCal = calendarState.clone() as Calendar
                            newCal.add(Calendar.MONTH, -1)
                            calendarState = newCal
                        }
                    ) {
                        Icon(Icons.Filled.ChevronLeft, contentDescription = "Mes Anterior")
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = monthTitle,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Del 1 al $daysInMonth de $monthTitle",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = {
                                val newCal = calendarState.clone() as Calendar
                                newCal.add(Calendar.MONTH, 1)
                                calendarState = newCal
                            }
                        ) {
                            Icon(Icons.Filled.ChevronRight, contentDescription = "Mes Siguiente")
                        }

                        Spacer(modifier = Modifier.width(8.dp))

                        Button(
                            onClick = {
                                val file = DashboardPdfExporter.generateMonthlyDashboardPdf(
                                    context = context,
                                    prefs = prefs,
                                    monthYearTitle = monthTitle,
                                    totalSalesUsd = totalRevenueUsd,
                                    totalSalesBs = totalRevenueBs,
                                    totalCogsUsd = totalCogsUsd,
                                    grossProfitUsd = grossProfitUsd,
                                    grossProfitMarginPct = grossProfitMarginPct,
                                    purchaseInvoicesCount = purchaseInvoicesCount,
                                    merchandiseInflowUnits = merchandiseInflowUnits,
                                    availableStockUnits = totalAvailableStockUnits,
                                    unitProductsCount = unitProductsCount,
                                    topProducts = topSellingProducts,
                                    topCustomers = topFrequentCustomers,
                                    paymentBreakdownUsd = paymentBreakdown
                                )

                                if (file != null) {
                                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    val intent = Intent(Intent.ACTION_VIEW).apply {
                                        setDataAndType(uri, "application/pdf")
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(intent, "Abrir Reporte PDF del Mes"))
                                    } catch (e: Exception) {
                                        Toast.makeText(context, "PDF guardado en: ${file.absolutePath}", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    Toast.makeText(context, "Error generando PDF", Toast.LENGTH_SHORT).show()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Filled.PictureAsPdf, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Exportar PDF", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // --- KEY FINANCIAL PANELS (2x2 Grid) ---
        item {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 1: Ingresos Totales
                    FinancialKpiCard(
                        modifier = Modifier.weight(1f),
                        title = "INGRESOS DEL MES",
                        valueUsd = String.format(Locale.US, "$%.2f USD", totalRevenueUsd),
                        valueBs = String.format(Locale.US, "%.2f Bs.", totalRevenueBs),
                        subtitle = "${monthSales.size} facturas emitidas",
                        icon = Icons.Filled.AttachMoney,
                        badgeColor = Color(0xFF10B981),
                        gradient = listOf(Color(0xFF064E3B), Color(0xFF022C22))
                    )

                    // Card 2: Margen de Ganancia
                    FinancialKpiCard(
                        modifier = Modifier.weight(1f),
                        title = "MARGEN DE GANANCIA",
                        valueUsd = String.format(Locale.US, "$%.2f USD", grossProfitUsd),
                        valueBs = String.format(Locale.US, "Margen: %.1f%%", grossProfitMarginPct),
                        subtitle = "Costo Mercancía: $${String.format(Locale.US, "%.2f", totalCogsUsd)} USD",
                        icon = Icons.Filled.TrendingUp,
                        badgeColor = Color(0xFF3B82F6),
                        gradient = listOf(Color(0xFF1E3A8A), Color(0xFF172554))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Card 3: Facturas de Compras
                    FinancialKpiCard(
                        modifier = Modifier.weight(1f),
                        title = "COMPRAS A PROVEEDORES",
                        valueUsd = "$purchaseInvoicesCount facturas",
                        valueBs = String.format(Locale.US, "$%.2f USD invertidos", totalPurchasesSpentUsd),
                        subtitle = "Entrada de insumos en el mes",
                        icon = Icons.Filled.LocalShipping,
                        badgeColor = Color(0xFF8B5CF6),
                        gradient = listOf(Color(0xFF4C1D95), Color(0xFF2E1065))
                    )

                    // Card 4: Mercancía Disponible
                    FinancialKpiCard(
                        modifier = Modifier.weight(1f),
                        title = "DISPONIBILIDAD EN STOCK",
                        valueUsd = "$totalAvailableStockUnits unidades",
                        valueBs = "$unitProductsCount ítems unitarios",
                        subtitle = "$recipeComboCount combos/recetas registrados",
                        icon = Icons.Filled.Inventory2,
                        badgeColor = Color(0xFFF59E0B),
                        gradient = listOf(Color(0xFF78350F), Color(0xFF451A03))
                    )
                }
            }
        }

        // --- DAILY SALES GROWTH BAR CHART ---
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
                elevation = CardDefaults.cardElevation(2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Crecimiento Diario de Ventas",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Evolución de dinero ingresado por día ($ monthTitle)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.15f)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.ShowChart, contentDescription = null, tint = Color(0xFF10B981), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Ventas Diarias", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Compose Canvas Daily Sales Bar Chart
                    val maxVal = remember(dailySalesData) {
                        val m = dailySalesData.maxOrNull() ?: 100f
                        if (m < 1f) 100f else m * 1.15f
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                    ) {
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val w = size.width
                            val h = size.height - 30f // Leave space for day labels
                            val barWidth = (w / (daysInMonth + 1)) * 0.75f
                            val stepX = w / (daysInMonth + 1)

                            // Horizontal Grid Lines
                            val gridColor = Color.Gray.copy(alpha = 0.2f)
                            for (i in 1..3) {
                                val gridY = h - (h * (i / 3f))
                                drawLine(
                                    color = gridColor,
                                    start = Offset(0f, gridY),
                                    end = Offset(w, gridY),
                                    strokeWidth = 1f
                                )
                            }

                            // Bars
                            for (day in 1..daysInMonth) {
                                val salesVal = dailySalesData[day]
                                val barHeight = (salesVal / maxVal) * h
                                val x = day * stepX

                                val barColor = if (salesVal > 0) Color(0xFF38BDF8) else Color.Gray.copy(alpha = 0.15f)

                                drawRoundRect(
                                    color = barColor,
                                    topLeft = Offset(x - barWidth / 2, h - barHeight),
                                    size = Size(barWidth, barHeight),
                                    cornerRadius = CornerRadius(4f, 4f)
                                )
                            }
                        }

                        // Day X-Axis Labels (Every 5 days)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            for (day in listOf(1, 5, 10, 15, 20, 25, daysInMonth)) {
                                Text(
                                    text = "Día $day",
                                    fontSize = 9.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        // --- RANKINGS ROW: TOP PRODUCTS & FREQUENT CUSTOMERS ---
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Top Selling Products Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Productos Más Vendidos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (topSellingProducts.isEmpty()) {
                            Text("Sin ventas registradas en este mes", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                        } else {
                            topSellingProducts.take(6).forEachIndexed { idx, item ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.primaryContainer,
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("${idx + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = item.productName,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${item.quantitySold} unid.", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                        Text(String.format(Locale.US, "$%.2f", item.totalRevenueUsd), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }

                // Frequent Customers Card
                Card(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.People, contentDescription = null, tint = Color(0xFF6366F1), modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clientes Frecuentes", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (topFrequentCustomers.isEmpty()) {
                            Text("Sin clientes con compras registradas", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 16.dp))
                        } else {
                            topFrequentCustomers.forEachIndexed { idx, cust ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.secondaryContainer,
                                            modifier = Modifier.size(22.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text("${idx + 1}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                            }
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column {
                                            Text(
                                                text = cust.customerName,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = cust.document.ifBlank { "Sin doc." },
                                                fontSize = 10.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("${cust.transactionCount} compras", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                                        Text(String.format(Locale.US, "$%.2f USD", cust.totalSpentUsd), fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FinancialKpiCard(
    modifier: Modifier = Modifier,
    title: String,
    valueUsd: String,
    valueBs: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    badgeColor: Color,
    gradient: List<Color>
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(Brush.linearGradient(gradient))
                .border(1.dp, badgeColor.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                .padding(14.dp)
        ) {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White.copy(alpha = 0.8f)
                    )
                    Surface(
                        shape = CircleShape,
                        color = badgeColor.copy(alpha = 0.25f),
                        modifier = Modifier.size(28.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(icon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = valueUsd,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )

                Text(
                    text = valueBs,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = badgeColor
                )

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = subtitle,
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.6f)
                )
            }
        }
    }
}

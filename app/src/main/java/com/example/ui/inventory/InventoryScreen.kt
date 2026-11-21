package com.example.ui.inventory

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.data.local.Product
import com.example.data.local.ComboComponent
import com.example.data.local.PurchaseInvoice
import com.example.data.local.Supplier
import com.example.ui.PosViewModel
import com.example.ui.components.LicenseDialog
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(viewModel: PosViewModel) {
    val context = LocalContext.current
    val products by viewModel.allProducts.collectAsStateWithLifecycle()
    val purchaseInvoices by viewModel.allPurchaseInvoices.collectAsStateWithLifecycle()
    val suppliers by viewModel.allSuppliers.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val canEdit = currentUser?.role == "ADMIN" || currentUser?.role == "SUPERVISOR"

    val isLicensed by viewModel.isLicensed.collectAsStateWithLifecycle()
    val isUnlimitedInventory by viewModel.isUnlimitedInventory.collectAsStateWithLifecycle()
    val customProductLimit by viewModel.customProductLimit.collectAsStateWithLifecycle()
    val maxProducts = viewModel.getMaxProductsAllowed()
    val isMinProfitMarginEnabled by viewModel.isMinProfitMarginEnabled.collectAsStateWithLifecycle()
    val minProfitMarginPercent by viewModel.minProfitMarginPercent.collectAsStateWithLifecycle()

    var showLicenseDialog by remember { mutableStateOf(false) }
    var showLimitReachedDialog by remember { mutableStateOf(false) }
    var showImportExportDialog by remember { mutableStateOf(false) }

    var showAddDialog by remember { mutableStateOf(false) }
    var showCsvImportDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var sortOption by remember { mutableStateOf("NAME_ASC") } // "NAME_ASC", "NAME_DESC", "PRICE_ASC", "PRICE_DESC", "STOCK_ASC", "STOCK_DESC"

    // Fast lookups for purchase invoices and suppliers
    val supplierById = remember(suppliers) { suppliers.associateBy { it.id } }
    val invoiceToSupplierMap = remember(purchaseInvoices, suppliers) {
        purchaseInvoices.associate { inv ->
            inv.invoiceNumber.trim() to supplierById[inv.supplierId]
        }
    }

    val categories = products.map { it.category }.distinct().sorted()

    val filteredProducts = products.filter { product ->
        val trimmedInvoice = product.invoiceNumber.trim()
        val supplier = invoiceToSupplierMap[trimmedInvoice]

        val matchesSearch = searchQuery.isBlank() ||
            product.name.contains(searchQuery, ignoreCase = true) ||
            product.code.contains(searchQuery, ignoreCase = true) ||
            (trimmedInvoice.isNotEmpty() && trimmedInvoice.contains(searchQuery, ignoreCase = true)) ||
            (supplier?.name?.contains(searchQuery, ignoreCase = true) == true) ||
            (supplier?.documentId?.contains(searchQuery, ignoreCase = true) == true)

        val matchesCategory = selectedCategory == null || product.category == selectedCategory
        matchesSearch && matchesCategory
    }

    val sortedAndFilteredProducts = remember(filteredProducts, sortOption) {
        when (sortOption) {
            "NAME_ASC" -> filteredProducts.sortedBy { it.name.lowercase() }
            "NAME_DESC" -> filteredProducts.sortedByDescending { it.name.lowercase() }
            "PRICE_ASC" -> filteredProducts.sortedBy { it.priceUsd }
            "PRICE_DESC" -> filteredProducts.sortedByDescending { it.priceUsd }
            "STOCK_ASC" -> filteredProducts.sortedBy { it.stock }
            "STOCK_DESC" -> filteredProducts.sortedByDescending { it.stock }
            else -> filteredProducts.sortedBy { it.name.lowercase() }
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            if (canEdit) {
                FloatingActionButton(
                    onClick = {
                        if (!viewModel.canAddMoreProducts()) {
                            showLimitReachedDialog = true
                        } else {
                            showAddDialog = true
                        }
                    },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Añadir Producto")
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            // License status and inventory capacity banner
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isLicensed) Color(0xFF059669).copy(alpha = 0.1f) else Color(0xFFD97706).copy(alpha = 0.12f),
                border = BorderStroke(1.dp, if (isLicensed) Color(0xFF059669).copy(alpha = 0.3f) else Color(0xFFD97706).copy(alpha = 0.35f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            if (isLicensed) Icons.Filled.VerifiedUser else Icons.Filled.LockClock,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = if (isLicensed) Color(0xFF059669) else Color(0xFFD97706)
                        )
                        Column {
                            Text(
                                text = if (isLicensed) {
                                    "Licencia Activa: ${products.size} / ${if (isUnlimitedInventory) "Ilimitado" else "$customProductLimit prod."}"
                                } else {
                                    "Modo Sin Licencia: ${products.size} / 10 productos máx."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (isLicensed) Color(0xFF059669) else Color(0xFFD97706)
                            )
                            if (!isLicensed && products.size >= 10) {
                                Text(
                                    "Límite de 10 productos alcanzado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                    TextButton(
                        onClick = { showLicenseDialog = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            if (isLicensed) "Configurar" else "Activar Clave",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Actions bar (Export/Import buttons)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = { showCsvImportDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF059669)),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cargar CSV", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { showImportExportDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Filled.ImportExport, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Exportar / Respaldos", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                }
            }

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Buscar por nombre, código, factura o proveedor...") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Clear, contentDescription = "Limpiar búsqueda")
                        }
                    }
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Categorías y Filtros de Ordenación
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    item {
                        FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("Todas las cat.") }
                        )
                    }
                    items(categories) { cat ->
                        FilterChip(
                            selected = selectedCategory == cat,
                            onClick = { selectedCategory = cat },
                            label = { Text(cat) }
                        )
                    }
                }
            }

            // Barra de Criterios de Ordenación
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp)
            ) {
                item {
                    Text(
                        "Ordenar:",
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(end = 4.dp, top = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                item {
                    FilterChip(
                        selected = sortOption == "NAME_ASC",
                        onClick = { sortOption = "NAME_ASC" },
                        label = { Text("Nombre (A-Z)") }
                    )
                }
                item {
                    FilterChip(
                        selected = sortOption == "NAME_DESC",
                        onClick = { sortOption = "NAME_DESC" },
                        label = { Text("Nombre (Z-A)") }
                    )
                }
                item {
                    FilterChip(
                        selected = sortOption == "PRICE_DESC",
                        onClick = { sortOption = "PRICE_DESC" },
                        label = { Text("Precio ($ Mayor)") }
                    )
                }
                item {
                    FilterChip(
                        selected = sortOption == "PRICE_ASC",
                        onClick = { sortOption = "PRICE_ASC" },
                        label = { Text("Precio ($ Menor)") }
                    )
                }
                item {
                    FilterChip(
                        selected = sortOption == "STOCK_DESC",
                        onClick = { sortOption = "STOCK_DESC" },
                        label = { Text("Stock (Mayor)") }
                    )
                }
                item {
                    FilterChip(
                        selected = sortOption == "STOCK_ASC",
                        onClick = { sortOption = "STOCK_ASC" },
                        label = { Text("Stock (Menor)") }
                    )
                }
            }

            if (sortedAndFilteredProducts.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(
                        if (products.isEmpty()) "No hay productos en inventario. Añade uno con el botón +" else "No se encontraron productos coincidentes.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 340.dp),
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(sortedAndFilteredProducts) { product ->
                        val supplier = invoiceToSupplierMap[product.invoiceNumber.trim()]
                        ProductItem(
                            product = product,
                            allProducts = products,
                            supplier = supplier,
                            canEdit = canEdit,
                            onAddStock = {
                                viewModel.updateProduct(product.copy(stock = product.stock + 1))
                            },
                            onEditClick = {
                                if (canEdit) {
                                    editingProduct = product
                                }
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        ProductFormDialog(
            initialProduct = null,
            allProducts = products,
            purchaseInvoices = purchaseInvoices,
            suppliers = suppliers,
            isMinProfitMarginEnabled = isMinProfitMarginEnabled,
            minProfitMarginPercent = minProfitMarginPercent,
            onDismiss = { showAddDialog = false },
            onConfirm = { code, name, cat, price, stock, costPrice, invoiceNum, isTaxExempt, hasCommission, commissionPercentage, commissionAmountUsd, isRecipe, isCombo, comboItemsJson, aisleNumber, aisleQty, depotQty, counterQty, allowDecimal, priceModifiable, wholesalePrice ->
                val added = viewModel.addProduct(
                    code = code,
                    name = name,
                    category = cat,
                    priceUsd = price,
                    stock = stock,
                    costPriceUsd = costPrice,
                    invoiceNumber = invoiceNum,
                    isTaxExempt = isTaxExempt,
                    hasCommission = hasCommission,
                    commissionPercentage = commissionPercentage,
                    commissionAmountUsd = commissionAmountUsd,
                    isRecipe = isRecipe,
                    isCombo = isCombo,
                    comboItemsJson = comboItemsJson,
                    aisleNumber = aisleNumber,
                    aisleQuantity = aisleQty,
                    depotQuantity = depotQty,
                    counterQuantity = counterQty,
                    allowDecimalQuantity = allowDecimal,
                    isPriceModifiable = priceModifiable,
                    wholesalePriceUsd = wholesalePrice,
                    onError = { errorMsg ->
                        Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
                    },
                    onSuccess = {
                        showAddDialog = false
                    }
                )
                if (!added) {
                    showLimitReachedDialog = true
                }
            }
        )
    }

    if (showLimitReachedDialog) {
        AlertDialog(
            onDismissRequest = { showLimitReachedDialog = false },
            icon = { Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Límite de Inventario Alcanzado", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showLimitReachedDialog = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            },
            text = {
                Text(
                    if (!isLicensed) {
                        "La versión sin licencia permite un máximo de 10 productos en inventario (Actualmente tienes ${products.size} productos registrados).\n\nPara registrar más productos o tener inventario ilimitado, el administrador debe registrar una clave de licencia."
                    } else {
                        "Has alcanzado el límite de $maxProducts productos configurado para esta licencia. Puedes ampliar el límite o activar inventario ilimitado desde la gestión de licencia."
                    }
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showLimitReachedDialog = false
                        showLicenseDialog = true
                    }
                ) {
                    Icon(Icons.Filled.VpnKey, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (!isLicensed) "Registrar Clave" else "Gestionar Licencia")
                }
            },
            dismissButton = {
                TextButton(onClick = { showLimitReachedDialog = false }) {
                    Text("Cerrar")
                }
            }
        )
    }

    if (showLicenseDialog) {
        LicenseDialog(
            viewModel = viewModel,
            onDismiss = { showLicenseDialog = false }
        )
    }

    if (showImportExportDialog) {
        InventoryImportExportDialog(
            viewModel = viewModel,
            onDismiss = { showImportExportDialog = false }
        )
    }

    if (showCsvImportDialog) {
        CsvInventoryImportDialog(
            viewModel = viewModel,
            onDismiss = { showCsvImportDialog = false }
        )
    }

    editingProduct?.let { prod ->
        ProductFormDialog(
            initialProduct = prod,
            allProducts = products,
            purchaseInvoices = purchaseInvoices,
            suppliers = suppliers,
            isMinProfitMarginEnabled = isMinProfitMarginEnabled,
            minProfitMarginPercent = minProfitMarginPercent,
            onDismiss = { editingProduct = null },
            onConfirm = { code, name, cat, price, stock, costPrice, invoiceNum, isTaxExempt, hasCommission, commissionPercentage, commissionAmountUsd, isRecipe, isCombo, comboItemsJson, aisleNumber, aisleQty, depotQty, counterQty, allowDecimal, priceModifiable, wholesalePrice ->
                viewModel.updateProduct(
                    prod.copy(
                        code = code,
                        name = name,
                        category = cat,
                        priceUsd = price,
                        stock = if (isRecipe || isCombo) 0.0 else stock,
                        costPriceUsd = if (isRecipe) 0.0 else costPrice,
                        invoiceNumber = if (isRecipe || isCombo) "" else invoiceNum,
                        isTaxExempt = isTaxExempt,
                        hasCommission = hasCommission,
                        commissionPercentage = commissionPercentage,
                        commissionAmountUsd = commissionAmountUsd,
                        isRecipe = isRecipe,
                        isCombo = isCombo,
                        comboItemsJson = comboItemsJson,
                        aisleNumber = aisleNumber,
                        aisleQuantity = aisleQty,
                        depotQuantity = depotQty,
                        counterQuantity = counterQty,
                        allowDecimalQuantity = allowDecimal,
                        isPriceModifiable = priceModifiable,
                        wholesalePriceUsd = wholesalePrice
                    )
                )
                editingProduct = null
            }
        )
    }
}

@Composable
fun ProductItem(
    product: Product,
    allProducts: List<Product> = emptyList(),
    supplier: Supplier?,
    canEdit: Boolean,
    onAddStock: () -> Unit,
    onEditClick: () -> Unit
) {
    val effectiveCost = if (product.isCombo) product.calculateCalculatedCostFromComponents(allProducts) else product.costPriceUsd
    val profit = product.priceUsd - effectiveCost
    val marginPercent = if (effectiveCost > 0) (profit / effectiveCost) * 100.0 else 0.0
    val comboStock = if (product.isCombo) product.calculateAvailableComboStock(allProducts) else product.stock
    val comboComponents = if (product.isCombo) product.getComboComponents() else emptyList()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = canEdit) { onEditClick() },
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = product.name,
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = product.code.ifBlank { "SIN CÓDIGO" },
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = product.category.ifBlank { "General" },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = if (product.isTaxExempt) Color(0xFF10B981).copy(alpha = 0.15f) else MaterialTheme.colorScheme.surfaceVariant,
                            border = if (product.isTaxExempt) BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.4f)) else null
                        ) {
                            Text(
                                text = if (product.isTaxExempt) "Exento IVA" else "IVA 16%",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (product.isTaxExempt) Color(0xFF047857) else MaterialTheme.colorScheme.onSurfaceVariant,
                                fontWeight = if (product.isTaxExempt) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        if (product.isRecipe) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.RestaurantMenu,
                                        contentDescription = null,
                                        tint = Color(0xFFD97706),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Receta",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFD97706),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                        if (product.isCombo) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.LocalOffer,
                                        contentDescription = null,
                                        tint = Color(0xFF7C3AED),
                                        modifier = Modifier.size(12.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "Combo",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFF7C3AED),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (product.isRecipe) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFF59E0B).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "Stock: Ilimitado",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFFD97706),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (product.isCombo) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF8B5CF6).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "Combo Disp: $comboStock",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.labelMedium,
                                color = Color(0xFF7C3AED),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else {
                        if (product.stock <= 5) {
                            Icon(
                                Icons.Filled.Warning,
                                contentDescription = "Stock Bajo",
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.padding(end = 6.dp).size(20.dp)
                            )
                        }
                        Text(
                            text = "Stock: ${product.stock}",
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(end = 10.dp)
                        )
                        if (canEdit) {
                            FilledTonalIconButton(onClick = onAddStock, modifier = Modifier.size(36.dp)) {
                                Icon(Icons.Filled.Add, contentDescription = "Aumentar Stock", modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }

            // Display combo items list if it's a combo
            if (product.isCombo && comboComponents.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF8B5CF6).copy(alpha = 0.08f),
                    border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.2f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text(
                            text = "Productos en este Combo:",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF7C3AED)
                        )
                        comboComponents.forEach { comp ->
                            Text(
                                text = "• ${comp.quantity}x ${comp.productName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.12f))
            Spacer(modifier = Modifier.height(8.dp))

            // Pricing & Profit Breakdown
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    if (!product.isRecipe) {
                        Text(
                            text = "Costo: \$${String.format("%.2f", product.costPriceUsd)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "Venta: \$${String.format("%.2f", product.priceUsd)}",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    if (product.wholesalePriceUsd > 0.0) {
                        Text(
                            text = "Mayor: \$${String.format("%.2f", product.wholesalePriceUsd)}",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF2563EB)
                        )
                    }
                }

                // Profit Chip
                if (product.isRecipe) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF59E0B).copy(alpha = 0.15f),
                        border = BorderStroke(1.dp, Color(0xFFF59E0B).copy(alpha = 0.35f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Filled.SoupKitchen,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Servicio / Receta",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFD97706)
                            )
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = if (profit >= 0) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f),
                        border = BorderStroke(
                            1.dp,
                            if (profit >= 0) Color(0xFF10B981).copy(alpha = 0.35f) else Color(0xFFEF4444).copy(alpha = 0.35f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                if (profit >= 0) Icons.Filled.TrendingUp else Icons.Filled.TrendingDown,
                                contentDescription = null,
                                tint = if (profit >= 0) Color(0xFF10B981) else Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = if (profit >= 0)
                                    "+$${String.format("%.2f", profit)} (${String.format("%.1f", marginPercent)}%)"
                                else
                                    "-$${String.format("%.2f", -profit)} (${String.format("%.1f", marginPercent)}%)",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = if (profit >= 0) Color(0xFF10B981) else Color(0xFFEF4444)
                            )
                        }
                    }
                }
            }

            // Commission Badge
            if (product.hasCommission) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Color(0xFF6366F1).copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            Icons.Filled.MonetizationOn,
                            contentDescription = null,
                            tint = Color(0xFF6366F1),
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Comisión Vendedor: " + if (product.commissionPercentage > 0) "${product.commissionPercentage}% ($${String.format("%.2f", product.calculateUnitCommission())}/u)" else "$${String.format("%.2f", product.commissionAmountUsd)}/u",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF4F46E5)
                        )
                    }
                }
            }

            // Supplier & Invoice information tag (if specified)
            if (product.invoiceNumber.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.ReceiptLong,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Factura Compra: #${product.invoiceNumber}",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            if (supplier != null) {
                                Text(
                                    text = "Comprado a: ${supplier.name} (${supplier.documentId})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                Text(
                                    text = "Factura cargada sin proveedor vinculado",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductFormDialog(
    initialProduct: Product?,
    allProducts: List<Product> = emptyList(),
    purchaseInvoices: List<PurchaseInvoice>,
    suppliers: List<Supplier>,
    isMinProfitMarginEnabled: Boolean = false,
    minProfitMarginPercent: Double = 0.0,
    onDismiss: () -> Unit,
    onConfirm: (
        code: String,
        name: String,
        category: String,
        price: Double,
        stock: Double,
        costPrice: Double,
        invoiceNumber: String,
        isTaxExempt: Boolean,
        hasCommission: Boolean,
        commissionPercentage: Double,
        commissionAmountUsd: Double,
        isRecipe: Boolean,
        isCombo: Boolean,
        comboItemsJson: String,
        aisleNumber: String,
        aisleQuantity: Double,
        depotQuantity: Double,
        counterQuantity: Double,
        allowDecimalQuantity: Boolean,
        isPriceModifiable: Boolean,
        wholesalePrice: Double
    ) -> Unit
) {
    var code by remember { mutableStateOf(initialProduct?.code ?: "") }
    var name by remember { mutableStateOf(initialProduct?.name ?: "") }
    var category by remember { mutableStateOf(initialProduct?.category ?: "") }
    var isRecipe by remember { mutableStateOf(initialProduct?.isRecipe ?: false) }
    var isCombo by remember { mutableStateOf(initialProduct?.isCombo ?: false) }
    var comboComponents by remember { mutableStateOf(initialProduct?.getComboComponents() ?: emptyList<ComboComponent>()) }
    var allowDecimalQuantity by remember { mutableStateOf(initialProduct?.allowDecimalQuantity ?: false) }
    var isPriceModifiable by remember { mutableStateOf(initialProduct?.isPriceModifiable ?: false) }
    
    // New location fields
    var enableLocationSection by remember {
        mutableStateOf(
            (initialProduct?.aisleNumber?.isNotBlank() == true) ||
            ((initialProduct?.aisleQuantity ?: 0.0) > 0.0) ||
            ((initialProduct?.depotQuantity ?: 0.0) > 0.0) ||
            ((initialProduct?.counterQuantity ?: 0.0) > 0.0)
        )
    }
    var aisleNumber by remember { mutableStateOf(initialProduct?.aisleNumber ?: "") }
    var aisleQuantityStr by remember { mutableStateOf(initialProduct?.aisleQuantity?.toString() ?: "0") }
    var depotQuantityStr by remember { mutableStateOf(initialProduct?.depotQuantity?.toString() ?: "0") }
    var counterQuantityStr by remember { mutableStateOf(initialProduct?.counterQuantity?.toString() ?: "0") }

    var costPriceStr by remember { mutableStateOf(if (initialProduct != null && initialProduct.costPriceUsd > 0) initialProduct.costPriceUsd.toString() else "") }
    var salePriceStr by remember { mutableStateOf(if (initialProduct != null && initialProduct.priceUsd > 0) initialProduct.priceUsd.toString() else "") }
    var isSalePriceManuallyEdited by remember { mutableStateOf(initialProduct != null && initialProduct.priceUsd > 0) }
    var wholesalePriceStr by remember { mutableStateOf(if (initialProduct != null && initialProduct.wholesalePriceUsd > 0) initialProduct.wholesalePriceUsd.toString() else "") }
    var stockStr by remember { mutableStateOf(if (initialProduct != null) initialProduct.stock.toString() else "") }
    
    var enableInvoiceSection by remember { mutableStateOf(initialProduct?.invoiceNumber?.isNotBlank() == true) }
    var invoiceNumber by remember { mutableStateOf(initialProduct?.invoiceNumber ?: "") }
    var isTaxExempt by remember { mutableStateOf(initialProduct?.isTaxExempt ?: false) }
    var isInvoiceDropdownExpanded by remember { mutableStateOf(false) }

    var enableSpecialSellingSection by remember {
        mutableStateOf(
            (initialProduct?.allowDecimalQuantity == true) ||
            (initialProduct?.isPriceModifiable == true)
        )
    }

    // Combo component state
    var selectedComponentProduct by remember { mutableStateOf<Product?>(null) }
    var componentQtyStr by remember { mutableStateOf("1") }
    var isComponentDropdownExpanded by remember { mutableStateOf(false) }
    var componentSearchQuery by remember { mutableStateOf("") }

    // Commission State
    var hasCommission by remember { mutableStateOf(initialProduct?.hasCommission ?: false) }
    var commissionMode by remember {
        mutableStateOf(
            if (initialProduct != null && initialProduct.commissionAmountUsd > 0 && initialProduct.commissionPercentage == 0.0) "FIXED" else "PERCENT"
        )
    }
    var commissionPercentageStr by remember {
        mutableStateOf(if (initialProduct != null && initialProduct.commissionPercentage > 0) initialProduct.commissionPercentage.toString() else "")
    }
    var commissionAmountStr by remember {
        mutableStateOf(if (initialProduct != null && initialProduct.commissionAmountUsd > 0) initialProduct.commissionAmountUsd.toString() else "")
    }

    val supplierById = remember(suppliers) { suppliers.associateBy { it.id } }
    val matchedSupplier = remember(invoiceNumber, purchaseInvoices, suppliers) {
        val trimmed = invoiceNumber.trim()
        if (trimmed.isEmpty()) null
        else {
            val inv = purchaseInvoices.find { it.invoiceNumber.trim().equals(trimmed, ignoreCase = true) }
            inv?.let { supplierById[it.supplierId] }
        }
    }

    // Combo calculations
    val calculatedComboCost = remember(comboComponents, allProducts) {
        comboComponents.sumOf { comp ->
            val p = allProducts.find { it.id == comp.productId }
            (p?.costPriceUsd ?: 0.0) * comp.quantity
        }
    }

    val calculatedComboStock = remember(comboComponents, allProducts) {
        if (comboComponents.isEmpty()) 0.0
        else comboComponents.minOfOrNull { comp ->
            val p = allProducts.find { it.id == comp.productId }
            if (p == null || p.isRecipe || p.isCombo) 9999.0 else p.stock / maxOf(1.0, comp.quantity)
        } ?: 0.0
    }

    val rawCostPrice = costPriceStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val costPrice = if (isCombo) calculatedComboCost else rawCostPrice

    // Auto-calculate sale price from cost using the configured minimum fixed profit percentage (when enabled)
    LaunchedEffect(costPrice, isMinProfitMarginEnabled, minProfitMarginPercent, isRecipe) {
        if (initialProduct == null && !isRecipe && isMinProfitMarginEnabled && minProfitMarginPercent > 0.0 && !isSalePriceManuallyEdited) {
            salePriceStr = if (costPrice > 0.0) {
                val autoPrice = costPrice * (1.0 + minProfitMarginPercent / 100.0)
                String.format("%.2f", autoPrice).replace(",", ".")
            } else {
                ""
            }
        }
    }

    val salePrice = salePriceStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val wholesalePrice = wholesalePriceStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val stock = if (isCombo) calculatedComboStock else stockStr.replace(",", ".").toDoubleOrNull() ?: 0.0

    val commPercent = commissionPercentageStr.replace(",", ".").toDoubleOrNull() ?: 0.0
    val commFixed = commissionAmountStr.replace(",", ".").toDoubleOrNull() ?: 0.0

    val unitCommission = if (hasCommission) {
        if (commissionMode == "PERCENT") {
            if (commPercent > 0 && salePrice > 0) salePrice * (commPercent / 100.0) else 0.0
        } else {
            commFixed
        }
    } else 0.0

    // Calculations
    val profitPerUnit = salePrice - costPrice
    val marginOnCostPercent = if (costPrice > 0.0) (profitPerUnit / costPrice) * 100.0 else 0.0
    val marginOnSalePercent = if (salePrice > 0.0) (profitPerUnit / salePrice) * 100.0 else 0.0
    val totalBatchProfit = profitPerUnit * stock

    val wholesaleProfitPerUnit = if (wholesalePrice > 0.0) wholesalePrice - costPrice else 0.0
    val wholesaleMarginOnCostPercent = if (costPrice > 0.0 && wholesalePrice > 0.0) (wholesaleProfitPerUnit / costPrice) * 100.0 else 0.0


    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialProduct == null) "Nuevo Producto / Combo" else "Editar Producto / Combo",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Type Selector Chips (Estándar, Receta, Combo)
                Text("Tipo de Producto:", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilterChip(
                        selected = !isRecipe && !isCombo,
                        onClick = {
                            isRecipe = false
                            isCombo = false
                        },
                        label = { Text("Estándar") },
                        leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isRecipe,
                        onClick = {
                            isRecipe = true
                            isCombo = false
                        },
                        label = { Text("Receta") },
                        leadingIcon = { Icon(Icons.Filled.RestaurantMenu, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                    FilterChip(
                        selected = isCombo,
                        onClick = {
                            isRecipe = false
                            isCombo = true
                        },
                        label = { Text("Combo") },
                        leadingIcon = { Icon(Icons.Filled.LocalOffer, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre del Producto *") },
                    leadingIcon = { Icon(Icons.Filled.ShoppingBag, contentDescription = null) },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = code,
                        onValueChange = { code = it },
                        label = { Text("Código / SKU") },
                        placeholder = { Text("Opcional") },
                        leadingIcon = { Icon(Icons.Filled.QrCode, contentDescription = null) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )

                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Categoría") },
                        leadingIcon = { Icon(Icons.Filled.Category, contentDescription = null) },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (isCombo) {
                    // COMBO BUILDER SECTION
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF8B5CF6).copy(alpha = 0.12f)),
                        border = BorderStroke(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(Icons.Filled.LocalOffer, contentDescription = null, tint = Color(0xFF7C3AED), modifier = Modifier.size(20.dp))
                                Text("Componentes del Combo", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium, color = Color(0xFF7C3AED))
                            }
                            Text(
                                "Selecciona los productos individuales del inventario que integran este combo. Al vender el combo se descontará la existencia individual de cada producto.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            val availableProductsForCombo = remember(allProducts, initialProduct) {
                                allProducts.filter { !it.isCombo && (initialProduct == null || it.id != initialProduct.id) }
                            }

                            val filteredForCombo = availableProductsForCombo.filter {
                                componentSearchQuery.isEmpty() ||
                                        it.name.contains(componentSearchQuery, ignoreCase = true) ||
                                        it.code.contains(componentSearchQuery, ignoreCase = true)
                            }

                            ExposedDropdownMenuBox(
                                expanded = isComponentDropdownExpanded,
                                onExpandedChange = { isComponentDropdownExpanded = !isComponentDropdownExpanded },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = selectedComponentProduct?.name ?: componentSearchQuery,
                                    onValueChange = {
                                        componentSearchQuery = it
                                        selectedComponentProduct = null
                                        isComponentDropdownExpanded = true
                                    },
                                    label = { Text("Buscar producto para añadir al combo...") },
                                    placeholder = { Text("Ej: Refresco 1L") },
                                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = isComponentDropdownExpanded) },
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryEditable).fillMaxWidth()
                                )

                                if (filteredForCombo.isNotEmpty()) {
                                    ExposedDropdownMenu(
                                        expanded = isComponentDropdownExpanded,
                                        onDismissRequest = { isComponentDropdownExpanded = false }
                                    ) {
                                        filteredForCombo.forEach { prod ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(prod.name, fontWeight = FontWeight.Bold)
                                                        Text("Stock indiv: ${prod.stock} • Costo: \$${String.format("%.2f", prod.costPriceUsd)} USD", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                                    }
                                                },
                                                onClick = {
                                                    selectedComponentProduct = prod
                                                    componentSearchQuery = prod.name
                                                    isComponentDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                OutlinedTextField(
                                    value = componentQtyStr,
                                    onValueChange = { componentQtyStr = it },
                                    label = { Text("Cant. en combo") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    shape = RoundedCornerShape(10.dp),
                                    modifier = Modifier.weight(1f)
                                )

                                Button(
                                    onClick = {
                                        val p = selectedComponentProduct
                                        val q = componentQtyStr.replace(",", ".").toDoubleOrNull() ?: 1.0
                                        if (p != null && q > 0) {
                                            val existingIndex = comboComponents.indexOfFirst { it.productId == p.id }
                                            if (existingIndex >= 0) {
                                                val updated = comboComponents.toMutableList()
                                                updated[existingIndex] = updated[existingIndex].copy(quantity = updated[existingIndex].quantity + q)
                                                comboComponents = updated
                                            } else {
                                                comboComponents = comboComponents + ComboComponent(p.id, p.name, q)
                                            }
                                            selectedComponentProduct = null
                                            componentSearchQuery = ""
                                            componentQtyStr = "1"
                                        }
                                    },
                                    enabled = selectedComponentProduct != null && (componentQtyStr.replace(",", ".").toDoubleOrNull() ?: 0.0) > 0.0,
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Añadir")
                                }
                            }

                            if (comboComponents.isNotEmpty()) {
                                HorizontalDivider(color = Color(0xFF8B5CF6).copy(alpha = 0.2f))
                                Text("Productos incluidos (${comboComponents.size}):", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)

                                comboComponents.forEach { comp ->
                                    val prod = allProducts.find { it.id == comp.productId }
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.surface,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text("${comp.quantity}x ${comp.productName}", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                                Text(
                                                    "Costo unit.: \$${String.format("%.2f", prod?.costPriceUsd ?: 0.0)} • Subtotal costo: \$${String.format("%.2f", (prod?.costPriceUsd ?: 0.0) * comp.quantity)} • Stock indiv: ${prod?.stock ?: 0}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    comboComponents = comboComponents.filter { it.productId != comp.productId }
                                                }
                                            ) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = Color(0xFF8B5CF6).copy(alpha = 0.15f),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Costo Calculado Total:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("\$${String.format("%.2f", calculatedComboCost)} USD", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF7C3AED))
                                        }
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Combos Disponibles en Inventario:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                            Text("$calculatedComboStock combos", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = Color(0xFF059669))
                                        }
                                    }
                                }
                            } else {
                                Text("Aún no has añadido productos a este combo.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = salePriceStr,
                            onValueChange = {
                                salePriceStr = it
                                isSalePriceManuallyEdited = it.isNotBlank()
                            },
                            label = { Text("Precio Venta Combo ($) *") },
                            leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = wholesalePriceStr,
                            onValueChange = { wholesalePriceStr = it },
                            label = { Text("Precio al Mayor ($)") },
                            placeholder = { Text("Opcional") },
                            leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                } else if (!isRecipe) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = salePriceStr,
                            onValueChange = {
                                salePriceStr = it
                                isSalePriceManuallyEdited = it.isNotBlank()
                            },
                            label = { Text("Precio de Venta ($) *") },
                            leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = wholesalePriceStr,
                            onValueChange = { wholesalePriceStr = it },
                            label = { Text("Precio al Mayor ($)") },
                            placeholder = { Text("Opcional") },
                            leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = costPriceStr,
                            onValueChange = { costPriceStr = it },
                            label = { Text("Precio Costo ($)") },
                            leadingIcon = { Icon(Icons.Filled.MoneyOff, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = stockStr,
                            onValueChange = { stockStr = it },
                            label = { Text("Existencias / Stock inicial") },
                            leadingIcon = { Icon(Icons.Filled.Inventory2, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (initialProduct == null && isMinProfitMarginEnabled && minProfitMarginPercent > 0.0) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFF10B981).copy(alpha = 0.12f),
                            border = BorderStroke(1.dp, Color(0xFF10B981).copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color(0xFF059669), modifier = Modifier.size(16.dp))
                                Text(
                                    text = "Ganancia mínima automática activa (+${String.format("%.1f", minProfitMarginPercent).removeSuffix(".0")}%). Ingresa el costo para calcular el precio de venta.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFF047857)
                                )
                            }
                        }
                    }

                    // Quick Margin Presets
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            "Margen rápido:",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        listOf(20, 30, 40, 50, 100).forEach { pct ->
                            SuggestionChip(
                                onClick = {
                                    if (costPrice > 0) {
                                        val calcSale = costPrice * (1 + pct / 100.0)
                                        salePriceStr = String.format("%.2f", calcSale).replace(",", ".")
                                        isSalePriceManuallyEdited = true
                                    }
                                },
                                label = { Text("+$pct%", fontSize = 11.sp) },
                                enabled = costPrice > 0,
                                modifier = Modifier.height(28.dp)
                            )
                        }
                    }

                    // Profit Calculator Card
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = if (profitPerUnit >= 0)
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else
                            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        border = BorderStroke(
                            1.dp,
                            if (profitPerUnit >= 0) MaterialTheme.colorScheme.primary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Calculate,
                                    contentDescription = null,
                                    tint = if (profitPerUnit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    "Calculador de Beneficio",
                                    style = MaterialTheme.typography.labelLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = if (profitPerUnit >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Ganancia Neta / Unidad:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = if (profitPerUnit >= 0) "+$${String.format("%.2f", profitPerUnit)}" else "-$${String.format("%.2f", -profitPerUnit)}",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (profitPerUnit >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Margen s/ Costo:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${String.format("%.1f", marginOnCostPercent)}%",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Margen s/ Venta:", style = MaterialTheme.typography.bodySmall)
                                Text(
                                    text = "${String.format("%.1f", marginOnSalePercent)}%",
                                    fontWeight = FontWeight.SemiBold,
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }

                            if (wholesalePrice > 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Ganancia Mayorista / Unidad:", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = if (wholesaleProfitPerUnit >= 0) "+$${String.format("%.2f", wholesaleProfitPerUnit)}" else "-$${String.format("%.2f", -wholesaleProfitPerUnit)}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (wholesaleProfitPerUnit >= 0) Color(0xFF0284C7) else MaterialTheme.colorScheme.error
                                    )
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Margen Mayorista s/ Costo:", style = MaterialTheme.typography.bodySmall)
                                    Text(
                                        text = "${String.format("%.1f", wholesaleMarginOnCostPercent)}%",
                                        fontWeight = FontWeight.SemiBold,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }

                            if (stock > 0) {
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Beneficio Total del Lote:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
                                    Text(
                                        text = if (totalBatchProfit >= 0) "+$${String.format("%.2f", totalBatchProfit)}" else "-$${String.format("%.2f", -totalBatchProfit)}",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (totalBatchProfit >= 0) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                    }
                } else {
                    // Modo Receta / Servicio: Solo precio de venta y mayor
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = salePriceStr,
                            onValueChange = { salePriceStr = it },
                            label = { Text("Precio de Venta ($) *") },
                            leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = wholesalePriceStr,
                            onValueChange = { wholesalePriceStr = it },
                            label = { Text("Precio al Mayor ($)") },
                            placeholder = { Text("Opcional") },
                            leadingIcon = { Icon(Icons.Filled.Payments, contentDescription = null) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Separador y Encabezado de Apartados Opcionales
                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
                
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "Apartados Opcionales",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        "Activa las casillas para desplegar opciones y configuraciones adicionales.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // 1. Ubicación y Stock por Zonas
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enableLocationSection) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, if (enableLocationSection) MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Place, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Ubicación y Stock por Zonas", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Pasillo, depósito y mostrador", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Checkbox(
                                checked = enableLocationSection,
                                onCheckedChange = { enableLocationSection = it }
                            )
                        }

                        AnimatedVisibility(visible = enableLocationSection) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                OutlinedTextField(
                                    value = aisleNumber,
                                    onValueChange = { aisleNumber = it },
                                    label = { Text("Número de Pasillo") },
                                    leadingIcon = { Icon(Icons.Filled.Place, contentDescription = null) },
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedTextField(
                                        value = aisleQuantityStr,
                                        onValueChange = { aisleQuantityStr = it },
                                        label = { Text("Stock Pasillo") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedTextField(
                                        value = depotQuantityStr,
                                        onValueChange = { depotQuantityStr = it },
                                        label = { Text("Stock Depósito") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                    OutlinedTextField(
                                        value = counterQuantityStr,
                                        onValueChange = { counterQuantityStr = it },
                                        label = { Text("Stock Mostrador") },
                                        modifier = Modifier.weight(1f),
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. Factura de Compra / Proveedor
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enableInvoiceSection) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, if (enableInvoiceSection) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.ReceiptLong, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Factura de Compra / Proveedor", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Asociar factura y proveedor de compra", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Checkbox(
                                checked = enableInvoiceSection,
                                onCheckedChange = {
                                    enableInvoiceSection = it
                                    if (!it) invoiceNumber = ""
                                }
                            )
                        }

                        AnimatedVisibility(visible = enableInvoiceSection) {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 4.dp)) {
                                ExposedDropdownMenuBox(
                                    expanded = isInvoiceDropdownExpanded,
                                    onExpandedChange = { isInvoiceDropdownExpanded = !isInvoiceDropdownExpanded },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = invoiceNumber,
                                        onValueChange = {
                                            invoiceNumber = it
                                            isInvoiceDropdownExpanded = true
                                        },
                                        label = { Text("N° de Factura") },
                                        placeholder = { Text("Ej: FAC-00123") },
                                        leadingIcon = { Icon(Icons.Filled.Receipt, contentDescription = null) },
                                        trailingIcon = {
                                            if (purchaseInvoices.isNotEmpty()) {
                                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = isInvoiceDropdownExpanded)
                                            } else if (invoiceNumber.isNotEmpty()) {
                                                IconButton(onClick = { invoiceNumber = "" }) {
                                                    Icon(Icons.Filled.Clear, contentDescription = "Limpiar")
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier
                                            .menuAnchor(MenuAnchorType.PrimaryEditable)
                                            .fillMaxWidth()
                                    )

                                    if (purchaseInvoices.isNotEmpty()) {
                                        ExposedDropdownMenu(
                                            expanded = isInvoiceDropdownExpanded,
                                            onDismissRequest = { isInvoiceDropdownExpanded = false }
                                        ) {
                                            DropdownMenuItem(
                                                text = { Text("Sin Factura (Dejar en blanco)", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold) },
                                                onClick = {
                                                    invoiceNumber = ""
                                                    isInvoiceDropdownExpanded = false
                                                }
                                            )
                                            HorizontalDivider()
                                            purchaseInvoices.forEach { invoice ->
                                                val supp = supplierById[invoice.supplierId]
                                                DropdownMenuItem(
                                                    text = {
                                                        Column {
                                                            Text("Factura: #${invoice.invoiceNumber}", fontWeight = FontWeight.Bold)
                                                            Text(
                                                                "Proveedor: ${supp?.name ?: "Desconocido"} (${supp?.documentId ?: ""}) • $${invoice.totalAmount}",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                    },
                                                    onClick = {
                                                        invoiceNumber = invoice.invoiceNumber
                                                        isInvoiceDropdownExpanded = false
                                                    }
                                                )
                                            }
                                        }
                                    }
                                }

                                if (matchedSupplier != null) {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                            Text(
                                                "Asociado a: ${matchedSupplier.name} (${matchedSupplier.documentId})",
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Exención de IVA
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (isTaxExempt) Color(0xFF10B981).copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, if (isTaxExempt) Color(0xFF10B981).copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(
                                    if (isTaxExempt) Icons.Filled.CheckCircle else Icons.Filled.Info,
                                    contentDescription = null,
                                    tint = if (isTaxExempt) Color(0xFF10B981) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column {
                                    Text("Exento de IVA", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Tasa del 0% de IVA al vender", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Checkbox(
                                checked = isTaxExempt,
                                onCheckedChange = { isTaxExempt = it }
                            )
                        }

                        AnimatedVisibility(visible = isTaxExempt) {
                            Text(
                                "Este producto no generará impuestos (0% IVA) en las facturas de venta.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF047857),
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // 4. Comisión de Venta
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (hasCommission) Color(0xFF6366F1).copy(alpha = 0.10f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, if (hasCommission) Color(0xFF6366F1).copy(alpha = 0.45f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.MonetizationOn, contentDescription = null, tint = if (hasCommission) Color(0xFF6366F1) else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Comisión de Venta", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Ganancia para el cajero o vendedor", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Checkbox(
                                checked = hasCommission,
                                onCheckedChange = { hasCommission = it }
                            )
                        }

                        AnimatedVisibility(visible = hasCommission) {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.padding(top = 4.dp)) {
                                HorizontalDivider(color = Color(0xFF6366F1).copy(alpha = 0.2f))

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    FilterChip(
                                        selected = commissionMode == "PERCENT",
                                        onClick = { commissionMode = "PERCENT" },
                                        label = { Text("% Porcentaje") },
                                        leadingIcon = { Icon(Icons.Filled.Percent, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    FilterChip(
                                        selected = commissionMode == "FIXED",
                                        onClick = { commissionMode = "FIXED" },
                                        label = { Text("\$ Monto Fijo") },
                                        leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null, modifier = Modifier.size(16.dp)) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                if (commissionMode == "PERCENT") {
                                    OutlinedTextField(
                                        value = commissionPercentageStr,
                                        onValueChange = { commissionPercentageStr = it },
                                        label = { Text("Porcentaje de Comisión (%)") },
                                        placeholder = { Text("Ej: 5.0") },
                                        leadingIcon = { Icon(Icons.Filled.Percent, contentDescription = null) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        listOf("2", "5", "8", "10", "15").forEach { pct ->
                                            SuggestionChip(
                                                onClick = { commissionPercentageStr = pct },
                                                label = { Text("$pct%") }
                                            )
                                        }
                                    }
                                } else {
                                    OutlinedTextField(
                                        value = commissionAmountStr,
                                        onValueChange = { commissionAmountStr = it },
                                        label = { Text("Monto de Comisión (\$ USD por unidad)") },
                                        placeholder = { Text("Ej: 1.00") },
                                        leadingIcon = { Icon(Icons.Filled.AttachMoney, contentDescription = null) },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                        shape = RoundedCornerShape(10.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color(0xFF6366F1).copy(alpha = 0.15f),
                                    border = BorderStroke(1.dp, Color(0xFF6366F1).copy(alpha = 0.35f)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("Ganancia Vendedor por unidad:", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                                            Text(
                                                text = "$${String.format("%.2f", unitCommission)} USD",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4338CA)
                                            )
                                        }
                                        Text(
                                            text = "* Se sumará automáticamente a la caja/vendedor al procesar ventas de este producto.",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 5. Opciones Especiales de Venta
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = if (enableSpecialSellingSection) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                    ),
                    border = BorderStroke(1.dp, if (enableSpecialSellingSection) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.weight(1f)) {
                                Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(20.dp))
                                Column {
                                    Text("Opciones Especiales de Venta", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Fracciones decimales o precio editable en caja", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Checkbox(
                                checked = enableSpecialSellingSection,
                                onCheckedChange = {
                                    enableSpecialSellingSection = it
                                    if (!it) {
                                        allowDecimalQuantity = false
                                        isPriceModifiable = false
                                    }
                                }
                            )
                        }

                        AnimatedVisibility(visible = enableSpecialSellingSection) {
                            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Permitir Decimales", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("Venta fraccionada o por peso (ej. 0.5, 1.25)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = allowDecimalQuantity, onCheckedChange = { allowDecimalQuantity = it })
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.15f))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Precio Modificable", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                        Text("Permite cambiar el precio unitario al momento de facturar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    Switch(checked = isPriceModifiable, onCheckedChange = { isPriceModifiable = it })
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancelar")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            if (name.isNotBlank() && salePrice > 0 && (!isCombo || comboComponents.isNotEmpty())) {
                                val pPct = if (hasCommission && commissionMode == "PERCENT") commPercent else 0.0
                                val pAmt = if (hasCommission && commissionMode == "FIXED") commFixed else 0.0
                                val comboJson = if (isCombo) Json.encodeToString(comboComponents) else ""

                                onConfirm(
                                    code.trim(),
                                    name.trim(),
                                    category.trim(),
                                    salePrice,
                                    if (isRecipe || isCombo) 0.0 else stock,
                                    if (isRecipe) 0.0 else costPrice,
                                    if (isRecipe || isCombo || !enableInvoiceSection) "" else invoiceNumber.trim(),
                                    isTaxExempt,
                                    hasCommission,
                                    pPct,
                                    pAmt,
                                    isRecipe,
                                    isCombo,
                                    comboJson,
                                    if (enableLocationSection) aisleNumber.trim() else "",
                                    if (enableLocationSection) aisleQuantityStr.replace(",", ".").toDoubleOrNull() ?: 0.0 else 0.0,
                                    if (enableLocationSection) depotQuantityStr.replace(",", ".").toDoubleOrNull() ?: 0.0 else 0.0,
                                    if (enableLocationSection) counterQuantityStr.replace(",", ".").toDoubleOrNull() ?: 0.0 else 0.0,
                                    allowDecimalQuantity,
                                    isPriceModifiable,
                                    wholesalePrice
                                )
                            }
                        },
                        enabled = name.isNotBlank() && salePrice > 0 && (!isCombo || comboComponents.isNotEmpty()),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text(if (initialProduct == null) "Guardar Producto" else "Actualizar Producto")
                    }
                }
            }
        }
    }
}

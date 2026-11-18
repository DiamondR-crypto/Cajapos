package com.example.ui.assistant

import com.example.data.local.Product
import com.example.data.local.PurchaseInvoice
import com.example.data.local.Sale
import com.example.data.local.Supplier
import com.example.data.repository.AssistantApiResponse
import com.example.data.repository.GeminiRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ConversationalAssistantEngine(
    private val geminiRepository: GeminiRepository
) {

    /**
     * Attempts to resolve user message locally first.
     * If not resolved locally, queries Gemini API.
     */
    suspend fun processMessage(
        rawText: String,
        currentPendingAction: PendingAssistantAction?,
        products: List<Product>,
        suppliers: List<Supplier>,
        sales: List<Sale>,
        bcvRate: Double,
        todaySalesTotalUsd: Double,
        todaySalesTotalBs: Double,
        todaySalesCount: Int
    ): ProcessResult {
        val trimmed = rawText.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. If there is a pending action awaiting confirmation
        if (currentPendingAction != null) {
            val isYes = isConfirmationAffirmative(lower)
            val isNo = isConfirmationNegative(lower)

            if (isYes) {
                return ProcessResult.ConfirmAction(currentPendingAction)
            } else if (isNo) {
                return ProcessResult.CancelAction("Operación cancelada. No se han aplicado cambios al sistema.")
            } else if (currentPendingAction is PendingAssistantAction.SelectDisambiguation) {
                // Check if user replied with a selection index (1, 2, 3...) or product name
                val candidates = currentPendingAction.candidates
                val chosenItem = parseDisambiguationChoice(lower, candidates)
                if (chosenItem != null) {
                    return resolveDisambiguation(chosenItem, currentPendingAction, products)
                }
            }
        }

        // 2. Local Fast Intent - BCV Exchange Rate
        if (isBcvRateIntent(lower)) {
            val formattedRate = "%.2f".format(Locale.US, bcvRate)
            val text = "📊 **Tasa Oficial del BCV:** Bs. $formattedRate por USD.\n(Calculada para todas las operaciones en Bolívares de la caja)."
            return ProcessResult.DirectReply(text)
        }

        // CSV Inventory Import Intent
        if (lower.contains("csv") || (lower.contains("cargar") && lower.contains("inventario")) || (lower.contains("importar") && lower.contains("inventario"))) {
            return ProcessResult.DirectReply(
                "📁 **Carga de Inventario CSV:**\nPuedes presionar el botón verde **'📁 Cargar CSV'** disponible en la barra de herramientas del asistente o en el módulo de Inventario para seleccionar tu archivo `.csv`, validar las columnas y calcular márgenes automáticamente."
            )
        }

        // 3. Local Fast Intent - Low Stock / Out of Stock
        if (isLowStockIntent(lower)) {
            val lowStockProducts = products.filter { !it.isRecipe && it.stock <= 5 }
            val text = if (lowStockProducts.isEmpty()) {
                "✅ Todo el inventario se encuentra en niveles óptimos (ningún producto con 5 o menos existencias)."
            } else {
                val sb = StringBuilder("⚠️ **Productos con Existencias Críticas (<= 5):**\n")
                lowStockProducts.take(10).forEach { p ->
                    sb.append("• **${p.name}**: ${p.stock} uds (Precio: $${"%.2f".format(Locale.US, p.priceUsd)})\n")
                }
                if (lowStockProducts.size > 10) {
                    sb.append("... y ${lowStockProducts.size - 10} productos más en inventario.")
                }
                sb.toString().trim()
            }
            return ProcessResult.DirectReply(text)
        }

        // 4. Local Fast Intent - Today's Sales Summary
        if (isDailySalesIntent(lower)) {
            val text = "📈 **Resumen de Ventas de Hoy:**\n" +
                    "• Total Facturado: **$${"%.2f".format(Locale.US, todaySalesTotalUsd)} USD**\n" +
                    "• Equivalente en Bolívares: **Bs. ${"%.2f".format(Locale.US, todaySalesTotalBs)}**\n" +
                    "• Cantidad de Facturas: **$todaySalesCount ventas registradas**"
            return ProcessResult.DirectReply(text)
        }

        // 5. Local Fast Intent - Add Stock to Existing Product
        val addStockMatch = tryParseAddStockLocal(trimmed, products)
        if (addStockMatch != null) {
            return handleAddStockCandidate(addStockMatch, products)
        }

        // 6. Local Fast Intent - Register New Product (Improved)
        val registerProductMatch = tryParseRegisterProductLocal(trimmed)
        if (registerProductMatch != null) {
            return prepareCreateProductAction(registerProductMatch, bcvRate)
        }

        // 7. Local Fast Intent - Register Customer
        val customerMatch = tryParseRegisterCustomerLocal(trimmed)
        if (customerMatch != null) {
            val action = PendingAssistantAction.CreateCustomerAction(
                name = customerMatch.name,
                documentId = customerMatch.documentId,
                phone = customerMatch.phone,
                address = customerMatch.address
            )
            val text = "He preparado el registro de un nuevo cliente:\n" +
                    "• Nombre: **${customerMatch.name}**\n" +
                    "• Cédula/RIF: **${customerMatch.documentId}**\n" +
                    "• Teléfono: **${customerMatch.phone}**\n\n" +
                    "¿Deseas confirmar el registro de este cliente?"
            return ProcessResult.ProposeAction(action, text)
        }

        // 8. Local Fast Intent - Delete Product
        val deleteProductMatch = tryParseDeleteProductLocal(trimmed, products)
        if (deleteProductMatch != null) {
            val p = deleteProductMatch
            val action = PendingAssistantAction.DeleteProductAction(p.id, p.name)
            val text = "⚠️ **ELIMINAR PRODUCTO**\n" +
                    "¿Estás seguro que deseas eliminar **${p.name}** del inventario?\n" +
                    "Esta acción no se puede deshacer."
            return ProcessResult.ProposeAction(action, text)
        }

        // 9. Local Fast Intent - Register Supplier Purchase Invoice
        val purchaseInvoiceMatch = tryParsePurchaseInvoiceLocal(trimmed, suppliers)
        if (purchaseInvoiceMatch != null) {
            val action = PendingAssistantAction.CreatePurchaseInvoiceAction(
                supplierId = purchaseInvoiceMatch.supplierId,
                supplierName = purchaseInvoiceMatch.supplierName,
                invoiceNumber = purchaseInvoiceMatch.invoiceNumber,
                totalAmount = purchaseInvoiceMatch.totalAmount,
                description = purchaseInvoiceMatch.description
            )
            val text = "He preparado el registro de la Factura de Compra de Proveedor:\n" +
                    "• Proveedor: **${purchaseInvoiceMatch.supplierName}**\n" +
                    "• Factura N°: **${purchaseInvoiceMatch.invoiceNumber}**\n" +
                    "• Monto Total: **$${"%.2f".format(Locale.US, purchaseInvoiceMatch.totalAmount)} USD**\n" +
                    "• Detalle: ${purchaseInvoiceMatch.description.ifBlank { "Compra de mercancía" }}\n\n" +
                    "¿Deseas confirmar el ingreso de esta factura al sistema? (Responde **'Sí'** o pulsa Confirmar)"
            return ProcessResult.ProposeAction(action, text)
        }

        // 10. Local Fast Intent - Simple Inventory / Price Query
        val queryMatch = tryParseInventoryQueryLocal(trimmed, products, bcvRate)
        if (queryMatch != null) {
            return queryMatch
        }

        // 11. Local Fast Intent - Invoice Query
        val invoiceQueryMatch = tryParseInvoiceQueryLocal(trimmed, sales)
        if (invoiceQueryMatch != null) {
            return invoiceQueryMatch
        }

        // 12. If not fully matched locally, delegate to Gemini AI
        val catalogSummary = products.take(40).joinToString("\n") {
            "- [ID:${it.id}] ${it.name} | Cat: ${it.category} | Stock: ${if (it.isRecipe) "Receta" else "${it.stock} uds"} | Costo: $${it.costPriceUsd} | Venta: $${it.priceUsd}"
        }
        val suppliersSummary = suppliers.joinToString("\n") {
            "- [ID:${it.id}] ${it.name} (RIF: ${it.documentId})"
        }

        val todaySalesSummary = "Ventas: $todaySalesCount | Total: $${"%.2f".format(Locale.US, todaySalesTotalUsd)} USD (Bs. ${"%.2f".format(Locale.US, todaySalesTotalBs)})"

        val apiResult = geminiRepository.processAssistantPrompt(
            userPrompt = trimmed,
            catalogSummary = catalogSummary,
            suppliersSummary = suppliersSummary,
            bcvRate = bcvRate,
            todaySalesSummary = todaySalesSummary
        )

        return when (apiResult) {
            is AssistantApiResponse.Message -> {
                ProcessResult.DirectReply(apiResult.text)
            }
            is AssistantApiResponse.Error -> {
                ProcessResult.DirectReply("⚠️ ${apiResult.errorMessage}")
            }
            is AssistantApiResponse.Function -> {
                handleGeminiFunctionCall(apiResult.functionName, apiResult.args, products, suppliers, sales, bcvRate, todaySalesTotalUsd, todaySalesTotalBs, todaySalesCount)
            }
        }
    }

    private fun handleGeminiFunctionCall(
        name: String,
        args: Map<String, Any>,
        products: List<Product>,
        suppliers: List<Supplier>,
        sales: List<Sale>,
        bcvRate: Double,
        todaySalesTotalUsd: Double,
        todaySalesTotalBs: Double,
        todaySalesCount: Int
    ): ProcessResult {
        when (name) {
            "queryInventory" -> {
                val pName = (args["productName"] as? String).orEmpty()
                return findProductAndReply(pName, products, bcvRate)
            }
            "registerNewProduct" -> {
                val pName = (args["name"] as? String).orEmpty().trim()
                val cat = (args["category"] as? String).orEmpty().ifBlank { "General" }
                val stock = ((args["stock"] as? Number)?.toDouble()) ?: 0.0
                val cost = ((args["costPriceUsd"] as? Number)?.toDouble()) ?: 0.0
                val price = ((args["priceUsd"] as? Number)?.toDouble()) ?: 0.0
                val isRecipe = (args["isRecipe"] as? Boolean) ?: false

                if (pName.isBlank() || price <= 0.0) {
                    return ProcessResult.DirectReply("No entendí tu solicitud, puedes repetir tu solicitud indicando al menos el nombre y precio del producto.")
                }

                val action = PendingAssistantAction.CreateProductAction(
                    name = pName,
                    category = cat,
                    stock = if (isRecipe) 0.0 else stock,
                    costPriceUsd = if (isRecipe) 0.0 else cost,
                    priceUsd = price,
                    isRecipe = isRecipe
                )

                val recipeNote = if (isRecipe) "\n• Tipo: **Receta / Servicio (Sin existencias requeridas)**" else "\n• Existencias iniciales: **$stock uds**\n• Costo unitario: **$${"%.2f".format(Locale.US, cost)}**"
                val text = "He preparado el registro del nuevo producto vía Gemini:\n" +
                        "• Nombre: **$pName**\n" +
                        "• Categoría: **$cat**" +
                        recipeNote + "\n" +
                        "• Precio de Venta: **$${"%.2f".format(Locale.US, price)}** (Bs. ${"%.2f".format(Locale.US, price * bcvRate)})\n\n" +
                        "¿Deseas confirmar la creación de este producto? (Responde **'Sí'** o pulsa Confirmar)"
                return ProcessResult.ProposeAction(action, text)
            }
            "addStock" -> {
                val pName = (args["productName"] as? String).orEmpty()
                val qty = ((args["quantity"] as? Number)?.toDouble()) ?: 0.0
                val newCost = (args["costPriceUsd"] as? Number)?.toDouble()

                if (pName.isBlank() || qty <= 0.0) {
                    return ProcessResult.DirectReply("No entendí tu solicitud, puedes repetir tu solicitud indicando el producto y la cantidad de existencias a sumar.")
                }

                return handleAddStockCandidate(AddStockParsed(pName, qty, newCost), products)
            }
            "registerPurchaseInvoice" -> {
                val supplierQuery = (args["supplierName"] as? String).orEmpty()
                val invNum = (args["invoiceNumber"] as? String).orEmpty()
                val total = ((args["totalAmount"] as? Number)?.toDouble()) ?: 0.0
                val desc = (args["description"] as? String).orEmpty()

                val matchedSupplier = suppliers.find { it.name.contains(supplierQuery, ignoreCase = true) }
                    ?: suppliers.firstOrNull()

                val sId = matchedSupplier?.id ?: 0
                val sName = matchedSupplier?.name ?: supplierQuery.ifBlank { "Proveedor General" }

                val action = PendingAssistantAction.CreatePurchaseInvoiceAction(
                    supplierId = sId,
                    supplierName = sName,
                    invoiceNumber = invNum.ifBlank { "F-${System.currentTimeMillis() % 10000}" },
                    totalAmount = total,
                    description = desc
                )

                val text = "He preparado el registro de la Factura de Proveedor:\n" +
                        "• Proveedor: **$sName**\n" +
                        "• Factura N°: **${action.invoiceNumber}**\n" +
                        "• Monto Total: **$${"%.2f".format(Locale.US, total)} USD**\n\n" +
                        "¿Deseas confirmar el ingreso de esta factura de compra? (Responde **'Sí'** o pulsa Confirmar)"
                return ProcessResult.ProposeAction(action, text)
            }
            "checkBcvRate" -> {
                val formattedRate = "%.2f".format(Locale.US, bcvRate)
                return ProcessResult.DirectReply("📊 **Tasa Oficial del BCV:** Bs. $formattedRate por USD.")
            }
            "registerCustomer" -> {
                val name = (args["name"] as? String).orEmpty()
                val doc = (args["documentId"] as? String).orEmpty()
                val phone = (args["phone"] as? String).orEmpty()
                val addr = (args["address"] as? String).orEmpty()
                val action = PendingAssistantAction.CreateCustomerAction(name, doc, phone, addr)
                val text = "Gemini ha preparado el registro del cliente:\n• Nombre: **$name**\n• RIF/Cédula: **$doc**\n\n¿Deseas confirmar?"
                return ProcessResult.ProposeAction(action, text)
            }
            "deleteProduct" -> {
                val pName = (args["productName"] as? String).orEmpty()
                val p = findMatchingProducts(pName, products).firstOrNull() ?: return ProcessResult.DirectReply("No encontré el producto '$pName' para eliminar.")
                val action = PendingAssistantAction.DeleteProductAction(p.id, p.name)
                return ProcessResult.ProposeAction(action, "⚠️ **CONFIRMAR ELIMINACIÓN**\n¿Estás seguro de eliminar **${p.name}**?")
            }
            "queryInvoice" -> {
                val invNum = (args["invoiceNumber"] as? String).orEmpty()
                return handleInvoiceQuery(invNum, sales)
            }
            "getDailySummary" -> {
                return handleDailySummary(todaySalesTotalUsd, todaySalesTotalBs, todaySalesCount)
            }
            else -> {
                return ProcessResult.DirectReply("No entendí tu solicitud, puedes repetir tu solicitud.")
            }
        }
    }

    private fun handleInvoiceQuery(invNum: String, sales: List<Sale>): ProcessResult {
        if (invNum.isBlank()) return ProcessResult.DirectReply("Por favor, indica el número de factura que deseas consultar.")
        val invoice = sales.find { it.id.toString() == invNum || it.withholdingVoucherNumber.contains(invNum, ignoreCase = true) }
        if (invoice == null) return ProcessResult.DirectReply("No encontré ninguna factura con el número '$invNum'.")

        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(invoice.timestamp))
        
        val methods = mutableListOf<String>()
        if (invoice.paymentCashUsd > 0) methods.add("Efectivo ($${invoice.paymentCashUsd})")
        if (invoice.paymentMobileBs > 0) methods.add("Pago Móvil (Bs. ${invoice.paymentMobileBs})")
        if (invoice.paymentCardBs > 0) methods.add("Punto/Tarjeta (Bs. ${invoice.paymentCardBs})")
        if (invoice.paymentCreditUsd > 0) methods.add("Crédito ($${invoice.paymentCreditUsd})")
        
        val text = "🧾 **Factura Encontrada:**\n" +
                "• N° Factura: **${invoice.id}**\n" +
                "• Fecha: $date\n" +
                "• Cliente: **${invoice.customerName}** (${invoice.customerDocument})\n" +
                "• Total: **$${"%.2f".format(Locale.US, invoice.totalUsd)} USD** / Bs. ${"%.2f".format(Locale.US, invoice.totalBs)}\n" +
                "• Métodos de Pago: ${methods.joinToString(", ")}"
        return ProcessResult.DirectReply(text)
    }

    private fun handleDailySummary(totalUsd: Double, totalBs: Double, count: Int): ProcessResult {
        val text = "📈 **Resumen de Ventas de Hoy:**\n" +
                "• Ventas realizadas: **$count**\n" +
                "• Total USD: **$${"%.2f".format(Locale.US, totalUsd)}**\n" +
                "• Total Bs: **Bs. ${"%.2f".format(Locale.US, totalBs)}**\n" +
                "Este resumen incluye todas las transacciones facturadas hoy."
        return ProcessResult.DirectReply(text)
    }

    private fun tryParseInvoiceQueryLocal(text: String, sales: List<Sale>): ProcessResult? {
        val lower = text.lowercase(Locale.ROOT)
        if (!lower.contains("factura") || (!lower.contains("número") && !lower.contains("numero") && !lower.contains("consulta"))) return null

        val numMatch = Regex("(?:número|numero|factura|nro)\\s*(?:es|de|:)?\\s*([a-z0-9\\-]+)", RegexOption.IGNORE_CASE).find(lower)
        val invNum = numMatch?.groupValues?.get(1)?.trim() ?: return null
        return handleInvoiceQuery(invNum, sales)
    }

    private fun prepareCreateProductAction(p: ParsedProduct, bcvRate: Double): ProcessResult {
        val action = PendingAssistantAction.CreateProductAction(
            name = p.name,
            category = p.category,
            stock = p.stock,
            costPriceUsd = p.costPriceUsd,
            priceUsd = p.priceUsd,
            isRecipe = p.isRecipe,
            isTaxExempt = p.isTaxExempt
        )
        val recipeNote = if (p.isRecipe) "\n• Tipo: **Receta / Servicio (Sin existencias requeridas)**" else "\n• Existencias iniciales: **${p.stock} uds**\n• Costo unitario: **$${"%.2f".format(Locale.US, p.costPriceUsd)}**"
        val text = "He preparado el registro del nuevo producto:\n" +
                "• Nombre: **${p.name}**\n" +
                "• Categoría: **${p.category}**" +
                recipeNote + "\n" +
                "• Precio de Venta: **$${"%.2f".format(Locale.US, p.priceUsd)}** (Bs. ${"%.2f".format(Locale.US, p.priceUsd * bcvRate)})\n\n" +
                "¿Deseas confirmar la creación de este producto? (Responde **'Sí'** o pulsa Confirmar)"
        return ProcessResult.ProposeAction(action, text)
    }

    private fun findProductAndReply(query: String, products: List<Product>, bcvRate: Double): ProcessResult {
        val matches = findMatchingProducts(query, products)
        if (matches.isEmpty()) {
            return ProcessResult.DirectReply("No encontré ningún producto que coincida con '$query' en el inventario.")
        }
        if (matches.size == 1) {
            val p = matches.first()
            val stockText = if (p.isRecipe) "Receta / Servicio (Sin control de existencias)" else "${p.stock} unidades disponibles"
            val text = "📦 **${p.name}**\n" +
                    "• Categoría: ${p.category}\n" +
                    "• Existencias: **$stockText**\n" +
                    "• Precio Venta: **$${"%.2f".format(Locale.US, p.priceUsd)}** (Bs. ${"%.2f".format(Locale.US, p.priceUsd * bcvRate)})\n" +
                    "• Precio Costo: $${"%.2f".format(Locale.US, p.costPriceUsd)}"
            return ProcessResult.DirectReply(text)
        }

        // Multiple matches -> show list and disambiguate
        val items = matches.take(5).map {
            DisambiguationItem(
                id = it.id,
                title = it.name,
                subtitle = if (it.isRecipe) "Receta • $${it.priceUsd}" else "Stock: ${it.stock} uds • $${it.priceUsd}",
                category = it.category,
                priceUsd = it.priceUsd,
                stock = it.stock
            )
        }
        val sb = StringBuilder("Encontré ${matches.size} productos con nombres similares:\n")
        items.forEachIndexed { index, item ->
            sb.append("${index + 1}. **${item.title}** (${item.subtitle})\n")
        }
        sb.append("\n¿Sobre cuál de estos productos deseas consultar o realizar cambios?")
        val action = PendingAssistantAction.SelectDisambiguation(
            candidates = items,
            actionIntent = "QUERY_PRODUCT"
        )
        return ProcessResult.ProposeAction(action, sb.toString().trim())
    }

    private fun handleAddStockCandidate(match: AddStockParsed, products: List<Product>): ProcessResult {
        val matchingProducts = findMatchingProducts(match.productQuery, products)

        if (matchingProducts.isEmpty()) {
            return ProcessResult.DirectReply("No encontré el producto '${match.productQuery}' en el inventario para añadir existencias.")
        }

        if (matchingProducts.size > 1) {
            // Multiple similar products found!
            val disambiguationItems = matchingProducts.take(4).map {
                DisambiguationItem(
                    id = it.id,
                    title = it.name,
                    subtitle = if (it.isRecipe) "Receta" else "Stock actual: ${it.stock} uds",
                    category = it.category,
                    priceUsd = it.priceUsd,
                    stock = it.stock
                )
            }
            val sb = StringBuilder("Encontré ${matchingProducts.size} productos con nombres similares:\n")
            disambiguationItems.forEachIndexed { index, item ->
                sb.append("${index + 1}. **${item.title}** (${item.subtitle})\n")
            }
            sb.append("\n¿A cuál de los dos productos modifico o añado existencias?")

            val action = PendingAssistantAction.SelectDisambiguation(
                candidates = disambiguationItems,
                actionIntent = "ADD_STOCK",
                quantity = match.quantity,
                newCost = match.newCostPrice
            )
            return ProcessResult.ProposeAction(action, sb.toString().trim())
        }

        val targetProduct = matchingProducts.first()
        val action = PendingAssistantAction.AddStockAction(
            productId = targetProduct.id,
            productName = targetProduct.name,
            currentStock = targetProduct.stock,
            quantityToAdd = match.quantity,
            newCostPriceUsd = match.newCostPrice
        )

        val newStock = targetProduct.stock + match.quantity
        val costNote = if (match.newCostPrice != null) "\n• Nuevo Costo: $${"%.2f".format(Locale.US, match.newCostPrice)}" else ""
        val text = "He preparado la adición de existencias:\n" +
                "• Producto: **${targetProduct.name}**\n" +
                "• Stock Actual: **${targetProduct.stock} uds**\n" +
                "• Cantidad a Sumar: **+${match.quantity} uds**\n" +
                "• Nuevo Stock Resultante: **$newStock uds**" +
                costNote + "\n\n" +
                "¿Deseas confirmar la adición de existencias a este producto? (Responde **'Sí'** o pulsa Confirmar)"
        return ProcessResult.ProposeAction(action, text)
    }

    private fun resolveDisambiguation(
        chosenItem: DisambiguationItem,
        pending: PendingAssistantAction.SelectDisambiguation,
        products: List<Product>
    ): ProcessResult {
        val targetProduct = products.find { it.id == chosenItem.id } ?: return ProcessResult.DirectReply("No pude localizar el producto seleccionado.")

        if (pending.actionIntent == "ADD_STOCK") {
            val qty = if (pending.quantity > 0.0) pending.quantity else 1.0
            val action = PendingAssistantAction.AddStockAction(
                productId = targetProduct.id,
                productName = targetProduct.name,
                currentStock = targetProduct.stock,
                quantityToAdd = qty,
                newCostPriceUsd = pending.newCost
            )
            val newStock = targetProduct.stock + qty
            val text = "Seleccionaste **${targetProduct.name}**.\n" +
                    "• Stock Actual: **${targetProduct.stock} uds**\n" +
                    "• Unidades a Sumar: **+$qty uds**\n" +
                    "• Nuevo Stock Total: **$newStock uds**\n\n" +
                    "¿Deseas confirmar esta operación? (Responde **'Sí'** o pulsa Confirmar)"
            return ProcessResult.ProposeAction(action, text)
        } else {
            val stockText = if (targetProduct.isRecipe) "Receta / Servicio" else "${targetProduct.stock} uds"
            val text = "📦 **${targetProduct.name}**\n" +
                    "• Categoría: ${targetProduct.category}\n" +
                    "• Existencias: **$stockText**\n" +
                    "• Precio Venta: **$${"%.2f".format(Locale.US, targetProduct.priceUsd)}**\n" +
                    "• Precio Costo: $${"%.2f".format(Locale.US, targetProduct.costPriceUsd)}"
            return ProcessResult.DirectReply(text)
        }
    }

    // --- Helpers and Matchers ---

    private fun isConfirmationAffirmative(text: String): Boolean {
        val clean = text.replace(Regex("[^a-záéíóúüñ]"), " ").trim()
        val affirmatives = setOf("si", "sí", "confirmar", "confirmo", "correcto", "dale", "hazlo", "proceder", "ok", "yes", "claro", "por favor", "adelante", "guardar", "acepto")
        return clean in affirmatives || clean.startsWith("si ") || clean.startsWith("sí ")
    }

    private fun isConfirmationNegative(text: String): Boolean {
        val clean = text.replace(Regex("[^a-záéíóúüñ]"), " ").trim()
        val negatives = setOf("no", "cancelar", "cancela", "cancelo", "anular", "anula", "no lo hagas", "rechazar", "abortar", "descartar", "ninguno")
        return clean in negatives || clean.startsWith("no ")
    }

    private fun parseDisambiguationChoice(text: String, candidates: List<DisambiguationItem>): DisambiguationItem? {
        val clean = text.trim()
        val num = clean.toIntOrNull()
        if (num != null && num in 1..candidates.size) {
            return candidates[num - 1]
        }
        if (clean.contains("primero", ignoreCase = true) || clean.contains("1", ignoreCase = true)) return candidates.getOrNull(0)
        if (clean.contains("segundo", ignoreCase = true) || clean.contains("2", ignoreCase = true)) return candidates.getOrNull(1)
        if (clean.contains("tercero", ignoreCase = true) || clean.contains("3", ignoreCase = true)) return candidates.getOrNull(2)

        return candidates.find { clean.contains(it.title.lowercase(Locale.ROOT)) || it.title.lowercase(Locale.ROOT).contains(clean) }
    }

    private fun isBcvRateIntent(lower: String): Boolean {
        return (lower.contains("tasa") && (lower.contains("bcv") || lower.contains("dolar") || lower.contains("dólar") || lower.contains("cambio") || lower.contains("hoy"))) ||
                lower == "tasa" || lower == "tasa bcv" || lower == "precio del dolar" || lower == "precio del dólar"
    }

    private fun isLowStockIntent(lower: String): Boolean {
        return lower.contains("bajo stock") || lower.contains("pocos productos") || lower.contains("por agotarse") ||
                lower.contains("sin stock") || lower.contains("stock critico") || lower.contains("stock crítico") || lower.contains("agotados")
    }

    private fun isDailySalesIntent(lower: String): Boolean {
        return lower.contains("ventas de hoy") || lower.contains("resumen de ventas") || lower.contains("cuanto se ha vendido") ||
                lower.contains("cuánto se ha vendido") || lower.contains("total facturado") || lower.contains("ventas del dia") || lower.contains("ventas del día")
    }

    private fun findMatchingProducts(query: String, products: List<Product>): List<Product> {
        val cleanQuery = query.lowercase(Locale.ROOT).trim()
        if (cleanQuery.isBlank()) return emptyList()

        // 1. Exact match
        val exact = products.filter { it.name.equals(cleanQuery, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact

        // 2. Substring match
        val substringMatches = products.filter { it.name.contains(cleanQuery, ignoreCase = true) }
        if (substringMatches.isNotEmpty()) return substringMatches

        // 3. Token / word overlap match
        val tokens = cleanQuery.split(" ").filter { it.length > 2 }
        if (tokens.isNotEmpty()) {
            val tokenMatches = products.filter { p ->
                tokens.any { token -> p.name.contains(token, ignoreCase = true) }
            }
            if (tokenMatches.isNotEmpty()) return tokenMatches
        }

        return emptyList()
    }

    private fun tryParseAddStockLocal(text: String, products: List<Product>): AddStockParsed? {
        val lower = text.lowercase(Locale.ROOT)
        // Match patterns like: "añade 15 existencias a Harina Pan" or "agregar 10 unidades a Coca Cola" or "sumar 5 a Harina Pan"
        val regexPatterns = listOf(
            Regex("(?:añadir|añade|agrega|agregar|suma|sumar|recargar|recarga)\\s+(\\d+(?:[.,]\\d+)?)\\s+(?:unidades|uds|existencias|stock)?\\s*(?:a|al producto|de)?\\s+([a-záéíóúüñ0-9\\s]+)", RegexOption.IGNORE_CASE),
            Regex("(?:a|al producto)\\s+([a-záéíóúüñ0-9\\s]+)\\s+(?:añade|añadir|agrega|agregar|suma|sumar)\\s+(\\d+(?:[.,]\\d+)?)\\s*(?:unidades|uds|existencias)?", RegexOption.IGNORE_CASE)
        )

        for (pattern in regexPatterns) {
            val match = pattern.find(lower)
            if (match != null) {
                val group1 = match.groupValues[1].trim()
                val group2 = match.groupValues[2].trim()

                val (qtyStr, pQuery) = if (group1.replace(",", ".").toDoubleOrNull() != null) {
                    Pair(group1, group2)
                } else {
                    Pair(group2, group1)
                }

                val qty = qtyStr.replace(",", ".").toDoubleOrNull() ?: continue
                if (pQuery.isNotBlank()) {
                    return AddStockParsed(productQuery = pQuery, quantity = qty)
                }
            }
        }
        return null
    }

    private fun tryParseRegisterProductLocal(text: String): ParsedProduct? {
        val lower = text.lowercase(Locale.ROOT)
        // Look for keywords: "Nuevo producto", "Cantidad", "Precio de costo", "Precio de venta"
        if (!lower.contains("nuevo producto") && !lower.contains("crear producto") && !lower.contains("agrega un producto")) {
            return null
        }

        // Extracts
        val nameRegex = Regex("(?:nuevo producto|crear producto|agrega un producto),?\\s*([a-záéíóúüñ0-9\\s]+?)(?:\\s+cantidad|\\s+precio|\\s+costo|\\s+venta|$)", RegexOption.IGNORE_CASE)
        val stockRegex = Regex("(?:cantidad|existencias|stock|unidades)\\s*(?:de|:)?\\s*([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)
        val costRegex = Regex("(?:precio costo|precio de costo|costo)\\s*(?:es|de|:)?\\s*\\$?([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
        val priceRegex = Regex("(?:precio de venta|precio venta|precio|venta)\\s*(?:es|de|:)?\\s*\\$?([0-9]+(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)

        val name = nameRegex.find(text)?.groupValues?.get(1)?.trim() ?: return null
        val stock = stockRegex.find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        val cost = costRegex.find(lower)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val price = priceRegex.find(lower)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0

        if (price == 0.0 && cost == 0.0) return null

        return ParsedProduct(
            name = name,
            category = "General",
            stock = stock,
            costPriceUsd = cost,
            priceUsd = if (price > 0.0) price else cost * 1.3,
            isRecipe = false
        )
    }

    private fun tryParseRegisterCustomerLocal(text: String): ParsedCustomer? {
        val lower = text.lowercase(Locale.ROOT)
        if (!lower.contains("agregar cliente") && !lower.contains("nuevo cliente") && !lower.contains("registrar cliente")) {
            return null
        }
        val nameRegex = Regex("(?:agregar cliente|nuevo cliente|registrar cliente),?\\s*([a-záéíóúüñ\\s]+?)(?:\\s+rif|\\s+cedula|\\s+cédula|\\s+telefono|\\s+teléfono|\\s+direccion|\\s+dirección|$)", RegexOption.IGNORE_CASE)
        val docRegex = Regex("(?:rif|cedula|cédula|id)\\s*(?:es|de|:)?\\s*([a-z0-9\\-]+)", RegexOption.IGNORE_CASE)
        val phoneRegex = Regex("(?:telefono|teléfono|celular)\\s*(?:es|de|:)?\\s*([0-9\\-]+)", RegexOption.IGNORE_CASE)

        val name = nameRegex.find(text)?.groupValues?.get(1)?.trim() ?: return null
        val doc = docRegex.find(lower)?.groupValues?.get(1)?.trim() ?: "V-00000000"
        val phone = phoneRegex.find(lower)?.groupValues?.get(1)?.trim() ?: ""

        return ParsedCustomer(name, doc, phone, "")
    }

    private fun tryParseDeleteProductLocal(text: String, products: List<Product>): Product? {
        val lower = text.lowercase(Locale.ROOT)
        if (!lower.contains("eliminar") && !lower.contains("borrar") && !lower.contains("quitar")) return null
        if (!lower.contains("producto") && !lower.contains("inventario")) return null

        val pQuery = text.replace(Regex("(?:eliminar|borrar|quitar|el|producto|del|inventario|de)", RegexOption.IGNORE_CASE), "").trim()
        if (pQuery.isBlank()) return null
        return findMatchingProducts(pQuery, products).firstOrNull()
    }

    private fun tryParsePurchaseInvoiceLocal(text: String, suppliers: List<Supplier>): ParsedPurchaseInvoice? {
        val lower = text.lowercase(Locale.ROOT)
        if (!lower.contains("factura") && !lower.contains("proveedor") && !lower.contains("compra")) {
            return null
        }

        val amountRegex = Regex("(?:por|monto|total|de)\\s*\\$?([0-9]+(?:\\.[0-9]+)?)\\s*(?:dolares|dólares|usd|\\$)?", RegexOption.IGNORE_CASE)
        val invNumRegex = Regex("(?:factura|numero|número|num|nro|#)\\s*(?:es|de|:)?\\s*([A-Za-z0-9\\-]+)", RegexOption.IGNORE_CASE)

        val amount = amountRegex.find(lower)?.groupValues?.get(1)?.toDoubleOrNull() ?: 0.0
        val invNum = invNumRegex.find(text)?.groupValues?.get(1) ?: "F-${System.currentTimeMillis() % 10000}"

        // Find supplier name in string
        var matchedSupplier = suppliers.find { text.contains(it.name, ignoreCase = true) }
        if (matchedSupplier == null && suppliers.isNotEmpty()) {
            matchedSupplier = suppliers.first()
        }

        if (amount <= 0.0 && matchedSupplier == null) return null

        val sId = matchedSupplier?.id ?: 0
        val sName = matchedSupplier?.name ?: "Proveedor General"

        return ParsedPurchaseInvoice(
            supplierId = sId,
            supplierName = sName,
            invoiceNumber = invNum,
            totalAmount = amount,
            description = "Compra registrada por Asistente"
        )
    }

    private fun tryParseInventoryQueryLocal(text: String, products: List<Product>, bcvRate: Double): ProcessResult? {
        val lower = text.lowercase(Locale.ROOT)
        val queryKeywords = listOf("cuanto stock", "cuánto stock", "stock de", "precio de", "buscar", "precio del", "precio de la", "cuanto hay de", "cuánto hay de", "cuanto cuesta", "cuánto cuesta", "cuanto vale", "cuánto vale", "info de", "información de")

        for (kw in queryKeywords) {
            if (lower.contains(kw)) {
                val pQuery = lower.substringAfter(kw).trim().trim('?', '¿', '.', ':')
                if (pQuery.isNotBlank()) {
                    return findProductAndReply(pQuery, products, bcvRate)
                }
            }
        }
        return null
    }
}

data class AddStockParsed(val productQuery: String, val quantity: Double, val newCostPrice: Double? = null)
data class ParsedProduct(val name: String, val category: String, val stock: Double, val costPriceUsd: Double, val priceUsd: Double, val isRecipe: Boolean, val isTaxExempt: Boolean = false)
data class ParsedCustomer(val name: String, val documentId: String, val phone: String, val address: String)
data class ParsedPurchaseInvoice(val supplierId: Int, val supplierName: String, val invoiceNumber: String, val totalAmount: Double, val description: String)

sealed class ProcessResult {
    data class DirectReply(val text: String) : ProcessResult()
    data class ProposeAction(val action: PendingAssistantAction, val proposalText: String) : ProcessResult()
    data class ConfirmAction(val action: PendingAssistantAction) : ProcessResult()
    data class CancelAction(val cancellationText: String) : ProcessResult()
}

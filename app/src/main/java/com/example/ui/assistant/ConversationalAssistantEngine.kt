package com.example.ui.assistant

import com.example.data.local.Customer
import com.example.data.local.Product
import com.example.data.local.Quote
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
     * Attempts to resolve user message locally first (fast path + real-time draft corrections).
     * If not resolved locally, delegates to Gemini API with multi-step reasoning and tool calls.
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
        todaySalesCount: Int,
        minProfitMarginPercent: Double? = null,
        customers: List<Customer> = emptyList(),
        quotes: List<Quote> = emptyList(),
        conversationHistory: List<Pair<String, String>> = emptyList()
    ): ProcessResult {
        val trimmed = rawText.trim()
        val lower = trimmed.lowercase(Locale.ROOT)

        // 1. If there is a pending action awaiting confirmation or real-time correction
        if (currentPendingAction != null) {
            val isYes = isConfirmationAffirmative(lower)
            val isNo = isConfirmationNegative(lower)

            if (isYes) {
                return ProcessResult.ConfirmAction(currentPendingAction)
            } else if (isNo) {
                return ProcessResult.CancelAction("Operación cancelada. No se han aplicado cambios al sistema.")
            } else if (currentPendingAction is PendingAssistantAction.SelectDisambiguation) {
                val candidates = currentPendingAction.candidates
                val chosenItem = parseDisambiguationChoice(lower, candidates)
                if (chosenItem != null) {
                    return resolveDisambiguation(chosenItem, currentPendingAction, products)
                }
            } else if (currentPendingAction is PendingAssistantAction.CreateProductAction) {
                val edited = tryApplyLocalProductCorrection(
                    text = trimmed,
                    current = currentPendingAction,
                    bcvRate = bcvRate,
                    minProfitMarginPercent = minProfitMarginPercent
                )
                if (edited != null) return edited
            } else if (currentPendingAction is PendingAssistantAction.CreateQuoteAction) {
                val edited = tryApplyLocalQuoteCorrection(
                    text = trimmed,
                    current = currentPendingAction,
                    products = products,
                    customers = customers,
                    bcvRate = bcvRate
                )
                if (edited != null) return edited
            }
        }

        // 2. Local Fast Intent - Navigation
        val navMatch = tryParseNavigationLocal(lower)
        if (navMatch != null) {
            return navMatch
        }

        // 3. Local Fast Intent - Update BCV Rate
        val updateRateMatch = tryParseUpdateBcvRateLocal(lower)
        if (updateRateMatch != null) {
            val action = PendingAssistantAction.UpdateBcvRateAction(updateRateMatch)
            val text = "💱 **Actualizar Tasa Oficial BCV**\n" +
                "• Tasa actual: Bs. ${"%.2f".format(Locale.US, bcvRate)}\n" +
                "• Nueva tasa propuesta: **Bs. ${"%.2f".format(Locale.US, updateRateMatch)} / USD**\n\n" +
                "¿Deseas aplicar esta nueva tasa?"
            return ProcessResult.ProposeAction(action, text)
        }

        // 4. Local Fast Intent - Query BCV Exchange Rate
        if (isBcvRateIntent(lower)) {
            val formattedRate = "%.2f".format(Locale.US, bcvRate)
            val text = "📊 **Tasa Oficial del BCV:** Bs. $formattedRate por USD.\n(Calculada para todas las operaciones en Bolívares de la caja)."
            return ProcessResult.DirectReply(text)
        }

        // 5. CSV Inventory Import Intent
        if (lower.contains("csv") || (lower.contains("cargar") && lower.contains("inventario")) || (lower.contains("importar") && lower.contains("inventario"))) {
            return ProcessResult.DirectReply(
                "📁 **Carga de Inventario CSV:**\nPuedes presionar el botón verde **'📁 Cargar CSV'** disponible en la barra de herramientas del asistente o en el módulo de Inventario para seleccionar tu archivo `.csv`, validar las columnas y calcular márgenes automáticamente."
            )
        }

        // 6. Local Fast Intent - Low Stock / Out of Stock
        if (isLowStockIntent(lower)) {
            return buildLowStockReply(products)
        }

        // 7. Local Fast Intent - Today's Sales Summary
        if (isDailySalesIntent(lower)) {
            return handleDailySummary(todaySalesTotalUsd, todaySalesTotalBs, todaySalesCount)
        }

        // 8. Local Fast Intent - Create Quote (Presupuesto)
        val quoteMatch = tryParseCreateQuoteLocal(trimmed, products, customers, bcvRate)
        if (quoteMatch != null) {
            return quoteMatch
        }

        // 9. Local Fast Intent - Query Quotes
        val queryQuotesMatch = tryParseQuotesQueryLocal(lower, quotes)
        if (queryQuotesMatch != null) {
            return queryQuotesMatch
        }

        // 10. Local Fast Intent - Register Expense
        val expenseMatch = tryParseRegisterExpenseLocal(trimmed)
        if (expenseMatch != null) {
            val action = PendingAssistantAction.RegisterExpenseAction(
                description = expenseMatch.description,
                amountUsd = expenseMatch.amountUsd,
                category = expenseMatch.category
            )
            val bs = expenseMatch.amountUsd * bcvRate
            val text = "💸 **Registrar Gasto Operativo**\n" +
                "• Concepto: **${action.description}**\n" +
                "• Categoría: **${action.category}**\n" +
                "• Monto: **$${"%.2f".format(Locale.US, action.amountUsd)} USD** (Bs. ${"%.2f".format(Locale.US, bs)})\n\n" +
                "¿Deseas confirmar el registro de este gasto?"
            return ProcessResult.ProposeAction(action, text)
        }

        // 11. Local Fast Intent - Add Stock to Existing Product
        val addStockMatch = tryParseAddStockLocal(trimmed)
        if (addStockMatch != null) {
            return handleAddStockCandidate(addStockMatch, products)
        }

        // 12. Local Fast Intent - Register New Product
        val registerProductMatch = tryParseRegisterProductLocal(trimmed, minProfitMarginPercent)
        if (registerProductMatch != null) {
            return prepareCreateProductAction(registerProductMatch, bcvRate)
        }

        // 13. Local Fast Intent - Register Customer
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
                "• Teléfono: **${customerMatch.phone.ifBlank { "Sin teléfono" }}**\n\n" +
                "¿Deseas confirmar el registro de este cliente?"
            return ProcessResult.ProposeAction(action, text)
        }

        // 14. Local Fast Intent - Query Customers
        val customerQueryMatch = tryParseCustomerQueryLocal(trimmed, customers)
        if (customerQueryMatch != null) {
            return customerQueryMatch
        }

        // 15. Local Fast Intent - Delete Product
        val deleteProductMatch = tryParseDeleteProductLocal(trimmed, products)
        if (deleteProductMatch != null) {
            val p = deleteProductMatch
            val action = PendingAssistantAction.DeleteProductAction(p.id, p.name)
            val text = "⚠️ **ELIMINAR PRODUCTO**\n" +
                "¿Estás seguro que deseas eliminar **${p.name}** del inventario?\n" +
                "Esta acción no se puede deshacer."
            return ProcessResult.ProposeAction(action, text)
        }

        // 16. Local Fast Intent - Register Supplier Purchase Invoice
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

        // 17. Local Fast Intent - Simple Inventory / Price Query
        val queryMatch = tryParseInventoryQueryLocal(trimmed, products, bcvRate)
        if (queryMatch != null) {
            return queryMatch
        }

        // 18. Local Fast Intent - Invoice Query
        val invoiceQueryMatch = tryParseInvoiceQueryLocal(trimmed, sales)
        if (invoiceQueryMatch != null) {
            return invoiceQueryMatch
        }

        // 19. Delegate to Gemini AI with multi-step read-only reasoning + action tools
        val catalogSummary = buildCatalogSummary(products)
        val suppliersSummary = buildSuppliersSummary(suppliers)
        val customersSummary = buildCustomersSummary(customers)
        val todaySalesSummary = "Ventas: $todaySalesCount | Total: $${"%.2f".format(Locale.US, todaySalesTotalUsd)} USD (Bs. ${"%.2f".format(Locale.US, todaySalesTotalBs)})"
        val pendingSummary = summarizePendingAction(currentPendingAction)

        val apiResult = geminiRepository.processAssistantPrompt(
            userPrompt = trimmed,
            catalogSummary = catalogSummary,
            suppliersSummary = suppliersSummary,
            customersSummary = customersSummary,
            bcvRate = bcvRate,
            todaySalesSummary = todaySalesSummary,
            minProfitMarginPercent = minProfitMarginPercent,
            pendingActionSummary = pendingSummary,
            conversationHistory = conversationHistory,
            onReadOnlyToolCall = { fnName, args ->
                executeReadOnlyLookup(
                    name = fnName,
                    args = args,
                    products = products,
                    customers = customers,
                    quotes = quotes,
                    sales = sales,
                    bcvRate = bcvRate,
                    todaySalesTotalUsd = todaySalesTotalUsd,
                    todaySalesTotalBs = todaySalesTotalBs,
                    todaySalesCount = todaySalesCount
                )
            }
        )

        return when (apiResult) {
            is AssistantApiResponse.Message -> ProcessResult.DirectReply(apiResult.text)
            is AssistantApiResponse.Error -> ProcessResult.DirectReply("⚠️ ${apiResult.errorMessage}")
            is AssistantApiResponse.Function -> {
                handleGeminiFunctionCall(
                    name = apiResult.functionName,
                    args = apiResult.args,
                    currentPendingAction = currentPendingAction,
                    products = products,
                    suppliers = suppliers,
                    customers = customers,
                    quotes = quotes,
                    sales = sales,
                    bcvRate = bcvRate,
                    todaySalesTotalUsd = todaySalesTotalUsd,
                    todaySalesTotalBs = todaySalesTotalBs,
                    todaySalesCount = todaySalesCount,
                    minProfitMarginPercent = minProfitMarginPercent
                )
            }
        }
    }

    /**
     * Read-only lookup handler used during Gemini's multi-step reasoning loop (both text and Live).
     * Returns null if the tool mutates state or requires UI confirmation.
     */
    fun executeReadOnlyLookup(
        name: String,
        args: Map<String, Any>,
        products: List<Product>,
        customers: List<Customer>,
        quotes: List<Quote>,
        sales: List<Sale>,
        bcvRate: Double,
        todaySalesTotalUsd: Double,
        todaySalesTotalBs: Double,
        todaySalesCount: Int
    ): Map<String, Any>? {
        return when (name) {
            "queryInventory" -> {
                val query = (args["productName"] as? String).orEmpty()
                val matches = if (query.isBlank()) products.take(15) else findMatchingProducts(query, products).take(10)
                mapOf(
                    "found" to matches.size,
                    "products" to matches.map {
                        mapOf(
                            "id" to it.id,
                            "code" to it.code,
                            "name" to it.name,
                            "category" to it.category,
                            "stock" to it.stock,
                            "priceUsd" to it.priceUsd,
                            "wholesalePriceUsd" to it.wholesalePriceUsd,
                            "costPriceUsd" to it.costPriceUsd,
                            "isTaxExempt" to it.isTaxExempt,
                            "isRecipe" to it.isRecipe
                        )
                    }
                )
            }
            "queryLowStock" -> {
                val low = products.filter { !it.isRecipe && it.stock <= 5.0 }
                mapOf(
                    "count" to low.size,
                    "products" to low.take(20).map {
                        mapOf("name" to it.name, "stock" to it.stock, "priceUsd" to it.priceUsd)
                    }
                )
            }
            "queryCustomers" -> {
                val query = (args["query"] as? String).orEmpty().trim()
                val matches = if (query.isBlank()) {
                    customers.take(15)
                } else {
                    customers.filter {
                        it.name.contains(query, ignoreCase = true) ||
                            it.documentId.contains(query, ignoreCase = true) ||
                            it.phone.contains(query, ignoreCase = true)
                    }.take(10)
                }
                mapOf(
                    "found" to matches.size,
                    "customers" to matches.map {
                        mapOf("id" to it.id, "name" to it.name, "documentId" to it.documentId, "phone" to it.phone, "address" to it.address)
                    }
                )
            }
            "queryQuotes" -> {
                val query = (args["query"] as? String).orEmpty().trim()
                val matches = if (query.isBlank()) {
                    quotes.take(10)
                } else {
                    quotes.filter {
                        it.quoteNumber.contains(query, ignoreCase = true) ||
                            it.customerName.contains(query, ignoreCase = true) ||
                            it.customerDocument.contains(query, ignoreCase = true) ||
                            it.status.contains(query, ignoreCase = true)
                    }.take(10)
                }
                mapOf(
                    "found" to matches.size,
                    "quotes" to matches.map {
                        mapOf(
                            "id" to it.id,
                            "quoteNumber" to it.quoteNumber,
                            "customerName" to it.customerName,
                            "customerPhone" to it.customerPhone,
                            "totalUsd" to it.totalUsd,
                            "totalBs" to it.totalBs,
                            "status" to it.status,
                            "validityDays" to it.validityDays
                        )
                    }
                )
            }
            "queryInvoice" -> {
                val invNum = (args["invoiceNumber"] as? String).orEmpty()
                val invoice = sales.find {
                    it.id.toString() == invNum || it.withholdingVoucherNumber.contains(invNum, ignoreCase = true)
                }
                if (invoice == null) {
                    mapOf("found" to false, "message" to "No se encontró la factura $invNum")
                } else {
                    mapOf(
                        "found" to true,
                        "id" to invoice.id,
                        "customerName" to invoice.customerName,
                        "customerDocument" to invoice.customerDocument,
                        "totalUsd" to invoice.totalUsd,
                        "totalBs" to invoice.totalBs
                    )
                }
            }
            "checkBcvRate" -> mapOf("bcvRateBs" to bcvRate)
            "getDailySummary" -> mapOf(
                "todaySalesCount" to todaySalesCount,
                "todaySalesTotalUsd" to todaySalesTotalUsd,
                "todaySalesTotalBs" to todaySalesTotalBs
            )
            else -> null
        }
    }

    /**
     * Converts a Gemini function call (from standard or Live API) into a ProcessResult.
     */
    fun handleGeminiFunctionCall(
        name: String,
        args: Map<String, Any>,
        currentPendingAction: PendingAssistantAction? = null,
        products: List<Product>,
        suppliers: List<Supplier>,
        customers: List<Customer> = emptyList(),
        quotes: List<Quote> = emptyList(),
        sales: List<Sale> = emptyList(),
        bcvRate: Double,
        todaySalesTotalUsd: Double = 0.0,
        todaySalesTotalBs: Double = 0.0,
        todaySalesCount: Int = 0,
        minProfitMarginPercent: Double? = null
    ): ProcessResult {
        return when (name) {
            "queryInventory" -> {
                val pName = (args["productName"] as? String).orEmpty()
                findProductAndReply(pName, products, bcvRate)
            }
            "queryLowStock" -> buildLowStockReply(products)
            "queryCustomers" -> {
                val query = (args["query"] as? String).orEmpty()
                buildCustomerSearchReply(query, customers)
            }
            "queryQuotes" -> {
                val query = (args["query"] as? String).orEmpty()
                buildQuoteSearchReply(query, quotes)
            }
            "registerNewProduct" -> {
                val pName = (args["name"] as? String).orEmpty().trim()
                val pCode = (args["code"] as? String).orEmpty().trim()
                val cat = (args["category"] as? String).orEmpty().ifBlank { "General" }
                val stock = ((args["stock"] as? Number)?.toDouble()) ?: 0.0
                val cost = ((args["costPriceUsd"] as? Number)?.toDouble()) ?: 0.0
                val rawProfitPct = ((args["profitPercentage"] as? Number)?.toDouble()) ?: 0.0
                var price = ((args["priceUsd"] as? Number)?.toDouble()) ?: 0.0
                val wholesale = ((args["wholesalePriceUsd"] as? Number)?.toDouble()) ?: 0.0
                val isRecipe = (args["isRecipe"] as? Boolean) ?: false
                val isTaxExempt = (args["isTaxExempt"] as? Boolean) ?: false

                var autoMinMarginUsed = false
                var effectiveProfitPct = rawProfitPct

                if (!isRecipe && price <= 0.0 && cost > 0.0) {
                    if (rawProfitPct > 0.0) {
                        price = cost * (1.0 + rawProfitPct / 100.0)
                    } else if (minProfitMarginPercent != null && minProfitMarginPercent > 0.0) {
                        price = cost * (1.0 + minProfitMarginPercent / 100.0)
                        effectiveProfitPct = minProfitMarginPercent
                        autoMinMarginUsed = true
                    }
                } else if (!isRecipe && price > 0.0 && cost > 0.0 && rawProfitPct <= 0.0) {
                    effectiveProfitPct = ((price - cost) / cost) * 100.0
                }

                if (pName.isBlank() || (price <= 0.0 && cost <= 0.0 && wholesale <= 0.0)) {
                    return ProcessResult.DirectReply("Para preparar el producto necesito al menos su nombre y su precio (o precio de costo).")
                }

                val action = PendingAssistantAction.CreateProductAction(
                    name = pName,
                    category = cat,
                    stock = if (isRecipe) 0.0 else stock,
                    costPriceUsd = if (isRecipe) 0.0 else cost,
                    priceUsd = price,
                    isRecipe = isRecipe,
                    isTaxExempt = isTaxExempt,
                    code = pCode,
                    profitPercentage = effectiveProfitPct,
                    wholesalePriceUsd = wholesale,
                    autoCalculatedWithMinMargin = autoMinMarginUsed
                )

                ProcessResult.ProposeAction(
                    action = action,
                    proposalText = formatCreateProductProposal(action, bcvRate, viaGemini = true)
                )
            }
            "updatePendingProduct" -> {
                val base = (currentPendingAction as? PendingAssistantAction.CreateProductAction)
                    ?: return ProcessResult.DirectReply("No hay ningún producto pendiente en pantalla para modificar. Pídeme crear un producto primero.")

                val newName = (args["name"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: base.name
                val newCode = (args["code"] as? String)?.trim() ?: base.code
                val newCat = (args["category"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: base.category
                val newStock = (args["stock"] as? Number)?.toDouble() ?: base.stock
                val newCost = (args["costPriceUsd"] as? Number)?.toDouble() ?: base.costPriceUsd
                val explicitProfit = (args["profitPercentage"] as? Number)?.toDouble()
                val explicitPrice = (args["priceUsd"] as? Number)?.toDouble()
                val newWholesale = (args["wholesalePriceUsd"] as? Number)?.toDouble() ?: base.wholesalePriceUsd
                val newIsRecipe = (args["isRecipe"] as? Boolean) ?: base.isRecipe
                val newIsExempt = (args["isTaxExempt"] as? Boolean) ?: base.isTaxExempt

                var finalPrice = explicitPrice ?: base.priceUsd
                var finalProfit = explicitProfit ?: base.profitPercentage
                var autoMin = base.autoCalculatedWithMinMargin

                if (!newIsRecipe && newCost > 0.0) {
                    if (explicitProfit != null && explicitPrice == null) {
                        finalPrice = newCost * (1.0 + explicitProfit / 100.0)
                        autoMin = false
                    } else if (explicitPrice != null && explicitProfit == null) {
                        finalProfit = ((explicitPrice - newCost) / newCost) * 100.0
                        autoMin = false
                    } else if (args["costPriceUsd"] != null && explicitPrice == null && explicitProfit == null) {
                        if (finalProfit > 0.0) {
                            finalPrice = newCost * (1.0 + finalProfit / 100.0)
                        } else if (minProfitMarginPercent != null && minProfitMarginPercent > 0.0) {
                            finalPrice = newCost * (1.0 + minProfitMarginPercent / 100.0)
                            finalProfit = minProfitMarginPercent
                            autoMin = true
                        }
                    }
                }

                val updated = base.copy(
                    name = newName,
                    code = newCode,
                    category = newCat,
                    stock = if (newIsRecipe) 0.0 else newStock,
                    costPriceUsd = if (newIsRecipe) 0.0 else newCost,
                    priceUsd = finalPrice,
                    wholesalePriceUsd = newWholesale,
                    profitPercentage = finalProfit,
                    isRecipe = newIsRecipe,
                    isTaxExempt = newIsExempt,
                    autoCalculatedWithMinMargin = autoMin
                )
                ProcessResult.ProposeAction(
                    action = updated,
                    proposalText = formatCreateProductProposal(updated, bcvRate, viaGemini = true)
                )
            }
            "addStock" -> {
                val pName = (args["productName"] as? String).orEmpty()
                val qty = ((args["quantity"] as? Number)?.toDouble()) ?: 0.0
                val newCost = (args["costPriceUsd"] as? Number)?.toDouble()

                if (pName.isBlank() || qty <= 0.0) {
                    ProcessResult.DirectReply("Indica el nombre del producto y la cantidad de existencias a sumar.")
                } else {
                    handleAddStockCandidate(AddStockParsed(pName, qty, newCost), products)
                }
            }
            "createQuote" -> {
                val customerQuery = (args["customerName"] as? String).orEmpty().trim()
                val docArg = (args["customerDocument"] as? String).orEmpty().trim()
                val phoneArg = (args["customerPhone"] as? String).orEmpty().trim()
                val validityDays = ((args["validityDays"] as? Number)?.toInt() ?: 7).coerceIn(1, 365)
                val discountUsd = ((args["discountUsd"] as? Number)?.toDouble() ?: 0.0).coerceAtLeast(0.0)
                val notes = (args["notesOrConditions"] as? String)?.trim()
                    ?.takeIf { it.isNotBlank() }
                    ?: "Precios sujetos a disponibilidad y tasa BCV vigente."

                val matchedCustomer = findMatchingCustomer(customerQuery, docArg, customers)
                val finalCustomerName = matchedCustomer?.name ?: customerQuery.ifBlank { "Consumidor Final" }
                val finalDoc = docArg.ifBlank { matchedCustomer?.documentId ?: "V-00000000" }
                val finalPhone = phoneArg.ifBlank { matchedCustomer?.phone ?: "" }

                @Suppress("UNCHECKED_CAST")
                val rawItems = (args["items"] as? List<Map<String, Any>>).orEmpty()
                val parsedItems = resolveQuoteItemsFromMaps(rawItems, products)

                if (parsedItems.isEmpty()) {
                    return ProcessResult.DirectReply("No encontré productos válidos del inventario para armar el presupuesto. Dime qué productos y cantidades deseas cotizar.")
                }

                val action = buildCreateQuoteAction(
                    customerName = finalCustomerName,
                    customerDocument = finalDoc,
                    customerPhone = finalPhone,
                    items = parsedItems,
                    validityDays = validityDays,
                    discountUsd = discountUsd,
                    notesOrConditions = notes,
                    bcvRate = bcvRate
                )
                ProcessResult.ProposeAction(
                    action = action,
                    proposalText = formatCreateQuoteProposal(action, bcvRate, viaGemini = true)
                )
            }
            "updatePendingQuote" -> {
                val base = (currentPendingAction as? PendingAssistantAction.CreateQuoteAction)
                    ?: return ProcessResult.DirectReply("No hay ningún presupuesto pendiente en pantalla para modificar. Pídeme crear un presupuesto primero.")

                val customerQuery = (args["customerName"] as? String)?.trim()
                val docArg = (args["customerDocument"] as? String)?.trim()
                val phoneArg = (args["customerPhone"] as? String)?.trim()
                val matchedCustomer = if (!customerQuery.isNullOrBlank() || !docArg.isNullOrBlank()) {
                    findMatchingCustomer(customerQuery.orEmpty(), docArg.orEmpty(), customers)
                } else null

                val newName = matchedCustomer?.name ?: customerQuery?.takeIf { it.isNotBlank() } ?: base.customerName
                val newDoc = docArg?.takeIf { it.isNotBlank() }
                    ?: matchedCustomer?.documentId?.takeIf { it.isNotBlank() }
                    ?: base.customerDocument
                val newPhone = phoneArg?.takeIf { it.isNotBlank() }
                    ?: matchedCustomer?.phone?.takeIf { it.isNotBlank() }
                    ?: base.customerPhone
                val newValidity = (args["validityDays"] as? Number)?.toInt()?.coerceIn(1, 365) ?: base.validityDays
                val newDiscount = (args["discountUsd"] as? Number)?.toDouble()?.coerceAtLeast(0.0) ?: base.discountUsd
                val newNotes = (args["notesOrConditions"] as? String)?.trim()?.takeIf { it.isNotBlank() } ?: base.notesOrConditions

                val updatedItems = base.items.toMutableList()

                @Suppress("UNCHECKED_CAST")
                val replaceMaps = args["replaceItems"] as? List<Map<String, Any>>
                if (!replaceMaps.isNullOrEmpty()) {
                    val replaced = resolveQuoteItemsFromMaps(replaceMaps, products)
                    if (replaced.isNotEmpty()) {
                        updatedItems.clear()
                        updatedItems.addAll(replaced)
                    }
                }

                @Suppress("UNCHECKED_CAST")
                val addMaps = args["addItems"] as? List<Map<String, Any>>
                if (!addMaps.isNullOrEmpty()) {
                    val added = resolveQuoteItemsFromMaps(addMaps, products)
                    added.forEach { newItem ->
                        val existingIdx = updatedItems.indexOfFirst { it.productId == newItem.productId && it.isWholesale == newItem.isWholesale }
                        if (existingIdx >= 0) {
                            val existing = updatedItems[existingIdx]
                            updatedItems[existingIdx] = existing.copy(
                                quantity = existing.quantity + newItem.quantity,
                                priceUsd = newItem.priceUsd
                            )
                        } else {
                            updatedItems.add(newItem)
                        }
                    }
                }

                val removeQuery = (args["removeProductName"] as? String)?.trim().orEmpty()
                if (removeQuery.isNotBlank()) {
                    updatedItems.removeAll { it.productName.contains(removeQuery, ignoreCase = true) }
                }

                if (updatedItems.isEmpty()) {
                    return ProcessResult.DirectReply("El presupuesto no puede quedar sin productos. Agrega al menos un artículo.")
                }

                val updated = buildCreateQuoteAction(
                    customerName = newName,
                    customerDocument = newDoc,
                    customerPhone = newPhone,
                    items = updatedItems,
                    validityDays = newValidity,
                    discountUsd = newDiscount,
                    notesOrConditions = newNotes,
                    bcvRate = bcvRate
                )
                ProcessResult.ProposeAction(
                    action = updated,
                    proposalText = formatCreateQuoteProposal(updated, bcvRate, viaGemini = true)
                )
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
                    "¿Deseas confirmar el ingreso de esta factura de compra?"
                ProcessResult.ProposeAction(action, text)
            }
            "registerExpense" -> {
                val desc = (args["description"] as? String).orEmpty().trim().ifBlank { "Gasto operativo" }
                val amount = ((args["amountUsd"] as? Number)?.toDouble()) ?: 0.0
                val category = (args["category"] as? String).orEmpty().trim().ifBlank { "Operativo" }
                if (amount <= 0.0) {
                    return ProcessResult.DirectReply("Indica un monto mayor a $0.00 para registrar el gasto.")
                }
                val action = PendingAssistantAction.RegisterExpenseAction(
                    description = desc,
                    amountUsd = amount,
                    category = category
                )
                val text = "💸 **Registrar Gasto Operativo**\n" +
                    "• Concepto: **$desc**\n" +
                    "• Categoría: **$category**\n" +
                    "• Monto: **$${"%.2f".format(Locale.US, amount)} USD** (Bs. ${"%.2f".format(Locale.US, amount * bcvRate)})\n\n" +
                    "¿Deseas confirmar el registro de este gasto?"
                ProcessResult.ProposeAction(action, text)
            }
            "updateBcvRate" -> {
                val rate = ((args["rateBs"] as? Number)?.toDouble()) ?: 0.0
                if (rate <= 0.0) {
                    return ProcessResult.DirectReply("Indica una tasa BCV válida mayor a 0.")
                }
                val action = PendingAssistantAction.UpdateBcvRateAction(rate)
                val text = "💱 **Actualizar Tasa BCV**\n" +
                    "• Tasa actual: Bs. ${"%.2f".format(Locale.US, bcvRate)}\n" +
                    "• Nueva tasa: **Bs. ${"%.2f".format(Locale.US, rate)} / USD**\n\n" +
                    "¿Deseas confirmar este cambio de tasa?"
                ProcessResult.ProposeAction(action, text)
            }
            "checkBcvRate" -> {
                val formattedRate = "%.2f".format(Locale.US, bcvRate)
                ProcessResult.DirectReply("📊 **Tasa Oficial del BCV:** Bs. $formattedRate por USD.")
            }
            "registerCustomer" -> {
                val cName = (args["name"] as? String).orEmpty().trim()
                val doc = (args["documentId"] as? String).orEmpty().trim().ifBlank { "V-00000000" }
                val phone = (args["phone"] as? String).orEmpty().trim()
                val addr = (args["address"] as? String).orEmpty().trim()
                if (cName.isBlank()) {
                    return ProcessResult.DirectReply("Indica al menos el nombre del cliente a registrar.")
                }
                val action = PendingAssistantAction.CreateCustomerAction(cName, doc, phone, addr)
                val text = "He preparado el registro del cliente:\n• Nombre: **$cName**\n• RIF/Cédula: **$doc**\n• Teléfono: **${phone.ifBlank { "N/D" }}**\n\n¿Deseas confirmar?"
                ProcessResult.ProposeAction(action, text)
            }
            "deleteProduct" -> {
                val pName = (args["productName"] as? String).orEmpty()
                val p = findMatchingProducts(pName, products).firstOrNull()
                    ?: return ProcessResult.DirectReply("No encontré el producto '$pName' para eliminar.")
                val action = PendingAssistantAction.DeleteProductAction(p.id, p.name)
                ProcessResult.ProposeAction(action, "⚠️ **CONFIRMAR ELIMINACIÓN**\n¿Estás seguro de eliminar **${p.name}**?")
            }
            "queryInvoice" -> {
                val invNum = (args["invoiceNumber"] as? String).orEmpty()
                handleInvoiceQuery(invNum, sales)
            }
            "getDailySummary" -> handleDailySummary(todaySalesTotalUsd, todaySalesTotalBs, todaySalesCount)
            "confirmCurrentAction" -> {
                if (currentPendingAction != null) {
                    ProcessResult.ConfirmAction(currentPendingAction)
                } else {
                    ProcessResult.DirectReply("No hay ninguna operación pendiente por confirmar en este momento.")
                }
            }
            "cancelCurrentAction" -> {
                if (currentPendingAction != null) {
                    ProcessResult.CancelAction("Operación cancelada. No se realizaron cambios.")
                } else {
                    ProcessResult.DirectReply("No hay ninguna operación pendiente para cancelar.")
                }
            }
            "navigateToModule" -> {
                val rawMod = (args["module"] as? String).orEmpty().trim().lowercase(Locale.ROOT)
                val route = mapModuleToRoute(rawMod)
                    ?: return ProcessResult.DirectReply("No reconocí el módulo '$rawMod'. Módulos disponibles: Facturación, Presupuestos, Inventario, Facturas, Gastos, Resumen, Clientes, Proveedores y Ajustes.")
                ProcessResult.Navigate(route, "🚀 Abriendo el módulo de **${routeDisplayName(route)}**.")
            }
            else -> ProcessResult.DirectReply("No entendí tu solicitud, puedes repetirla de otra forma.")
        }
    }

    // --- Real-Time Local Corrections for Pending Drafts ---

    private fun tryApplyLocalProductCorrection(
        text: String,
        current: PendingAssistantAction.CreateProductAction,
        bcvRate: Double,
        minProfitMarginPercent: Double?
    ): ProcessResult? {
        val lower = text.lowercase(Locale.ROOT)
        val correctionHints = listOf(
            "cambia", "corrige", "ponle", "pon ", "actualiza", "modifica", "agrega", "agregale",
            "agréga", "mejor", "código", "codigo", "sku", "precio", "costo", "ganancia",
            "margen", "mayor", "stock", "existencias", "cantidad", "exento", "iva", "categoría", "categoria", "nombre"
        )
        if (correctionHints.none { lower.contains(it) }) return null

        val codeRegex = Regex("(?:c[oó]digo|sku|ref(?:erencia)?)\\s*(?:a|es|de|:)?\\s*([A-Za-z0-9\\-_]+)", RegexOption.IGNORE_CASE)
        val categoryRegex = Regex("(?:categor[ií]a)\\s*(?:a|es|de|:)?\\s*([a-záéíóúüñ0-9\\s]+?)(?:$|,|\\sy\\s)", RegexOption.IGNORE_CASE)
        val stockRegex = Regex("(?:cantidad|existencias|stock|unidades)\\s*(?:a|de|en|:)?\\s*([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)
        val costRegex = Regex("(?:precio\\s+de\\s+costo|precio\\s+costo|costo)\\s*(?:a|es|de|en|:)?\\s*\\$?([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)
        val wholesaleRegex = Regex("(?:precio\\s+al\\s+mayor|precio\\s+mayorista|al\\s+mayor|mayorista)\\s*(?:a|es|de|en|:)?\\s*\\$?([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)
        val profitRegex = Regex("(?:porcentaje\\s+de\\s+ganancia|ganancia|margen)\\s*(?:a|al|del|de|:)?\\s*([0-9]+(?:[.,][0-9]+)?)\\s*%?", RegexOption.IGNORE_CASE)
        val priceRegex = Regex("(?:precio\\s+de\\s+venta|precio\\s+venta|pvp|precio|venta)\\s*(?:a|es|de|en|:)?\\s*\\$?([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)

        var changed = false
        var newCode = current.code
        var newCategory = current.category
        var newStock = current.stock
        var newCost = current.costPriceUsd
        var newWholesale = current.wholesalePriceUsd
        var newProfit = current.profitPercentage
        var newPrice = current.priceUsd
        var newTaxExempt = current.isTaxExempt
        var autoMin = current.autoCalculatedWithMinMargin

        codeRegex.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            newCode = it
            changed = true
        }
        categoryRegex.find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let {
            newCategory = it
            changed = true
        }
        stockRegex.find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()?.let {
            newStock = it
            changed = true
        }
        val parsedCost = costRegex.find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
        if (parsedCost != null) {
            newCost = parsedCost
            changed = true
        }
        val parsedWholesale = wholesaleRegex.find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
        if (parsedWholesale != null) {
            newWholesale = parsedWholesale
            changed = true
        }
        val parsedProfit = profitRegex.find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
        if (parsedProfit != null) {
            newProfit = parsedProfit
            if (newCost > 0.0) {
                newPrice = newCost * (1.0 + parsedProfit / 100.0)
                autoMin = false
            }
            changed = true
        }
        val parsedPrice = if (parsedCost == null && parsedWholesale == null) {
            priceRegex.find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
        } else {
            Regex("(?:precio\\s+de\\s+venta|precio\\s+venta|pvp)\\s*(?:a|es|de|en|:)?\\s*\\$?([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)
                .find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
        }
        if (parsedPrice != null) {
            newPrice = parsedPrice
            autoMin = false
            if (newCost > 0.0) {
                newProfit = ((parsedPrice - newCost) / newCost) * 100.0
            }
            changed = true
        } else if (parsedCost != null && parsedProfit == null && !current.isRecipe && newCost > 0.0) {
            if (newProfit > 0.0) {
                newPrice = newCost * (1.0 + newProfit / 100.0)
            } else if (minProfitMarginPercent != null && minProfitMarginPercent > 0.0) {
                newPrice = newCost * (1.0 + minProfitMarginPercent / 100.0)
                newProfit = minProfitMarginPercent
                autoMin = true
            }
        }

        if (lower.contains("exento")) {
            newTaxExempt = !lower.contains("no exento") && !lower.contains("con iva") && !lower.contains("grava")
            changed = true
        } else if (lower.contains("con iva")) {
            newTaxExempt = false
            changed = true
        }

        if (!changed) return null

        val updated = current.copy(
            code = newCode,
            category = newCategory,
            stock = newStock,
            costPriceUsd = newCost,
            priceUsd = newPrice,
            wholesalePriceUsd = newWholesale,
            profitPercentage = newProfit,
            isTaxExempt = newTaxExempt,
            autoCalculatedWithMinMargin = autoMin
        )
        return ProcessResult.ProposeAction(
            action = updated,
            proposalText = formatCreateProductProposal(updated, bcvRate, viaGemini = false)
        )
    }

    private fun tryApplyLocalQuoteCorrection(
        text: String,
        current: PendingAssistantAction.CreateQuoteAction,
        products: List<Product>,
        customers: List<Customer>,
        bcvRate: Double
    ): ProcessResult? {
        val lower = text.lowercase(Locale.ROOT)
        val hints = listOf(
            "cambia", "corrige", "agrega", "añade", "anade", "quita", "elimina", "saca",
            "validez", "dias", "días", "descuento", "cliente", "telefono", "teléfono", "whatsapp"
        )
        if (hints.none { lower.contains(it) }) return null

        var changed = false
        var newCustomer = current.customerName
        var newDoc = current.customerDocument
        var newPhone = current.customerPhone
        var newValidity = current.validityDays
        var newDiscount = current.discountUsd
        val items = current.items.toMutableList()

        // Validity days
        Regex("(?:validez|v[aá]lido\\s+por)\\s*(?:a|de)?\\s*(\\d+)\\s*(?:d[ií]as)?", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.get(1)?.toIntOrNull()?.let {
                newValidity = it.coerceIn(1, 365)
                changed = true
            }

        // Discount
        Regex("(?:descuento)\\s*(?:a|de)?\\s*\\$?([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()?.let {
                newDiscount = it.coerceAtLeast(0.0)
                changed = true
            }

        // WhatsApp / phone
        Regex("(?:whatsapp|tel[eé]fono|cel(?:ular)?)\\s*(?:a|es|de|:)?\\s*([+0-9\\-\\s]{7,18})", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()?.let {
                newPhone = it
                changed = true
            }

        // Customer change
        Regex("(?:cliente)\\s*(?:a|es|para|:)?\\s*([a-záéíóúüñ0-9\\s]+?)(?:$|,|\\s+con\\s+|\\s+tel[eé]fono|\\s+whatsapp)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotBlank() }?.let { query ->
                val matched = findMatchingCustomer(query, "", customers)
                newCustomer = matched?.name ?: query
                if (matched != null) {
                    if (matched.documentId.isNotBlank()) newDoc = matched.documentId
                    if (matched.phone.isNotBlank() && newPhone.isBlank()) newPhone = matched.phone
                }
                changed = true
            }

        // Remove product
        if (lower.contains("quita") || lower.contains("elimina") || lower.contains("saca")) {
            val toRemove = lower.substringAfter("quita").substringAfter("elimina").substringAfter("saca")
                .replace(Regex("\\b(el|la|los|las|producto|del presupuesto)\\b"), " ")
                .trim()
            if (toRemove.isNotBlank()) {
                val removed = items.removeAll { it.productName.contains(toRemove, ignoreCase = true) }
                if (removed) changed = true
            }
        }

        // Add item
        if (lower.contains("agrega") || lower.contains("añade") || lower.contains("anade") || lower.contains("ponle")) {
            val addSegment = text.replace(Regex(".*?(?:agrega|añade|anade|ponle)\\s+", RegexOption.IGNORE_CASE), "")
            val parsedAdds = parseQuoteItemsFromSegment(addSegment, products)
            if (parsedAdds.isNotEmpty()) {
                parsedAdds.forEach { added ->
                    val idx = items.indexOfFirst { it.productId == added.productId && it.isWholesale == added.isWholesale }
                    if (idx >= 0) {
                        val existing = items[idx]
                        items[idx] = existing.copy(quantity = existing.quantity + added.quantity)
                    } else {
                        items.add(added)
                    }
                }
                changed = true
            }
        }

        if (!changed || items.isEmpty()) return null

        val updated = buildCreateQuoteAction(
            customerName = newCustomer,
            customerDocument = newDoc,
            customerPhone = newPhone,
            items = items,
            validityDays = newValidity,
            discountUsd = newDiscount,
            notesOrConditions = current.notesOrConditions,
            bcvRate = bcvRate
        )
        return ProcessResult.ProposeAction(
            action = updated,
            proposalText = formatCreateQuoteProposal(updated, bcvRate, viaGemini = false)
        )
    }

    // --- Local Quote & Business Fast-Path Parsers ---

    private fun tryParseCreateQuoteLocal(
        text: String,
        products: List<Product>,
        customers: List<Customer>,
        bcvRate: Double
    ): ProcessResult? {
        val lower = text.lowercase(Locale.ROOT)
        val triggers = listOf(
            "presupuesto", "cotización", "cotizacion", "cotizar", "cotízame", "cotizame", "presupuestar"
        )
        if (triggers.none { lower.contains(it) }) return null
        if (lower.contains("ver presupuesto") || lower.contains("consulta") || lower.contains("buscar presupuesto") ||
            lower.contains("lista de presupuesto") || lower.contains("ir a presupuesto") || lower.contains("abrir presupuesto")
        ) {
            return null
        }

        val customerNameMatch = Regex(
            "(?:para\\s+el\\s+cliente|para\\s+cliente|cliente|para)\\s+([a-záéíóúüñ0-9\\s]+?)(?=\\s+(?:con|de|por|rif|c[eé]dula|whatsapp|tel[eé]fono|validez|descuento|\\d)|$)",
            RegexOption.IGNORE_CASE
        ).find(text)?.groupValues?.get(1)?.trim()

        val docMatch = Regex("(?:rif|c[eé]dula)\\s*(?:es|de|:)?\\s*([A-Za-z0-9\\-]+)", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val phoneMatch = Regex("(?:whatsapp|tel[eé]fono|cel(?:ular)?)\\s*(?:es|de|:)?\\s*([+0-9\\-\\s]{7,18})", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val validityDays = Regex("(?:validez|v[aá]lido\\s+por)\\s*(?:de)?\\s*(\\d+)\\s*d[ií]as", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.get(1)?.toIntOrNull()?.coerceIn(1, 365) ?: 7
        val discountUsd = Regex("descuento\\s*(?:de)?\\s*\\$?([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()?.coerceAtLeast(0.0) ?: 0.0

        val parsedItems = parseQuoteItemsFromSegment(text, products)
        if (parsedItems.isEmpty()) return null

        val matchedCustomer = findMatchingCustomer(customerNameMatch.orEmpty(), docMatch, customers)
        val finalCustomer = matchedCustomer?.name ?: customerNameMatch?.takeIf { it.isNotBlank() } ?: "Consumidor Final"
        val finalDoc = docMatch.ifBlank { matchedCustomer?.documentId ?: "V-00000000" }
        val finalPhone = phoneMatch.ifBlank { matchedCustomer?.phone ?: "" }

        val action = buildCreateQuoteAction(
            customerName = finalCustomer,
            customerDocument = finalDoc,
            customerPhone = finalPhone,
            items = parsedItems,
            validityDays = validityDays,
            discountUsd = discountUsd,
            notesOrConditions = "Precios sujetos a disponibilidad y tasa BCV vigente.",
            bcvRate = bcvRate
        )
        return ProcessResult.ProposeAction(
            action = action,
            proposalText = formatCreateQuoteProposal(action, bcvRate, viaGemini = false)
        )
    }

    private fun parseQuoteItemsFromSegment(text: String, products: List<Product>): List<QuoteDraftItem> {
        if (products.isEmpty()) return emptyList()
        val useWholesale = text.contains("al mayor", ignoreCase = true) || text.contains("mayorista", ignoreCase = true)
        val result = mutableListOf<QuoteDraftItem>()

        // Split by commas or " y " to handle multi-item phrases like "2 harina pan y 3 aceite"
        val chunks = text.split(Regex(",|\\sy\\s", RegexOption.IGNORE_CASE))
        val matchedProductIds = mutableSetOf<Int>()

        for (chunk in chunks) {
            val cleanChunk = chunk.trim()
            if (cleanChunk.isBlank()) continue

            // Match leading quantity inside chunk, e.g. "con 2 harina pan" or "3 x aceite"
            val qtyRegex = Regex("(?:^|\\s)(\\d+(?:[.,]\\d+)?)\\s*(?:x|unidades\\s+de|uds\\s+de|de)?\\s*([a-záéíóúüñ0-9\\s\\-]+)$", RegexOption.IGNORE_CASE)
            val qtyMatch = qtyRegex.find(cleanChunk)

            val candidateProducts = products.sortedByDescending { it.name.length }
            val directMatch = candidateProducts.firstOrNull { product ->
                cleanChunk.contains(product.name, ignoreCase = true) ||
                    (product.code.isNotBlank() && cleanChunk.contains(product.code, ignoreCase = true))
            }

            if (directMatch != null && matchedProductIds.add(directMatch.id)) {
                val qty = Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:x|unidades|uds)?\\s*(?:de\\s+)?${Regex.escape(directMatch.name)}", RegexOption.IGNORE_CASE)
                    .find(cleanChunk)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
                    ?: qtyMatch?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
                    ?: 1.0
                val unitPrice = if (useWholesale && directMatch.wholesalePriceUsd > 0.0) {
                    directMatch.wholesalePriceUsd
                } else {
                    directMatch.priceUsd
                }
                result.add(
                    QuoteDraftItem(
                        productId = directMatch.id,
                        productName = directMatch.name,
                        quantity = qty.coerceAtLeast(0.01),
                        priceUsd = unitPrice,
                        isTaxExempt = directMatch.isTaxExempt,
                        isWholesale = useWholesale && directMatch.wholesalePriceUsd > 0.0
                    )
                )
            } else if (qtyMatch != null) {
                val qty = qtyMatch.groupValues[1].replace(",", ".").toDoubleOrNull() ?: 1.0
                val queryPart = qtyMatch.groupValues[2]
                    .replace(Regex("\\b(para|cliente|al mayor|mayorista|con|de)\\b.*", RegexOption.IGNORE_CASE), "")
                    .trim()
                val tokenMatch = findMatchingProducts(queryPart, products).firstOrNull()
                if (tokenMatch != null && matchedProductIds.add(tokenMatch.id)) {
                    val unitPrice = if (useWholesale && tokenMatch.wholesalePriceUsd > 0.0) {
                        tokenMatch.wholesalePriceUsd
                    } else {
                        tokenMatch.priceUsd
                    }
                    result.add(
                        QuoteDraftItem(
                            productId = tokenMatch.id,
                            productName = tokenMatch.name,
                            quantity = qty.coerceAtLeast(0.01),
                            priceUsd = unitPrice,
                            isTaxExempt = tokenMatch.isTaxExempt,
                            isWholesale = useWholesale && tokenMatch.wholesalePriceUsd > 0.0
                        )
                    )
                }
            }
        }
        return result
    }

    private fun resolveQuoteItemsFromMaps(
        rawItems: List<Map<String, Any>>,
        products: List<Product>
    ): List<QuoteDraftItem> {
        val resolved = mutableListOf<QuoteDraftItem>()
        for (itemMap in rawItems) {
            val nameQuery = (itemMap["productName"] as? String).orEmpty().trim()
            if (nameQuery.isBlank()) continue
            val product = products.find { it.code.equals(nameQuery, ignoreCase = true) }
                ?: findMatchingProducts(nameQuery, products).firstOrNull()
                ?: continue

            val qty = ((itemMap["quantity"] as? Number)?.toDouble() ?: 1.0).coerceAtLeast(0.01)
            val useWholesale = (itemMap["useWholesale"] as? Boolean) ?: false
            val customPrice = (itemMap["customPriceUsd"] as? Number)?.toDouble()

            val effectivePrice = when {
                customPrice != null && customPrice > 0.0 -> customPrice
                useWholesale && product.wholesalePriceUsd > 0.0 -> product.wholesalePriceUsd
                else -> product.priceUsd
            }

            resolved.add(
                QuoteDraftItem(
                    productId = product.id,
                    productName = product.name,
                    quantity = qty,
                    priceUsd = effectivePrice,
                    isTaxExempt = product.isTaxExempt,
                    isWholesale = useWholesale && product.wholesalePriceUsd > 0.0
                )
            )
        }
        return resolved
    }

    private fun tryParseQuotesQueryLocal(lower: String, quotes: List<Quote>): ProcessResult? {
        val isQuoteQuery = (lower.contains("presupuesto") || lower.contains("cotización") || lower.contains("cotizacion")) &&
            (lower.contains("consulta") || lower.contains("buscar") || lower.contains("último") || lower.contains("ultimo") ||
                lower.contains("ver ") || lower.contains("lista") || lower.contains("pre-"))
        if (!isQuoteQuery) return null

        val preNum = Regex("pre-?\\d+", RegexOption.IGNORE_CASE).find(lower)?.value
        val filtered = if (preNum != null) {
            quotes.filter { it.quoteNumber.contains(preNum, ignoreCase = true) }
        } else {
            quotes.take(5)
        }
        return buildQuoteSearchReply(preNum.orEmpty(), filtered)
    }

    private fun buildQuoteSearchReply(query: String, quotes: List<Quote>): ProcessResult {
        val matches = if (query.isBlank()) {
            quotes.take(5)
        } else {
            quotes.filter {
                it.quoteNumber.contains(query, ignoreCase = true) ||
                    it.customerName.contains(query, ignoreCase = true) ||
                    it.customerDocument.contains(query, ignoreCase = true)
            }.take(5)
        }
        if (matches.isEmpty()) {
            return ProcessResult.DirectReply("No encontré presupuestos guardados${if (query.isNotBlank()) " para '$query'" else ""}.")
        }
        val sb = StringBuilder("📋 **Presupuestos Registrados:**\n")
        matches.forEach { q ->
            sb.append("• **${q.quoteNumber}** — ${q.customerName} | $${"%.2f".format(Locale.US, q.totalUsd)} USD (${q.status})\n")
        }
        return ProcessResult.DirectReply(sb.toString().trim())
    }

    private fun tryParseRegisterExpenseLocal(text: String): ParsedExpense? {
        val lower = text.lowercase(Locale.ROOT)
        if (!lower.contains("gasto") && !lower.contains("egreso")) return null
        if (!lower.contains("registra") && !lower.contains("nuevo") && !lower.contains("agrega") && !lower.contains("anota")) return null

        val amount = Regex("\\$?([0-9]+(?:[.,][0-9]+)?)\\s*(?:dolares|dólares|usd|\\$)?", RegexOption.IGNORE_CASE)
            .find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: return null
        if (amount <= 0.0) return null

        val desc = Regex("(?:por|de|concepto|en)\\s+([a-záéíóúüñ0-9\\s]+?)$", RegexOption.IGNORE_CASE)
            .find(text)?.groupValues?.get(1)?.trim()
            ?.takeIf { it.isNotBlank() && it.toDoubleOrNull() == null }
            ?: "Gasto operativo"

        val category = when {
            lower.contains("luz") || lower.contains("agua") || lower.contains("internet") || lower.contains("servicio") -> "Servicios"
            lower.contains("alquiler") || lower.contains("renta") -> "Alquiler"
            lower.contains("nómina") || lower.contains("nomina") || lower.contains("sueldo") -> "Nómina"
            else -> "Operativo"
        }

        return ParsedExpense(description = desc, amountUsd = amount, category = category)
    }

    private fun tryParseUpdateBcvRateLocal(lower: String): Double? {
        val hasIntent = (lower.contains("cambiar") || lower.contains("actualizar") || lower.contains("pon") || lower.contains("fija") || lower.contains("subir") || lower.contains("bajar")) &&
            (lower.contains("tasa") || lower.contains("bcv") || lower.contains("dolar") || lower.contains("dólar"))
        if (!hasIntent) return null
        return Regex("([0-9]+(?:[.,][0-9]+)?)").find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull()
            ?.takeIf { it > 0.0 }
    }

    private fun tryParseNavigationLocal(lower: String): ProcessResult? {
        val isNav = lower.startsWith("ir a ") || lower.startsWith("abre ") || lower.startsWith("abrir ") ||
            lower.startsWith("llévame a ") || lower.startsWith("llevame a ") || lower.startsWith("ver módulo ") ||
            lower.startsWith("mostrar ")
        if (!isNav) return null

        val route = mapModuleToRoute(lower) ?: return null
        return ProcessResult.Navigate(route, "🚀 Abriendo **${routeDisplayName(route)}**.")
    }

    private fun tryParseCustomerQueryLocal(text: String, customers: List<Customer>): ProcessResult? {
        val lower = text.lowercase(Locale.ROOT)
        if (!lower.contains("cliente")) return null
        if (!lower.contains("busca") && !lower.contains("consulta") && !lower.contains("teléfono") && !lower.contains("telefono") && !lower.contains("datos")) return null

        val query = text.replace(Regex(".*?(?:cliente|de)\\s+", RegexOption.IGNORE_CASE), "").trim('?', '¿', '.', ' ')
        return buildCustomerSearchReply(query, customers)
    }

    private fun buildCustomerSearchReply(query: String, customers: List<Customer>): ProcessResult {
        val matches = if (query.isBlank()) {
            customers.take(8)
        } else {
            customers.filter {
                it.name.contains(query, ignoreCase = true) ||
                    it.documentId.contains(query, ignoreCase = true) ||
                    it.phone.contains(query, ignoreCase = true)
            }.take(8)
        }
        if (matches.isEmpty()) {
            return ProcessResult.DirectReply("No encontré clientes registrados que coincidan con '$query'.")
        }
        val sb = StringBuilder("👤 **Clientes Encontrados:**\n")
        matches.forEach { c ->
            sb.append("• **${c.name}** (${c.documentId}) • Tel: ${c.phone.ifBlank { "N/D" }}\n")
        }
        return ProcessResult.DirectReply(sb.toString().trim())
    }

    private fun buildLowStockReply(products: List<Product>): ProcessResult {
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

    private fun handleInvoiceQuery(invNum: String, sales: List<Sale>): ProcessResult {
        if (invNum.isBlank()) return ProcessResult.DirectReply("Por favor, indica el número de factura que deseas consultar.")
        val invoice = sales.find { it.id.toString() == invNum || it.withholdingVoucherNumber.contains(invNum, ignoreCase = true) }
        if (invoice == null) return ProcessResult.DirectReply("No encontré ninguna factura con el número '$invNum'.")

        val date = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(invoice.timestamp))

        val methods = mutableListOf<String>()
        if (invoice.paymentCashUsd > 0) methods.add("Efectivo ($${invoice.paymentCashUsd})")
        if (invoice.paymentMobileBs > 0) methods.add("Pago Móvil (Bs. ${invoice.paymentMobileBs})")
        if (invoice.paymentCardBs > 0) methods.add("Punto/Tarjeta (Bs. ${invoice.paymentCardBs})")
        if (invoice.paymentCasheaUsd > 0) methods.add("Cashea ($${invoice.paymentCasheaUsd})")
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
            isTaxExempt = p.isTaxExempt,
            code = p.code,
            profitPercentage = p.profitPercentage,
            wholesalePriceUsd = p.wholesalePriceUsd,
            autoCalculatedWithMinMargin = p.autoCalculatedWithMinMargin
        )
        val text = formatCreateProductProposal(action, bcvRate, viaGemini = false)
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
            val wholesaleLine = if (p.wholesalePriceUsd > 0.0) "\n• Precio al Mayor: **$${"%.2f".format(Locale.US, p.wholesalePriceUsd)}**" else ""
            val text = "📦 **${p.name}**${if (p.code.isNotBlank()) " (${p.code})" else ""}\n" +
                "• Categoría: ${p.category}\n" +
                "• Existencias: **$stockText**\n" +
                "• Precio Venta: **$${"%.2f".format(Locale.US, p.priceUsd)}** (Bs. ${"%.2f".format(Locale.US, p.priceUsd * bcvRate)})" +
                wholesaleLine + "\n" +
                "• Precio Costo: $${"%.2f".format(Locale.US, p.costPriceUsd)}"
            return ProcessResult.DirectReply(text)
        }

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
            sb.append("\n¿A cuál de estos productos modifico o añado existencias?")

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
        val targetProduct = products.find { it.id == chosenItem.id }
            ?: return ProcessResult.DirectReply("No pude localizar el producto seleccionado.")

        return if (pending.actionIntent == "ADD_STOCK") {
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
            ProcessResult.ProposeAction(action, text)
        } else {
            val stockText = if (targetProduct.isRecipe) "Receta / Servicio" else "${targetProduct.stock} uds"
            val text = "📦 **${targetProduct.name}**\n" +
                "• Categoría: ${targetProduct.category}\n" +
                "• Existencias: **$stockText**\n" +
                "• Precio Venta: **$${"%.2f".format(Locale.US, targetProduct.priceUsd)}**\n" +
                "• Precio Costo: $${"%.2f".format(Locale.US, targetProduct.costPriceUsd)}"
            ProcessResult.DirectReply(text)
        }
    }

    // --- Helpers and Matchers ---

    private fun findMatchingCustomer(
        nameQuery: String,
        docQuery: String,
        customers: List<Customer>
    ): Customer? {
        if (docQuery.isNotBlank()) {
            customers.find { it.documentId.equals(docQuery, ignoreCase = true) }?.let { return it }
        }
        if (nameQuery.isNotBlank()) {
            return customers.find { it.name.equals(nameQuery, ignoreCase = true) }
                ?: customers.find { it.name.contains(nameQuery, ignoreCase = true) }
        }
        return null
    }

    private fun isConfirmationAffirmative(text: String): Boolean {
        val clean = text.replace(Regex("[^a-záéíóúüñ]"), " ").trim()
        val affirmatives = setOf("si", "sí", "confirmar", "confirmo", "correcto", "dale", "hazlo", "proceder", "ok", "yes", "claro", "por favor", "adelante", "guardar", "acepto", "listo")
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

        val exact = products.filter { it.name.equals(cleanQuery, ignoreCase = true) || it.code.equals(cleanQuery, ignoreCase = true) }
        if (exact.isNotEmpty()) return exact

        val substringMatches = products.filter { it.name.contains(cleanQuery, ignoreCase = true) }
        if (substringMatches.isNotEmpty()) return substringMatches

        val tokens = cleanQuery.split(" ").filter { it.length > 2 }
        if (tokens.isNotEmpty()) {
            val tokenMatches = products.filter { p ->
                tokens.any { token -> p.name.contains(token, ignoreCase = true) }
            }
            if (tokenMatches.isNotEmpty()) return tokenMatches
        }

        return emptyList()
    }

    private fun tryParseAddStockLocal(text: String): AddStockParsed? {
        val lower = text.lowercase(Locale.ROOT)
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

    private fun tryParseRegisterProductLocal(
        text: String,
        minProfitMarginPercent: Double? = null
    ): ParsedProduct? {
        val lower = text.lowercase(Locale.ROOT)
        if (!lower.contains("nuevo producto") && !lower.contains("crear producto") && !lower.contains("agrega un producto") && !lower.contains("agregar producto")) {
            return null
        }

        val nameRegex = Regex(
            "(?:nuevo producto|crear producto|agrega un producto|agregar producto),?\\s*([a-záéíóúüñ0-9\\s\\-]+?)(?:\\s+c[oó]digo|\\s+sku|\\s+con|\\s+cantidad|\\s+existencias|\\s+stock|\\s+precio|\\s+costo|\\s+ganancia|\\s+margen|\\s+al mayor|\\s+mayorista|\\s+venta|$)",
            RegexOption.IGNORE_CASE
        )
        val codeRegex = Regex("(?:c[oó]digo|sku|ref(?:erencia)?)\\s*(?:es|de|:)?\\s*([A-Za-z0-9\\-_]+)", RegexOption.IGNORE_CASE)
        val stockRegex = Regex("(?:cantidad|existencias|stock|unidades)\\s*(?:de|con|:)?\\s*([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)
        val costRegex = Regex("(?:precio\\s+de\\s+costo|precio\\s+costo|costo)\\s*(?:es|de|a|:)?\\s*\\$?([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)
        val wholesaleRegex = Regex("(?:precio\\s+al\\s+mayor|precio\\s+mayorista|al\\s+mayor|mayorista)\\s*(?:es|de|a|:)?\\s*\\$?([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)
        val profitRegex = Regex("(?:porcentaje\\s+de\\s+ganancia|ganancia|margen)\\s*(?:del|de|:)?\\s*([0-9]+(?:[.,][0-9]+)?)\\s*%?", RegexOption.IGNORE_CASE)
        val priceRegex = Regex("(?:precio\\s+de\\s+venta|precio\\s+venta|pvp|venta)\\s*(?:es|de|a|:)?\\s*\\$?([0-9]+(?:[.,][0-9]+)?)", RegexOption.IGNORE_CASE)

        val name = nameRegex.find(text)?.groupValues?.get(1)?.trim() ?: return null
        if (name.isBlank()) return null

        val code = codeRegex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val stock = stockRegex.find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        val cost = costRegex.find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        val wholesale = wholesaleRegex.find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        val parsedProfit = profitRegex.find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0
        val parsedPrice = priceRegex.find(lower)?.groupValues?.get(1)?.replace(",", ".")?.toDoubleOrNull() ?: 0.0

        if (parsedPrice == 0.0 && cost == 0.0 && wholesale == 0.0) return null

        var finalPrice = parsedPrice
        var effectiveProfit = parsedProfit
        var usedAutoMinMargin = false

        if (finalPrice <= 0.0 && cost > 0.0) {
            if (parsedProfit > 0.0) {
                finalPrice = cost * (1.0 + parsedProfit / 100.0)
            } else if (minProfitMarginPercent != null && minProfitMarginPercent > 0.0) {
                finalPrice = cost * (1.0 + minProfitMarginPercent / 100.0)
                effectiveProfit = minProfitMarginPercent
                usedAutoMinMargin = true
            }
        } else if (finalPrice > 0.0 && cost > 0.0 && parsedProfit <= 0.0) {
            effectiveProfit = ((finalPrice - cost) / cost) * 100.0
        }

        return ParsedProduct(
            name = name,
            category = "General",
            stock = stock,
            costPriceUsd = cost,
            priceUsd = finalPrice,
            isRecipe = false,
            code = code,
            profitPercentage = effectiveProfit,
            wholesalePriceUsd = wholesale,
            autoCalculatedWithMinMargin = usedAutoMinMargin
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

    companion object {
        fun buildCatalogSummary(products: List<Product>): String {
            return products.take(45).joinToString("\n") {
                val codeInfo = if (it.code.isNotBlank()) " | Cód: ${it.code}" else ""
                val wholesaleInfo = if (it.wholesalePriceUsd > 0.0) " | Mayor: $${it.wholesalePriceUsd}" else ""
                val exemptInfo = if (it.isTaxExempt) " | Exento" else ""
                "- [ID:${it.id}] ${it.name}$codeInfo | Cat: ${it.category} | Stock: ${if (it.isRecipe) "Receta" else "${it.stock} uds"} | Costo: $${it.costPriceUsd} | Venta: $${it.priceUsd}$wholesaleInfo$exemptInfo"
            }
        }

        fun buildSuppliersSummary(suppliers: List<Supplier>): String {
            return suppliers.joinToString("\n") {
                "- [ID:${it.id}] ${it.name} (RIF: ${it.documentId})"
            }
        }

        fun buildCustomersSummary(customers: List<Customer>): String {
            return customers.take(30).joinToString("\n") {
                "- [ID:${it.id}] ${it.name} | Doc: ${it.documentId} | Tel: ${it.phone}"
            }
        }

        fun summarizePendingAction(action: PendingAssistantAction?): String? {
            return when (action) {
                is PendingAssistantAction.CreateProductAction ->
                    "Producto pendiente -> Nombre: ${action.name}, Código: ${action.code}, Categoría: ${action.category}, Stock: ${action.stock}, Costo: $${action.costPriceUsd}, Ganancia: ${action.profitPercentage}%, Venta: $${action.priceUsd}, Mayor: $${action.wholesalePriceUsd}, ExentoIVA: ${action.isTaxExempt}"
                is PendingAssistantAction.CreateQuoteAction ->
                    "Presupuesto pendiente -> Cliente: ${action.customerName} (${action.customerDocument}), Tel: ${action.customerPhone}, Validez: ${action.validityDays} días, Descuento: $${action.discountUsd}, Total: $${"%.2f".format(Locale.US, action.totalUsd)}, Items: ${action.items.joinToString { "${it.quantity}x ${it.productName} ($${it.priceUsd})" }}"
                is PendingAssistantAction.AddStockAction ->
                    "Adición de stock pendiente -> ${action.productName}: +${action.quantityToAdd} uds"
                is PendingAssistantAction.CreateCustomerAction ->
                    "Registro de cliente pendiente -> ${action.name} (${action.documentId})"
                is PendingAssistantAction.CreatePurchaseInvoiceAction ->
                    "Factura proveedor pendiente -> ${action.supplierName} #${action.invoiceNumber} por $${action.totalAmount}"
                is PendingAssistantAction.RegisterExpenseAction ->
                    "Gasto pendiente -> ${action.description} ($${action.amountUsd})"
                is PendingAssistantAction.UpdateBcvRateAction ->
                    "Cambio de tasa BCV pendiente -> Bs. ${action.newRateBs}"
                is PendingAssistantAction.DeleteProductAction ->
                    "Eliminación pendiente -> ${action.productName}"
                is PendingAssistantAction.SelectDisambiguation ->
                    "Selección pendiente entre: ${action.candidates.joinToString { it.title }}"
                null -> null
            }
        }

        fun calculateQuoteTotals(
            items: List<QuoteDraftItem>,
            discountUsd: Double,
            bcvRate: Double
        ): QuoteTotals {
            val taxableGrossUsd = items.filter { !it.isTaxExempt }.sumOf { it.priceUsd * it.quantity }
            val exemptGrossUsd = items.filter { it.isTaxExempt }.sumOf { it.priceUsd * it.quantity }

            val subtotalTaxableBase = taxableGrossUsd / 1.16
            val subtotalExemptBase = exemptGrossUsd
            val subtotalBeforeTax = subtotalTaxableBase + subtotalExemptBase

            val effectiveDiscount = minOf(maxOf(0.0, discountUsd), subtotalBeforeTax)
            val ratioTaxable = if (subtotalBeforeTax > 0.0001) subtotalTaxableBase / subtotalBeforeTax else 1.0
            val ratioExempt = if (subtotalBeforeTax > 0.0001) subtotalExemptBase / subtotalBeforeTax else 0.0

            val baseTaxableUsd = maxOf(0.0, subtotalTaxableBase - (effectiveDiscount * ratioTaxable))
            val baseExemptUsd = maxOf(0.0, subtotalExemptBase - (effectiveDiscount * ratioExempt))
            val ivaUsd = baseTaxableUsd * 0.16
            val totalUsd = baseTaxableUsd + baseExemptUsd + ivaUsd
            val safeRate = if (bcvRate > 0.001) bcvRate else 1.0
            val totalBs = totalUsd * safeRate

            return QuoteTotals(
                discountUsd = effectiveDiscount,
                baseTaxableUsd = baseTaxableUsd,
                baseExemptUsd = baseExemptUsd,
                ivaUsd = ivaUsd,
                totalUsd = totalUsd,
                totalBs = totalBs
            )
        }

        fun buildCreateQuoteAction(
            customerName: String,
            customerDocument: String,
            customerPhone: String,
            items: List<QuoteDraftItem>,
            validityDays: Int,
            discountUsd: Double,
            notesOrConditions: String,
            bcvRate: Double
        ): PendingAssistantAction.CreateQuoteAction {
            val totals = calculateQuoteTotals(items, discountUsd, bcvRate)
            return PendingAssistantAction.CreateQuoteAction(
                customerName = customerName.ifBlank { "Consumidor Final" },
                customerDocument = customerDocument.ifBlank { "V-00000000" },
                customerPhone = customerPhone.trim(),
                items = items,
                validityDays = validityDays.coerceIn(1, 365),
                discountUsd = totals.discountUsd,
                notesOrConditions = notesOrConditions,
                baseTaxableUsd = totals.baseTaxableUsd,
                baseExemptUsd = totals.baseExemptUsd,
                ivaUsd = totals.ivaUsd,
                totalUsd = totals.totalUsd,
                totalBs = totals.totalBs
            )
        }

        fun formatCreateQuoteProposal(
            action: PendingAssistantAction.CreateQuoteAction,
            bcvRate: Double,
            viaGemini: Boolean = false
        ): String {
            val header = if (viaGemini) "📋 **Presupuesto Preparado vía Gemini:**" else "📋 **Presupuesto Preparado:**"
            val phoneLine = if (action.customerPhone.isNotBlank()) "\n• WhatsApp / Tel: **${action.customerPhone}**" else ""
            val itemsBlock = action.items.joinToString("\n") { item ->
                val qtyStr = if (item.quantity % 1.0 == 0.0) item.quantity.toInt().toString() else "%.2f".format(Locale.US, item.quantity)
                val wholesaleTag = if (item.isWholesale) " *(Mayor)*" else ""
                val lineTotal = item.quantity * item.priceUsd
                "  - ${qtyStr}x **${item.productName}**$wholesaleTag ($${"%.2f".format(Locale.US, item.priceUsd)}) = **$${"%.2f".format(Locale.US, lineTotal)}**"
            }
            val discountLine = if (action.discountUsd > 0.0) "\n• Descuento: **-$${"%.2f".format(Locale.US, action.discountUsd)}**" else ""
            val rate = if (bcvRate > 0.001) bcvRate else 1.0
            val totalBs = action.totalUsd * rate

            return "$header\n" +
                "• Cliente: **${action.customerName}** (${action.customerDocument})" +
                phoneLine + "\n" +
                "• Validez: **${action.validityDays} días**\n" +
                "• Productos:\n$itemsBlock\n" +
                "• Base Imponible: $${"%.2f".format(Locale.US, action.baseTaxableUsd)} | Exento: $${"%.2f".format(Locale.US, action.baseExemptUsd)}\n" +
                "• IVA (16%): $${"%.2f".format(Locale.US, action.ivaUsd)}" +
                discountLine + "\n" +
                "• **TOTAL PRESUPUESTO: $${"%.2f".format(Locale.US, action.totalUsd)} USD** (Bs. ${"%.2f".format(Locale.US, totalBs)})\n\n" +
                "Puedes corregirme por voz en tiempo real, editarlo manualmente o confirmar para generar el ticket y enviarlo por WhatsApp."
        }

        fun formatCreateProductProposal(
            action: PendingAssistantAction.CreateProductAction,
            bcvRate: Double,
            viaGemini: Boolean = false
        ): String {
            val header = if (viaGemini) "He preparado el registro del nuevo producto vía Gemini:" else "He preparado el registro del nuevo producto:"
            val codeLine = if (action.code.isNotBlank()) "\n• Código / SKU: **${action.code}**" else ""
            val recipeNote = if (action.isRecipe) {
                "\n• Tipo: **Receta / Servicio (Sin existencias requeridas)**"
            } else {
                "\n• Existencias iniciales: **${action.stock} uds**\n• Costo unitario: **$${"%.2f".format(Locale.US, action.costPriceUsd)}**"
            }
            val profitLine = if (!action.isRecipe && action.profitPercentage > 0.0) {
                val autoTag = if (action.autoCalculatedWithMinMargin) " (Margen mínimo configurado)" else ""
                "\n• Ganancia: **${"%.1f".format(Locale.US, action.profitPercentage)}%**$autoTag"
            } else ""
            val priceLine = if (action.priceUsd > 0.0) {
                "\n• Precio de Venta: **$${"%.2f".format(Locale.US, action.priceUsd)}** (Bs. ${"%.2f".format(Locale.US, action.priceUsd * bcvRate)})"
            } else {
                "\n• Precio de Venta: **Pendiente** *(Toca 'Modificar / Agregar Info' para definirlo)*"
            }
            val wholesaleLine = if (action.wholesalePriceUsd > 0.0) {
                "\n• Precio al Mayor: **$${"%.2f".format(Locale.US, action.wholesalePriceUsd)}** (Bs. ${"%.2f".format(Locale.US, action.wholesalePriceUsd * bcvRate)})"
            } else ""
            val taxLine = if (action.isTaxExempt) "\n• Impuesto: **Exento de IVA**" else ""

            return "$header\n" +
                "• Nombre: **${action.name}**" +
                codeLine + "\n" +
                "• Categoría: **${action.category}**" +
                recipeNote +
                profitLine +
                priceLine +
                wholesaleLine +
                taxLine + "\n\n" +
                "Puedes corregirme por voz en vivo, pulsar **'Modificar / Agregar Info'** o confirmar para guardar."
        }

        fun mapModuleToRoute(raw: String): String? {
            return when {
                raw.contains("pos") || raw.contains("facturación") || raw.contains("facturacion") || raw.contains("caja") || raw.contains("vender") -> "pos"
                raw.contains("presupuesto") || raw.contains("cotizacion") || raw.contains("cotización") || raw == "quotes" -> "quotes"
                raw.contains("inventario") || raw.contains("producto") || raw == "inventory" -> "inventory"
                raw.contains("factura") || raw == "invoices" -> "invoices"
                raw.contains("gasto") || raw == "expenses" -> "expenses"
                raw.contains("resumen") || raw == "summary" -> "summary"
                raw.contains("cliente") || raw == "customers" -> "customers"
                raw.contains("proveedor") || raw == "suppliers" -> "suppliers"
                raw.contains("ajuste") || raw.contains("configuraci") || raw == "settings" -> "settings"
                else -> null
            }
        }

        fun routeDisplayName(route: String): String = when (route) {
            "pos" -> "Facturación / POS"
            "quotes" -> "Presupuestos"
            "inventory" -> "Inventario"
            "invoices" -> "Facturas"
            "expenses" -> "Gastos"
            "summary" -> "Resumen Diario"
            "customers" -> "Clientes"
            "suppliers" -> "Proveedores"
            "settings" -> "Ajustes"
            else -> route
        }
    }
}

data class QuoteTotals(
    val discountUsd: Double,
    val baseTaxableUsd: Double,
    val baseExemptUsd: Double,
    val ivaUsd: Double,
    val totalUsd: Double,
    val totalBs: Double
)

data class AddStockParsed(val productQuery: String, val quantity: Double, val newCostPrice: Double? = null)
data class ParsedProduct(
    val name: String,
    val category: String,
    val stock: Double,
    val costPriceUsd: Double,
    val priceUsd: Double,
    val isRecipe: Boolean,
    val isTaxExempt: Boolean = false,
    val code: String = "",
    val profitPercentage: Double = 0.0,
    val wholesalePriceUsd: Double = 0.0,
    val autoCalculatedWithMinMargin: Boolean = false
)
data class ParsedCustomer(val name: String, val documentId: String, val phone: String, val address: String)
data class ParsedPurchaseInvoice(val supplierId: Int, val supplierName: String, val invoiceNumber: String, val totalAmount: Double, val description: String)
data class ParsedExpense(val description: String, val amountUsd: Double, val category: String)

sealed class ProcessResult {
    data class DirectReply(val text: String) : ProcessResult()
    data class ProposeAction(val action: PendingAssistantAction, val proposalText: String) : ProcessResult()
    data class ConfirmAction(val action: PendingAssistantAction) : ProcessResult()
    data class CancelAction(val cancellationText: String) : ProcessResult()
    data class Navigate(val route: String, val text: String) : ProcessResult()
}

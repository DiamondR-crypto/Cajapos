package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.PreferencesManager
import com.example.data.remote.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class GeminiRepository(private val prefs: PreferencesManager? = null) {
    private val service = RetrofitClient.geminiService

    private val quoteItemSchema = PropertySchema(
        type = "OBJECT",
        description = "Producto y cantidad a incluir en el presupuesto",
        properties = mapOf(
            "productName" to PropertySchema(type = "STRING", description = "Nombre o código del producto del inventario"),
            "quantity" to PropertySchema(type = "NUMBER", description = "Cantidad solicitada (por defecto 1)"),
            "customPriceUsd" to PropertySchema(type = "NUMBER", description = "Precio unitario personalizado en USD (opcional)"),
            "useWholesale" to PropertySchema(type = "BOOLEAN", description = "True si el cliente solicita precio al mayor")
        ),
        required = listOf("productName", "quantity")
    )

    private val functionDeclarations: List<FunctionDeclaration> = listOf(
        FunctionDeclaration(
            name = "queryInventory",
            description = "Consulta la información de stock, precios en USD/Bs, precio al mayor y detalles de productos en el inventario.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "productName" to PropertySchema(type = "STRING", description = "Nombre o palabra clave del producto a consultar")
                ),
                required = listOf("productName")
            )
        ),
        FunctionDeclaration(
            name = "queryLowStock",
            description = "Lista los productos que tienen existencias bajas o agotadas (stock <= 5).",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "registerNewProduct",
            description = "Prepara el registro de un nuevo producto en el inventario.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "name" to PropertySchema(type = "STRING", description = "Nombre descriptivo del producto"),
                    "code" to PropertySchema(type = "STRING", description = "Código o SKU del producto (opcional)"),
                    "category" to PropertySchema(type = "STRING", description = "Categoría del producto"),
                    "stock" to PropertySchema(type = "NUMBER", description = "Cantidad de existencias"),
                    "costPriceUsd" to PropertySchema(type = "NUMBER", description = "Precio de costo en dólares"),
                    "profitPercentage" to PropertySchema(type = "NUMBER", description = "Porcentaje de ganancia sobre el costo (opcional, ej. 30)"),
                    "priceUsd" to PropertySchema(type = "NUMBER", description = "Precio de venta al detal en dólares"),
                    "wholesalePriceUsd" to PropertySchema(type = "NUMBER", description = "Precio de venta al mayor en dólares (opcional)"),
                    "isRecipe" to PropertySchema(type = "BOOLEAN", description = "True si es receta o servicio"),
                    "isTaxExempt" to PropertySchema(type = "BOOLEAN", description = "True si el producto está exento de IVA")
                ),
                required = listOf("name")
            )
        ),
        FunctionDeclaration(
            name = "updatePendingProduct",
            description = "Modifica o corrige en tiempo real cualquier campo del producto pendiente que se está preparando actualmente.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "name" to PropertySchema(type = "STRING", description = "Nuevo nombre del producto si cambia"),
                    "code" to PropertySchema(type = "STRING", description = "Nuevo código/SKU si cambia"),
                    "category" to PropertySchema(type = "STRING", description = "Nueva categoría si cambia"),
                    "stock" to PropertySchema(type = "NUMBER", description = "Nueva cantidad de existencias"),
                    "costPriceUsd" to PropertySchema(type = "NUMBER", description = "Nuevo precio de costo en USD"),
                    "profitPercentage" to PropertySchema(type = "NUMBER", description = "Nuevo porcentaje de ganancia"),
                    "priceUsd" to PropertySchema(type = "NUMBER", description = "Nuevo precio de venta al detal en USD"),
                    "wholesalePriceUsd" to PropertySchema(type = "NUMBER", description = "Nuevo precio al mayor en USD"),
                    "isRecipe" to PropertySchema(type = "BOOLEAN", description = "Si es receta o servicio"),
                    "isTaxExempt" to PropertySchema(type = "BOOLEAN", description = "Si es exento de IVA")
                )
            )
        ),
        FunctionDeclaration(
            name = "addStock",
            description = "Prepara la adición de existencias a un producto existente en el inventario.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "productName" to PropertySchema(type = "STRING", description = "Nombre del producto"),
                    "quantity" to PropertySchema(type = "NUMBER", description = "Cantidad a sumar"),
                    "costPriceUsd" to PropertySchema(type = "NUMBER", description = "Nuevo precio de costo opcional")
                ),
                required = listOf("productName", "quantity")
            )
        ),
        FunctionDeclaration(
            name = "deleteProduct",
            description = "Prepara la eliminación de un producto del inventario.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "productName" to PropertySchema(type = "STRING", description = "Nombre del producto a eliminar")
                ),
                required = listOf("productName")
            )
        ),
        FunctionDeclaration(
            name = "createQuote",
            description = "Prepara y calcula un presupuesto (cotización) para un cliente con uno o más productos del inventario, sin descontar stock. Permite indicar validez, descuento y teléfono WhatsApp.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "customerName" to PropertySchema(type = "STRING", description = "Nombre del cliente (o 'Consumidor Final')"),
                    "customerDocument" to PropertySchema(type = "STRING", description = "Cédula o RIF del cliente (opcional)"),
                    "customerPhone" to PropertySchema(type = "STRING", description = "Teléfono o WhatsApp del cliente (opcional)"),
                    "items" to PropertySchema(
                        type = "ARRAY",
                        description = "Lista de productos y cantidades para el presupuesto",
                        items = quoteItemSchema
                    ),
                    "validityDays" to PropertySchema(type = "INTEGER", description = "Días de validez del presupuesto (por defecto 7)"),
                    "discountUsd" to PropertySchema(type = "NUMBER", description = "Descuento total en dólares (opcional)"),
                    "notesOrConditions" to PropertySchema(type = "STRING", description = "Observaciones o condiciones del presupuesto")
                ),
                required = listOf("items")
            )
        ),
        FunctionDeclaration(
            name = "updatePendingQuote",
            description = "Modifica o corrige en tiempo real el presupuesto pendiente actual: cambia cliente, teléfono, días de validez, descuento, notas, o agrega/reemplaza/quita productos.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "customerName" to PropertySchema(type = "STRING", description = "Nuevo nombre de cliente"),
                    "customerDocument" to PropertySchema(type = "STRING", description = "Nueva cédula o RIF"),
                    "customerPhone" to PropertySchema(type = "STRING", description = "Nuevo teléfono WhatsApp"),
                    "validityDays" to PropertySchema(type = "INTEGER", description = "Nuevos días de validez"),
                    "discountUsd" to PropertySchema(type = "NUMBER", description = "Nuevo descuento en USD"),
                    "notesOrConditions" to PropertySchema(type = "STRING", description = "Nuevas observaciones o condiciones"),
                    "addItems" to PropertySchema(type = "ARRAY", description = "Productos a agregar o sumar al presupuesto pendiente", items = quoteItemSchema),
                    "replaceItems" to PropertySchema(type = "ARRAY", description = "Lista completa que reemplaza los productos del presupuesto pendiente", items = quoteItemSchema),
                    "removeProductName" to PropertySchema(type = "STRING", description = "Nombre de un producto a quitar del presupuesto")
                )
            )
        ),
        FunctionDeclaration(
            name = "queryQuotes",
            description = "Consulta los presupuestos guardados (por número PRE-XXXXX, cliente o estado) para informar al usuario o abrir su ticket.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "query" to PropertySchema(type = "STRING", description = "Número de presupuesto, nombre del cliente o vacío para listar los últimos")
                )
            )
        ),
        FunctionDeclaration(
            name = "registerCustomer",
            description = "Prepara el registro de un nuevo cliente en el sistema.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "name" to PropertySchema(type = "STRING", description = "Nombre completo del cliente"),
                    "documentId" to PropertySchema(type = "STRING", description = "Cédula o RIF del cliente"),
                    "phone" to PropertySchema(type = "STRING", description = "Número de teléfono / WhatsApp"),
                    "address" to PropertySchema(type = "STRING", description = "Dirección")
                ),
                required = listOf("name")
            )
        ),
        FunctionDeclaration(
            name = "queryCustomers",
            description = "Busca clientes registrados por nombre o cédula/RIF para ver su teléfono y datos.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "query" to PropertySchema(type = "STRING", description = "Nombre o cédula/RIF a buscar")
                ),
                required = listOf("query")
            )
        ),
        FunctionDeclaration(
            name = "queryInvoice",
            description = "Consulta la información de una factura de venta por su número.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "invoiceNumber" to PropertySchema(type = "STRING", description = "Número de la factura a consultar")
                ),
                required = listOf("invoiceNumber")
            )
        ),
        FunctionDeclaration(
            name = "registerPurchaseInvoice",
            description = "Prepara el registro de una factura de compra a un proveedor.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "supplierName" to PropertySchema(type = "STRING", description = "Nombre del proveedor"),
                    "invoiceNumber" to PropertySchema(type = "STRING", description = "Número de factura del proveedor"),
                    "totalAmount" to PropertySchema(type = "NUMBER", description = "Monto total de la factura en USD"),
                    "description" to PropertySchema(type = "STRING", description = "Detalle breve de la compra")
                ),
                required = listOf("supplierName", "invoiceNumber", "totalAmount")
            )
        ),
        FunctionDeclaration(
            name = "registerExpense",
            description = "Prepara el registro de un gasto operativo del negocio en USD.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "description" to PropertySchema(type = "STRING", description = "Concepto o descripción del gasto"),
                    "amountUsd" to PropertySchema(type = "NUMBER", description = "Monto del gasto en USD"),
                    "category" to PropertySchema(type = "STRING", description = "Categoría del gasto (ej. Servicios, Nómina, Alquiler, Operativo)")
                ),
                required = listOf("description", "amountUsd")
            )
        ),
        FunctionDeclaration(
            name = "updateBcvRate",
            description = "Prepara la actualización manual de la tasa oficial del BCV en Bolívares por Dólar.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "rateBs" to PropertySchema(type = "NUMBER", description = "Nueva tasa BCV en Bolívares por 1 USD")
                ),
                required = listOf("rateBs")
            )
        ),
        FunctionDeclaration(
            name = "checkBcvRate",
            description = "Consulta la tasa de cambio oficial actual del BCV (Bolívares por Dólar).",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "getDailySummary",
            description = "Consulta el resumen financiero de ventas, gastos y balance del día actual.",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "confirmCurrentAction",
            description = "Confirma y guarda definitivamente en la base de datos la acción pendiente actual (producto, presupuesto, stock, cliente, gasto, factura o tasa) cuando el usuario dice 'sí', 'confirma', 'guárdalo' o 'listo'.",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "cancelCurrentAction",
            description = "Cancela y descarta la acción pendiente actual cuando el usuario dice 'no', 'cancela' o 'descarta'.",
            parameters = FunctionParameters(type = "OBJECT", properties = emptyMap())
        ),
        FunctionDeclaration(
            name = "navigateToModule",
            description = "Navega a una pantalla o módulo específico de la aplicación SmartPOS286.",
            parameters = FunctionParameters(
                type = "OBJECT",
                properties = mapOf(
                    "module" to PropertySchema(
                        type = "STRING",
                        description = "Nombre del módulo: 'pos' (facturación), 'quotes' (presupuestos), 'inventory' (inventario), 'invoices' (facturas), 'expenses' (gastos), 'summary' (resumen), 'customers' (clientes), 'suppliers' (proveedores), 'settings' (ajustes)"
                    )
                ),
                required = listOf("module")
            )
        )
    )

    private val tools = listOf(Tool(functionDeclarations = functionDeclarations))

    fun getEffectiveApiKey(): String {
        val customKey = prefs?.geminiCustomApiKey?.trim().orEmpty()
        if (customKey.isNotBlank()) return customKey
        return BuildConfig.GEMINI_API_KEY.trim()
    }

    fun getEffectiveApiKeyOrNull(): String? {
        val key = getEffectiveApiKey()
        if (key.isBlank() || key.equals("demo", ignoreCase = true) || key.equals("YOUR_API_KEY", ignoreCase = true)) {
            return null
        }
        return key
    }

    fun hasValidApiKey(): Boolean = getEffectiveApiKeyOrNull() != null

    fun getEffectiveModel(): String {
        val model = prefs?.geminiSelectedModel?.trim().orEmpty()
        if (model.isNotBlank()) return model
        return "gemini-3.5-flash"
    }

    fun getLiveModel(): String = "models/gemini-2.5-flash-native-audio-preview-12-2025"

    fun buildSystemInstruction(
        catalogSummary: String,
        suppliersSummary: String,
        customersSummary: String,
        bcvRate: Double,
        todaySalesSummary: String,
        minProfitMarginPercent: Double? = null,
        pendingActionSummary: String? = null
    ): String {
        val marginRule = if (minProfitMarginPercent != null && minProfitMarginPercent > 0.0) {
            "- Hay una ganancia porcentual mínima configurada de $minProfitMarginPercent%. Si se da costo sin precio de venta en 'registerNewProduct' o 'updatePendingProduct', el sistema calculará el precio de venta automáticamente."
        } else {
            "- Si el usuario da costo sin precio de venta al crear un producto, igualmente puedes preparar 'registerNewProduct' con costPriceUsd para que lo complete."
        }

        val pendingBlock = if (!pendingActionSummary.isNullOrBlank()) {
            """
            ACCIÓN PENDIENTE ACTUAL EN PANTALLA (el usuario puede corregirla o confirmarla en tiempo real):
            $pendingActionSummary
            """.trimIndent()
        } else {
            "ACCIÓN PENDIENTE ACTUAL EN PANTALLA: Ninguna."
        }

        return """
            Eres el Asistente Inteligente en Tiempo Real de SmartPOS286, una aplicación POS de facturación, inventario y presupuestos.
            Razona paso a paso antes de responder o llamar herramientas para elegir la acción exacta.
            Tasa oficial BCV vigente: $bcvRate Bs/USD.

            $pendingBlock

            RESUMEN DEL DÍA:
            $todaySalesSummary

            INVENTARIO ACTUAL (muestra):
            $catalogSummary

            CLIENTES REGISTRADOS (muestra):
            $customersSummary

            PROVEEDORES REGISTRADOS:
            $suppliersSummary

            CAPACIDADES Y REGLAS ESTRICTAS:
            1. PRESUPUESTOS: Si el usuario pide crear, armar o cotizar un presupuesto, usa 'createQuote' buscando los productos reales del inventario. Si ya hay un presupuesto pendiente y el usuario corrige cantidades, agrega/quita productos, cambia cliente, WhatsApp, validez o descuento, usa 'updatePendingQuote'.
            2. PRODUCTOS Y CORRECCIONES EN VIVO: Usa 'registerNewProduct' para productos nuevos. Si ya hay un producto pendiente y el usuario hace correcciones en vivo (cambia precio, costo, código, porcentaje de ganancia, precio al mayor, stock o exento), usa 'updatePendingProduct'.
            $marginRule
            3. CONFIRMACIÓN POR VOZ/CHAT: Si hay una acción pendiente y el usuario dice "sí", "confirma", "guárdalo" o "listo", llama a 'confirmCurrentAction'. Si dice "no" o "cancela", llama a 'cancelCurrentAction'.
            4. OTRAS FUNCIONES: Puedes añadir stock ('addStock'), registrar clientes ('registerCustomer'), registrar gastos ('registerExpense'), registrar facturas de compra ('registerPurchaseInvoice'), actualizar tasa BCV ('updateBcvRate'), consultar presupuestos ('queryQuotes'), clientes ('queryCustomers'), facturas ('queryInvoice'), bajo stock ('queryLowStock') o navegar a pantallas ('navigateToModule').
            5. CONSULTAS Y RAZONAMIENTO: Usa herramientas de consulta si necesitas datos exactos antes de responder o proponer un presupuesto.
            6. REGLA ESTRICTA DE IDIOMA Y EXTENSIÓN: Responde SIEMPRE Y EXCLUSIVAMENTE EN ESPAÑOL. NUNCA respondas en inglés. En modo Live / voz (y chat), da respuestas EXTREMADAMENTE CORTAS, BREVES Y PUNTUALES (máximo 1 o 2 oraciones directas). No des explicaciones largas, descripciones detalladas ni información excesiva; ve directo al grano para que el flujo de trabajo sea rápido, ágil y cómodo.
        """.trimIndent()
    }

    fun getToolDeclarationsJson(): JSONArray {
        val arr = JSONArray()
        functionDeclarations.forEach { decl ->
            val fnJson = JSONObject().apply {
                put("name", decl.name)
                put("description", decl.description)
                decl.parameters?.let { params ->
                    put("parameters", functionParametersToJson(params))
                }
            }
            arr.put(fnJson)
        }
        return arr
    }

    private fun functionParametersToJson(params: FunctionParameters): JSONObject {
        val obj = JSONObject()
        obj.put("type", params.type)
        val propsObj = JSONObject()
        params.properties?.forEach { (k, schema) ->
            propsObj.put(k, propertySchemaToJson(schema))
        }
        obj.put("properties", propsObj)
        if (!params.required.isNullOrEmpty()) {
            obj.put("required", JSONArray(params.required))
        }
        return obj
    }

    private fun propertySchemaToJson(schema: PropertySchema): JSONObject {
        val obj = JSONObject()
        obj.put("type", schema.type)
        schema.description?.let { obj.put("description", it) }
        schema.items?.let { obj.put("items", propertySchemaToJson(it)) }
        schema.properties?.let { nestedProps ->
            val nestedObj = JSONObject()
            nestedProps.forEach { (k, v) -> nestedObj.put(k, propertySchemaToJson(v)) }
            obj.put("properties", nestedObj)
        }
        if (!schema.required.isNullOrEmpty()) {
            obj.put("required", JSONArray(schema.required))
        }
        return obj
    }

    suspend fun processAssistantPrompt(
        userPrompt: String,
        catalogSummary: String,
        suppliersSummary: String,
        customersSummary: String = "",
        bcvRate: Double,
        todaySalesSummary: String = "",
        minProfitMarginPercent: Double? = null,
        pendingActionSummary: String? = null,
        conversationHistory: List<Pair<String, String>> = emptyList(),
        onReadOnlyToolCall: (suspend (name: String, args: Map<String, Any>) -> Map<String, Any>?)? = null
    ): AssistantApiResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKeyOrNull()
            ?: return@withContext AssistantApiResponse.Error("No se ha configurado ninguna clave API de Gemini válida. Puedes ingresar una en Ajustes.")

        val model = getEffectiveModel()
        val systemInstructionText = buildSystemInstruction(
            catalogSummary = catalogSummary,
            suppliersSummary = suppliersSummary,
            customersSummary = customersSummary,
            bcvRate = bcvRate,
            todaySalesSummary = todaySalesSummary,
            minProfitMarginPercent = minProfitMarginPercent,
            pendingActionSummary = pendingActionSummary
        )

        val contents = mutableListOf<Content>()
        conversationHistory.takeLast(8).forEach { (role, text) ->
            if (text.isNotBlank()) {
                contents.add(Content(role = if (role == "USER") "user" else "model", parts = listOf(Part(text = text))))
            }
        }
        if (contents.lastOrNull()?.role != "user" || contents.lastOrNull()?.parts?.firstOrNull()?.text != userPrompt) {
            contents.add(Content(role = "user", parts = listOf(Part(text = userPrompt))))
        }

        try {
            // Up to 3 reasoning/tool-response steps for read-only lookups
            repeat(3) {
                val request = GenerateContentRequest(
                    contents = contents,
                    systemInstruction = Content(role = "system", parts = listOf(Part(text = systemInstructionText))),
                    tools = tools
                )
                val response = service.generateContent(
                    model = model,
                    apiKey = apiKey,
                    request = request
                )
                val candidate = response.candidates?.firstOrNull()
                val parts = candidate?.content?.parts.orEmpty()
                val functionCallPart = parts.firstOrNull { it.functionCall != null }

                if (functionCallPart?.functionCall != null) {
                    val call = functionCallPart.functionCall
                    val args = call.args ?: emptyMap()
                    val readResult = onReadOnlyToolCall?.invoke(call.name, args)
                    if (readResult != null) {
                        contents.add(Content(role = "model", parts = listOf(Part(functionCall = call))))
                        contents.add(
                            Content(
                                role = "user",
                                parts = listOf(
                                    Part(
                                        functionResponse = FunctionResponse(
                                            name = call.name,
                                            response = readResult
                                        )
                                    )
                                )
                            )
                        )
                    } else {
                        return@withContext AssistantApiResponse.Function(call.name, args)
                    }
                } else {
                    val reply = parts.mapNotNull { it.text }.joinToString("\n").trim()
                    if (reply.isBlank()) {
                        return@withContext AssistantApiResponse.Message("No entendí tu solicitud, puedes repetir tu solicitud.")
                    }
                    return@withContext AssistantApiResponse.Message(reply)
                }
            }
            return@withContext AssistantApiResponse.Message("No entendí tu solicitud, puedes repetir tu solicitud.")
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.localizedMessage ?: "Error desconocido"
            return@withContext AssistantApiResponse.Error("Error al conectar con Gemini ($model): $msg")
        }
    }

    suspend fun testGeminiConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        val keyToTest = apiKey.ifBlank { BuildConfig.GEMINI_API_KEY.trim() }
        if (keyToTest.isBlank() || keyToTest.equals("demo", ignoreCase = true)) {
            return@withContext Result.failure(Exception("Clave API de Gemini vacía o de demostración."))
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(role = "user", parts = listOf(Part(text = "Di exactamente 'OK_GEMINI_ONLINE' si estás activo.")))
            )
        )

        try {
            val response = service.generateContent(
                model = model.ifBlank { "gemini-3.5-flash" },
                apiKey = keyToTest,
                request = request
            )
            val text = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text.orEmpty()
            if (text.isNotBlank()) {
                Result.success("Conexión exitosa con $model.")
            } else {
                Result.failure(Exception("Respuesta vacía de Gemini."))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

sealed class AssistantApiResponse {
    data class Message(val text: String) : AssistantApiResponse()
    data class Function(val functionName: String, val args: Map<String, Any>) : AssistantApiResponse()
    data class Error(val errorMessage: String) : AssistantApiResponse()
}

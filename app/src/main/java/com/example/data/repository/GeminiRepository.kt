package com.example.data.repository

import com.example.BuildConfig
import com.example.data.local.PreferencesManager
import com.example.data.remote.*
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class GeminiRepository(private val prefs: PreferencesManager? = null) {
    private val service = RetrofitClient.geminiService

    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val tools = listOf(
        Tool(
            functionDeclarations = listOf(
                FunctionDeclaration(
                    name = "queryInventory",
                    description = "Consulta la información de stock, precios en USD/Bs y detalles de uno o varios productos en el inventario.",
                    parameters = FunctionParameters(
                        type = "OBJECT",
                        properties = mapOf(
                            "productName" to PropertySchema(type = "STRING", description = "Nombre o palabra clave del producto a consultar")
                        ),
                        required = listOf("productName")
                    )
                ),
                FunctionDeclaration(
                    name = "registerNewProduct",
                    description = "Prepara el registro de un nuevo producto en el inventario. Busca palabras clave como 'Nuevo producto', 'Cantidad', 'Precio de costo' y 'Precio de venta'.",
                    parameters = FunctionParameters(
                        type = "OBJECT",
                        properties = mapOf(
                            "name" to PropertySchema(type = "STRING", description = "Nombre descriptivo del producto"),
                            "category" to PropertySchema(type = "STRING", description = "Categoría del producto"),
                            "stock" to PropertySchema(type = "INTEGER", description = "Cantidad de existencias (usa la palabra clave 'Cantidad')"),
                            "costPriceUsd" to PropertySchema(type = "NUMBER", description = "Precio de costo en dólares (usa 'Precio de costo')"),
                            "priceUsd" to PropertySchema(type = "NUMBER", description = "Precio de venta en dólares (usa 'Precio de venta')"),
                            "isRecipe" to PropertySchema(type = "BOOLEAN", description = "True si es receta o servicio")
                        ),
                        required = listOf("name")
                    )
                ),
                FunctionDeclaration(
                    name = "addStock",
                    description = "Prepara la adición de existencias a un producto. Usa 'Cantidad' para las unidades.",
                    parameters = FunctionParameters(
                        type = "OBJECT",
                        properties = mapOf(
                            "productName" to PropertySchema(type = "STRING", description = "Nombre del producto"),
                            "quantity" to PropertySchema(type = "INTEGER", description = "Cantidad a sumar (usa 'Cantidad')"),
                            "costPriceUsd" to PropertySchema(type = "NUMBER", description = "Nuevo precio de costo opcional")
                        ),
                        required = listOf("productName", "quantity")
                    )
                ),
                FunctionDeclaration(
                    name = "registerCustomer",
                    description = "Registra un nuevo cliente en el sistema.",
                    parameters = FunctionParameters(
                        type = "OBJECT",
                        properties = mapOf(
                            "name" to PropertySchema(type = "STRING", description = "Nombre completo del cliente"),
                            "documentId" to PropertySchema(type = "STRING", description = "Cédula o RIF del cliente"),
                            "phone" to PropertySchema(type = "STRING", description = "Número de teléfono"),
                            "address" to PropertySchema(type = "STRING", description = "Dirección de habitación o negocio")
                        ),
                        required = listOf("name")
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
                            "invoiceNumber" to PropertySchema(type = "STRING", description = "Número o código de la factura del proveedor"),
                            "totalAmount" to PropertySchema(type = "NUMBER", description = "Monto total de la factura de compra en USD"),
                            "description" to PropertySchema(type = "STRING", description = "Descripción breve o detalle de la compra")
                        ),
                        required = listOf("supplierName", "invoiceNumber", "totalAmount")
                    )
                ),
                FunctionDeclaration(
                    name = "checkBcvRate",
                    description = "Consulta la tasa de cambio oficial del BCV (Bolívares por Dólar)."
                ),
                FunctionDeclaration(
                    name = "getDailySummary",
                    description = "Muestra el resumen de ventas del día actual (total en USD, Bs y cantidad de ventas)."
                )
            )
        )
    )

    private fun getEffectiveApiKey(): String {
        val customKey = prefs?.geminiCustomApiKey?.trim().orEmpty()
        if (customKey.isNotBlank()) return customKey
        return BuildConfig.GEMINI_API_KEY.trim()
    }

    fun getEffectiveModel(): String {
        val model = prefs?.geminiSelectedModel?.trim().orEmpty()
        if (model.isNotBlank()) return model
        return "gemini-3.5-flash"
    }

    suspend fun processAssistantPrompt(
        userPrompt: String,
        catalogSummary: String,
        suppliersSummary: String,
        bcvRate: Double,
        todaySalesSummary: String = ""
    ): AssistantApiResponse = withContext(Dispatchers.IO) {
        val apiKey = getEffectiveApiKey()
        if (apiKey.isBlank()) {
            return@withContext AssistantApiResponse.Error("No se ha configurado ninguna clave API de Gemini. Puedes ingresar una en Ajustes.")
        }

        val model = getEffectiveModel()

        val systemInstructionText = if (prefs?.isGeminiLearningAiEnabled == true) {
            """
                Eres el Asistente Inteligente de SmartPOS286, una plataforma integral de facturación con inteligencia artificial.
                Tu función es entender consultas de inventario, registro de productos, clientes, stock y facturas.
                Tasa actual del BCV: $bcvRate Bs/USD.
                
                RESUMEN DE VENTAS DE HOY:
                $todaySalesSummary
                
                PRODUCTOS ACTUALES EN INVENTARIO:
                $catalogSummary
                
                PROVEEDORES REGISTRADOS:
                $suppliersSummary
                
                REGLAS ESTRICTAS:
                1. Usa 'Nuevo producto' para registros. Extrae: Nombre, Cantidad (stock), Precio de costo, Precio de venta.
                2. Usa 'registerCustomer' para nuevos clientes.
                3. Si el usuario pide resúmenes, usa 'getDailySummary'.
                4. Si el usuario pide eliminar, usa 'deleteProduct'.
                5. Si no entiendes en absoluto la solicitud, responde exactamente: "No entendí tu solicitud, puedes repetir tu solicitud."
                6. Responde siempre en español, de forma clara, profesional y concisa.
            """.trimIndent()
        } else {
            """
                Eres el Asistente Inteligente de SmartPOS286, una plataforma integral de facturación con inteligencia artificial.
                Tu función es entender consultas de inventario, registro de productos, clientes, stock y facturas.
                Tasa actual del BCV: $bcvRate Bs/USD.
                
                RESUMEN DE VENTAS DE HOY:
                $todaySalesSummary
                
                PRODUCTOS ACTUALES EN INVENTARIO:
                $catalogSummary
                
                PROVEEDORES REGISTRADOS:
                $suppliersSummary
                
                REGLAS ESTRICTAS:
                1. Usa 'Nuevo producto' para registros. Extrae: Nombre, Cantidad (stock), Precio de costo, Precio de venta.
                2. Usa 'registerCustomer' para nuevos clientes.
                3. Si el usuario pide resúmenes, usa 'getDailySummary'.
                4. Si el usuario pide eliminar, usa 'deleteProduct'.
                5. Si no entiendes en absoluto la solicitud, responde exactamente: "No entendí tu solicitud, puedes repetir tu solicitud."
                6. Responde siempre en español, de forma clara, profesional y concisa.
            """.trimIndent()
        }

        val request = GenerateContentRequest(
            contents = listOf(Content(role = "user", parts = listOf(Part(text = userPrompt)))),
            systemInstruction = Content(role = "system", parts = listOf(Part(text = systemInstructionText))),
            tools = tools
        )

        try {
            val response = service.generateContent(
                model = model,
                apiKey = apiKey,
                request = request
            )
            val candidate = response.candidates?.firstOrNull()
            val part = candidate?.content?.parts?.firstOrNull()

            if (part?.functionCall != null) {
                val call = part.functionCall
                return@withContext AssistantApiResponse.Function(call.name, call.args ?: emptyMap())
            }

            val reply = part?.text?.trim()
            if (reply.isNullOrBlank()) {
                return@withContext AssistantApiResponse.Message("No entendí tu solicitud, puedes repetir tu solicitud.")
            }
            return@withContext AssistantApiResponse.Message(reply)
        } catch (e: Exception) {
            e.printStackTrace()
            val msg = e.localizedMessage ?: "Error desconocido"
            return@withContext AssistantApiResponse.Error("Error al conectar con Gemini ($model): $msg")
        }
    }

    suspend fun testGeminiConnection(apiKey: String, model: String): Result<String> = withContext(Dispatchers.IO) {
        val keyToTest = apiKey.ifBlank { BuildConfig.GEMINI_API_KEY.trim() }
        if (keyToTest.isBlank()) {
            return@withContext Result.failure(Exception("Clave API de Gemini vacía."))
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

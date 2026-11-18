#!/bin/bash
cat << 'INNER_EOF' > app/src/main/java/com/example/ui/PosViewModel.kt
package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.Customer
import com.example.data.local.Product
import com.example.data.local.Sale
import com.example.data.local.SaleItem
import com.example.data.local.User
import com.example.data.repository.AppRepository
import com.example.data.repository.GeminiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PosViewModel(
    private val repository: AppRepository,
    private val geminiRepository: GeminiRepository
) : ViewModel() {

    val prefs = repository.prefs

    val allProducts = repository.allProducts.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allCustomers = repository.allCustomers.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allSales = repository.allSales.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allSuppliers = repository.allSuppliers.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allPurchaseInvoices = repository.allPurchaseInvoices.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    val allUsers = repository.allUsers.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val _currentUser = MutableStateFlow<User?>(null)
    val currentUser = _currentUser.asStateFlow()

    private val _bcvRate = MutableStateFlow(prefs.lastBcvRate.toDouble())
    val bcvRate = _bcvRate.asStateFlow()

    private val _cart = MutableStateFlow<List<CartItem>>(emptyList())
    val cart = _cart.asStateFlow()

    private val _voiceStatus = MutableStateFlow("")
    val voiceStatus = _voiceStatus.asStateFlow()

    init {
        viewModelScope.launch {
            if (repository.getUsersCount() == 0) {
                repository.insertUser(User(username = "admin", pin = "1234", role = "ADMIN"))
                repository.insertUser(User(username = "supervisor", pin = "0000", role = "SUPERVISOR"))
                repository.insertUser(User(username = "caja", pin = "1111", role = "CASHIER"))
            }
        }
        refreshRate()
    }

    suspend fun login(username: String, pin: String): Boolean {
        val user = repository.getUserByUsername(username)
        if (user != null && user.pin == pin) {
            _currentUser.value = user
            return true
        }
        return false
    }

    fun logout() {
        _currentUser.value = null
    }

    fun updateUserPin(user: User, newPin: String) {
        viewModelScope.launch {
            repository.updateUser(user.copy(pin = newPin))
        }
    }

    fun refreshRate() {
        viewModelScope.launch {
            _bcvRate.value = repository.fetchCurrentBcvRate()
        }
    }

    fun addSupplier(name: String, documentId: String, phone: String, address: String) {
        viewModelScope.launch {
            repository.insertSupplier(com.example.data.local.Supplier(name = name, documentId = documentId, phone = phone, address = address))
        }
    }

    fun addPurchaseInvoice(supplierId: Int, invoiceNumber: String, totalAmount: Double, description: String) {
        viewModelScope.launch {
            repository.insertPurchaseInvoice(
                com.example.data.local.PurchaseInvoice(
                    supplierId = supplierId,
                    invoiceNumber = invoiceNumber,
                    timestamp = System.currentTimeMillis(),
                    totalAmount = totalAmount,
                    description = description
                )
            )
        }
    }

    fun getItemsForSale(saleId: Int): kotlinx.coroutines.flow.Flow<List<com.example.data.local.SaleItem>> {
        return repository.getItemsForSale(saleId)
    }

    fun getItemsForSales(saleIds: List<Int>): kotlinx.coroutines.flow.Flow<List<com.example.data.local.SaleItem>> {
        return repository.getItemsForSales(saleIds)
    }

    fun addProduct(code: String, name: String, category: String, priceUsd: Double, stock: Int) {
        viewModelScope.launch {
            repository.insertProduct(Product(code = code, name = name, category = category, priceUsd = priceUsd, stock = stock))
        }
    }
    
    fun updateProduct(product: Product) {
        viewModelScope.launch {
            repository.updateProduct(product)
        }
    }

    fun addCustomer(name: String, documentId: String, address: String, phone: String) {
        viewModelScope.launch {
            repository.insertCustomer(Customer(name = name, documentId = documentId, address = address, phone = phone))
        }
    }

    fun addToCart(product: Product) {
        val current = _cart.value.toMutableList()
        val existing = current.find { it.product.id == product.id }
        if (existing != null) {
            if (existing.quantity < product.stock) {
                current[current.indexOf(existing)] = existing.copy(quantity = existing.quantity + 1)
            }
        } else {
            if (product.stock > 0) {
                current.add(CartItem(product, 1))
            }
        }
        _cart.value = current
    }

    fun removeFromCart(product: Product) {
        val current = _cart.value.toMutableList()
        val existing = current.find { it.product.id == product.id }
        if (existing != null) {
            if (existing.quantity > 1) {
                current[current.indexOf(existing)] = existing.copy(quantity = existing.quantity - 1)
            } else {
                current.remove(existing)
            }
        }
        _cart.value = current
    }

    fun clearCart() {
        _cart.value = emptyList()
    }

    fun processSale(
        customerName: String,
        customerDocument: String,
        paymentCashUsd: Double,
        paymentMobileBs: Double,
        paymentCardBs: Double,
        onSuccess: (Sale) -> Unit
    ) {
        if (_cart.value.isEmpty()) return

        val totalUsd = _cart.value.sumOf { it.product.priceUsd * it.quantity }
        val totalBs = totalUsd * _bcvRate.value
        val sale = Sale(
            customerName = customerName,
            customerDocument = customerDocument,
            timestamp = System.currentTimeMillis(),
            rateBcv = _bcvRate.value,
            totalUsd = totalUsd,
            totalBs = totalBs,
            paymentCashUsd = paymentCashUsd,
            paymentMobileBs = paymentMobileBs,
            paymentCardBs = paymentCardBs
        )

        val items = _cart.value.map {
            SaleItem(
                saleId = 0,
                productId = it.product.id,
                productName = it.product.name,
                quantity = it.quantity,
                priceUsd = it.product.priceUsd
            )
        }

        viewModelScope.launch {
            repository.processSale(sale, items)
            clearCart()
            onSuccess(sale)
        }
    }

    fun processVoiceCommand(text: String) {
        _voiceStatus.value = "Procesando..."
        viewModelScope.launch {
            val response = geminiRepository.processVoiceCommand(text)
            if (response.startsWith("FUNCTION_CALL:")) {
                handleFunctionCall(response.removePrefix("FUNCTION_CALL:"))
            } else {
                _voiceStatus.value = response
            }
        }
    }

    private suspend fun handleFunctionCall(callString: String) {
        try {
            val parts = callString.split(":", limit = 2)
            val name = parts[0]
            val argsJson = parts[1]
            val args = com.squareup.moshi.Moshi.Builder().build().adapter(Map::class.java).fromJson(argsJson) as Map<String, Any>

            when (name) {
                "checkPrice" -> {
                    val pName = args["productName"] as? String ?: ""
                    val product = allProducts.value.find { it.name.contains(pName, ignoreCase = true) }
                    if (product != null) {
                        val bsPrice = product.priceUsd * _bcvRate.value
                        _voiceStatus.value = "El precio de ${product.name} es \$${product.priceUsd} o Bs. ${"%.2f".format(bsPrice)}."
                    } else {
                        _voiceStatus.value = "No encontré el producto $pName en el inventario."
                    }
                }
                "addProduct" -> {
                    val pName = args["name"] as? String ?: ""
                    val cat = args["category"] as? String ?: "General"
                    val price = (args["priceUsd"] as? Double) ?: 0.0
                    val stock = (args["stock"] as? Double)?.toInt() ?: 0
                    addProduct(code = "", name = pName, category = cat, priceUsd = price, stock = stock)
                    _voiceStatus.value = "Producto $pName agregado al inventario."
                }
                "checkRate" -> {
                    _voiceStatus.value = "La tasa actual del BCV es Bs. ${_bcvRate.value} por dólar."
                }
                "registerSale" -> {
                    val pName = args["productName"] as? String ?: ""
                    val qty = (args["quantity"] as? Double)?.toInt() ?: 1
                    val method = args["paymentMethod"] as? String ?: "cashUsd"
                    
                    val product = allProducts.value.find { it.name.contains(pName, ignoreCase = true) }
                    if (product != null && product.stock >= qty) {
                        val totalUsd = product.priceUsd * qty
                        val totalBs = totalUsd * _bcvRate.value
                        
                        val sale = Sale(
                            customerName = "Cliente por Voz",
                            customerDocument = "V-00000000",
                            timestamp = System.currentTimeMillis(),
                            rateBcv = _bcvRate.value,
                            totalUsd = totalUsd,
                            totalBs = totalBs,
                            paymentCashUsd = if (method == "cashUsd") totalUsd else 0.0,
                            paymentMobileBs = if (method == "mobileBs") totalBs else 0.0,
                            paymentCardBs = if (method == "cardBs") totalBs else 0.0
                        )

                        repository.processSale(sale, listOf(SaleItem(0, 0, product.id, product.name, qty, product.priceUsd)))
                        _voiceStatus.value = "Venta registrada para $qty x ${product.name}."
                    } else {
                        _voiceStatus.value = "No hay suficiente stock o no encontré el producto $pName."
                    }
                }
                else -> {
                    _voiceStatus.value = "Acción no soportada."
                }
            }
        } catch (e: Exception) {
            _voiceStatus.value = "Error ejecutando la acción."
        }
    }
}

data class CartItem(val product: Product, val quantity: Int)
INNER_EOF

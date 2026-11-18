package com.example.ui.assistant

import java.util.UUID

enum class AssistantSender {
    USER,
    ASSISTANT,
    SYSTEM
}

data class DisambiguationItem(
    val id: Int,
    val title: String,
    val subtitle: String,
    val category: String = "",
    val priceUsd: Double = 0.0,
    val stock: Double = 0.0
)

sealed class PendingAssistantAction {
    data class CreateProductAction(
        val name: String,
        val category: String,
        val stock: Double,
        val costPriceUsd: Double,
        val priceUsd: Double,
        val isRecipe: Boolean,
        val isTaxExempt: Boolean = false
    ) : PendingAssistantAction()

    data class AddStockAction(
        val productId: Int,
        val productName: String,
        val currentStock: Double,
        val quantityToAdd: Double,
        val newCostPriceUsd: Double? = null
    ) : PendingAssistantAction()

    data class CreatePurchaseInvoiceAction(
        val supplierId: Int,
        val supplierName: String,
        val invoiceNumber: String,
        val totalAmount: Double,
        val description: String
    ) : PendingAssistantAction()

    data class CreateCustomerAction(
        val name: String,
        val documentId: String,
        val phone: String,
        val address: String
    ) : PendingAssistantAction()

    data class DeleteProductAction(
        val productId: Int,
        val productName: String
    ) : PendingAssistantAction()

    data class SelectDisambiguation(
        val candidates: List<DisambiguationItem>,
        val actionIntent: String, // "ADD_STOCK" or "QUERY_PRODUCT"
        val quantity: Double = 0.0,
        val newCost: Double? = null
    ) : PendingAssistantAction()
}

data class AssistantChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val sender: AssistantSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val pendingAction: PendingAssistantAction? = null,
    val isConfirmedOrResolved: Boolean = false
)

package com.example.data.repository

import android.content.Context
import android.util.Log
import com.example.R
import com.example.data.local.*
import com.example.data.remote.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.tasks.await
import java.util.UUID

class FirestoreSyncRepository(
    private val localRepository: AppRepository,
    private val context: Context,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) {
    private val TAG = "FirestoreSync"
    private val databaseId: String = try {
        context.getString(R.string.firestore_database_id)
    } catch (_: Exception) {
        "ai-studio-android-cajapos-eb114155-a570-45fb-9f2f-7385db700fe0"
    }

    val firestore: FirebaseFirestore = FirebaseFirestore.getInstance(databaseId)

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var syncJob: Job? = null
    private val listenerRegistrations = mutableListOf<ListenerRegistration>()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastError = MutableStateFlow<String?>(null)
    val lastError: StateFlow<String?> = _lastError.asStateFlow()

    private val _companyProfile = MutableStateFlow<FsCompanyProfile?>(null)
    val companyProfile: StateFlow<FsCompanyProfile?> = _companyProfile.asStateFlow()

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    val currentUserId: String?
        get() = auth.currentUser?.uid

    private fun isDefaultPlaceholderCompany(name: String, rif: String): Boolean {
        val cleanName = name.trim()
        val cleanRif = rif.trim()
        val isDefaultName = cleanName.isBlank() || cleanName.equals("Mi Tienda", ignoreCase = true)
        val isDefaultRif = cleanRif.isBlank() ||
            cleanRif.equals("J-00000000-0", ignoreCase = true) ||
            cleanRif == "00000000"
        return isDefaultName && isDefaultRif
    }

    fun startSync() {
        stopSync()
        val uid = currentUserId
        if (uid.isNullOrBlank()) {
            Log.i(TAG, "Not starting Firestore sync: user not authenticated.")
            return
        }

        syncJob = scope.launch {
            _isSyncing.value = true
            try {
                Log.i(TAG, "Starting Firestore real-time sync for user: $uid")
                bootstrapInitialSync(uid)
                attachSnapshotListeners(uid)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                val err = FirestoreErrorHandler.handleFirestoreError(e, OperationType.GET, "users/$uid")
                _lastError.value = err
            } finally {
                _isSyncing.value = false
            }
        }
    }

    fun stopSync() {
        synchronized(listenerRegistrations) {
            listenerRegistrations.forEach {
                try { it.remove() } catch (_: Exception) {}
            }
            listenerRegistrations.clear()
        }
        syncJob?.cancel()
        syncJob = null
        _isSyncing.value = false
    }

    private fun applyRemoteCompanyToLocalIfValid(p: FsCompanyProfile, uid: String) {
        if (isDefaultPlaceholderCompany(p.storeName, p.storeRif)) {
            return
        }
        val prefs = localRepository.prefs
        if (p.storeName.isNotBlank() && !p.storeName.equals("Mi Tienda", ignoreCase = true)) {
            prefs.storeName = p.storeName
        }
        if (p.storeRif.isNotBlank() && !p.storeRif.equals("J-00000000-0", ignoreCase = true) && p.storeRif != "00000000") {
            prefs.storeRif = p.storeRif
        }
        if (p.storePhone.isNotBlank() && p.storePhone != "0000-0000000") {
            prefs.storePhone = p.storePhone
        }
        if (p.storeAddress.isNotBlank() && p.storeAddress != "Dirección") {
            prefs.storeAddress = p.storeAddress
        }
        if (p.googleEmail.isNotBlank()) {
            prefs.companyGoogleAccount = p.googleEmail
        }
        if (p.bcvRate > 0) {
            prefs.lastBcvRate = p.bcvRate.toFloat()
        }
        prefs.isCompanyRegistered = true
        _companyProfile.value = p
    }

    private suspend fun bootstrapInitialSync(uid: String) {
        try {
            val userRef = firestore.collection("users").document(uid)
            val doc = userRef.get().await()
            if (!doc.exists()) {
                val acc = FsUserAccount(
                    userId = uid,
                    email = auth.currentUser?.email ?: "",
                    displayName = auth.currentUser?.displayName ?: "Administrador"
                )
                userRef.set(acc.toCreateMap(uid)).await()
            }

            val prefs = localRepository.prefs
            val hasLocalRealCompany = prefs.isCompanyRegistered &&
                !isDefaultPlaceholderCompany(prefs.storeName, prefs.storeRif)

            val profileRef = userRef.collection("company_profile").document("main")
            val profileSnap = profileRef.get().await()

            if (!profileSnap.exists()) {
                if (hasLocalRealCompany) {
                    val profile = FsCompanyProfile(
                        userId = uid,
                        googleEmail = auth.currentUser?.email ?: "",
                        storeName = prefs.storeName,
                        storeRif = prefs.storeRif,
                        storePhone = prefs.storePhone,
                        storeAddress = prefs.storeAddress,
                        bcvRate = maxOf(1.0, prefs.lastBcvRate.toDouble()),
                        minProfitMarginEnabled = prefs.isMinProfitMarginEnabled,
                        minProfitMarginPercent = maxOf(0.0, prefs.minProfitMarginPercent.toDouble()),
                        cashRegisterCommissionEnabled = prefs.isCashRegisterCommissionEnabled,
                        commissionEnabledRegisters = prefs.commissionEnabledRegisters
                    )
                    profileRef.set(profile.toCreateMap(uid)).await()
                } else {
                    val emptyProfile = FsCompanyProfile(
                        userId = uid,
                        googleEmail = auth.currentUser?.email ?: "",
                        storeName = "",
                        storeRif = "",
                        storePhone = "",
                        storeAddress = "",
                        bcvRate = maxOf(1.0, prefs.lastBcvRate.toDouble()),
                        minProfitMarginEnabled = prefs.isMinProfitMarginEnabled,
                        minProfitMarginPercent = maxOf(0.0, prefs.minProfitMarginPercent.toDouble()),
                        cashRegisterCommissionEnabled = prefs.isCashRegisterCommissionEnabled,
                        commissionEnabledRegisters = prefs.commissionEnabledRegisters
                    )
                    profileRef.set(emptyProfile.toCreateMap(uid)).await()
                }
            } else {
                val p = profileSnap.toObject(FsCompanyProfile::class.java)
                if (p != null && !isDefaultPlaceholderCompany(p.storeName, p.storeRif)) {
                    applyRemoteCompanyToLocalIfValid(p, uid)
                } else if (hasLocalRealCompany) {
                    // Remote only has blank or default "Mi Tienda" / "J-00000000-0": push local real company up
                    syncCompanyProfile(
                        storeName = prefs.storeName,
                        storeRif = prefs.storeRif,
                        storePhone = prefs.storePhone,
                        storeAddress = prefs.storeAddress
                    )
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.w(TAG, "Bootstrap sync error: ${e.message}")
        }
    }

    private fun attachSnapshotListeners(uid: String) {
        val userRef = firestore.collection("users").document(uid)

        // Company profile real-time listener
        val profileListener = userRef.collection("company_profile").document("main")
            .addSnapshotListener { snapshot, error ->
                if (error != null) return@addSnapshotListener
                if (snapshot != null && snapshot.exists()) {
                    val p = snapshot.toObject(FsCompanyProfile::class.java)
                    if (p != null && !isDefaultPlaceholderCompany(p.storeName, p.storeRif)) {
                        applyRemoteCompanyToLocalIfValid(p, uid)
                    }
                }
            }

        // Products snapshot listener
        val productsListener = userRef.collection("products").addSnapshotListener { snapshot, error ->
            if (error != null) {
                _lastError.value = FirestoreErrorHandler.handleFirestoreError(error, OperationType.LIST, "users/$uid/products")
                return@addSnapshotListener
            }
            if (snapshot != null) {
                scope.launch {
                    try {
                        val localProducts = localRepository.allProducts.first()
                        for (doc in snapshot.documents) {
                            val fsProd = doc.toObject(FsProduct::class.java) ?: continue
                            val existing = localProducts.find { it.code == fsProd.code }
                            if (existing == null) {
                                val newProd = Product(
                                    code = fsProd.code,
                                    name = fsProd.name,
                                    category = fsProd.category,
                                    priceUsd = fsProd.priceUsd,
                                    stock = fsProd.stock,
                                    costPriceUsd = fsProd.costPriceUsd,
                                    invoiceNumber = fsProd.invoiceNumber,
                                    isTaxExempt = fsProd.taxExempt,
                                    hasCommission = fsProd.hasCommission,
                                    commissionPercentage = fsProd.commissionPercentage,
                                    commissionAmountUsd = fsProd.commissionAmountUsd,
                                    isRecipe = fsProd.recipe,
                                    isCombo = fsProd.combo,
                                    comboItemsJson = fsProd.comboItemsJson,
                                    allowDecimalQuantity = fsProd.allowDecimalQuantity,
                                    isPriceModifiable = fsProd.priceModifiable,
                                    wholesalePriceUsd = fsProd.wholesalePriceUsd,
                                    aisleNumber = fsProd.aisleNumber,
                                    aisleQuantity = fsProd.aisleQuantity,
                                    depotQuantity = fsProd.depotQuantity,
                                    counterQuantity = fsProd.counterQuantity
                                )
                                localRepository.insertProduct(newProd)
                            } else {
                                val updated = existing.copy(
                                    name = fsProd.name,
                                    category = fsProd.category,
                                    priceUsd = fsProd.priceUsd,
                                    stock = fsProd.stock,
                                    costPriceUsd = fsProd.costPriceUsd,
                                    invoiceNumber = fsProd.invoiceNumber,
                                    isTaxExempt = fsProd.taxExempt,
                                    hasCommission = fsProd.hasCommission,
                                    commissionPercentage = fsProd.commissionPercentage,
                                    commissionAmountUsd = fsProd.commissionAmountUsd,
                                    isRecipe = fsProd.recipe,
                                    isCombo = fsProd.combo,
                                    comboItemsJson = fsProd.comboItemsJson,
                                    allowDecimalQuantity = fsProd.allowDecimalQuantity,
                                    isPriceModifiable = fsProd.priceModifiable,
                                    wholesalePriceUsd = fsProd.wholesalePriceUsd,
                                    aisleNumber = fsProd.aisleNumber,
                                    aisleQuantity = fsProd.aisleQuantity,
                                    depotQuantity = fsProd.depotQuantity,
                                    counterQuantity = fsProd.counterQuantity
                                )
                                localRepository.updateProduct(updated)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing product snapshot: ${e.message}")
                    }
                }
            }
        }

        // Sales snapshot listener
        val salesListener = userRef.collection("sales").addSnapshotListener { snapshot, error ->
            if (error != null) return@addSnapshotListener
            if (snapshot != null) {
                scope.launch {
                    try {
                        val localSales = localRepository.allSales.first()
                        for (doc in snapshot.documents) {
                            val fsSale = doc.toObject(FsSale::class.java) ?: continue
                            val existing = localSales.find { it.timestamp == fsSale.timestampMs }
                            if (existing == null) {
                                val sale = Sale(
                                    customerName = fsSale.customerName,
                                    customerDocument = fsSale.customerDocument,
                                    timestamp = fsSale.timestampMs,
                                    rateBcv = fsSale.rateBcv,
                                    totalUsd = fsSale.totalUsd,
                                    totalBs = fsSale.totalBs,
                                    paymentCashUsd = fsSale.paymentCashUsd,
                                    paymentMobileBs = fsSale.paymentMobileBs,
                                    paymentCardBs = fsSale.paymentCardBs,
                                    paymentCreditUsd = fsSale.paymentCreditUsd,
                                    paymentCasheaUsd = fsSale.paymentCasheaUsd,
                                    isCreditPaid = fsSale.creditPaid,
                                    creditPaidAmountUsd = fsSale.creditPaidAmountUsd,
                                    creditDueDate = fsSale.creditDueDate,
                                    creditNotes = fsSale.creditNotes,
                                    igtfUsd = fsSale.igtfUsd,
                                    igtfBs = fsSale.igtfBs,
                                    baseTaxableUsd = fsSale.baseTaxableUsd,
                                    baseExemptUsd = fsSale.baseExemptUsd,
                                    ivaUsd = fsSale.ivaUsd,
                                    discountUsd = fsSale.discountUsd,
                                    cashierName = fsSale.cashierName,
                                    cashRegisterNumber = fsSale.cashRegisterNumber,
                                    totalCommissionUsd = fsSale.totalCommissionUsd,
                                    retencionIvaUsd = fsSale.retencionIvaUsd,
                                    retencionIvaPercentage = fsSale.retencionIvaPercentage,
                                    retencionIslrUsd = fsSale.retencionIslrUsd,
                                    retencionIslrPercentage = fsSale.retencionIslrPercentage,
                                    withholdingVoucherNumber = fsSale.withholdingVoucherNumber,
                                    withholdingStatus = fsSale.withholdingStatus
                                )
                                localRepository.insertSale(sale)
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error processing sales snapshot: ${e.message}")
                    }
                }
            }
        }

        synchronized(listenerRegistrations) {
            listenerRegistrations.add(profileListener)
            listenerRegistrations.add(productsListener)
            listenerRegistrations.add(salesListener)
        }
    }

    suspend fun syncCompanyProfile(
        storeName: String,
        storeRif: String,
        storePhone: String,
        storeAddress: String
    ): Result<Unit> {
        val uid = currentUserId ?: return Result.success(Unit)
        val prefs = localRepository.prefs
        return try {
            val userRef = firestore.collection("users").document(uid)
            val userDoc = userRef.get().await()
            if (!userDoc.exists()) {
                val acc = FsUserAccount(
                    userId = uid,
                    email = auth.currentUser?.email ?: "",
                    displayName = auth.currentUser?.displayName ?: "Administrador"
                )
                userRef.set(acc.toCreateMap(uid)).await()
            }

            val profileRef = userRef.collection("company_profile").document("main")
            val snap = profileRef.get().await()
            val email = auth.currentUser?.email ?: prefs.googleAccount
            val bcv = maxOf(1.0, prefs.lastBcvRate.toDouble())
            val minMargin = maxOf(0.0, prefs.minProfitMarginPercent.toDouble())

            if (!snap.exists()) {
                val profile = FsCompanyProfile(
                    userId = uid,
                    googleEmail = email,
                    storeName = storeName.take(120),
                    storeRif = storeRif.take(40),
                    storePhone = storePhone.take(40),
                    storeAddress = storeAddress.take(300),
                    bcvRate = bcv,
                    minProfitMarginEnabled = prefs.isMinProfitMarginEnabled,
                    minProfitMarginPercent = minMargin,
                    cashRegisterCommissionEnabled = prefs.isCashRegisterCommissionEnabled,
                    commissionEnabledRegisters = prefs.commissionEnabledRegisters.take(200)
                )
                profileRef.set(profile.toCreateMap(uid)).await()
            } else {
                profileRef.update(
                    mapOf(
                        "googleEmail" to email.take(200),
                        "storeName" to storeName.take(120),
                        "storeRif" to storeRif.take(40),
                        "storePhone" to storePhone.take(40),
                        "storeAddress" to storeAddress.take(300),
                        "bcvRate" to bcv,
                        "minProfitMarginEnabled" to prefs.isMinProfitMarginEnabled,
                        "minProfitMarginPercent" to minMargin,
                        "cashRegisterCommissionEnabled" to prefs.isCashRegisterCommissionEnabled,
                        "commissionEnabledRegisters" to prefs.commissionEnabledRegisters.take(200),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val err = FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, "users/$uid/company_profile/main")
            Result.failure(Exception(err))
        }
    }

    suspend fun syncProductCreateOrUpdate(product: Product): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Usuario no autenticado."))
        return try {
            val docId = product.code.ifBlank { UUID.randomUUID().toString() }
            val ref = firestore.collection("users").document(uid).collection("products").document(docId)
            val fsProd = FsProduct(
                userId = uid,
                code = product.code,
                name = product.name,
                category = product.category,
                priceUsd = product.priceUsd,
                stock = product.stock,
                costPriceUsd = product.costPriceUsd,
                invoiceNumber = product.invoiceNumber,
                taxExempt = product.isTaxExempt,
                hasCommission = product.hasCommission,
                commissionPercentage = product.commissionPercentage,
                commissionAmountUsd = product.commissionAmountUsd,
                recipe = product.isRecipe,
                combo = product.isCombo,
                comboItemsJson = product.comboItemsJson,
                allowDecimalQuantity = product.allowDecimalQuantity,
                priceModifiable = product.isPriceModifiable,
                wholesalePriceUsd = product.wholesalePriceUsd,
                aisleNumber = product.aisleNumber,
                aisleQuantity = product.aisleQuantity,
                depotQuantity = product.depotQuantity,
                counterQuantity = product.counterQuantity
            )
            ref.set(fsProd.toCreateMap(uid)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val err = FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, "products")
            Result.failure(Exception(err))
        }
    }

    suspend fun recordSaleAtomically(sale: Sale, items: List<SaleItem>): Result<Unit> {
        val uid = currentUserId ?: return Result.failure(Exception("Usuario no autenticado."))
        val saleCloudId = UUID.randomUUID().toString()
        val userRef = firestore.collection("users").document(uid)
        val saleRef = userRef.collection("sales").document(saleCloudId)

        return try {
            firestore.runTransaction { tx ->
                for (item in items) {
                    val prodRef = userRef.collection("products").document(item.productId.toString())
                    val prodSnap = tx.get(prodRef)
                    if (prodSnap.exists()) {
                        val currentStock = prodSnap.getDouble("stock") ?: 0.0
                        val newStock = maxOf(0.0, currentStock - item.quantity)
                        tx.update(prodRef, mapOf("stock" to newStock, "updatedAt" to FieldValue.serverTimestamp()))
                    }
                }

                val fsSale = FsSale(
                    userId = uid,
                    customerName = sale.customerName,
                    customerDocument = sale.customerDocument,
                    timestampMs = sale.timestamp,
                    rateBcv = sale.rateBcv,
                    totalUsd = sale.totalUsd,
                    totalBs = sale.totalBs,
                    paymentCashUsd = sale.paymentCashUsd,
                    paymentMobileBs = sale.paymentMobileBs,
                    paymentCardBs = sale.paymentCardBs,
                    paymentCreditUsd = sale.paymentCreditUsd,
                    paymentCasheaUsd = sale.paymentCasheaUsd,
                    creditPaid = sale.isCreditPaid,
                    creditPaidAmountUsd = sale.creditPaidAmountUsd,
                    creditDueDate = sale.creditDueDate,
                    creditNotes = sale.creditNotes,
                    igtfUsd = sale.igtfUsd,
                    igtfBs = sale.igtfBs,
                    baseTaxableUsd = sale.baseTaxableUsd,
                    baseExemptUsd = sale.baseExemptUsd,
                    ivaUsd = sale.ivaUsd,
                    discountUsd = sale.discountUsd,
                    cashierName = sale.cashierName,
                    cashRegisterNumber = sale.cashRegisterNumber,
                    totalCommissionUsd = sale.totalCommissionUsd,
                    retencionIvaUsd = sale.retencionIvaUsd,
                    retencionIvaPercentage = sale.retencionIvaPercentage,
                    retencionIslrUsd = sale.retencionIslrUsd,
                    retencionIslrPercentage = sale.retencionIslrPercentage,
                    withholdingVoucherNumber = sale.withholdingVoucherNumber,
                    withholdingStatus = sale.withholdingStatus,
                    deviceId = localRepository.prefs.deviceUniqueId
                )
                tx.set(saleRef, fsSale.toCreateMap(uid))

                for (item in items) {
                    val itemCloudId = UUID.randomUUID().toString()
                    val itemRef = userRef.collection("sale_items").document(itemCloudId)
                    val fsItem = FsSaleItem(
                        userId = uid,
                        saleCloudId = saleCloudId,
                        productCloudId = item.productId.toString(),
                        productName = item.productName,
                        quantity = item.quantity,
                        priceUsd = item.priceUsd,
                        taxExempt = item.isTaxExempt,
                        commissionUsd = item.commissionUsd
                    )
                    tx.set(itemRef, fsItem.toCreateMap(uid))
                }
            }.await()

            Result.success(Unit)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val err = FirestoreErrorHandler.handleFirestoreError(e, OperationType.WRITE, "sales/$saleCloudId")
            Result.failure(Exception(err))
        }
    }
}

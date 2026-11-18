package com.example.utils

import android.content.Context
import android.net.Uri
import com.example.data.local.*
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Data structure containing complete POS, inventory, billing, expenses, and user records.
 */
@JsonClass(generateAdapter = true)
data class EncryptedPosBackupPackage(
    val formatVersion: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val storeName: String = "",
    val storeRif: String = "",
    val products: List<Product> = emptyList(),
    val customers: List<Customer> = emptyList(),
    val sales: List<Sale> = emptyList(),
    val saleItems: List<SaleItem> = emptyList(),
    val suppliers: List<Supplier> = emptyList(),
    val purchaseInvoices: List<PurchaseInvoice> = emptyList(),
    val deliveryNotes: List<DeliveryNote> = emptyList(),
    val deliveryNoteItems: List<DeliveryNoteItem> = emptyList(),
    val expenses: List<Expense> = emptyList(),
    val users: List<User> = emptyList()
)

data class ImportBackupResult(
    val success: Boolean,
    val message: String,
    val productsCount: Int = 0,
    val customersCount: Int = 0,
    val salesCount: Int = 0,
    val invoicesCount: Int = 0,
    val deliveryNotesCount: Int = 0,
    val expensesCount: Int = 0
)

class SecureEncryptedBackupManager(
    private val context: Context,
    private val database: AppDatabase
) {
    private val moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    companion object {
        private const val CIPHER_ALGO = "AES/GCM/NoPadding"
        private const val TAG_LENGTH_BITS = 128
        private const val IV_LENGTH_BYTES = 12
        private const val SALT_LENGTH_BYTES = 16
        private const val PBKDF2_ITERATIONS = 65536
        private const val KEY_LENGTH_BITS = 256
        
        // System master secret for seamless cross-device interoperability between app installations
        const val DEFAULT_SYSTEM_PASSPHRASE = "CAJA_POS_SECURE_MASTER_SYNC_KEY_2026_V1"
        private val FILE_MAGIC_HEADER = "CAJAPOS_ENC_JSON_V1".toByteArray(StandardCharsets.UTF_8)
    }

    private fun deriveAesKey(passphrase: String, salt: ByteArray): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(passphrase.toCharArray(), salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /**
     * Gathers all local database entities and serializes them into an encrypted, tamper-evident binary JSON package.
     */
    suspend fun exportEncryptedDataToUri(
        uri: Uri,
        storeName: String = "",
        storeRif: String = "",
        customPassphrase: String? = null
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            val products = database.productDao().getAllProducts().first()
            val customers = database.customerDao().getAllCustomers().first()
            val sales = database.saleDao().getAllSales().first()
            val saleItems = database.saleDao().getAllSaleItems().first()
            val suppliers = database.supplierDao().getAllSuppliers().first()
            val purchaseInvoices = database.purchaseInvoiceDao().getAllInvoices().first()
            val deliveryNotes = database.deliveryNoteDao().getAllDeliveryNotes().first()
            val deliveryNoteItems = database.deliveryNoteDao().getAllDeliveryNotes().first().flatMap { 
                database.deliveryNoteDao().getItemsForDeliveryNoteDirect(it.id)
            }
            val expenses = database.expenseDao().getAllExpenses().first()
            val users = database.userDao().getAllUsers().first()

            val backupPackage = EncryptedPosBackupPackage(
                formatVersion = 1,
                exportedAt = System.currentTimeMillis(),
                storeName = storeName,
                storeRif = storeRif,
                products = products,
                customers = customers,
                sales = sales,
                saleItems = saleItems,
                suppliers = suppliers,
                purchaseInvoices = purchaseInvoices,
                deliveryNotes = deliveryNotes,
                deliveryNoteItems = deliveryNoteItems,
                expenses = expenses,
                users = users
            )

            val jsonString = moshi.adapter(EncryptedPosBackupPackage::class.java).toJson(backupPackage)
            val plainBytes = jsonString.toByteArray(StandardCharsets.UTF_8)

            // Setup crypto parameters
            val random = SecureRandom()
            val salt = ByteArray(SALT_LENGTH_BYTES).apply { random.nextBytes(this) }
            val iv = ByteArray(IV_LENGTH_BYTES).apply { random.nextBytes(this) }

            val effectivePassphrase = customPassphrase?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PASSPHRASE
            val secretKey = deriveAesKey(effectivePassphrase, salt)

            val cipher = Cipher.getInstance(CIPHER_ALGO)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, gcmSpec)
            // Bind header as Authenticated Additional Data (AAD) to prevent tampering
            cipher.updateAAD(FILE_MAGIC_HEADER)

            val ciphertextWithTag = cipher.doFinal(plainBytes)

            context.contentResolver.openOutputStream(uri)?.use { os ->
                os.write(FILE_MAGIC_HEADER)
                os.write(salt)
                os.write(iv)
                os.write(ciphertextWithTag)
                os.flush()
            } ?: return@withContext Result.failure(Exception("No se pudo abrir el archivo de destino para escritura."))

            val summaryMsg = "Exportación encriptada con éxito: ${products.size} productos, ${sales.size} ventas, ${purchaseInvoices.size} facturas y ${customers.size} clientes protegidos con AES-256 GCM."
            Result.success(summaryMsg)
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    /**
     * Reads, verifies the cryptographic authenticity, decrypts, and optionally appends/merges
     * or replaces data into the local Room database.
     */
    suspend fun importAndProcessEncryptedUri(
        uri: Uri,
        appendMode: Boolean = true,
        customPassphrase: String? = null
    ): ImportBackupResult = withContext(Dispatchers.IO) {
        try {
            val inputStream: InputStream = context.contentResolver.openInputStream(uri)
                ?: return@withContext ImportBackupResult(false, "No se pudo abrir el archivo seleccionado.")

            val fileBytes = inputStream.use { it.readBytes() }
            val headerLen = FILE_MAGIC_HEADER.size
            val minLength = headerLen + SALT_LENGTH_BYTES + IV_LENGTH_BYTES + 16 // Header + Salt + IV + GCM Auth Tag

            if (fileBytes.size < minLength) {
                return@withContext ImportBackupResult(false, "El archivo es inválido o no corresponde a una copia encriptada de la aplicación.")
            }

            val buffer = ByteBuffer.wrap(fileBytes)

            // 1. Verify Magic Header
            val header = ByteArray(headerLen)
            buffer.get(header)
            if (!header.contentEquals(FILE_MAGIC_HEADER)) {
                return@withContext ImportBackupResult(false, "Formato de archivo no reconocido. Debe ser un paquete JSON encriptado (.enc o .cajapos).")
            }

            // 2. Extract Salt and IV
            val salt = ByteArray(SALT_LENGTH_BYTES)
            buffer.get(salt)

            val iv = ByteArray(IV_LENGTH_BYTES)
            buffer.get(iv)

            // 3. Extract Ciphertext and Auth Tag
            val cipherBytes = ByteArray(buffer.remaining())
            buffer.get(cipherBytes)

            // 4. Decrypt & Authenticate Integrity
            val effectivePassphrase = customPassphrase?.takeIf { it.isNotBlank() } ?: DEFAULT_SYSTEM_PASSPHRASE
            val secretKey = deriveAesKey(effectivePassphrase, salt)

            val cipher = Cipher.getInstance(CIPHER_ALGO)
            val gcmSpec = GCMParameterSpec(TAG_LENGTH_BITS, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, gcmSpec)
            cipher.updateAAD(FILE_MAGIC_HEADER)

            val plainBytes = try {
                cipher.doFinal(cipherBytes)
            } catch (e: Exception) {
                // If the file was tampered with or modified outside, GCM auth tag fails!
                return@withContext ImportBackupResult(
                    false,
                    "Error de integridad: El archivo está dañado, fue modificado externamente o la clave de desencriptación es incorrecta."
                )
            }

            val jsonString = String(plainBytes, StandardCharsets.UTF_8)
            val backupPackage = moshi.adapter(EncryptedPosBackupPackage::class.java).fromJson(jsonString)
                ?: return@withContext ImportBackupResult(false, "Error al decodificar la estructura JSON de la copia.")

            // 5. Apply to database (Merge / Anexar or Overwrite)
            if (!appendMode) {
                database.productDao().deleteAllProducts()
            }

            // Products: Upsert / Insert
            for (p in backupPackage.products) {
                val existing = if (p.code.isNotBlank()) database.productDao().getProductByCode(p.code) else null
                if (existing != null) {
                    if (appendMode) {
                        // Merge stock if appending
                        val mergedStock = existing.stock + p.stock
                        database.productDao().updateProduct(existing.copy(stock = mergedStock))
                    } else {
                        database.productDao().updateProduct(p.copy(id = existing.id))
                    }
                } else {
                    database.productDao().insertProduct(p.copy(id = 0))
                }
            }

            // Customers
            for (c in backupPackage.customers) {
                val existing = if (c.documentId.isNotBlank()) database.customerDao().getCustomerByDocument(c.documentId) else null
                if (existing == null) {
                    database.customerDao().insertCustomer(c.copy(id = 0))
                }
            }

            // Suppliers
            for (s in backupPackage.suppliers) {
                database.supplierDao().insertSupplier(s.copy(id = 0))
            }

            // Sales & Sale Items
            for (s in backupPackage.sales) {
                val newSaleId = database.saleDao().insertSale(s.copy(id = 0))
                val relatedItems = backupPackage.saleItems.filter { it.saleId == s.id }
                if (relatedItems.isNotEmpty()) {
                    val itemsToInsert = relatedItems.map { it.copy(id = 0, saleId = newSaleId.toInt()) }
                    database.saleDao().insertSaleItems(itemsToInsert)
                }
            }

            // Purchase Invoices
            for (inv in backupPackage.purchaseInvoices) {
                database.purchaseInvoiceDao().insertInvoice(inv.copy(id = 0))
            }

            // Delivery Notes
            for (dn in backupPackage.deliveryNotes) {
                val newDnId = database.deliveryNoteDao().insertDeliveryNote(dn.copy(id = 0))
                val relatedItems = backupPackage.deliveryNoteItems.filter { it.deliveryNoteId == dn.id }
                if (relatedItems.isNotEmpty()) {
                    val itemsToInsert = relatedItems.map { it.copy(id = 0, deliveryNoteId = newDnId.toInt()) }
                    database.deliveryNoteDao().insertDeliveryNoteItems(itemsToInsert)
                }
            }

            // Expenses
            for (exp in backupPackage.expenses) {
                database.expenseDao().insertExpense(exp.copy(id = 0))
            }

            // Users (Preserve existing, append non-existing usernames)
            for (u in backupPackage.users) {
                val existing = database.userDao().getUserByUsername(u.username)
                if (existing == null && !u.username.equals("Olivtech", ignoreCase = true) && !u.username.equals("default", ignoreCase = true)) {
                    database.userDao().insertUser(u.copy(id = 0))
                }
            }

            ImportBackupResult(
                success = true,
                message = "Datos desencriptados y anexados exitosamente: ${backupPackage.products.size} productos, ${backupPackage.sales.size} ventas y ${backupPackage.purchaseInvoices.size} facturas.",
                productsCount = backupPackage.products.size,
                customersCount = backupPackage.customers.size,
                salesCount = backupPackage.sales.size,
                invoicesCount = backupPackage.purchaseInvoices.size,
                deliveryNotesCount = backupPackage.deliveryNotes.size,
                expensesCount = backupPackage.expenses.size
            )
        } catch (e: Exception) {
            e.printStackTrace()
            ImportBackupResult(false, "Fallo durante la importación: ${e.message}")
        }
    }
}

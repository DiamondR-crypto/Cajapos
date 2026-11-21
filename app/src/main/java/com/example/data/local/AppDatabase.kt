package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        Product::class,
        Customer::class,
        Sale::class,
        SaleItem::class,
        Supplier::class,
        PurchaseInvoice::class,
        User::class,
        HeldSale::class,
        HeldSaleItem::class,
        DeliveryNote::class,
        DeliveryNoteItem::class,
        Expense::class,
        Quote::class,
        QuoteItem::class
    ],
    version = 23,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun productDao(): ProductDao
    abstract fun customerDao(): CustomerDao
    abstract fun saleDao(): SaleDao
    abstract fun supplierDao(): SupplierDao
    abstract fun purchaseInvoiceDao(): PurchaseInvoiceDao
    abstract fun userDao(): UserDao
    abstract fun heldSaleDao(): HeldSaleDao
    abstract fun deliveryNoteDao(): DeliveryNoteDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun quoteDao(): QuoteDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

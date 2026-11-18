package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

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
        Expense::class
    ],
    version = 19,
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

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_18_19 = object : Migration(18, 19) {
            override fun migrate(db: SupportSQLiteDatabase) {
                try {
                    db.execSQL("ALTER TABLE products ADD COLUMN wholesalePriceUsd REAL NOT NULL DEFAULT 0.0")
                } catch (_: Throwable) {}
            }
        }

        private val allMigrations: Array<Migration> by lazy {
            val list = mutableListOf<Migration>()
            for (from in 1..18) {
                list.add(object : Migration(from, 19) {
                    override fun migrate(db: SupportSQLiteDatabase) {
                        try {
                            db.execSQL("ALTER TABLE products ADD COLUMN allowDecimalQuantity INTEGER NOT NULL DEFAULT 0")
                        } catch (_: Throwable) {}
                        try {
                            db.execSQL("ALTER TABLE products ADD COLUMN isPriceModifiable INTEGER NOT NULL DEFAULT 0")
                        } catch (_: Throwable) {}
                        try {
                            db.execSQL("ALTER TABLE products ADD COLUMN wholesalePriceUsd REAL NOT NULL DEFAULT 0.0")
                        } catch (_: Throwable) {}
                    }
                })
            }
            list.toTypedArray()
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "pos_database"
                )
                .addMigrations(*allMigrations)
                .fallbackToDestructiveMigration(dropAllTables = true)
                .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}


package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.PreferencesManager
import com.example.data.local.Product
import com.example.data.local.Sale
import com.example.ui.dashboard.calculateInventoryValuation
import com.example.ui.summary.DailyClosurePdfGenerator
import com.example.utils.AccountingLedgerCsvExporter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DashboardAndCasheaTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var prefs: PreferencesManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        prefs = PreferencesManager(context).apply {
            storeName = "Abastos Central"
            storeRif = "J-98765432-1"
            lastBcvRate = 50.0f
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun calculateInventoryValuation_computesCapitalAndRetailIgnoringCombosAndZeroStock() {
        val products = listOf(
            Product(id = 1, code = "001", name = "Arroz", category = "Víveres", priceUsd = 2.0, costPriceUsd = 1.25, stock = 10.0),
            Product(id = 2, code = "002", name = "Refresco", category = "Bebidas", priceUsd = 1.5, costPriceUsd = 0.90, stock = 20.0),
            Product(id = 3, code = "003", name = "Agotado", category = "Víveres", priceUsd = 5.0, costPriceUsd = 3.0, stock = 0.0),
            Product(id = 4, code = "004", name = "Combo Desayuno", category = "Combos", priceUsd = 10.0, costPriceUsd = 6.0, stock = 5.0, isCombo = true),
            Product(id = 5, code = "005", name = "Café Preparado", category = "Cafetería", priceUsd = 2.5, costPriceUsd = 0.8, stock = 100.0, isRecipe = true)
        )

        val valuation = calculateInventoryValuation(products)

        // Capital neto: (10 * 1.25) + (20 * 0.90) = 12.5 + 18.0 = 30.50
        assertEquals(30.50, valuation.netInvestedCapitalUsd, 0.001)
        // Valor venta total: (10 * 2.0) + (20 * 1.5) = 20.0 + 30.0 = 50.00
        assertEquals(50.00, valuation.totalRetailValueUsd, 0.001)
        assertEquals(30.0, valuation.totalStockUnits, 0.001)
        assertEquals(3, valuation.unitProductsCount)
        assertEquals(2, valuation.valuedProductsCount)
    }

    @Test
    fun saleDao_persistsPaymentCasheaUsdAndExportsIncludeCashea() = runBlocking {
        val sale = Sale(
            customerName = "Carlos Gómez",
            customerDocument = "V-20111222",
            timestamp = System.currentTimeMillis(),
            rateBcv = 50.0,
            totalUsd = 25.0,
            totalBs = 1250.0,
            paymentCashUsd = 0.0,
            paymentMobileBs = 0.0,
            paymentCardBs = 0.0,
            paymentCreditUsd = 0.0,
            paymentCasheaUsd = 25.0,
            baseTaxableUsd = 21.55,
            ivaUsd = 3.45
        )
        database.saleDao().insertSale(sale)

        val storedSales = database.saleDao().getAllSales().first()
        assertEquals(1, storedSales.size)
        assertEquals(25.0, storedSales.first().paymentCasheaUsd, 0.001)

        val csvFile = AccountingLedgerCsvExporter.exportLedgerToCsv(
            context = context,
            prefs = prefs,
            sales = storedSales,
            deliveryNotes = emptyList(),
            expenses = emptyList(),
            purchaseInvoices = emptyList(),
            periodName = "Prueba Cashea"
        )
        assertNotNull(csvFile)
        val csvContent = csvFile!!.readText()
        assertTrue(csvContent.contains("Cashea (Bs.)"))
        assertTrue(csvContent.contains("Total Recaudado en Cashea Bs."))
    }
}

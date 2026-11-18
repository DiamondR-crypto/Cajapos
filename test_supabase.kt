package com.example.data.repository

import android.util.Log
import com.example.BuildConfig
import com.example.data.local.*
import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.PostgresAction
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

class CloudSyncRepository(private val localRepository: AppRepository) {

    private val supabase = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
        install(Realtime) {
            // Optional configs
        }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val TAG = "CloudSyncSupabase"

    fun startSync() {
        Log.d(TAG, "Iniciando sincronización con Supabase...")
        if (BuildConfig.SUPABASE_URL.isBlank() || BuildConfig.SUPABASE_KEY.isBlank()) {
            Log.e(TAG, "Las credenciales de Supabase están vacías.")
            return
        }
        
        scope.launch {
            try {
                supabase.realtime.connect()
                listenToProducts()
                listenToSales()
                listenToUsers()
            } catch (e: Exception) {
                Log.e(TAG, "Error conectando a Supabase Realtime: ${e.message}")
            }
        }
    }

    private suspend fun listenToProducts() {
        try {
            val channel = supabase.realtime.channel("public-products")
            val productChanges = channel.postgresChangeFlow<PostgresAction>("public") { table = "products" }
            
            productChanges.onEach { action ->
                try {
                    val localProducts = localRepository.allProducts.first()
                    when (action) {
                        is PostgresAction.Insert -> {
                            val data = Json.decodeFromJsonElement<Product>(action.record)
                            val existing = localProducts.find { it.code == data.code || it.name == data.name }
                            if (existing == null) {
                                localRepository.insertProduct(data.copy(id = 0)) 
                            }
                        }
                        is PostgresAction.Update -> {
                            val data = Json.decodeFromJsonElement<Product>(action.record)
                            val existing = localProducts.find { it.code == data.code }
                            if (existing != null) {
                                localRepository.updateProduct(existing.copy(priceUsd = data.priceUsd, stock = data.stock))
                            }
                        }
                        else -> {}
                    }
                } catch(e: Exception) {
                    Log.e(TAG, "Error procesando producto en realtime: ${e.message}")
                }
            }.launchIn(scope)
            
            channel.subscribe()
        } catch(e: Exception) {
            Log.e(TAG, "Error listening to products: ${e.message}")
        }
    }

    fun pushProduct(product: Product) {
        if (BuildConfig.SUPABASE_URL.isBlank()) return
        scope.launch {
            try {
                supabase.postgrest["products"].upsert(product)
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing product", e)
            }
        }
    }

    private suspend fun listenToSales() {
        // Implementation for sales
    }

    fun pushSale(sale: Sale, items: List<SaleItem>) {
        if (BuildConfig.SUPABASE_URL.isBlank()) return
        scope.launch {
            try {
                supabase.postgrest["sales"].insert(sale)
                items.forEach { item ->
                    supabase.postgrest["sale_items"].insert(item)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing sale", e)
            }
        }
    }

    private suspend fun listenToUsers() {
        try {
            val channel = supabase.realtime.channel("public-users")
            val userChanges = channel.postgresChangeFlow<PostgresAction>("public") { table = "users" }
            
            userChanges.onEach { action ->
                try {
                    when (action) {
                        is PostgresAction.Insert -> {
                            val data = Json.decodeFromJsonElement<User>(action.record)
                            val existing = localRepository.getUserByUsername(data.username)
                            if (existing == null) {
                                localRepository.insertUser(data)
                            }
                        }
                        is PostgresAction.Update -> {
                            val data = Json.decodeFromJsonElement<User>(action.record)
                            val existing = localRepository.getUserByUsername(data.username)
                            if (existing != null) {
                                localRepository.updateUser(existing.copy(pin = data.pin, role = data.role))
                            }
                        }
                        else -> {}
                    }
                } catch(e: Exception) {
                    Log.e(TAG, "Error procesando usuario en realtime: ${e.message}")
                }
            }.launchIn(scope)
            
            channel.subscribe()
        } catch(e: Exception) {
            Log.e(TAG, "Error listening to users: ${e.message}")
        }
    }
    
    fun pushUser(user: User) {
        if (BuildConfig.SUPABASE_URL.isBlank()) return
        scope.launch {
            try {
                supabase.postgrest["users"].upsert(user)
            } catch (e: Exception) {
                Log.e(TAG, "Error pushing user", e)
            }
        }
    }
}

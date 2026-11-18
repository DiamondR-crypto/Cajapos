package com.example.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.ui.customers.CustomersScreen
import com.example.ui.inventory.InventoryScreen
import com.example.ui.invoices.InvoicesScreen
import com.example.ui.pos.PosScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.summary.SummaryScreen
import com.example.ui.suppliers.SuppliersScreen
import com.example.ui.users.UsersScreen
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material.icons.automirrored.filled.ExitToApp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: PosViewModel) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    if (currentUser == null) {
        LoginScreen(viewModel)
        return
    }

    val navController = rememberNavController()
    
    val allItems = listOf(
        Screen.Pos,
        Screen.Inventory,
        Screen.Invoices,
        Screen.Summary,
        Screen.Customers,
        Screen.Suppliers,
        Screen.Users,
        Screen.Settings
    )

    // Filter items based on role
    val items = allItems.filter { screen ->
        when (currentUser!!.role) {
            "ADMIN" -> true
            "SUPERVISOR" -> screen == Screen.Inventory
            "CASHIER" -> screen == Screen.Pos || screen == Screen.Inventory || screen == Screen.Invoices
            else -> false
        }
    }
    
    val startDest = if (currentUser!!.role == "SUPERVISOR") Screen.Inventory.route else Screen.Pos.route

    var expanded by remember { mutableStateOf(false) }
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val currentTitle = items.find { it.route == currentRoute }?.title ?: "Caja Pos"

    Scaffold(
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    titleContentColor = MaterialTheme.colorScheme.onPrimary,
                    actionIconContentColor = MaterialTheme.colorScheme.onPrimary
                ),
                title = { Text(currentTitle) },
                actions = {
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Menú")
                    }
                    DropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        items.forEach { screen ->
                            DropdownMenuItem(
                                text = { Text(screen.title) },
                                leadingIcon = { Icon(screen.icon, contentDescription = null) },
                                onClick = {
                                    expanded = false
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Cerrar Sesión") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = null) },
                            onClick = {
                                expanded = false
                                viewModel.logout()
                            }
                        )
                    }
                }
            )
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = startDest, Modifier.padding(innerPadding)) {
            composable(Screen.Pos.route) { PosScreen(viewModel) }
            composable(Screen.Inventory.route) { InventoryScreen(viewModel) }
            composable(Screen.Invoices.route) { InvoicesScreen(viewModel) }
            composable(Screen.Summary.route) { SummaryScreen(viewModel) }
            composable(Screen.Customers.route) { CustomersScreen(viewModel) }
            composable(Screen.Suppliers.route) { SuppliersScreen(viewModel) }
            composable(Screen.Users.route) { UsersScreen(viewModel) }
            composable(Screen.Settings.route) { SettingsScreen(viewModel) }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Pos : Screen("pos", "Caja", Icons.Filled.Home)
    object Inventory : Screen("inventory", "Inventario", Icons.Filled.Inventory)
    object Invoices : Screen("invoices", "Facturas", Icons.Filled.Receipt)
    object Summary : Screen("summary", "Resumen", Icons.Filled.Analytics)
    object Customers : Screen("customers", "Clientes", Icons.Filled.People)
    object Suppliers : Screen("suppliers", "Proveedores", Icons.Filled.LocalShipping)
    object Users : Screen("users", "Usuarios", Icons.Filled.Person)
    object Settings : Screen("settings", "Configuración", Icons.Filled.Settings)
}

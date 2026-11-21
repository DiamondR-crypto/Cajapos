package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.ui.customers.CustomersScreen
import com.example.ui.dashboard.DashboardScreen
import com.example.ui.inventory.InventoryScreen
import com.example.ui.invoices.InvoicesScreen
import com.example.ui.pos.PosScreen
import com.example.ui.quotes.QuotesScreen
import com.example.ui.settings.SettingsScreen
import com.example.ui.summary.SummaryScreen
import com.example.ui.suppliers.SuppliersScreen
import com.example.ui.users.UsersScreen
import com.example.ui.expenses.ExpensesScreen
import com.example.ui.withholding.WithholdingsScreen
import com.example.ui.assistant.AssistantChatDialog
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: PosViewModel) {
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val backupMode by viewModel.backupMode.collectAsStateWithLifecycle()
    val googleAccount by viewModel.googleAccount.collectAsStateWithLifecycle()
    
    if (currentUser == null) {
        LoginScreen(viewModel)
        return
    }

    val navController = rememberNavController()
    var showAssistantChat by remember { mutableStateOf(false) }
    var isSidebarVisible by remember { mutableStateOf(false) } // Default hidden to give maximum screen space
    var isSidebarExpanded by remember { mutableStateOf(true) }

    val canUseAssistant = remember(currentUser?.role) {
        val r = currentUser?.role?.uppercase(java.util.Locale.ROOT).orEmpty()
        r == "ADMIN" || r == "ADMINISTRADOR" || r == "SUPERVISOR"
    }
    
    val storeName by viewModel.storeName.collectAsStateWithLifecycle()
    val cleanStoreName = storeName.ifBlank { "Mi Negocio" }

    val allItems = listOf(
        Screen.Pos,
        Screen.Quotes,
        Screen.Dashboard,
        Screen.Inventory,
        Screen.Invoices,
        Screen.Expenses,
        Screen.Withholdings,
        Screen.Summary,
        Screen.Customers,
        Screen.Suppliers,
        Screen.Users,
        Screen.Settings
    )

    // Filter items based on role (Dashboard ONLY for ADMIN/ADMINISTRADOR)
    val items = allItems.filter { screen ->
        val role = currentUser!!.role.uppercase(java.util.Locale.ROOT)
        when {
            role == "ADMIN" || role == "ADMINISTRADOR" -> true
            role == "SUPERVISOR" -> screen != Screen.Dashboard && screen != Screen.Users && screen != Screen.Settings && screen != Screen.Pos
            role == "CASHIER" || role == "CAJERO" -> screen != Screen.Dashboard && screen != Screen.Users && screen != Screen.Settings && screen != Screen.Suppliers
            else -> false
        }
    }
    
    val startDest = if (currentUser!!.role == "SUPERVISOR") Screen.Inventory.route else Screen.Pos.route
    
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    val dynamicTitle = when (currentRoute) {
        Screen.Inventory.route -> "Inventario de \"$cleanStoreName\""
        Screen.Dashboard.route -> "Resumen gráfico de \"$cleanStoreName\""
        Screen.Invoices.route -> "Facturas de \"$cleanStoreName\""
        Screen.Customers.route -> "Clientes de \"$cleanStoreName\""
        Screen.Summary.route -> "Resumen diario de \"$cleanStoreName\""
        Screen.Suppliers.route -> "Proveedores de \"$cleanStoreName\""
        Screen.Expenses.route -> "Gastos de \"$cleanStoreName\""
        Screen.Withholdings.route -> "Retenciones de \"$cleanStoreName\""
        Screen.Users.route -> "Usuarios de \"$cleanStoreName\""
        Screen.Settings.route -> "Ajustes de \"$cleanStoreName\""
        Screen.Pos.route -> "SmartPOS286 de \"$cleanStoreName\""
        Screen.Quotes.route -> "Presupuestos de \"$cleanStoreName\""
        else -> items.find { it.route == currentRoute }?.title ?: "SmartPOS286"
    }

    val appTheme by viewModel.appTheme.collectAsStateWithLifecycle()
    val isDark = (appTheme == "DARK")

    // Windows 11 dynamic acrylic background
    val win11Background = if (isDark) {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFF0F172A),
                Color(0xFF1E1B4B),
                Color(0xFF090D16)
            )
        )
    } else {
        Brush.verticalGradient(
            colors = listOf(
                Color(0xFFF8FAFC),
                Color(0xFFE0F2FE),
                Color(0xFFF0F9FF)
            )
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(win11Background)
    ) {
        // Ambient soft glowing orbs (safe across all API levels)
        Box(
            modifier = Modifier
                .size(450.dp)
                .offset(x = (-150).dp, y = (-200).dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF6366F1).copy(alpha = 0.2f) else Color(0xFF38BDF8).copy(alpha = 0.25f))
        )
        Box(
            modifier = Modifier
                .size(500.dp)
                .align(Alignment.BottomEnd)
                .offset(x = 100.dp, y = 100.dp)
                .clip(CircleShape)
                .background(if (isDark) Color(0xFF38BDF8).copy(alpha = 0.15f) else Color(0xFF818CF8).copy(alpha = 0.2f))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            // Fully Hidable & Collapsible Navigation Rail / Drawer
            AnimatedVisibility(
                visible = isSidebarVisible,
                enter = slideInHorizontally(initialOffsetX = { -it }) + fadeIn(),
                exit = slideOutHorizontally(targetOffsetX = { -it }) + fadeOut()
            ) {
                NavigationRail(
                    modifier = Modifier
                        .widthIn(min = if (isSidebarExpanded) 190.dp else 72.dp, max = if (isSidebarExpanded) 200.dp else 76.dp)
                        .fillMaxHeight(),
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    header = {
                        Column(
                            modifier = Modifier
                                .padding(top = 10.dp, bottom = 10.dp)
                                .padding(horizontal = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Close / Expand Buttons Row
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Collapse / Expand width toggle
                                IconButton(
                                    onClick = { isSidebarExpanded = !isSidebarExpanded },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        if (isSidebarExpanded) Icons.Filled.MenuOpen else Icons.Filled.Menu,
                                        contentDescription = if (isSidebarExpanded) "Plegar menú" else "Desplegar menú",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                // Hide completely button
                                IconButton(
                                    onClick = { isSidebarVisible = false },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = "Ocultar menú lateral",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            // SmartPOS286 Logo Badge
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.padding(horizontal = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            Brush.linearGradient(
                                                colors = listOf(
                                                    Color(0xFF1E293B),
                                                    Color(0xFF312E81)
                                                )
                                            )
                                        )
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                Brush.linearGradient(
                                                    colors = listOf(
                                                        Color(0xFF38BDF8).copy(alpha = 0.6f),
                                                        Color(0xFF6366F1).copy(alpha = 0.3f)
                                                    )
                                                )
                                            ),
                                            RoundedCornerShape(12.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.ic_pos_286_logo),
                                        contentDescription = "SmartPOS286 Logo",
                                        tint = Color.Unspecified,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                if (isSidebarExpanded) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "SmartPOS286",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "${currentUser!!.username} • v1.0.1 BETA",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        items.forEach { screen ->
                            val selected = currentRoute == screen.route
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    isSidebarVisible = false
                                    navController.navigate(screen.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(screen.icon, contentDescription = screen.title) },
                                label = {
                                    Text(
                                        screen.title,
                                        maxLines = 1,
                                        fontSize = if (isSidebarExpanded) 12.sp else 10.5.sp,
                                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                alwaysShowLabel = isSidebarExpanded || selected,
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        
                        NavigationRailItem(
                            selected = false,
                            onClick = { viewModel.logout() },
                            icon = { Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Salir") },
                            label = { Text("Salir", fontSize = 11.sp) },
                            alwaysShowLabel = isSidebarExpanded,
                            colors = NavigationRailItemDefaults.colors(
                                unselectedIconColor = MaterialTheme.colorScheme.error,
                                unselectedTextColor = MaterialTheme.colorScheme.error
                            )
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }

            // Main Content Area
            Scaffold(
                containerColor = Color.Transparent, // Let the background show through
                topBar = {
                    TopAppBar(
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        navigationIcon = {
                            // Menu toggle button in TopBar to show/hide sidebar completely
                            IconButton(onClick = { isSidebarVisible = !isSidebarVisible }) {
                                Icon(
                                    if (isSidebarVisible) Icons.Filled.MenuOpen else Icons.Filled.Menu,
                                    contentDescription = "Alternar menú principal",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        },
                        title = { 
                            Text(
                                text = dynamicTitle, 
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.headlineSmall
                            ) 
                        },
                        actions = {
                            // Backup Mode status pill
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (backupMode == "GOOGLE_CLOUD") 
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                else 
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.padding(end = 16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        if (backupMode == "GOOGLE_CLOUD") Icons.Filled.CloudDone else Icons.Filled.CloudOff,
                                        contentDescription = null,
                                        tint = if (backupMode == "GOOGLE_CLOUD") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (backupMode == "GOOGLE_CLOUD") "Nube Google" else "Offline",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    )
                }
            ) { innerPadding ->
                NavHost(
                    navController = navController, 
                    startDestination = startDest, 
                    modifier = Modifier
                        .padding(innerPadding)
                        .padding(end = 12.dp, bottom = 12.dp)
                        .imePadding() // Adjust layout dynamically when software keyboard opens
                        .fillMaxSize()
                        .clip(MaterialTheme.shapes.large)
                ) {
                    composable(Screen.Pos.route) { PosScreen(viewModel) }
                    composable(Screen.Quotes.route) {
                        QuotesScreen(
                            viewModel = viewModel,
                            onNavigateToPos = {
                                navController.navigate(Screen.Pos.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                    composable(Screen.Dashboard.route) { DashboardScreen(viewModel) }
                    composable(Screen.Inventory.route) { InventoryScreen(viewModel) }
                    composable(Screen.Invoices.route) { InvoicesScreen(viewModel) }
                    composable(Screen.Expenses.route) { ExpensesScreen(viewModel) }
                    composable(Screen.Withholdings.route) { WithholdingsScreen(viewModel) }
                    composable(Screen.Summary.route) { SummaryScreen(viewModel) }
                    composable(Screen.Customers.route) { CustomersScreen(viewModel) }
                    composable(Screen.Suppliers.route) { SuppliersScreen(viewModel) }
                    composable(Screen.Users.route) { UsersScreen(viewModel) }
                    composable(Screen.Settings.route) { SettingsScreen(viewModel) }
                }
            }
        }

        // Draggable floating assistant chat bubble positioned comfortably above interface buttons
        DraggableAssistantBubble(
            canUseAssistant = canUseAssistant,
            onOpenChat = { showAssistantChat = true }
        )
    }

    if (showAssistantChat) {
        AssistantChatDialog(
            viewModel = viewModel,
            onNavigateToRoute = { route ->
                navController.navigate(route) {
                    popUpTo(navController.graph.findStartDestination().id) {
                        saveState = true
                    }
                    launchSingleTop = true
                    restoreState = true
                }
            },
            onDismiss = { showAssistantChat = false }
        )
    }
}

@Composable
fun DraggableAssistantBubble(
    canUseAssistant: Boolean,
    onOpenChat: () -> Unit
) {
    if (!canUseAssistant) return

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val bubbleSizeDp = 56.dp
        val bubbleSizePx = with(density) { bubbleSizeDp.toPx() }
        
        val maxPxX = (constraints.maxWidth - bubbleSizePx - with(density) { 16.dp.toPx() }).coerceAtLeast(0f)
        val maxPxY = (constraints.maxHeight - bubbleSizePx - with(density) { 60.dp.toPx() }).coerceAtLeast(0f)
        val minPxX = with(density) { 16.dp.toPx() }
        val minPxY = with(density) { 64.dp.toPx() } // Avoid overlapping TopAppBar completely

        // Default position elevated above standard action buttons (e.g. ~240dp from top or 35% height)
        val defaultX = maxPxX
        val defaultY = with(density) { 240.dp.toPx() }.coerceIn(minPxY, maxPxY)

        var offsetX by remember { mutableFloatStateOf(defaultX) }
        var offsetY by remember { mutableFloatStateOf(defaultY) }

        // Keep inside bounds if screen rotates or changes size
        LaunchedEffect(maxPxX, maxPxY) {
            if (offsetX > maxPxX) offsetX = maxPxX
            if (offsetY > maxPxY) offsetY = maxPxY
            if (offsetX < minPxX) offsetX = minPxX
            if (offsetY < minPxY) offsetY = minPxY
        }

        Surface(
            shape = CircleShape,
            color = Color(0xFF4F46E5),
            contentColor = Color.White,
            shadowElevation = 8.dp,
            tonalElevation = 6.dp,
            border = BorderStroke(1.5.dp, Color.White.copy(alpha = 0.4f)),
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(bubbleSizeDp)
                .pointerInput(Unit) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        var isDrag = false
                        val pointerId = down.id
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == pointerId } ?: break
                            if (change.isConsumed) break
                            val dragDistance = (change.position - down.position).getDistance()
                            if (!isDrag && dragDistance > 8f) {
                                isDrag = true
                            }
                            if (isDrag) {
                                change.consume()
                                val delta = change.position - change.previousPosition
                                offsetX = (offsetX + delta.x).coerceIn(minPxX, maxPxX)
                                offsetY = (offsetY + delta.y).coerceIn(minPxY, maxPxY)
                            }
                            if (!change.pressed) {
                                if (!isDrag) {
                                    onOpenChat()
                                }
                                break
                            }
                        }
                    }
                }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = Icons.Filled.Chat,
                    contentDescription = "Asistente Conversacional (Arrastrar para mover)",
                    tint = Color.White,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
    }
}

sealed class Screen(val route: String, val title: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    object Pos : Screen("pos", "Facturación", Icons.Filled.Home)
    object Quotes : Screen("quotes", "Presupuestos", Icons.Filled.RequestQuote)
    object Dashboard : Screen("dashboard", "Dashboard", Icons.Filled.SpaceDashboard)
    object Inventory : Screen("inventory", "Inventario", Icons.Filled.Inventory)
    object Invoices : Screen("invoices", "Facturas", Icons.Filled.Receipt)
    object Expenses : Screen("expenses", "Gastos", Icons.Filled.ReceiptLong)
    object Withholdings : Screen("withholdings", "Retenciones", Icons.Filled.Policy)
    object Summary : Screen("summary", "Resumen", Icons.Filled.Analytics)
    object Customers : Screen("customers", "Clientes", Icons.Filled.People)
    object Suppliers : Screen("suppliers", "Proveedores", Icons.Filled.LocalShipping)
    object Users : Screen("users", "Usuarios", Icons.Filled.Person)
    object Settings : Screen("settings", "Ajustes", Icons.Filled.Settings)
}

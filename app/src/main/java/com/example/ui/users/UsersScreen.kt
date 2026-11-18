package com.example.ui.users

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.example.data.local.User
import com.example.ui.PosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(viewModel: PosViewModel) {
    val users by viewModel.allUsers.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    var selectedUser by remember { mutableStateOf<User?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir Usuario")
            }
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
            Text(
                "Gestión de Usuarios y Cajeros",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(16.dp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 320.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(users) { user ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (user.avatarUrl.isNotBlank()) {
                                            AsyncImage(
                                                model = user.avatarUrl,
                                                contentDescription = "Avatar",
                                                modifier = Modifier.fillMaxSize(),
                                                contentScale = ContentScale.Crop
                                            )
                                        } else {
                                            Text(
                                                text = user.username.take(2).uppercase(),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onPrimaryContainer
                                            )
                                        }
                                    }
                                    Column {
                                        Text(user.username.uppercase(), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                        Text("Rol: ${user.role}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                IconButton(onClick = {
                                    selectedUser = user
                                    showEditDialog = true
                                }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Editar Usuario")
                                }
                            }

                            // Commission status badge
                            val isSupervisor = user.role.equals("SUPERVISOR", ignoreCase = true)
                            val effectiveCanEarn = if (isSupervisor) false else user.canEarnCommission
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = if (effectiveCanEarn) Color(0xFF6366F1).copy(alpha = 0.12f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                border = BorderStroke(1.dp, if (effectiveCanEarn) Color(0xFF6366F1).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        if (effectiveCanEarn) Icons.Filled.MonetizationOn else Icons.Filled.MoneyOff,
                                        contentDescription = null,
                                        tint = if (effectiveCanEarn) Color(0xFF6366F1) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isSupervisor) "Comisión de Ventas: No Autorizada (Supervisor no genera ventas)"
                                               else if (effectiveCanEarn) "Comisión de Ventas: Habilitada"
                                               else "Comisión de Ventas: No Autorizada",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (effectiveCanEarn) Color(0xFF4F46E5) else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEditDialog && selectedUser != null) {
        val userToEdit = selectedUser!!
        var newPin by remember(userToEdit) { mutableStateOf("") }
        var newAvatarUrl by remember(userToEdit) { mutableStateOf(userToEdit.avatarUrl) }
        var selectedRole by remember(userToEdit) { mutableStateOf(userToEdit.role) }
        var canEarnCommission by remember(userToEdit, selectedRole) {
            mutableStateOf(if (selectedRole.equals("SUPERVISOR", ignoreCase = true)) false else userToEdit.canEarnCommission)
        }
        var roleExpanded by remember { mutableStateOf(false) }
        val roles = listOf("ADMIN", "SUPERVISOR", "CASHIER")

        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Editar Usuario: ${userToEdit.username}", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    IconButton(onClick = { showEditDialog = false }) {
                        Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { newPin = it },
                        label = { Text("Nuevo PIN (Dejar en blanco para no cambiar)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newAvatarUrl,
                        onValueChange = { newAvatarUrl = it },
                        label = { Text("URL del Avatar / Logo de Empresa") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    ExposedDropdownMenuBox(
                        expanded = roleExpanded,
                        onExpandedChange = { roleExpanded = !roleExpanded },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        OutlinedTextField(
                            value = selectedRole,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Rol del Usuario") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                            modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = roleExpanded,
                            onDismissRequest = { roleExpanded = false }
                        ) {
                            roles.forEach { r ->
                                DropdownMenuItem(
                                    text = { Text(r) },
                                    onClick = {
                                        selectedRole = r
                                        if (r.equals("SUPERVISOR", ignoreCase = true)) {
                                            canEarnCommission = false
                                        }
                                        roleExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    // Commission Toggle / Info for Admin
                    if (selectedRole.equals("SUPERVISOR", ignoreCase = true)) {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Text(
                                    "El rol de Supervisor no genera ventas directamente en caja, por lo que no percibe comisiones por venta.",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Card(
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (canEarnCommission) Color(0xFF6366F1).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            ),
                            border = BorderStroke(1.dp, if (canEarnCommission) Color(0xFF6366F1).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Ganar Comisiones", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                    Text("Permite a este cajero recibir comisiones por productos con comisión activa.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Switch(
                                    checked = canEarnCommission,
                                    onCheckedChange = { canEarnCommission = it }
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val finalAvatarUrl = if (newAvatarUrl.isNotBlank()) newAvatarUrl else userToEdit.avatarUrl
                    val finalCanEarn = if (selectedRole.equals("SUPERVISOR", ignoreCase = true)) false else canEarnCommission
                    val updatedUser = userToEdit.copy(
                        pin = if (newPin.isNotBlank()) newPin else userToEdit.pin,
                        avatarUrl = finalAvatarUrl,
                        role = selectedRole,
                        canEarnCommission = finalCanEarn
                    )
                    viewModel.updateUser(updatedUser)
                    showEditDialog = false
                }) { Text("Guardar") }
            },
            dismissButton = {
                Row {
                    if (users.size > 1 && userToEdit.id != currentUser?.id) {
                        TextButton(
                            onClick = {
                                viewModel.deleteUser(userToEdit)
                                showEditDialog = false
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                        ) {
                            Text("Eliminar")
                        }
                    }
                    TextButton(onClick = { showEditDialog = false }) { Text("Cancelar") }
                }
            }
        )
    }

    if (showAddDialog) {
        AddUserDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { username, pin, role, avatarUrl, canEarnCommission ->
                if (username.isNotBlank() && pin.isNotBlank()) {
                    val finalCanEarn = if (role.equals("SUPERVISOR", ignoreCase = true)) false else canEarnCommission
                    viewModel.insertUser(
                        User(
                            username = username.trim(),
                            pin = pin.trim(),
                            role = role,
                            avatarUrl = avatarUrl.trim(),
                            canEarnCommission = finalCanEarn
                        )
                    )
                    showAddDialog = false
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (username: String, pin: String, role: String, avatarUrl: String, canEarnCommission: Boolean) -> Unit
) {
    var username by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var avatarUrl by remember { mutableStateOf("") }
    var selectedRole by remember { mutableStateOf("CASHIER") }
    var canEarnCommission by remember { mutableStateOf(true) }
    var roleExpanded by remember { mutableStateOf(false) }
    val roles = listOf("ADMIN", "SUPERVISOR", "CASHIER")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nuevo Usuario / Cajero", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Cerrar")
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Nombre de Usuario") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = pin,
                    onValueChange = { pin = it },
                    label = { Text("PIN (Contraseña)") },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = avatarUrl,
                    onValueChange = { avatarUrl = it },
                    label = { Text("URL del Avatar / Logo") },
                    modifier = Modifier.fillMaxWidth()
                )
                ExposedDropdownMenuBox(
                    expanded = roleExpanded,
                    onExpandedChange = { roleExpanded = !roleExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedRole,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Rol") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = roleExpanded) },
                        modifier = Modifier.menuAnchor(MenuAnchorType.PrimaryNotEditable).fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = roleExpanded,
                        onDismissRequest = { roleExpanded = false }
                    ) {
                        roles.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role) },
                                onClick = {
                                    selectedRole = role
                                    if (role.equals("SUPERVISOR", ignoreCase = true)) {
                                        canEarnCommission = false
                                    }
                                    roleExpanded = false
                                }
                            )
                        }
                    }
                }

                // Commission toggle / supervisor disclaimer
                if (selectedRole.equals("SUPERVISOR", ignoreCase = true)) {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            Text(
                                "El rol de Supervisor no genera ventas directamente en caja, por lo que no percibe comisiones por venta.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    Card(
                        shape = RoundedCornerShape(10.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (canEarnCommission) Color(0xFF6366F1).copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        border = BorderStroke(1.dp, if (canEarnCommission) Color(0xFF6366F1).copy(alpha = 0.35f) else MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp).fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text("Ganar Comisiones", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                                Text("Autorizar a este usuario para generar comisiones de venta.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = canEarnCommission,
                                onCheckedChange = { canEarnCommission = it }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            val finalCanEarn = if (selectedRole.equals("SUPERVISOR", ignoreCase = true)) false else canEarnCommission
            TextButton(onClick = { onConfirm(username, pin, selectedRole, avatarUrl, finalCanEarn) }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}


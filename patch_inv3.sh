#!/bin/bash
sed -i 's/val products by viewModel.allProducts.collectAsStateWithLifecycle()/val products by viewModel.allProducts.collectAsStateWithLifecycle()\n    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()\n    val canEdit = currentUser?.role == "ADMIN" || currentUser?.role == "SUPERVISOR"/g' app/src/main/java/com/example/ui/inventory/InventoryScreen.kt
sed -i 's/floatingActionButton = {/floatingActionButton = {\n            if (canEdit) {/g' app/src/main/java/com/example/ui/inventory/InventoryScreen.kt
sed -i 's/Icon(Icons.Filled.Add, contentDescription = "Añadir")/Icon(Icons.Filled.Add, contentDescription = "Añadir")\n            }/g' app/src/main/java/com/example/ui/inventory/InventoryScreen.kt
sed -i 's/ProductItem(product) {/ProductItem(product, canEdit) {/g' app/src/main/java/com/example/ui/inventory/InventoryScreen.kt
sed -i 's/fun ProductItem(product: Product, onAddStock: () -> Unit) {/fun ProductItem(product: Product, canEdit: Boolean, onAddStock: () -> Unit) {/g' app/src/main/java/com/example/ui/inventory/InventoryScreen.kt
sed -i 's/Button(onClick = onAddStock) { Text("+") }/if (canEdit) {\n                    Button(onClick = onAddStock) { Text("+") }\n                }/g' app/src/main/java/com/example/ui/inventory/InventoryScreen.kt

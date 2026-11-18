#!/bin/bash
sed -i 's/Card(modifier = Modifier.fillMaxWidth()) {/Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {/g' app/src/main/java/com/example/ui/customers/CustomersScreen.kt

#!/bin/bash
sed -i 's/elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)/elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)/g' app/src/main/java/com/example/ui/invoices/InvoicesScreen.kt

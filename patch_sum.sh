#!/bin/bash
sed -i 's/Card(/Card(\n        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),\n        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),/g' app/src/main/java/com/example/ui/summary/SummaryScreen.kt

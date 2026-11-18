with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    content = f.read()

# Wrap channel.subscribe() in try-catch and add status logging
old_sub1 = "channel.subscribe()"
new_sub1 = """try {
                channel.subscribe()
            } catch (e: Exception) {
                Log.w(TAG, "Realtime subscription skipped or failed (ensure Realtime is enabled in Supabase dashboard): ${e.message}")
            }"""

content = content.replace(old_sub1, new_sub1)

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(content)
print("Updated CloudSyncRepository.kt successfully")

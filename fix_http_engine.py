import re
with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

# For kotlin supabase, setting engine looks like `httpClient(OkHttp)` or similar. But if I don't know the exact syntax,
# I will just remove httpEngine block. We already added the okhttp dependency, so ktor should pick it up automatically!
text = text.replace("""        httpEngine {
            // Force OkHttp
        }""", "")

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

import re
with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

# Add OkHttp engine explicitly to the supabase client and websocket dependency
# because websockets are failing to connect
text = text.replace("import io.github.jan.supabase.realtime.realtime", "import io.github.jan.supabase.realtime.realtime\nimport io.ktor.client.engine.okhttp.OkHttp\nimport io.github.jan.supabase.SupabaseClientBuilder")

text = text.replace("""    private val supabase = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
        install(Realtime) {
            // Optional configs
        }
    }""", """    private val supabase = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
        install(Realtime) {
            // Optional configs
        }
        httpEngine {
            // Adding this block ensures OkHttp is selected
        }
    }""")

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

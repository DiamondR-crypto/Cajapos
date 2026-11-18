import re
with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

# Make sure Realtime can connect.
text = text.replace("import io.ktor.client.plugins.websocket.WebSockets", "")
text = text.replace("""    private val supabase = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
        install(Realtime) {
            /*httpEngine {
            // Needed to ensure WebSockets can connect properly on Android via OkHttp
            // The default engine sometimes fails with websockets.
        }*/
        }
    }""", """    private val supabase = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_KEY
    ) {
        install(Postgrest)
        install(Realtime)
    }""")

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

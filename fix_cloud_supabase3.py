import re
with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

# Add websocket plugin to ktor client
if "install(io.ktor.client.plugins.websocket.WebSockets)" not in text:
    # Need to add import first
    text = text.replace("import io.github.jan.supabase.realtime.realtime", "import io.github.jan.supabase.realtime.realtime\nimport io.ktor.client.plugins.websocket.WebSockets")
    
text = text.replace("""        install(Realtime) {
            secure = true // for wss
        }""", """        install(Realtime) {
            // No need for specific websocket config in newest supabase versions usually unless custom ktor client is used
            // But let's let supabase handle it.
            // secure = true is for older versions. 
        }""")

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

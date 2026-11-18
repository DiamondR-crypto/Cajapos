import re
with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

text = text.replace("import io.ktor.client.engine.cio.CIO", "")
text = text.replace("""        httpEngine {
            // Force CIO
        }""", "")

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

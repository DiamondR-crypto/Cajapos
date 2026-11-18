import re
with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

# Try explicitly installing Ktor OkHttp if httpEngine block isn't working
text = text.replace("httpEngine {", "/*httpEngine {")
text = text.replace("        }", "        }*/")

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

import re
with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

# Make sure io.ktor.client.engine.okhttp.OkHttp is not there and we don't mention httpEngine at all.
text = text.replace("import io.ktor.client.engine.okhttp.OkHttp", "")

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

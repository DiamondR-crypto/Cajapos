import re
with open("app/build.gradle.kts", "r") as f:
    text = f.read()

# make sure ktor-client-cio is replaced with ktor-client-okhttp in build.gradle.kts if we are missing the websocket engine
if "ktor-client-websockets" not in text:
    text = text.replace("  implementation(\"io.ktor:ktor-client-okhttp:2.3.12\")", "  implementation(\"io.ktor:ktor-client-okhttp:2.3.12\")\n  implementation(\"io.ktor:ktor-client-websockets:2.3.12\")")

with open("app/build.gradle.kts", "w") as f:
    f.write(text)

import re
with open("app/build.gradle.kts", "r") as f:
    text = f.read()

# Add Ktor cio engine instead of okhttp for realtime
text = text.replace("  implementation(libs.ktor.client.okhttp)", "  implementation(\"io.ktor:ktor-client-cio:2.3.12\")")

with open("app/build.gradle.kts", "w") as f:
    f.write(text)

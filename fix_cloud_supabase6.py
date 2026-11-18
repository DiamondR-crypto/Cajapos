import re
with open("app/build.gradle.kts", "r") as f:
    text = f.read()

# Replace cio with okhttp again
text = text.replace("  implementation(\"io.ktor:ktor-client-cio:2.3.12\")", "  implementation(libs.ktor.client.okhttp)")

with open("app/build.gradle.kts", "w") as f:
    f.write(text)

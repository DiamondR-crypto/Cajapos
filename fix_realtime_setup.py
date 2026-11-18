with open("app/build.gradle.kts", "r") as f:
    text = f.read()

# Make sure ktor client websockets is there for supabase realtime
if "implementation(libs.ktor.client.okhttp)" in text and "ktor.client.websockets" not in text:
    text = text.replace("  implementation(libs.ktor.client.okhttp)", "  implementation(libs.ktor.client.okhttp)\n  implementation(\"io.ktor:ktor-client-websockets:2.3.12\")")

with open("app/build.gradle.kts", "w") as f:
    f.write(text)

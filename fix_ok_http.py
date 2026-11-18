import re
with open("app/build.gradle.kts", "r") as f:
    text = f.read()

# make sure ktor-client-okhttp is in build.gradle.kts
if "implementation(libs.ktor.client.okhttp)" not in text:
    if "implementation(\"io.ktor:ktor-client-cio:2.3.12\")" in text:
        text = text.replace("  implementation(\"io.ktor:ktor-client-cio:2.3.12\")", "  implementation(\"io.ktor:ktor-client-okhttp:2.3.12\")")
    else:
        text = text.replace("  implementation(libs.supabase.realtime)", "  implementation(libs.supabase.realtime)\n  implementation(\"io.ktor:ktor-client-okhttp:2.3.12\")")

with open("app/build.gradle.kts", "w") as f:
    f.write(text)

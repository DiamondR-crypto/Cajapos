import re
with open("app/build.gradle.kts", "r") as f:
    text = f.read()

# Make sure cio engine is in the dependencies so websockets will work properly with it
if "ktor-client-cio" not in text:
    text = text.replace("implementation(libs.supabase.realtime)", "implementation(libs.supabase.realtime)\n  implementation(\"io.ktor:ktor-client-cio:2.3.12\")")

with open("app/build.gradle.kts", "w") as f:
    f.write(text)

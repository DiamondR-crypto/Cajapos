import re
with open("app/build.gradle.kts", "r") as f:
    text = f.read()

# Make sure okhttp engine is used instead if cio is not working properly with it
if "ktor-client-okhttp" not in text:
    text = text.replace("implementation(\"io.ktor:ktor-client-cio:2.3.12\")", "implementation(\"io.ktor:ktor-client-okhttp:2.3.12\")")

with open("app/build.gradle.kts", "w") as f:
    f.write(text)

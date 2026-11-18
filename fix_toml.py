import re
with open("gradle/libs.versions.toml", "r") as f:
    text = f.read()

# Remove the appended lines first
text = text.replace("supabase = \"2.7.1\"\n", "")
text = text.replace("ktor = \"2.3.12\"\n", "")
text = text.replace("supabase-bom = { group = \"io.github.jan-tennert.supabase\", name = \"bom\", version.ref = \"supabase\" }\n", "")
text = text.replace("supabase-postgrest = { group = \"io.github.jan-tennert.supabase\", name = \"postgrest-kt\" }\n", "")
text = text.replace("supabase-realtime = { group = \"io.github.jan-tennert.supabase\", name = \"realtime-kt\" }\n", "")
text = text.replace("ktor-client-okhttp = { group = \"io.ktor\", name = \"ktor-client-okhttp\", version.ref = \"ktor\" }\n", "")

# Now inject versions under [versions]
text = text.replace("[libraries]", "supabase = \"2.7.1\"\nktor = \"2.3.12\"\n[libraries]")

# And inject libraries under [libraries]
text = text.replace("[plugins]", "supabase-bom = { group = \"io.github.jan-tennert.supabase\", name = \"bom\", version.ref = \"supabase\" }\nsupabase-postgrest = { group = \"io.github.jan-tennert.supabase\", name = \"postgrest-kt\" }\nsupabase-realtime = { group = \"io.github.jan-tennert.supabase\", name = \"realtime-kt\" }\nktor-client-okhttp = { group = \"io.ktor\", name = \"ktor-client-okhttp\", version.ref = \"ktor\" }\n\n[plugins]")

with open("gradle/libs.versions.toml", "w") as f:
    f.write(text)

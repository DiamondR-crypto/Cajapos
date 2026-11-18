with open("gradle/libs.versions.toml", "r") as f:
    text = f.read()

text = text.replace("supabase = \"2.7.1\"\n", "supabase = \"2.7.1\"\nkotlinxSerializationJson = \"1.7.3\"\n")
text = text.replace("supabase-bom =", "kotlinx-serialization-json = { group = \"org.jetbrains.kotlinx\", name = \"kotlinx-serialization-json\", version.ref = \"kotlinxSerializationJson\" }\nsupabase-bom =")

with open("gradle/libs.versions.toml", "w") as f:
    f.write(text)

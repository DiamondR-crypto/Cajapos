with open("gradle/libs.versions.toml", "r") as f:
    text = f.read()

text = text.replace('supabase = "2.7.1"', 'supabase = "3.1.4"')

with open("gradle/libs.versions.toml", "w") as f:
    f.write(text)

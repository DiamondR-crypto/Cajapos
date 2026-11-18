import re
with open("app/build.gradle.kts", "r") as f:
    text = f.read()

# Comment out firebase
text = text.replace("  implementation(libs.firebase.firestore)", "  // implementation(libs.firebase.firestore)")

# Add supabase libs
supabase_libs = """
  implementation(platform(libs.supabase.bom))
  implementation(libs.supabase.postgrest)
  implementation(libs.supabase.realtime)
  implementation(libs.ktor.client.okhttp)
  
  implementation(libs.kotlinx.serialization.json)
"""

# check if kotlinx.serialization.json is in versions toml
text = text.replace("  // implementation(libs.firebase.firestore)", "  // implementation(libs.firebase.firestore)\n" + supabase_libs)

# Add kotlinx.serialization plugin
text = text.replace("alias(libs.plugins.google.services)", "alias(libs.plugins.google.services)\n  kotlin(\"plugin.serialization\") version \"2.0.21\"") 

with open("app/build.gradle.kts", "w") as f:
    f.write(text)

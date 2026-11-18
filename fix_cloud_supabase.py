import re
with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

# fix postgresChangeFlow
text = text.replace('channel.postgresChangeFlow<PostgresAction>("public", "products")', 'channel.postgresChangeFlow<PostgresAction>("public") { table = "products" }')
text = text.replace('channel.postgresChangeFlow<PostgresAction>("public", "users")', 'channel.postgresChangeFlow<PostgresAction>("public") { table = "users" }')

# fix decodeRecord
# I'll use action.record.decodeAs<Product>()? Wait, let's try just action.record.let { ... } but Supabase has decodeRecord<T>() in `io.github.jan.supabase.realtime.decodeRecord` extension maybe?
# I'll add the import: `import io.github.jan.supabase.realtime.decodeRecord` just in case, but let's see if there's `decodeAs`
# Actually, the method is `action.decodeRecord<T>()` in some versions, but if it's unresolved, it might need an import, or it's `action.record.decodeAs<T>()` 
# I will use `kotlinx.serialization.json.Json.decodeFromJsonElement(Product.serializer(), action.record)`

text = text.replace("import io.github.jan.supabase.realtime.PostgresAction", "import io.github.jan.supabase.realtime.PostgresAction\nimport io.github.jan.supabase.realtime.decodeRecord\nimport kotlinx.serialization.json.Json\nimport kotlinx.serialization.json.decodeFromJsonElement")

text = text.replace("action.decodeRecord<Product>()", "Json.decodeFromJsonElement<Product>(action.record)")
text = text.replace("action.decodeRecord<User>()", "Json.decodeFromJsonElement<User>(action.record)")

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

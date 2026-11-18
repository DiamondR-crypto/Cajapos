import re
with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

def replace_db(method_name, text):
    # Find method start
    parts = text.split(f"    private fun {method_name}() {{\n        if (db == null) return\n")
    if len(parts) > 1:
        rest = parts[1]
        rest = rest.replace("db.collection", "currentDb.collection")
        return parts[0] + f"    private fun {method_name}() {{\n        val currentDb = db ?: return\n" + rest
    return text

def replace_db_public(method_name, text):
    parts = text.split(f"    fun {method_name}")
    if len(parts) > 1:
        rest = parts[1]
        rest = rest.replace("        if (db == null) return\n", "        val currentDb = db ?: return\n", 1)
        # Inside this method block, replace db.collection
        # Let's just do a simple replacement up to the next fun
        subparts = rest.split("    fun ")
        subparts[0] = subparts[0].replace("db.collection", "currentDb.collection")
        rest = "    fun ".join(subparts)
        return parts[0] + f"    fun {method_name}" + rest
    return text

text = replace_db("listenToProducts", text)
text = replace_db("listenToSales", text)
text = replace_db("listenToUsers", text)

text = replace_db_public("pushProduct(product: Product)", text)
text = replace_db_public("pushSale(sale: Sale, items: List<SaleItem>)", text)
text = replace_db_public("pushUser(user: User)", text)

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

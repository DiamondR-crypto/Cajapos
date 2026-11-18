import re
with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

# Replace all db?.collection with db.collection to undo previous step
text = text.replace("db?.collection", "db.collection")

# Add if (db == null) return inside listen and push methods
def insert_null_check(method_name, text):
    return text.replace(f"    private fun {method_name}() {{\n", f"    private fun {method_name}() {{\n        if (db == null) return\n")

def insert_null_check_public(method_name, text):
    # Find the opening brace of the method, then insert the check before scope.launch
    parts = text.split(f"    fun {method_name}")
    if len(parts) > 1:
        rest = parts[1]
        brace_idx = rest.find("{")
        if brace_idx != -1:
            rest = rest[:brace_idx+1] + "\n        if (db == null) return\n" + rest[brace_idx+1:]
            return parts[0] + f"    fun {method_name}" + rest
    return text

text = insert_null_check("listenToProducts", text)
text = insert_null_check("listenToSales", text)
text = insert_null_check("listenToUsers", text)

text = insert_null_check_public("pushProduct(product: Product)", text)
text = insert_null_check_public("pushSale(sale: Sale, items: List<SaleItem>)", text)
text = insert_null_check_public("pushUser(user: User)", text)

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

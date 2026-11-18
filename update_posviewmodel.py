import re
with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()

# Add import
if "CloudSyncRepository" not in text:
    text = text.replace("import com.example.data.repository.GeminiRepository", "import com.example.data.repository.GeminiRepository\nimport com.example.data.repository.CloudSyncRepository")

# Update constructor
text = text.replace("    private val geminiRepository: GeminiRepository\n) : ViewModel() {", "    private val geminiRepository: GeminiRepository,\n    private val cloudSyncRepository: CloudSyncRepository\n) : ViewModel() {")

# Update addProduct
text = text.replace("""        viewModelScope.launch {
            repository.insertProduct(Product(code = code, name = name, category = category, priceUsd = priceUsd, stock = stock))
        }""", """        viewModelScope.launch {
            val product = Product(code = code, name = name, category = category, priceUsd = priceUsd, stock = stock)
            repository.insertProduct(product)
            cloudSyncRepository.pushProduct(product)
        }""")

# Update updateProduct
text = text.replace("""        viewModelScope.launch {
            repository.updateProduct(product)
        }""", """        viewModelScope.launch {
            repository.updateProduct(product)
            cloudSyncRepository.pushProduct(product)
        }""")

# Update processSale
text = text.replace("""        viewModelScope.launch {
            repository.processSale(sale, items)
            clearCart()
            onSuccess(sale)
        }""", """        viewModelScope.launch {
            repository.processSale(sale, items)
            cloudSyncRepository.pushSale(sale, items)
            clearCart()
            onSuccess(sale)
        }""")

# Update insertUser
text = text.replace("""    fun insertUser(user: User) {
        viewModelScope.launch {
            repository.insertUser(user)
        }
    }""", """    fun insertUser(user: User) {
        viewModelScope.launch {
            repository.insertUser(user)
            cloudSyncRepository.pushUser(user)
        }
    }""")

# Update updateUserPin
text = text.replace("""    fun updateUserPin(user: User, newPin: String) {
        viewModelScope.launch {
            repository.updateUser(user.copy(pin = newPin))
        }
    }""", """    fun updateUserPin(user: User, newPin: String) {
        viewModelScope.launch {
            val updated = user.copy(pin = newPin)
            repository.updateUser(updated)
            cloudSyncRepository.pushUser(updated)
        }
    }""")

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
    f.write(text)

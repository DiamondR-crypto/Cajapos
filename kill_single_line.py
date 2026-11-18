with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()

bad_string1 = "    fun insertUser(user: User) {        viewModelScope.launch {            repository.insertUser(user)        }    }"
bad_string2 = "fun insertUser(user: User) {viewModelScope.launch {repository.insertUser(user)}}"

# Remove all copies
text = text.replace(bad_string1, "")
text = text.replace(bad_string2, "")

# We might have left over empty lines, let's add one back properly
insert_code = """
    fun insertUser(user: User) {
        viewModelScope.launch {
            repository.insertUser(user)
        }
    }

"""

# Ensure it's not already there properly
if "fun insertUser(user: User)" not in text:
    text = text.replace("    fun updateUserPin", insert_code + "    fun updateUserPin")

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
    f.write(text)

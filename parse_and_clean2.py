import re

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()

# Just remove ALL fun insertUser(user: User) { ... }
# since we know exactly what is inside.
text = re.sub(r'[ \t]*fun insertUser\(user: User\)[ \t\n]*\{[ \t\n]*viewModelScope\.launch[ \t\n]*\{[ \t\n]*repository\.insertUser\(user\)[ \t\n]*\}[ \t\n]*\}', '', text)

text = text.replace("    fun updateUserPin", """    fun insertUser(user: User) {
        viewModelScope.launch {
            repository.insertUser(user)
        }
    }

    fun updateUserPin""")

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
    f.write(text)

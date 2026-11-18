import re

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()

# Completely remove all insertUser blocks
# the blocks are exactly:
#    fun insertUser(user: User) {
#        viewModelScope.launch {
#            repository.insertUser(user)
#        }
#    }
pattern = r"[ \t]*fun insertUser\(user: User\)[ \t]*\{[ \t\n]*viewModelScope\.launch[ \t]*\{[ \t\n]*repository\.insertUser\(user\)[ \t\n]*\}[ \t\n]*\}"

text = re.sub(pattern, "", text)

# Now just add one before updateUserPin
text = text.replace("    fun updateUserPin(user: User, newPin: String) {\n", 
"""    fun insertUser(user: User) {
        viewModelScope.launch {
            repository.insertUser(user)
        }
    }

    fun updateUserPin(user: User, newPin: String) {
""")

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
    f.write(text)

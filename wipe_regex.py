import re

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()

# Match anything that looks like the function, regardless of exact spacing, newlines, etc.
# We'll use re.sub with a very flexible pattern
pattern = re.compile(r'fun\s+insertUser\(user:\s*User\)\s*\{\s*viewModelScope\.launch\s*\{\s*repository\.insertUser\(user\)\s*\}\s*\}')
text = pattern.sub('', text)

insert_code = """
    fun insertUser(user: User) {
        viewModelScope.launch {
            repository.insertUser(user)
        }
    }

"""

text = text.replace("    fun updateUserPin", insert_code + "    fun updateUserPin")

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
    f.write(text)

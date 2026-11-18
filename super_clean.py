import re

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()

# Normalize spacing for the block we want to kill
# The fork bomb inserted exactly:
#    fun insertUser(user: User) {
#        viewModelScope.launch {
#            repository.insertUser(user)
#        }
#    }
# We use a regex that ignores all whitespace variations between tokens.
pattern = re.compile(
    r"fun\s+insertUser\(\s*user:\s*User\s*\)\s*\{\s*viewModelScope\.launch\s*\{\s*repository\.insertUser\(\s*user\s*\)\s*\}\s*\}",
    re.MULTILINE
)

cleaned_text = pattern.sub("", text)

# Now we need to ensure ONE copy exists before updateUserPin
insert_code = """
    fun insertUser(user: User) {
        viewModelScope.launch {
            repository.insertUser(user)
        }
    }

"""

cleaned_text = cleaned_text.replace("    fun updateUserPin", insert_code + "    fun updateUserPin")

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
    f.write(cleaned_text)

print("Done")

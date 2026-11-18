import re

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    content = f.read()

# Completely remove all insertUser functions
cleaned = re.sub(r'    fun insertUser\(user: User\) \{\s+viewModelScope\.launch \{\s+repository\.insertUser\(user\)\s+\}\s+\}', '', content)

# Remove any empty lines created by the removal
cleaned = re.sub(r'\n\s*\n', '\n\n', cleaned)

parts = cleaned.split('    fun updateUserPin')
if len(parts) == 2:
    new_content = parts[0] + "    fun insertUser(user: User) {\n        viewModelScope.launch {\n            repository.insertUser(user)\n        }\n    }\n\n    fun updateUserPin" + parts[1]
    with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
        f.write(new_content)
    print("Fixed")
else:
    print("Error splitting")

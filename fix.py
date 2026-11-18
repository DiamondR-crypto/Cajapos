import re

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    content = f.read()

# Replace all occurrences of the insertUser block, then add exactly one
cleaned = re.sub(r'    fun insertUser\(user: User\) \{\s+viewModelScope\.launch \{\s+repository\.insertUser\(user\)\s+\}\s+\}\n', '', content)

# insert right before updateUserPin
parts = cleaned.split('    fun updateUserPin')
if len(parts) == 2:
    new_content = parts[0] + "    fun insertUser(user: User) {\n        viewModelScope.launch {\n            repository.insertUser(user)\n        }\n    }\n\n    fun updateUserPin" + parts[1]
    with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
        f.write(new_content)
    print("Fixed")
else:
    print("Error splitting")

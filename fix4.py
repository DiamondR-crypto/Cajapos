import re

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()

# Replace any number of these functions with a single one
pattern = re.compile(r'(?:\s*fun insertUser\(user: User\) \{\s*viewModelScope\.launch \{\s*repository\.insertUser\(user\)\s*\}\s*\})+')
text = pattern.sub('\n    fun insertUser(user: User) {\n        viewModelScope.launch {\n            repository.insertUser(user)\n        }\n    }\n', text)

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
    f.write(text)


with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()

start_str = "    fun logout() {\n        _currentUser.value = null\n    }\n"
end_str = "    fun updateUserPin(user: User, newPin: String) {\n"

start_idx = text.find(start_str)
end_idx = text.find(end_str)

if start_idx != -1 and end_idx != -1:
    new_text = text[:start_idx + len(start_str)] + """
    fun insertUser(user: User) {
        viewModelScope.launch {
            repository.insertUser(user)
        }
    }

""" + text[end_idx:]
    with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
        f.write(new_text)
    print("Success")
else:
    print(f"Failed to find indices: start={start_idx}, end={end_idx}")


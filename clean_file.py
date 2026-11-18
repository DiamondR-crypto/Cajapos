import os

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()

logout_str = "    fun logout() {\n        _currentUser.value = null\n    }\n"
update_str = "    fun updateUserPin(user: User, newPin: String) {\n"

start_idx = text.find(logout_str)
end_idx = text.find(update_str)

top_part = text[:start_idx + len(logout_str)]
bottom_part = text[end_idx:]

new_text = top_part + """
    fun insertUser(user: User) {
        viewModelScope.launch {
            repository.insertUser(user)
        }
    }

""" + bottom_part

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
    f.write(new_text)

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()

# The file starts at 0 and goes up to fun logout() { ... }
logout_idx = text.find("    fun logout() {")
if logout_idx == -1:
    print("logout not found")
    exit(1)

logout_end = text.find("}", logout_idx) + 1
while text[logout_end] in ['\n', '\r', ' ']:
    logout_end += 1

top_part = text[:logout_end]

# Find fun updateUserPin(user: User, newPin: String) {
# We will search from the END of the file to find the LAST one, just in case!
update_idx = text.rfind("    fun updateUserPin(user: User, newPin: String) {")
if update_idx == -1:
    print("updateUserPin not found")
    exit(1)

bottom_part = text[update_idx:]

new_text = top_part + """
    fun insertUser(user: User) {
        viewModelScope.launch {
            repository.insertUser(user)
        }
    }

""" + bottom_part

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
    f.write(new_text)

print("Nuked and paved. New len:", len(new_text))

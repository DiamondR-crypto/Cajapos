with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()
print("logout:", text.find("fun logout()"))
print("update:", text.find("fun updateUserPin"))
print("insert:", text.find("fun insertUser("))
print("insert count:", text.count("fun insertUser("))

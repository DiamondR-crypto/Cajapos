with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()

idx = text.find("fun updateUserPin")
print("Before updateUserPin:")
print(repr(text[idx-200:idx]))
print("After updateUserPin:")
print(repr(text[idx:idx+200]))

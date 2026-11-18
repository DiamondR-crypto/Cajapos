import re
with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    text = f.read()
for m in re.finditer(r"fun updateUserPin", text):
    print(m.start())

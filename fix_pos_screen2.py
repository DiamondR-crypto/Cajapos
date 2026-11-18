import re
with open("app/src/main/java/com/example/ui/pos/PosScreen.kt", "r") as f:
    text = f.read()

text = text.replace("Modifier.menuAnchor()", "Modifier.menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)")

with open("app/src/main/java/com/example/ui/pos/PosScreen.kt", "w") as f:
    f.write(text)

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    lines = f.readlines()

new_lines = []
skip = False
for line in lines:
    if "fun insertUser(" in line:
        skip = True
        continue
    if skip:
        if line.strip() == "}":
            skip = False
        continue
    
    new_lines.append(line)

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
    f.writelines(new_lines)

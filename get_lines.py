with open("app/src/main/java/com/example/ui/PosViewModel.kt", "r") as f:
    lines = f.readlines()

out = []
in_insert = False
for i, line in enumerate(lines):
    if "fun insertUser(" in line:
        in_insert = True
    
    if not in_insert:
        out.append(line)
        
    if in_insert and line.strip() == "}":
        in_insert = False

with open("app/src/main/java/com/example/ui/PosViewModel.kt", "w") as f:
    f.writelines(out)

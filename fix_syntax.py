import re
with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

# I accidentally added "*/" everywhere in a previous regex
text = text.replace("}*/", "}")
text = text.replace("*/", "")

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

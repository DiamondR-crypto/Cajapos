import os
import re

dir_path = "app/src/main/java/com/example/data/local"

for filename in os.listdir(dir_path):
    if filename.endswith(".kt") and not filename.endswith("Dao.kt") and not filename.endswith("Database.kt") and not filename.endswith("Manager.kt"):
        filepath = os.path.join(dir_path, filename)
        with open(filepath, "r") as f:
            text = f.read()
        
        if "@Serializable" not in text:
            # Add import
            text = text.replace("import androidx.room.Entity", "import androidx.room.Entity\nimport kotlinx.serialization.Serializable")
            # Add annotation before @Entity
            text = text.replace("@Entity", "@Serializable\n@Entity")
            
            with open(filepath, "w") as f:
                f.write(text)

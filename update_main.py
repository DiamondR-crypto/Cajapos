with open("app/src/main/java/com/example/MainActivity.kt", "r") as f:
    text = f.read()

import_str = "import com.example.data.repository.CloudSyncRepository\n"
if "CloudSyncRepository" not in text:
    text = text.replace("import com.example.data.repository.GeminiRepository", "import com.example.data.repository.GeminiRepository\n" + import_str)

creation_code = """
                val prefs = PreferencesManager(applicationContext)
                val repository = AppRepository(database, prefs)
                val cloudSyncRepository = CloudSyncRepository(repository)
                cloudSyncRepository.startSync()
                val geminiRepository = GeminiRepository()
                return PosViewModel(repository, geminiRepository, cloudSyncRepository) as T
"""

import re
text = re.sub(r'val prefs = PreferencesManager\(applicationContext\)\s*val repository = AppRepository\(database, prefs\)\s*val geminiRepository = GeminiRepository\(\)\s*return PosViewModel\(repository, geminiRepository\) as T', creation_code, text)

with open("app/src/main/java/com/example/MainActivity.kt", "w") as f:
    f.write(text)

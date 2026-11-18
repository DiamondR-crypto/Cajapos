import re
with open("app/src/test/java/com/example/GreetingScreenshotTest.kt", "r") as f:
    text = f.read()

text = text.replace("Greeting(\"Robolectric\")", "com.example.ui.LoginScreen(com.example.ui.PosViewModel(com.example.data.repository.AppRepository(com.example.data.local.AppDatabase.getDatabase(androidx.test.core.app.ApplicationProvider.getApplicationContext()).appDao())))")

with open("app/src/test/java/com/example/GreetingScreenshotTest.kt", "w") as f:
    f.write(text)

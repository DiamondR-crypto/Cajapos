import re
with open("app/src/test/java/com/example/ExampleRobolectricTest.kt", "r") as f:
    text = f.read()

text = text.replace("assertEquals(\"My Application\", appName)", "assertEquals(\"PosSystem\", appName)")

with open("app/src/test/java/com/example/ExampleRobolectricTest.kt", "w") as f:
    f.write(text)

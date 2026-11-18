with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "r") as f:
    text = f.read()

text = text.replace("""    private val db: FirebaseFirestore by lazy {
        val firestore = FirebaseFirestore.getInstance()
        firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .build()
        firestore
    }""", """    private val db: FirebaseFirestore? by lazy {
        try {
            val firestore = FirebaseFirestore.getInstance()
            firestore.firestoreSettings = FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build()
            firestore
        } catch (e: Exception) {
            Log.e(TAG, "Firebase no está inicializado. ¿Falta google-services.json?", e)
            null
        }
    }""")

text = text.replace("db.collection", "db?.collection")

with open("app/src/main/java/com/example/data/repository/CloudSyncRepository.kt", "w") as f:
    f.write(text)

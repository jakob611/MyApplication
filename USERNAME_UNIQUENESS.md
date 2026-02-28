# Username Uniqueness Check - UserPreferences.kt

## Dodaj novo funkcijo pred vrstico 364 (pred closing bracket):

```kotlin
    /**
     * Preveri ali je username že zaseden
     * Returns true če je username available, false če je že uporabljen
     */
    suspend fun isUsernameAvailable(username: String, currentUserEmail: String): Boolean {
        if (username.isBlank()) return false
        
        try {
            val querySnapshot = db.collection("users")
                .whereEqualTo("username", username)
                .get()
                .await()
            
            // Če ni rezultatov, je username available
            if (querySnapshot.isEmpty) return true
            
            // Če je en rezultat in je to trenutni uporabnik, je OK
            if (querySnapshot.documents.size == 1) {
                val doc = querySnapshot.documents[0]
                return doc.id == currentUserEmail
            }
            
            // Če je več rezultatov ali rezultat ni trenutni uporabnik, NI available
            return false
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }
}
```

## Dodaj v MainActivity.kt - FigmaDrawerContent (okoli vrstica 1300)

V drawer-ju kjer urejamo username, dodaj preverjanje pred save-anjem.

NAJDI blok kjer je:
```kotlin
TextButton(onClick = {
    if (isEditingPersonal) {
        onProfileUpdate(editedProfile)
    }
    isEditingPersonal = !isEditingPersonal
}) {
    Text(if (isEditingPersonal) "Save" else "Edit", color = PrimaryBlue)
}
```

ZAMENJAJ Z:
```kotlin
val scope = rememberCoroutineScope()
var usernameError by remember { mutableStateOf<String?>(null) }

TextButton(onClick = {
    if (isEditingPersonal) {
        // Preveri username uniqueness pred save-anjem
        if (editedProfile.username != userProfile.username) {
            scope.launch {
                val isAvailable = UserPreferences.isUsernameAvailable(
                    editedProfile.username,
                    userProfile.email
                )
                if (isAvailable) {
                    onProfileUpdate(editedProfile)
                    isEditingPersonal = false
                    usernameError = null
                } else {
                    usernameError = "Username already taken!"
                }
            }
        } else {
            // Username se ni spremenil
            onProfileUpdate(editedProfile)
            isEditingPersonal = false
            usernameError = null
        }
    } else {
        isEditingPersonal = true
        usernameError = null
    }
}) {
    Text(if (isEditingPersonal) "Save" else "Edit", color = PrimaryBlue)
}

// Prikaži error
if (usernameError != null) {
    Spacer(Modifier.height(8.dp))
    Text(
        usernameError!!,
        color = Color.Red,
        fontSize = 12.sp
    )
}
```

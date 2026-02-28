# Profile Picture Upload Implementation

## 1. Dodaj `profilePictureUrl` v UserProfile.kt

V vrstici 33 (za totalPlansCreated), dodaj:
```kotlin
val totalPlansCreated: Int = 0,
val profilePictureUrl: String? = null  // DODAJ TO
```

## 2. Posodobi UserPreferences.kt

### Dodaj KEY konstanto (vrstica 38):
```kotlin
private const val KEY_TOTAL_PLANS = "total_plans_created"
private const val KEY_PROFILE_PICTURE = "profile_picture_url"  // DODAJ TO
```

### Dodaj v saveProfile funkcijo (vrstica 79):
```kotlin
putInt(userKey(profile.email, KEY_TOTAL_PLANS), profile.totalPlansCreated)
putString(userKey(profile.email, KEY_PROFILE_PICTURE), profile.profilePictureUrl)  // DODAJ TO
apply()
```

### Dodaj v loadProfile funkcijo (okoli vrstica 118):
```kotlin
totalPlansCreated = prefs.getInt(userKey(email, KEY_TOTAL_PLANS), 0),
profilePictureUrl = prefs.getString(userKey(email, KEY_PROFILE_PICTURE), null)  // DODAJ TO
```

### Dodaj v saveProfileFirestore funkcijo (okoli vrstica 145):
```kotlin
"total_plans_created" to profile.totalPlansCreated,
"profile_picture_url" to profile.profilePictureUrl  // DODAJ TO
```

### Dodaj v loadProfileFromFirestore funkcijo (okoli vrstica 195):
```kotlin
totalPlansCreated = (doc.getLong("total_plans_created")?.toInt() ?: 0),
profilePictureUrl = doc.getString("profile_picture_url")  // DODAJ TO
```

## 3. Dodaj upload funkcijo v UserPreferences.kt (pred deleteUserData):

```kotlin
/**
 * Upload profile picture to Firebase Storage
 */
suspend fun uploadProfilePicture(
    email: String,
    imageUri: android.net.Uri,
    context: Context
): String? {
    return try {
        val storage = com.google.firebase.storage.FirebaseStorage.getInstance()
        val storageRef = storage.reference
        val profilePicRef = storageRef.child("profile_pictures/$email.jpg")
        
        profilePicRef.putFile(imageUri).await()
        val downloadUrl = profilePicRef.downloadUrl.await()
        
        downloadUrl.toString()
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}
```

## 4. Dodaj v build.gradle.kts (app level):

```kotlin
implementation("com.google.firebase:firebase-storage-ktx")
```

## 5. MainActivity.kt - FigmaDrawerContent

Dodaj image picker launcher:

```kotlin
val context = LocalContext.current
val imagePickerLauncher = rememberLauncherForActivityResult(
    contract = ActivityResultContracts.GetContent()
) { uri: Uri? ->
    uri?.let {
        scope.launch {
            val downloadUrl = UserPreferences.uploadProfilePicture(userProfile.email, it, context)
            if (downloadUrl != null) {
                val updatedProfile = userProfile.copy(profilePictureUrl = downloadUrl)
                onProfileUpdate(updatedProfile)
            }
        }
    }
}
```

Zamenjaj Surface(color = PrimaryBlue) s klikabilnim:

```kotlin
Surface(
    color = PrimaryBlue,
    shape = RoundedCornerShape(28.dp),
    modifier = Modifier
        .fillMaxWidth()
        .heightIn(min = 160.dp)
        .padding(vertical = 8.dp)
        .clickable { imagePickerLauncher.launch("image/*") }  // DODAJ clickable
) {
    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
        if (userProfile.profilePictureUrl != null) {
            // Prikaži sliko (uporabi Coil ali AsyncImage)
            AsyncImage(
                model = userProfile.profilePictureUrl,
                contentDescription = "Profile Picture",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        } else {
            Text(
                text = "User's\nStickman",
                color = Color.White,
                textAlign = TextAlign.Center,
                fontSize = 32.sp,
                lineHeight = 38.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}
```

## 6. Dodaj Coil za image loading v build.gradle.kts:

```kotlin
implementation("io.coil-kt:coil-compose:2.5.0")
```

## 7. Import v MainActivity.kt:

```kotlin
import coil.compose.AsyncImage
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import android.net.Uri
import androidx.compose.ui.layout.ContentScale
```

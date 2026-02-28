#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Script za dodajanje uploadProfilePicture funkcije v UserPreferences.kt

with open('app/src/main/java/com/example/myapplication/data/UserPreferences.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Najdi vrstico kjer je "// Deletes the user document from Firestore"
insert_idx = None
for i, line in enumerate(lines):
    if '// Deletes the user document from Firestore' in line:
        insert_idx = i
        break

if insert_idx is not None:
    # Nova upload funkcija
    new_function = [
        '    /**\n',
        '     * Upload profile picture to Firebase Storage\n',
        '     */\n',
        '    suspend fun uploadProfilePicture(\n',
        '        email: String,\n',
        '        imageUri: android.net.Uri\n',
        '    ): String? {\n',
        '        return try {\n',
        '            val storage = com.google.firebase.storage.FirebaseStorage.getInstance()\n',
        '            val storageRef = storage.reference\n',
        '            val profilePicRef = storageRef.child("profile_pictures/$email.jpg")\n',
        '            \n',
        '            profilePicRef.putFile(imageUri).await()\n',
        '            val downloadUrl = profilePicRef.downloadUrl.await()\n',
        '            \n',
        '            downloadUrl.toString()\n',
        '        } catch (e: Exception) {\n',
        '            e.printStackTrace()\n',
        '            null\n',
        '        }\n',
        '    }\n',
        '\n',
    ]

    # Vstavi novo funkcijo pred deleteUserData
    new_content = lines[:insert_idx] + new_function + lines[insert_idx:]

    with open('app/src/main/java/com/example/myapplication/data/UserPreferences.kt', 'w', encoding='utf-8') as f:
        f.writelines(new_content)

    print('SUCCESS: uploadProfilePicture function added to UserPreferences.kt!')
    print(f'Inserted at line {insert_idx+1}')
else:
    print('ERROR: Could not find insertion point!')

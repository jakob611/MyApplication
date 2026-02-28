#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Script za dodajanje isUsernameAvailable funkcije v UserPreferences.kt

with open('app/src/main/java/com/example/myapplication/data/UserPreferences.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Najdi vrstico kjer je "// Deletes the user document from Firestore"
insert_idx = None
for i, line in enumerate(lines):
    if '// Deletes the user document from Firestore' in line:
        insert_idx = i
        break

if insert_idx is not None:
    # Nova funkcija
    new_function = [
        '    /**\n',
        '     * Preveri ali je username že zaseden\n',
        '     * Returns true če je username available, false če je že uporabljen\n',
        '     */\n',
        '    suspend fun isUsernameAvailable(username: String, currentUserEmail: String): Boolean {\n',
        '        if (username.isBlank()) return false\n',
        '        \n',
        '        try {\n',
        '            val querySnapshot = db.collection("users")\n',
        '                .whereEqualTo("username", username)\n',
        '                .get()\n',
        '                .await()\n',
        '            \n',
        '            // Če ni rezultatov, je username available\n',
        '            if (querySnapshot.isEmpty) return true\n',
        '            \n',
        '            // Če je en rezultat in je to trenutni uporabnik, je OK\n',
        '            if (querySnapshot.documents.size == 1) {\n',
        '                val doc = querySnapshot.documents[0]\n',
        '                return doc.id == currentUserEmail\n',
        '            }\n',
        '            \n',
        '            // Če je več rezultatov ali rezultat ni trenutni uporabnik, NI available\n',
        '            return false\n',
        '        } catch (e: Exception) {\n',
        '            e.printStackTrace()\n',
        '            return false\n',
        '        }\n',
        '    }\n',
        '\n',
    ]

    # Vstavi novo funkcijo pred deleteUserData
    new_content = lines[:insert_idx] + new_function + lines[insert_idx:]

    with open('app/src/main/java/com/example/myapplication/data/UserPreferences.kt', 'w', encoding='utf-8') as f:
        f.writelines(new_content)

    print('SUCCESS: isUsernameAvailable function added to UserPreferences.kt!')
    print(f'Inserted at line {insert_idx+1}')
else:
    print('ERROR: Could not find insertion point!')

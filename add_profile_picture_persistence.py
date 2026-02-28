#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Script za dodajanje profilePictureUrl persistence v UserPreferences.kt

with open('app/src/main/java/com/example/myapplication/data/UserPreferences.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# 1. Dodaj KEY konstanto
content = content.replace(
    'private const val KEY_TOTAL_PLANS = "total_plans_created"',
    'private const val KEY_TOTAL_PLANS = "total_plans_created"\n    private const val KEY_PROFILE_PICTURE = "profile_picture_url"'
)

# 2. Dodaj v saveProfile (after totalPlansCreated)
content = content.replace(
    'putInt(userKey(profile.email, KEY_TOTAL_PLANS), profile.totalPlansCreated)',
    'putInt(userKey(profile.email, KEY_TOTAL_PLANS), profile.totalPlansCreated)\n            putString(userKey(profile.email, KEY_PROFILE_PICTURE), profile.profilePictureUrl)'
)

# 3. Dodaj v loadProfile (after totalPlansCreated)
content = content.replace(
    'totalPlansCreated = prefs.getInt(userKey(email, KEY_TOTAL_PLANS), 0)',
    'totalPlansCreated = prefs.getInt(userKey(email, KEY_TOTAL_PLANS), 0),\n            profilePictureUrl = prefs.getString(userKey(email, KEY_PROFILE_PICTURE), null)'
)

# 4. Dodaj v saveProfileFirestore
content = content.replace(
    '"total_plans_created" to profile.totalPlansCreated',
    '"total_plans_created" to profile.totalPlansCreated,\n                "profile_picture_url" to profile.profilePictureUrl'
)

# 5. Dodaj v loadProfileFromFirestore (after totalPlansCreated)
content = content.replace(
    'totalPlansCreated = (doc.getLong("total_plans_created")?.toInt() ?: 0)',
    'totalPlansCreated = (doc.getLong("total_plans_created")?.toInt() ?: 0),\n                profilePictureUrl = doc.getString("profile_picture_url")'
)

with open('app/src/main/java/com/example/myapplication/data/UserPreferences.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print('SUCCESS: profilePictureUrl persistence added to UserPreferences.kt!')

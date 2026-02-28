import re

# Read file
with open('app/src/main/java/com/example/myapplication/data/UserPreferences.kt', 'r', encoding='utf-8') as f:
    content = f.read()

# Add import if missing
if 'import android.util.Log' not in content:
    content = content.replace(
        'import kotlinx.coroutines.tasks.await',
        'import kotlinx.coroutines.tasks.await\nimport android.util.Log'
    )
    print("✅ Added import Log")
else:
    print("ℹ️ Log already imported")

# Add log at start of saveProfileFirestore
old_start = '''    // Firestore: save/load full user profile (remote single source of truth)
    suspend fun saveProfileFirestore(profile: UserProfile) {
        if (profile.email.isBlank()) return
        val uid = Firebase.auth.currentUser?.uid ?: return'''

new_start = '''    // Firestore: save/load full user profile (remote single source of truth)
    suspend fun saveProfileFirestore(profile: UserProfile) {
        Log.d("UserPreferences", "🔥 saveProfileFirestore CALLED! email=${profile.email}, height=${profile.height}, age=${profile.age}, gender=${profile.gender}")
        if (profile.email.isBlank()) {
            Log.e("UserPreferences", "❌ Email is blank!")
            return
        }
        val uid = Firebase.auth.currentUser?.uid
        if (uid == null) {
            Log.e("UserPreferences", "❌ UID is null! User not authenticated!")
            return
        }
        Log.d("UserPreferences", "🔥 Got UID: $uid")'''

if old_start in content:
    content = content.replace(old_start, new_start)
    print("✅ Added log at function start")
else:
    print("⚠️ Could not find function start pattern")

# Add log after successful save
old_save = '''            db.collection("users")
                .document(uid)  // ?? POPRAVEK: uporablja UID!
                .set(data, SetOptions.merge())
                .await()'''

new_save = '''            db.collection("users")
                .document(uid)  // 🔥 POPRAVEK: uporablja UID!
                .set(data, SetOptions.merge())
                .await()
            Log.d("UserPreferences", "✅ Profile SAVED to Firestore! UID=$uid, height=${profile.height}, age=${profile.age}, gender=${profile.gender}")'''

if old_save in content:
    content = content.replace(old_save, new_save)
    print("✅ Added log after save")
else:
    print("⚠️ Could not find save pattern")

# Add log on error
old_error = '''        } catch (e: Exception) {
            e.printStackTrace()
        }
    }'''

new_error = '''        } catch (e: Exception) {
            Log.e("UserPreferences", "❌ Error saving profile to Firestore", e)
            e.printStackTrace()
        }
    }'''

if old_error in content:
    content = content.replace(old_error, new_error)
    print("✅ Added log on error")
else:
    print("⚠️ Could not find error pattern")

# Write back
with open('app/src/main/java/com/example/myapplication/data/UserPreferences.kt', 'w', encoding='utf-8') as f:
    f.write(content)

print("\n✅ All LOG messages added to UserPreferences.kt!")

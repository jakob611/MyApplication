# Privacy Settings Fix - MyAccountScreen.kt

## Problem
Ko klikneš na sub-setting (Show Level, Show Badges...), se Public Profile samodejno ugasne.

## Root Cause
V vrstici 151, 167, 183, 199, 215:
```kotlin
onProfileUpdate(userProfile.copy(showLevel = it))
```

Ko narediš `userProfile.copy(showLevel = it)`, **NE ohraniš** ostalih fieldov kot `isPublicProfile`.
Ker UserProfile ima default `isPublicProfile = false`, se to resetira na false!

## Fix

### Master Toggle (vrstica 128-131)
SPREMENI:
```kotlin
onCheckedChange = {
    isPublic = it
    onProfileUpdate(userProfile.copy(isPublicProfile = it))
}
```

V:
```kotlin
onCheckedChange = { newValue ->
    isPublic = newValue
    if (!newValue) {
        showLevel = false
        showBadges = false
        showPlanPath = false
        showChallenges = false
        showFollowers = false
    }
    onProfileUpdate(userProfile.copy(
        isPublicProfile = newValue,
        showLevel = if (newValue) showLevel else false,
        showBadges = if (newValue) showBadges else false,
        showPlanPath = if (newValue) showPlanPath else false,
        showChallenges = if (newValue) showChallenges else false,
        showFollowers = if (newValue) showFollowers else false
    ))
}
```

### Show Level (vrstica 149-152)
SPREMENI:
```kotlin
onCheckedChange = {
    showLevel = it
    onProfileUpdate(userProfile.copy(showLevel = it))
}
```

V:
```kotlin
onCheckedChange = { newValue ->
    showLevel = newValue
    onProfileUpdate(userProfile.copy(
        isPublicProfile = true,
        showLevel = newValue
    ))
}
```

### Show Badges (vrstica 165-168)
SPREMENI:
```kotlin
onCheckedChange = {
    showBadges = it
    onProfileUpdate(userProfile.copy(showBadges = it))
}
```

V:
```kotlin
onCheckedChange = { newValue ->
    showBadges = newValue
    onProfileUpdate(userProfile.copy(
        isPublicProfile = true,
        showBadges = newValue
    ))
}
```

### Show Plan Path (vrstica 181-184)
SPREMENI:
```kotlin
onCheckedChange = {
    showPlanPath = it
    onProfileUpdate(userProfile.copy(showPlanPath = it))
}
```

V:
```kotlin
onCheckedChange = { newValue ->
    showPlanPath = newValue
    onProfileUpdate(userProfile.copy(
        isPublicProfile = true,
        showPlanPath = newValue
    ))
}
```

### Show Challenges (vrstica 197-200)
SPREMENI:
```kotlin
onCheckedChange = {
    showChallenges = it
    onProfileUpdate(userProfile.copy(showChallenges = it))
}
```

V:
```kotlin
onCheckedChange = { newValue ->
    showChallenges = newValue
    onProfileUpdate(userProfile.copy(
        isPublicProfile = true,
        showChallenges = newValue
    ))
}
```

### Show Followers (vrstica 213-216)
SPREMENI:
```kotlin
onCheckedChange = {
    showFollowers = it
    onProfileUpdate(userProfile.copy(showFollowers = it))
}
```

V:
```kotlin
onCheckedChange = { newValue ->
    showFollowers = newValue
    onProfileUpdate(userProfile.copy(
        isPublicProfile = true,
        showFollowers = newValue
    ))
}
```

## KEY FIX
Dodaj `isPublicProfile = true` v vsak `userProfile.copy()` klic znotraj sub-settings!

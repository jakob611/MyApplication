# Follow vlastnega profila - PublicProfileScreen.kt

## Sprememba v vrstici 137-177

ZAMENJAJ:
```kotlin
// Follow/Unfollow Button
Button(
    onClick = {
        scope.launch {
            isLoading = true
            val success = if (isFollowing) {
                FollowStore.unfollowUser(currentUserId, profile.userId)
            } else {
                FollowStore.followUser(currentUserId, profile.userId)
            }

            if (success) {
                isFollowing = !isFollowing
                followerCount += if (isFollowing) 1 else -1
            }
            isLoading = false
        }
    },
    enabled = !isLoading && profile.userId != currentUserId,
    colors = ButtonDefaults.buttonColors(
        containerColor = if (isFollowing) Color.Gray else accentBlue
    ),
    modifier = Modifier.fillMaxWidth(0.8f)
) {
    if (isLoading) {
        CircularProgressIndicator(
            modifier = Modifier.size(20.dp),
            color = Color.White
        )
    } else {
        Icon(
            if (isFollowing) Icons.Filled.PersonRemove else Icons.Filled.PersonAdd,
            contentDescription = null
        )
        Spacer(Modifier.width(8.dp))
        Text(
            if (isFollowing) "Unfollow" else "Follow",
            fontWeight = FontWeight.Bold
        )
    }
}
```

Z:
```kotlin
// Follow/Unfollow Button ali "Your Profile" indicator
if (profile.userId == currentUserId) {
    // Če je tvoj profil, prikaži indicator namesto gumba
    Card(
        modifier = Modifier.fillMaxWidth(0.8f),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF374151)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Person,
                contentDescription = null,
                tint = accentGreen
            )
            Spacer(Modifier.width(8.dp))
            Text(
                "This is your profile",
                fontWeight = FontWeight.Bold,
                color = accentGreen
            )
        }
    }
} else {
    // Če je tuj profil, prikaži Follow/Unfollow gumb
    Button(
        onClick = {
            scope.launch {
                isLoading = true
                val success = if (isFollowing) {
                    FollowStore.unfollowUser(currentUserId, profile.userId)
                } else {
                    FollowStore.followUser(currentUserId, profile.userId)
                }

                if (success) {
                    isFollowing = !isFollowing
                    followerCount += if (isFollowing) 1 else -1
                }
                isLoading = false
            }
        },
        enabled = !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = if (isFollowing) Color.Gray else accentBlue
        ),
        modifier = Modifier.fillMaxWidth(0.8f)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color.White
            )
        } else {
            Icon(
                if (isFollowing) Icons.Filled.PersonRemove else Icons.Filled.PersonAdd,
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isFollowing) "Unfollow" else "Follow",
                fontWeight = FontWeight.Bold
            )
        }
    }
}
```

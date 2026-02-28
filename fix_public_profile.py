#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Script za popravek PublicProfileScreen.kt - onemogoči follow na lastnem profilu

with open('app/src/main/java/com/example/myapplication/ui/screens/PublicProfileScreen.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Najdi start (vrstica 137: // Follow/Unfollow Button)
# Najdi end (vrstica 179: // Stats)
start_idx = None
end_idx = None

for i, line in enumerate(lines):
    if '// Follow/Unfollow Button' in line:
        start_idx = i
    if start_idx is not None and '// Stats' in line:
        end_idx = i
        break

if start_idx is not None and end_idx is not None:
    # Sestavi nov blok kode
    new_block_lines = [
        '                        // Follow/Unfollow Button ali "Your Profile" indicator\n',
        '                        if (profile.userId == currentUserId) {\n',
        '                            // Če je tvoj profil, prikaži indicator namesto gumba\n',
        '                            Card(\n',
        '                                modifier = Modifier.fillMaxWidth(0.8f),\n',
        '                                colors = CardDefaults.cardColors(\n',
        '                                    containerColor = Color(0xFF374151)\n',
        '                                )\n',
        '                            ) {\n',
        '                                Row(\n',
        '                                    modifier = Modifier\n',
        '                                        .fillMaxWidth()\n',
        '                                        .padding(16.dp),\n',
        '                                    horizontalArrangement = Arrangement.Center,\n',
        '                                    verticalAlignment = Alignment.CenterVertically\n',
        '                                ) {\n',
        '                                    Icon(\n',
        '                                        Icons.Filled.Person,\n',
        '                                        contentDescription = null,\n',
        '                                        tint = accentGreen\n',
        '                                    )\n',
        '                                    Spacer(Modifier.width(8.dp))\n',
        '                                    Text(\n',
        '                                        "This is your profile",\n',
        '                                        fontWeight = FontWeight.Bold,\n',
        '                                        color = accentGreen\n',
        '                                    )\n',
        '                                }\n',
        '                            }\n',
        '                        } else {\n',
        '                            // Če je tuj profil, prikaži Follow/Unfollow gumb\n',
        '                            Button(\n',
        '                                onClick = {\n',
        '                                    scope.launch {\n',
        '                                        isLoading = true\n',
        '                                        val success = if (isFollowing) {\n',
        '                                            FollowStore.unfollowUser(currentUserId, profile.userId)\n',
        '                                        } else {\n',
        '                                            FollowStore.followUser(currentUserId, profile.userId)\n',
        '                                        }\n',
        '\n',
        '                                        if (success) {\n',
        '                                            isFollowing = !isFollowing\n',
        '                                            followerCount += if (isFollowing) 1 else -1\n',
        '                                        }\n',
        '                                        isLoading = false\n',
        '                                    }\n',
        '                                },\n',
        '                                enabled = !isLoading,\n',
        '                                colors = ButtonDefaults.buttonColors(\n',
        '                                    containerColor = if (isFollowing) Color.Gray else accentBlue\n',
        '                                ),\n',
        '                                modifier = Modifier.fillMaxWidth(0.8f)\n',
        '                            ) {\n',
        '                                if (isLoading) {\n',
        '                                    CircularProgressIndicator(\n',
        '                                        modifier = Modifier.size(20.dp),\n',
        '                                        color = Color.White\n',
        '                                    )\n',
        '                                } else {\n',
        '                                    Icon(\n',
        '                                        if (isFollowing) Icons.Filled.PersonRemove else Icons.Filled.PersonAdd,\n',
        '                                        contentDescription = null\n',
        '                                    )\n',
        '                                    Spacer(Modifier.width(8.dp))\n',
        '                                    Text(\n',
        '                                        if (isFollowing) "Unfollow" else "Follow",\n',
        '                                        fontWeight = FontWeight.Bold\n',
        '                                    )\n',
        '                                }\n',
        '                            }\n',
        '                        }\n',
        '\n',
    ]

    # Sestavi nov file: začetek + novi blok + konec
    new_content = lines[:start_idx] + new_block_lines + lines[end_idx:]

    with open('app/src/main/java/com/example/myapplication/ui/screens/PublicProfileScreen.kt', 'w', encoding='utf-8') as f:
        f.writelines(new_content)

    print('SUCCESS: PublicProfileScreen.kt fixed!')
    print(f'Replaced lines {start_idx+1} to {end_idx}')
else:
    print('ERROR: Could not find markers!')
    print(f'start_idx={start_idx}, end_idx={end_idx}')

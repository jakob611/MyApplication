#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Script za dodajanje profilePictureUrl v UserProfile.kt

with open('app/src/main/java/com/example/myapplication/data/UserProfile.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

# Najdi vrstico "val totalPlansCreated: Int = 0"
for i, line in enumerate(lines):
    if 'val totalPlansCreated: Int = 0' in line:
        # Zamenjaj z dvema vrsticama
        lines[i] = '    val totalPlansCreated: Int = 0,\n'
        lines.insert(i + 1, '    val profilePictureUrl: String? = null\n')
        break

with open('app/src/main/java/com/example/myapplication/data/UserProfile.kt', 'w', encoding='utf-8') as f:
    f.writelines(lines)

print('SUCCESS: profilePictureUrl added to UserProfile.kt!')

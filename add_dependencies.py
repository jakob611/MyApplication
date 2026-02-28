#!/usr/bin/env python3
# -*- coding: utf-8 -*-

# Script za dodajanje Firebase Storage in Coil dependencies

with open('app/build.gradle.kts', 'r', encoding='utf-8') as f:
    content = f.read()

# Dodaj Firebase Storage dependency (after firebase-firestore-ktx)
content = content.replace(
    'implementation("com.google.firebase:firebase-firestore-ktx")',
    'implementation("com.google.firebase:firebase-firestore-ktx")\n    implementation("com.google.firebase:firebase-storage-ktx")'
)

# Dodaj Coil dependency (after gson)
content = content.replace(
    'implementation("com.google.code.gson:gson:2.10.1")',
    'implementation("com.google.code.gson:gson:2.10.1")\n    implementation("io.coil-kt:coil-compose:2.5.0")'
)

with open('app/build.gradle.kts', 'w', encoding='utf-8') as f:
    f.write(content)

print('SUCCESS: Firebase Storage and Coil dependencies added!')

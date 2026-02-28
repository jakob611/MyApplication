# Firebase Storage Setup - KRITIČNO ZA PROFILE PICTURE UPLOAD

## Problem
Profile picture upload NE DELA ker Firebase Storage Rules **zavračajo** upload requeste!

## Rešitev

### Korak 1: Odpri Firebase Console
1. Pojdi na https://console.firebase.google.com/
2. Izberi svoj projekt
3. V levem meniju klikni **Storage**
4. Klikni na **Rules** tab

### Korak 2: Posodobi Storage Rules
Zamenjaj obstoječe rules s temi (POZOR: brez .jpg v match patternu!):

```
rules_version = '2';
service firebase.storage {
  match /b/{bucket}/o {
    // Profile pictures folder - authenticated users lahko uploadajo
    match /profile_pictures/{fileName} {
      allow read: if true; // Vsi lahko berejo
      allow write: if request.auth != null; // Samo prijavljeni lahko uploadajo
    }
    
    // Catch-all za ostale datoteke
    match /{allPaths=**} {
      allow read, write: if request.auth != null;
    }
  }
}
```

**POMEMBNO:** Firebase Storage Rules **NE PODPIRAJO** ekstenzij v match pattern (npr. `{userId}.jpg` NE DELA)!
Zato app uporablja Firebase Auth UID za filename (`{uid}.jpg`), rules pa samo preverjajo ali je user prijavljen.

### Korak 3: Publish Rules
Klikni **Publish** gumb v Firebase Console

## Kaj te rules omogočajo?
- ✅ Authenticated users lahko naložijo svojo profile picture
- ✅ Vsi lahko berejo profile pictures (za public profiles)
- ✅ User lahko samo svojo sliko uploada (ne more tuje)
- ✅ Email se uporablja kot filename (varno)

## Preverba
Po nastavitvi rules, poskusi ponovno:
1. Odpri app
2. Klikni na avatar v drawer-ju
3. Izberi sliko
4. Če vidiš krogec in slika se NE naloži → preveri Logcat za errore
5. Filter: `ProfilePicture` za debug messages

## Logcat Komande
```bash
# Windows PowerShell
adb logcat -s ProfilePicture

# Če vidiš "Upload failed" → preveri error message
# Če vidiš "Permission denied" → Firebase Storage Rules niso pravilno nastavljene
```

## Pogoste Napake
- **Permission denied** → Storage rules zavračajo upload
- **Network error** → Preveri internet povezavo
- **Auth error** → User ni prijavljen
- **Null URI** → Image picker ni vrnil slike

## Backup Rešitev (Če ne moreš urediti rules)
Če nimaš dostopa do Firebase Console, lahko v UserPreferences.kt:
1. Spremeniš `profile_pictures/$email.jpg` v `profile_pictures/test.jpg`
2. V Storage Rules dodaj public write pravico (NI varno za produkcijo!)

```
match /profile_pictures/test.jpg {
  allow read, write: if true;
}
```

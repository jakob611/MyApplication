# Exercise Video Animations

## Pregled

Vsaka vaja v workout sessionu zdaj prikazuje video animacijo, ki uporabniku kaže pravilno izvedbo vaje.

## Implementacija

### 1. Video Storage
Videi so shranjeni v Google Cloud Storage bucket:
```
https://storage.cloud.google.com/fitness-videos-glowupp/
```

### 2. Video URL Format
Video URL-ji sledijo standardnemu formatu:
```
https://storage.cloud.google.com/fitness-videos-glowupp/[Exercise_Name]_female.mp4
```

Primeri:
- Vaja: "180 Jump Turns"
- URL: `https://storage.cloud.google.com/fitness-videos-glowupp/180_Jump_Turns_female.mp4`

- Vaja: "3 Leg Chatarunga Pose"  
- URL: `https://storage.cloud.google.com/fitness-videos-glowupp/3_Leg_Chatarunga_Pose_female.mp4`

- Vaja: "Push-ups"
- URL: `https://storage.cloud.google.com/fitness-videos-glowupp/Pushups_female.mp4`

### 3. Normalizacija Imen Vaj
Funkcija `getVideoUrlForExercise()` normalizira ime vaje:
- Presledki → `_`
- Pomišljaji → `_`  
- Odstrani posebne znake
- Dodaj `_female.mp4` suffix

Primer transformacij:
- "180 Jump Turns" → "180_Jump_Turns_female.mp4"
- "3 Leg Chatarunga Pose" → "3_Leg_Chatarunga_Pose_female.mp4"
- "Push-ups" → "Pushups_female.mp4"
- "Goblet Squat" → "Goblet_Squat_female.mp4"
- "Barbell Bench Press" → "Barbell_Bench_Press_female.mp4"

### 4. ExoPlayer Integracija

#### Dependencies
```kotlin
implementation("androidx.media3:media3-exoplayer:1.2.1")
implementation("androidx.media3:media3-ui:1.2.1")
implementation("androidx.media3:media3-common:1.2.1")
```

#### Uporaba
Video player je vgrajen v `ManualExerciseLogScreen` composable:

**V ExerciseListItem** (prikaz seznama vaj):
```kotlin
val videoUrl = remember(exercise.name) { getVideoUrlForExercise(exercise.name) }
val exoPlayer = remember(videoUrl) {
    ExoPlayer.Builder(context).build().apply {
        setMediaItem(MediaItem.fromUri(videoUrl))
        prepare()
        repeatMode = Player.REPEAT_MODE_ALL  // Loop video
        playWhenReady = true  // Auto-play
        volume = 0f  // Mute video
    }
}

// Cleanup
DisposableEffect(exoPlayer) {
    onDispose { exoPlayer.release() }
}

// Player view
AndroidView(
    factory = { ctx ->
        PlayerView(ctx).apply {
            player = exoPlayer
            useController = false  // Auto-play, no controls
        }
    },
    modifier = Modifier.fillMaxWidth().height(200.dp)
)
```

**V ExerciseEntryScreen** (vnos podatkov za vajo):
- Isti setup kot zgoraj
- Video je prikazan na vrhu ekrana (250dp višine)
- Uporabnik vidi pravilno izvedbo vaje med vnašanjem podatkov

### 5. Značilnosti

✅ **Auto-play**: Video se avtomatsko začne predvajati
✅ **Loop**: Video se ponovi v zanki
✅ **No controls**: Brez kontrolnih gumbov za čist UI
✅ **Responsive**: Prilagaja se širini ekrana
✅ **Memory efficient**: ExoPlayer se avtomatsko release-a ko uporabnik zapusti vajo

## Dodajanje Novih Videov

Za dodajanje novih video animacij:

1. **Priprava videa**:
   - Format: MP4
   - Resolucija: 720p ali 1080p
   - Dolžina: 10-30 sekund (loop-able)
   - Velikost: < 5MB za optimalno nalaganje

2. **Poimenovanje**:
   - Uporabljaj enako ime kot ima vaja v bazi
   - Odstrani posebne znake
   - Primer: `Exercise_Name_Female.mp4`

3. **Upload v Cloud Storage**:
   ```bash
   gsutil cp your_video.mp4 gs://fitness-videos-glowupp/
   ```

4. **Preverjanje**:
   - URL mora biti javno dostopen
   - Test URL v brskalniku
   - Preveri v aplikaciji

## Male vs Female Verzije

Trenutno implementacija uporablja `_Female.mp4` verzijo.
Za dodajanje moških verzij:
1. Dodaj `_Male.mp4` videe v isti bucket
2. Posodobi `getVideoUrlForExercise()` funkcijo da prebere user profile gender
3. Vrni ustrezen URL glede na spol

## Fallback Strategy

Če video ni na voljo:
- ExoPlayer bo prikazal prazen player
- Vaja se lahko še vedno izvede normalno
- Uporabnik vidi description text kot navodila

## Troubleshooting

### Video se ne naloži
1. Preveri internet povezavo
2. Preveri da URL obstaja (odpri v brskalniku)
3. Preveri public access permissions v Cloud Storage
4. Preveri ExoPlayer error logs

### Video se ne ponavlja
- Preveri da je `repeatMode = Player.REPEAT_MODE_ALL` nastavljen

### Počasno nalaganje
- Zmanjšaj velikost videa (compress)
- Uporabljaj CDN ali caching
- Preveri video codec (H.264 je najboljši)

## Prihodnje Izboljšave

🔄 **Prednalaganje**: Prednaložitev naslednjega videa med currentnim exercise  
🎯 **Caching**: Lokalno cachanje pogosto uporabljenih videov  
👤 **Gender Selection**: Izbira med male/female verzijami  
🌐 **Offline Mode**: Download videov za offline uporabo  
📊 **Analytics**: Spremljanje katere vaje potrebujejo boljše videe  
🎨 **Thumbnail Preview**: Preview frame pred začetkom vaje

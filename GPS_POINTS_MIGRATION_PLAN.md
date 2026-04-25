# GPS polylinePoints — Migracijski načrt (1MB Firestore limit fix)

**Datum:** 2026-04-25  
**Prioriteta:** 🔴 VISOKA — crash pri tekih > 60 min zaporedoma

---

## 🚨 PROBLEM

`runSessions/{sessionId}` dokument vsebuje `polylinePoints` kot **vgraden array**:

```kotlin
"polylinePoints" to finalLocationPoints.map { mapOf("lat", "lng", "alt", "spd", "acc", "ts") }
```

### Izračun tveganja
| Tek | GPS točke (1/s) | Ocenjena velikost dokumenta |
|-----|-----------------|------------------------------|
| 30 min | ~1.800 točk | ~90 KB ✅ |
| 1 ura | ~3.600 točk | ~180 KB ✅ |
| 2 ure | ~7.200 točk | ~360 KB ⚠️ |
| Maraton (4h) | ~14.400 točk | ~720 KB + ostala polja → **>1 MB** 💥 |

Firestore trdi dokument limit = **1.048.576 bytes (1MB)**. Preseganje povzroči `FAILED_PRECONDITION` crash.

---

## ✅ REŠITEV: Sub-kolekcija `points/`

### Ciljna struktura Firestore
```
users/{uid}/
  runSessions/{sessionId}    ← metadata (brez polylinePoints)
    points/{chunk_0}         ← GPS točke 0–499
    points/{chunk_1}         ← GPS točke 500–999
    ...
```

### Migracija v RunTrackerScreen.kt

**Korak 1 — Shrani session brez `polylinePoints`:**
```kotlin
val runMap = hashMapOf<String, Any>(
    "id" to sessionId,
    // ... ostala polja ...
    // ⛔ NE: "polylinePoints" to finalLocationPoints.map { ... }
    "pointsCount" to finalLocationPoints.size  // samo metadata
)
resolvedDocRef.collection("runSessions").document(sessionId).set(runMap).await()
```

**Korak 2 — Shrani GPS točke v sub-kolekcijo po chunkih:**
```kotlin
suspend fun savePointsToSubcollection(
    sessionRef: DocumentReference,
    points: List<LocationPoint>,
    chunkSize: Int = 500
) {
    val batch = FirebaseFirestore.getInstance().batch()
    points.chunked(chunkSize).forEachIndexed { idx, chunk ->
        val chunkRef = sessionRef.collection("points").document("chunk_$idx")
        batch.set(chunkRef, mapOf(
            "pts" to chunk.map { mapOf("lat" to it.latitude, "lng" to it.longitude,
                "alt" to it.altitude, "ts" to it.timestamp) },
            "chunkIndex" to idx
        ))
    }
    batch.commit().await()
}
```

**Korak 3 — Branje v ActivityLogScreen / RunTrackerViewModel:**
```kotlin
suspend fun loadPoints(sessionRef: DocumentReference): List<LocationPoint> {
    val chunks = sessionRef.collection("points")
        .orderBy("chunkIndex")
        .get().await()
    return chunks.documents.flatMap { doc ->
        @Suppress("UNCHECKED_CAST")
        val pts = doc.get("pts") as? List<Map<String, Any>> ?: emptyList()
        pts.mapNotNull { pt ->
            LocationPoint(
                latitude  = (pt["lat"] as? Number)?.toDouble() ?: return@mapNotNull null,
                longitude = (pt["lng"] as? Number)?.toDouble() ?: return@mapNotNull null,
                altitude  = (pt["alt"] as? Number)?.toDouble() ?: 0.0,
                speed     = 0f, accuracy = 0f,
                timestamp = (pt["ts"] as? Number)?.toLong() ?: 0L
            )
        }
    }
}
```

---

## 📋 VRSTNI RED IMPLEMENTACIJE

1. **RunTrackerScreen.kt** (~vrstica 657):
   - Odstrani `"polylinePoints" to ...` iz `runMap`
   - Po uspešnem `set(runMap)` kliči `savePointsToSubcollection()`

2. **RunTrackerViewModel.kt** / **ActivityLogScreen.kt**:
   - Zamenjaj `run.polylinePoints` z `loadPoints(sessionRef)`
   - Dodaj lazy loading (nalagaj točke samo ko je sesija odprta)

3. **FirestoreWorkoutRepository.kt** (vrstica 64):
   - Že vrača `polylinePoints = emptyList()` → v redu, ne potrebuje sprememb

4. **RunSession.toFirestoreMap()**:
   - Odstrani `"polylinePoints"` iz mape
   - Dodaj `"pointsCount" to polylinePoints.size`

---

## ⚠️ BACKWARDS COMPATIBILITY

Stari dokumenti (pred migracijo) imajo `polylinePoints` inline.  
V bralni logiki naredi fallback:

```kotlin
// Najprej poskusi sub-kolekcijo (novi format)
val subPts = loadPoints(sessionRef)
if (subPts.isNotEmpty()) {
    return subPts
}
// Fallback: stari vgradeni array
val inline = doc.get("polylinePoints") as? List<*> ?: emptyList<Any>()
return inline.mapNotNull { /* parse */ }
```

---

## OCENA DELA

| Korak | Zahtevnost | Datoteke |
|-------|-----------|---------|
| Write migration | 🟡 2h | `RunTrackerScreen.kt` |
| Read fallback | 🟡 2h | `RunTrackerViewModel.kt`, `ActivityLogScreen.kt` |
| RunSession model cleanup | 🟢 30min | `RunSession.kt` |
| Test z dolgim tekom | 🟡 1h | — |

**Skupaj: ~5-6h dela**


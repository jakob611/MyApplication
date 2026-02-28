# ✅ LoadingWorkout Screen - Fix za PlanPathDialog blisk

## 🎯 Problem

Ko uporabnik klikne "START DAY X" ali "Extra Workout" v PlanPathDialog-u, se je dialog zaprl in za kratek trenutek prikazal BodyModuleHomeScreen preden se je prikazal WorkoutSession.

**Razlog:** Dialog se je najprej zaprl (`showPlanPath.value = false`), potem je šele začela navigacija (`onStartWorkout(currentPlan)`).

---

## ✅ Rešitev

Implementiral sem **LoadingWorkout Screen** z animiranim "Advanced algorithm working..." sporočilom, ki se prikaže namesto bliska BodyModuleHome screena.

---

## 🔧 Implementirane spremembe

### 1. **Nov LoadingWorkout Screen** (`LoadingWorkoutScreen.kt`)

```kotlin
@Composable
fun LoadingWorkoutScreen(
    onLoadingComplete: () -> Unit
) {
    // Dark background gradient
    val backgroundGradient = Brush.verticalGradient(...)
    
    // Animate loading
    LaunchedEffect(Unit) {
        delay(1500) // 1.5 seconds loading simulation
        onLoadingComplete()
    }
    
    // Spinning loader + pulsing text + animated dots
    Box(...) {
        Column {
            // Spinning arc loader
            Canvas { ... }
            
            // "Advanced Algorithm Working..." text
            Text(...)
            
            // "Preparing your personalized workout" subtitle
            Text(...)
            
            // Animated dots
            LoadingDots()
        }
    }
}
```

**Features:**
- ✅ Dark gradient background (prehod iz temne modre v črno)
- ✅ Animiran krog (spinning arc loader)
- ✅ Pulsirajoči glavni tekst "Advanced Algorithm Working..."
- ✅ Subtitle "Preparing your personalized workout"
- ✅ Animirane pike (3 dots ki pulzirajo)
- ✅ Loading simulacija 1.5 sekunde

---

### 2. **Screen.LoadingWorkout** dodan v MainActivity.kt

```kotlin
sealed class Screen {
    // ...existing screens...
    object LoadingWorkout : Screen() // Loading screen for workout preparation
    // ...
}
```

---

### 3. **BodyModuleHomeScreen posodobljen**

#### Dodan nov parameter `onStartWorkoutLoading`

```kotlin
@Composable
fun BodyModuleHomeScreen(
    onBack: () -> Unit,
    onStartPlan: () -> Unit,
    onStartWorkout: (PlanResult?) -> Unit,
    onStartWorkoutLoading: () -> Unit = {}, // ← NOV parameter
    currentPlan: PlanResult?,
    onOpenHistory: () -> Unit,
    onOpenManualLog: () -> Unit,
    onStartRun: () -> Unit = {}
) { ... }
```

#### PlanPathDialog callback posodobljen

**PREJ:**
```kotlin
PlanPathDialog(
    onStartToday = {
        showPlanPath.value = false  // ← Najprej zapri dialog
        onStartWorkout(currentPlan) // ← Potem navigiraj
    },
    onStartAdditional = {
        showPlanPath.value = false
        onStartWorkout(currentPlan)
    }
)
```

**SEDAJ:**
```kotlin
PlanPathDialog(
    onStartToday = {
        // Don't close dialog, navigate to loading screen
        onStartWorkoutLoading() // ← Direktno na loading brez zapiranja
    },
    onStartAdditional = {
        // Don't close dialog, navigate to loading screen
        onStartWorkoutLoading()
    }
)
```

**Zakaj to reši problem?**
- Dialog ostane odprt (ne prikaže se BodyModuleHome v ozadju)
- Navigacija gre direktno na LoadingWorkout screen preko celotnega dialoga
- LoadingWorkout screen pokrije celoten zaslon z loading animacijo

---

### 4. **MainActivity posodobljena**

#### LoadingWorkout screen handling

```kotlin
when {
    currentScreen is Screen.LoadingWorkout -> LoadingWorkoutScreen(
        onLoadingComplete = {
            // After loading, go to workout session with the plan
            selectedPlan = plans.maxByOrNull { it.createdAt }
            navigateTo(Screen.WorkoutSession)
        }
    )
    currentScreen is Screen.BodyModuleHome -> BodyModuleHomeScreen(
        // ...existing params...
        onStartWorkoutLoading = {
            // Go directly to loading screen without closing dialog
            navigateTo(Screen.LoadingWorkout)
        },
        // ...
    )
    // ...
}
```

#### Top bar visibility

```kotlin
topBar = {
    if (currentScreen !is Screen.Index &&
        currentScreen !is Screen.ProFeatures &&
        currentScreen !is Screen.BarcodeScanner &&
        currentScreen !is Screen.WorkoutSession &&
        currentScreen !is Screen.LoadingWorkout && // ← Dodan
        currentScreen !is Screen.RunTracker) {
        GlobalHeaderBar(...)
    }
}
```

#### Bottom bar visibility

```kotlin
val showBottomBar by remember {
    derivedStateOf {
        isLoggedIn && selectedPlan == null &&
        when (currentScreen) {
            // ...screens that show bottom bar...
            is Screen.LoadingWorkout -> false // ← Dodan
            // ...
        }
    }
}
```

---

## 🎬 User Flow - PREJ vs SEDAJ

### ❌ PREJ (z bliskom)

1. User klikne "START DAY X" v PlanPathDialog
2. Dialog se **zapre** → prikaže se BodyModuleHome (BLISK)
3. Async check če je workout že narejen
4. Navigacija na WorkoutSession

**Problem:** V koraku 2-3 se vidi BodyModuleHome za kratek trenutek.

---

### ✅ SEDAJ (brez bliska)

1. User klikne "START DAY X" v PlanPathDialog
2. Navigacija na **LoadingWorkout Screen** → prikaže se "Advanced algorithm working..." (dialog ostane odprt v ozadju, ampak loading screen pokrije vse)
3. LoadingWorkout simulira loading 1.5 sekunde z animacijo
4. Avtomatski callback `onLoadingComplete()` navigira na WorkoutSession

**Result:** Smooth transition brez bliska, z profesionalno loading animacijo!

---

## 🎨 Loading Screen Design

### Visual Elements

1. **Background**
   - Dark gradient: `#17223B` → `#25304A` → `#193446`
   - Vertikalni prehod za smooth look

2. **Spinner**
   - 120dp velikost
   - Vijolična barva (`#6366F1`)
   - 360° rotacija v 1 sekundi
   - 280° arc (ne poln krog)
   - Rounded caps

3. **Main Text**
   - "Advanced Algorithm\nWorking..."
   - 24sp velikost
   - Bold font weight
   - Pulsirajoč alpha (0.6 → 1.0 → 0.6)
   - 800ms animation cycle

4. **Subtitle**
   - "Preparing your personalized workout"
   - 14sp velikost
   - Siva barva (`#B0B8C4`)

5. **Animated Dots**
   - 3 pike
   - Vijolična barva (`#6366F1`)
   - Staggered alpha animation
   - 200ms delay med pikami
   - 600ms animation cycle

---

## 📁 Nove/spremenjene datoteke

### Nova datoteka
- `LoadingWorkoutScreen.kt` - Complete loading screen with animations

### Spremenjene datoteke
1. `MainActivity.kt`
   - LoadingWorkout Screen object
   - LoadingWorkout screen handling
   - onStartWorkoutLoading callback
   - Top/bottom bar visibility

2. `BodyModuleHomeScreen.kt`
   - onStartWorkoutLoading parameter
   - PlanPathDialog callback update

---

## 🧪 Testiranje

### Test koraki:
1. Odpri aplikacijo → Body Module Home
2. Če imaš plan, klikni "Start workout" → odpre se PlanPathDialog
3. Klikni "START DAY X" ali "Extra Workout"
4. **Pričakovano:**
   - ✅ LoadingWorkout screen se prikaže takoj (brez bliska)
   - ✅ Vidiš "Advanced algorithm working..." z animacijo
   - ✅ Spinner se vrti
   - ✅ Tekst pulzira
   - ✅ Pike se animirajo
   - ✅ Po ~1.5 sekunde → navigacija na WorkoutSession

### Kaj NE sme biti:
- ❌ Blisk BodyModuleHome screena
- ❌ Prazen zaslon
- ❌ Trenutna navigacija brez loading-a

---

## 🚀 Rezultat

**Professional loading experience!**

✅ Ni več bliska BodyModuleHome screena  
✅ Smooth transition z animacijo  
✅ "Advanced algorithm working..." sporočilo  
✅ Loading screen pokrije celoten zaslon  
✅ Clean, modern design  
✅ Consistent z ostalimi screeni (dark theme)  

---

## 💡 Dodatne možnosti

Če želiš v prihodnosti:
- Spremeni loading duration (trenutno 1500ms → spremeni `delay(1500)`)
- Dodaj real loading logic (fetch data, initialize workout, itd.)
- Spremeni animacije (spinner speed, colors, itd.)
- Dodaj progress indicator (če imaš več korakov loadinga)

Vse to lahko spremeniš v `LoadingWorkoutScreen.kt` composable funkciji!


# 🧪 BackHandler Testing Guide

## Kako testirati novi generalen BackHandler sistem

### ✅ Test Scenariji

---

## 📱 Test 1: Osnovna Back Navigation
**Cilj:** Preveri da back button sledi zgodovini navigacije

**Koraki:**
1. Odpri aplikacijo → Dashboard
2. Klikni na "Body" modul → Body Module Home
3. Klikni "View History" → Exercise History Screen
4. **Pritisni back button** → Pričakovano: Vrne se na Body Module Home ✅
5. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅
6. **Pritisni back button** → Pričakovano: Aplikacija se zapre ✅

---

## 🎨 Test 2: Face Module Navigation
**Cilj:** Preveri multi-level navigation

**Koraki:**
1. Dashboard → Klikni "Face" modul → Face Module Screen
2. Klikni "Golden Ratio Analysis" → Golden Ratio Screen
3. **Pritisni back button** → Pričakovano: Vrne se na Face Module ✅
4. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅

---

## 🍔 Test 3: Nutrition Deep Navigation
**Cilj:** Preveri kompleksno navigacijo z dialogi

**Koraki:**
1. Dashboard → Klikni "Nutrition" (bottom bar)
2. Klikni "Scan Barcode" → Barcode Scanner Screen
3. **Pritisni back button** → Pričakovano: Vrne se na Nutrition ✅
4. Klikni "E-Additives" → E-Additives Screen
5. **Pritisni back button** → Pričakovano: Vrne se na Nutrition ✅
6. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅

---

## 📊 Test 4: Bottom Navigation Bar
**Cilj:** Preveri da bottom bar navigacija pravilno dela

**Koraki:**
1. Dashboard → Klikni "Progress" (bottom bar) → Progress Screen
2. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅
3. Dashboard → Klikni "Community" (bottom bar) → Community Screen
4. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅

---

## 🎯 Test 5: Workout Session Flow
**Cilj:** Preveri workout flow back navigation

**Koraki:**
1. Dashboard → Body Module Home
2. Klikni "START DAY X" → Workout Session Screen
3. **Pritisni back button** → Pričakovano: Vrne se na Body Module Home ✅
4. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅

---

## 📂 Test 6: Drawer Navigation
**Cilj:** Preveri drawer navigacije

**Koraki:**
1. Dashboard → Odpri drawer (menu ikona)
2. Klikni "Privacy Policy" → Privacy Policy Screen
3. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅
4. Odpri drawer → Klikni "Level Path" → Level Path Screen
5. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅

---

## 🔐 Test 7: Login Flow & Stack Reset
**Cilj:** Preveri da se navigation stack resetira pri login/logout

**Koraki:**
1. Index Screen → Klikni "Login" → Login Screen
2. **Pritisni back button** → Pričakovano: Vrne se na Index ✅
3. Ponovno login → Uspešen login → Dashboard
4. **Pritisni back button na Dashboard** → Pričakovano: Aplikacija se zapre (NI zgodovine iz Index-a) ✅
5. Logout → Preveri da si na Index screen
6. **Pritisni back button** → Pričakovano: Aplikacija se zapre ✅

---

## 💎 Test 8: ProFeatures Flow
**Cilj:** Preveri special case navigation za Pro features

**Koraki:**
1. Dashboard → Klikni "PRO" (top bar) → ProFeatures Screen
2. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅
3. ProFeatures → Klikni "Continue" → ProSubscription Screen
4. **Pritisni back button** → Pričakovano: Vrne se na ProFeatures ✅
5. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅

---

## 🎪 Test 9: Dialog Back Button
**Cilj:** Preveri da se dialogi zaprejo z back buttonom

**Koraki:**
1. Body Module Home → Klikni "View Path" → Odpre se PlanPathDialog (full screen)
2. **Pritisni back button** → Pričakovano: Dialog se zapre, ostaneš na Body Module Home ✅
3. Body Module Home → Klikni "Knowledge Hub" → Odpre se Knowledge Hub
4. **Pritisni back button** → Pričakovano: Knowledge Hub se zapre ✅

---

## 🏃 Test 10: Run Tracker
**Cilj:** Preveri run tracker navigation

**Koraki:**
1. Body Module Home → Klikni "START RUN" → Run Tracker Screen
2. **Pritisni back button** → Pričakovano: Vrne se na Body Module Home ✅
3. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅

---

## 🎯 Test 11: Plan Report Selection
**Cilj:** Preveri selected plan behavior

**Koraki:**
1. Body Overview → Klikni na plan → PlanReportScreen se prikaže
2. **Pritisni back button** → Pričakovano: selectedPlan se počisti, vrne se na Body Overview ✅
3. **Pritisni back button** → Pričakovano: Vrne se na Dashboard ✅

---

## 🔄 Test 12: Complex Multi-Level Navigation
**Cilj:** Preveri dolgo navigacijsko pot

**Koraki:**
1. Dashboard
2. → Body Module Home
3. → Exercise History
4. → (back) Body Module Home
5. → Generate Workout
6. → (back) Body Module Home
7. → (back) Dashboard
8. → Face Module
9. → Golden Ratio
10. → (back) Face Module
11. → (back) Dashboard
12. → (back) **App close** ✅

**Pričakovano:** Vsak back button korektno vrne na predhodni screen v tem vrstnem redu.

---

## ❌ Napake ki jih preveri

### 🚫 NEPRAVILNO obnašanje:
- ❌ Back button ne naredi ničesar
- ❌ Back button zapre aplikacijo nepričakovano
- ❌ Back button te vrže na napačen screen
- ❌ Back button ne zapre dialoga
- ❌ Navigation stack se ne počisti po login/logout
- ❌ Back iz Dashboard-a ne zapre aplikacije (ko logged in)

### ✅ PRAVILNO obnašanje:
- ✅ Back button vedno sledi zgodovini navigacije
- ✅ Dialogi se zaprejo z back buttonom
- ✅ Login/logout resetira navigation stack
- ✅ Posebni primeri (ProFeatures, Login) delujejo kot specificirano
- ✅ Dashboard back zapre aplikacijo (ko logged in)

---

## 📝 Beleženje napak

Če najdeš napako, zabeleži:
1. **Točen navigation flow** (kateri screeni, v kakšnem vrstnem redu)
2. **Kaj si pričakoval** da se zgodi
3. **Kaj se je dejansko zgodilo**
4. **Screen state** (selectedPlan, isLoggedIn, itd.)

### Primer:
```
Navigation: Dashboard → Body Module → Exercise History → (back)
Pričakoval: Vrne se na Body Module Home
Zgodilo se: Vrnil se na Dashboard (preskočil Body Module)
State: isLoggedIn=true, selectedPlan=null
```

---

## 🎉 Uspešen test

Če vsi test scenariji zgoraj **uspejo** ✅, potem je generalni BackHandler sistem **pravilno implementiran**!


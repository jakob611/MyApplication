# Weight Widget Documentation

## Overview
Quick weight tracking widget (2x1) for home screen with instant response and background Firestore sync.

## Features
- ✅ **Instant updates** - Local cache first, Firestore sync in background
- ✅ **2x1 size** - Optimized for Samsung One UI 7
- ✅ **0.1 kg increments** - Tap +/- to adjust weight
- ✅ **Offline capable** - Works from cache without internet
- ✅ **Multi-device sync** - Via Firestore

## Widget Layout
```
┌─────────────────────────────┐
│  −  │  75.5 kg  │    +     │
└─────────────────────────────┘
```

## Actions
- **Tap "-"** → Decrease by 0.1 kg (instant)
- **Tap "+"** → Increase by 0.1 kg (instant)
- **Tap center** → Sync from Firestore (~500ms)

## Data Storage

### Local Cache
- **Location:** SharedPreferences (`weight_widget_prefs`)
- **Key format:** `weight_YYYY-MM-DD`
- **Type:** Float (kg)
- **Purpose:** Instant UI updates

### Firestore
- **Collection:** `users/{uid}/dailyMetrics/{YYYY-MM-DD}`
- **Field:** `weight` (Float)
- **Sync:** Background after each widget update

## Performance
| Action | Response Time |
|--------|--------------|
| +/- click | ~50ms |
| Widget display | ~20ms |
| Sync to Firestore | Background (~200-800ms) |
| Refresh from Firestore | ~500ms |

## Integration with Progress Screen

The widget syncs with the weight tracking in Progress screen:
- Widget updates → Firestore → Progress screen chart
- Progress screen updates → Firestore → Widget (on refresh)

### Data Flow
```
Widget +/- Click:
  └─> SharedPreferences (instant)
  └─> Widget UI update (50ms)
  └─> Firestore write (background, 200-800ms)
      └─> Progress screen chart (snapshot listener)

Progress Screen Update:
  └─> Firestore write
      └─> Widget cache (on next refresh/tap center)
```

## Technical Details

### Step Size
- Default: 0.1 kg
- Configurable via `STEP_KG` constant in `WeightWidgetProvider`

### Display Format
- Format: `"%.1f kg"` (e.g., "75.5 kg")
- Empty state: `"-- kg"` (when weight = 0)

### Consistency Model
- **Optimistic updates** - UI updates immediately
- **Eventual consistency** - Firestore syncs in background
- **Manual sync** - Tap center to pull latest from server

## Files
- `WeightWidgetProvider.kt` - Widget logic
- `widget_weight.xml` - Layout (2x1)
- `weight_widget_info.xml` - Widget configuration
- AndroidManifest.xml - Widget registration

## Installation

1. Widget appears in home screen widget picker as "Weight Tracker"
2. Under app name "Glow Upp"
3. Size: 2x1 (146dp × 48dp on Samsung)

## Testing

### Rapid Click Test
```kotlin
// Tap +/- buttons rapidly (10+ times/second)
// Expected: Smooth updates, no lag, no dropped clicks
```

### Offline Test
```kotlin
// 1. Disable internet
// 2. Use widget normally
// 3. Re-enable internet
// Expected: Widget works offline, syncs when online
```

### Multi-device Sync Test
```kotlin
// 1. Device A: Update weight via widget
// 2. Device B: Open Progress screen
// 3. Device B: Widget tap center to refresh
// Expected: Both devices show same weight
```

## Known Limitations
- Weight cannot go below 0 kg
- Displays today's weight only (not historical)
- Requires initial sync when widget first added
- Background sync may fail if Firestore is down (UI still updates)

## Future Enhancements
- [ ] Configurable step size (0.1, 0.5, 1.0 kg)
- [ ] Historical weight graph in widget
- [ ] Weekly average display
- [ ] Goal weight indicator
- [ ] Units toggle (kg/lbs)


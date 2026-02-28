package com.example.myapplication

// =====================================================================
// AppDrawer.kt
// Vsebuje: FigmaDrawerContent (stranski meni),
//          GlobalHeaderBar (zgornji header z menijem in Pro gumbom),
//          ter vse pomožne composable in funkcije za drawer.
// =====================================================================

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.UserProfile

// -----------------------------------------------------------------------
// GLOBAL HEADER BAR (prikazan na Dashboard, Progress, Nutrition, Community)
// -----------------------------------------------------------------------
@Composable
fun GlobalHeaderBar(
    onOpenMenu: () -> Unit,
    onProClick: () -> Unit
) {
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .statusBarsPadding()
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ElevatedCard(
                onClick = onOpenMenu,
                modifier = Modifier.size(56.dp),
                shape = RoundedCornerShape(14.dp),
                colors = CardDefaults.elevatedCardColors(containerColor = DrawerBlue),
                elevation = CardDefaults.elevatedCardElevation(defaultElevation = 1.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "User's\nStickman",
                        color = Color.White,
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            AssistChip(
                onClick = onProClick,
                label = {
                    Text("Upgrade to Premium", color = Color.White, fontWeight = FontWeight.SemiBold)
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = DrawerBlue,
                    labelColor = Color.White
                )
            )
        }
    }
}

// -----------------------------------------------------------------------
// FIGMA DRAWER CONTENT (stranski meni)
// -----------------------------------------------------------------------
@Composable
fun FigmaDrawerContent(
    userProfile: UserProfile,
    onClose: () -> Unit,
    onLogout: () -> Unit,
    onProfileUpdate: (UserProfile) -> Unit,
    isDarkMode: Boolean,
    onDarkModeToggle: () -> Unit,
    onNavigateToPrivacyPolicy: () -> Unit,
    onNavigateToTermsOfService: () -> Unit,
    onNavigateToContact: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToLevelPath: () -> Unit = {},
    onNavigateToBadges: () -> Unit = {},
    onNavigateToHealthConnect: () -> Unit = {},
    onNavigateToMyAccount: () -> Unit
) {
    val PrimaryBlue = Color(0xFF2563EB)
    val SheetBg = if (isDarkMode) Color(0xFF1F2937) else Color.White
    val CardBg = if (isDarkMode) Color(0xFF374151) else Color(0xFFF3F4F6)
    val TextPrimary = if (isDarkMode) Color.White else Color.Black
    val TextSecondary = if (isDarkMode) Color(0xFFD1D5DB) else Color(0xFF6B7280)

    var isEditingPersonal by remember { mutableStateOf(false) }
    var editedProfile by remember { mutableStateOf(userProfile) }
    var showEquipmentDialog by remember { mutableStateOf(false) }
    var isPublicPrivacy by remember { mutableStateOf(userProfile.isPublicProfile) }
    var showLevelPrivacy by remember { mutableStateOf(userProfile.showLevel) }
    var showBadgesPrivacy by remember { mutableStateOf(userProfile.showBadges) }
    var showPlanPathPrivacy by remember { mutableStateOf(userProfile.showPlanPath) }
    var showChallengesPrivacy by remember { mutableStateOf(userProfile.showChallenges) }
    var showFollowersPrivacy by remember { mutableStateOf(userProfile.showFollowers) }

    LaunchedEffect(userProfile) {
        editedProfile = userProfile
        isPublicPrivacy = userProfile.isPublicProfile
        showLevelPrivacy = userProfile.showLevel
        showBadgesPrivacy = userProfile.showBadges
        showPlanPathPrivacy = userProfile.showPlanPath
        showChallengesPrivacy = userProfile.showChallenges
        showFollowersPrivacy = userProfile.showFollowers
    }

    Surface(
        color = SheetBg,
        modifier = Modifier.fillMaxHeight().width(320.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            // Gumb za zaprtje
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top))
            ) {
                TextButton(onClick = onClose) { Text("← Back", color = TextPrimary) }
                Spacer(Modifier.weight(1f))
            }

            // 1. Stickman avatar
            Surface(
                color = PrimaryBlue,
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier.fillMaxWidth().heightIn(min = 160.dp).padding(vertical = 8.dp)
            ) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Text(
                        text = "User's\nStickman",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        fontSize = 28.sp,
                        lineHeight = 32.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            // 2. Stats (Level, XP, Followers, Badges)
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(16.dp)).clickable { onNavigateToLevelPath() }
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    // Level & XP header
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(PrimaryBlue.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("LEVEL", color = TextSecondary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("${userProfile.level}", color = PrimaryBlue, fontSize = 32.sp, fontWeight = FontWeight.ExtraBold)
                        }
                        Column(horizontalAlignment = Alignment.End, modifier = Modifier.weight(1f)) {
                            Text("⭐ ${userProfile.xp}", color = Color(0xFFFEE440), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Total XP", color = TextSecondary, fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // XP Progress bar
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Next Level", color = TextSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${(userProfile.progressToNextLevel * 100).toInt()}%",
                                color = PrimaryBlue, fontSize = 12.sp, fontWeight = FontWeight.Bold
                            )
                        }
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier.fillMaxWidth().height(12.dp)
                                .background(TextSecondary.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                                .clip(RoundedCornerShape(6.dp))
                        ) {
                            Box(
                                modifier = Modifier.fillMaxHeight()
                                    .fillMaxWidth(userProfile.progressToNextLevel)
                                    .background(
                                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(PrimaryBlue, Color(0xFF60A5FA))
                                        ),
                                        shape = RoundedCornerShape(6.dp)
                                    )
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${userProfile.xp - userProfile.xpForCurrentLevel} / ${userProfile.xpForNextLevel - userProfile.xpForCurrentLevel} XP",
                            color = TextSecondary, fontSize = 11.sp,
                            modifier = Modifier.align(Alignment.End)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = TextSecondary.copy(alpha = 0.2f))
                    Spacer(Modifier.height(12.dp))

                    // Followers & Badges
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                            Text("👥", fontSize = 24.sp)
                            Text(userProfile.followers.toString(), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Followers", color = TextSecondary, fontSize = 10.sp)
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.weight(1f).clickable { onNavigateToBadges() }
                        ) {
                            Text("🏆", fontSize = 24.sp)
                            val autoUnlockedCount = calculateAutoUnlockedBadgeCount(userProfile)
                            val totalUnlockedBadges = (userProfile.badges.toSet() + autoUnlockedCount.first).size
                            Text(totalUnlockedBadges.toString(), color = TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("Badges", color = TextSecondary, fontSize = 10.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 3. Health Connect gumb
            Button(
                onClick = { onNavigateToHealthConnect(); onClose() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6366F1), contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Filled.HealthAndSafety, "Health Connect", modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text("Connect with Health Connect", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))

            // 4. Dark Mode
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Dark Mode", color = TextPrimary, fontWeight = FontWeight.Bold)
                Switch(checked = isDarkMode, onCheckedChange = { onDarkModeToggle() })
            }

            Spacer(Modifier.height(12.dp))

            // 5. Settings & Privacy
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Settings & Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))

                    SettingsDropdownRow("Weight Unit", userProfile.weightUnit, listOf("kg", "lb"), TextPrimary) {
                        onProfileUpdate(userProfile.copy(weightUnit = it))
                    }
                    SettingsDropdownRow("Speed Unit", userProfile.speedUnit, listOf("km/h", "mph", "m/s"), TextPrimary) {
                        onProfileUpdate(userProfile.copy(speedUnit = it))
                    }
                    SettingsDropdownRow("Start of Week", userProfile.startOfWeek, listOf("Monday", "Saturday", "Sunday"), TextPrimary) {
                        onProfileUpdate(userProfile.copy(startOfWeek = it))
                    }

                    // Detailed Calories toggle
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Detailed Calories", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("Show fat/protein/carbs segments", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = userProfile.detailedCalories,
                            onCheckedChange = { onProfileUpdate(userProfile.copy(detailedCalories = it)) }
                        )
                    }

                    Spacer(Modifier.height(8.dp))
                    HorizontalDivider(color = TextSecondary.copy(alpha = 0.3f))
                    Spacer(Modifier.height(8.dp))

                    Text("Privacy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(8.dp))

                    // Public Profile toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Public Profile", style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                            Text("Allow others to see your profile", style = MaterialTheme.typography.bodySmall, color = TextSecondary)
                        }
                        Switch(
                            checked = isPublicPrivacy,
                            onCheckedChange = { newValue ->
                                isPublicPrivacy = newValue
                                if (!newValue) {
                                    showLevelPrivacy = false; showBadgesPrivacy = false
                                    showPlanPathPrivacy = false; showChallengesPrivacy = false; showFollowersPrivacy = false
                                }
                                onProfileUpdate(userProfile.copy(
                                    isPublicProfile = newValue,
                                    showLevel = if (newValue) showLevelPrivacy else false,
                                    showBadges = if (newValue) showBadgesPrivacy else false,
                                    showPlanPath = if (newValue) showPlanPathPrivacy else false,
                                    showChallenges = if (newValue) showChallengesPrivacy else false,
                                    showFollowers = if (newValue) showFollowersPrivacy else false
                                ))
                            }
                        )
                    }

                    // Privacy sub-toggles (samo ko je profil javen)
                    if (isPublicPrivacy) {
                        Spacer(Modifier.height(4.dp))
                        val privacyToggles = listOf(
                            "Show Level" to showLevelPrivacy,
                            "Show Badges" to showBadgesPrivacy,
                            "Show Plan Path" to showPlanPathPrivacy,
                            "Show Challenges" to showChallengesPrivacy,
                            "Show Followers" to showFollowersPrivacy
                        )
                        privacyToggles.forEachIndexed { index, (label, checked) ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(label, style = MaterialTheme.typography.bodyMedium, color = TextPrimary)
                                Switch(
                                    checked = checked,
                                    onCheckedChange = { newValue ->
                                        when (index) {
                                            0 -> { showLevelPrivacy = newValue; onProfileUpdate(userProfile.copy(isPublicProfile = true, showLevel = newValue)) }
                                            1 -> { showBadgesPrivacy = newValue; onProfileUpdate(userProfile.copy(isPublicProfile = true, showBadges = newValue)) }
                                            2 -> { showPlanPathPrivacy = newValue; onProfileUpdate(userProfile.copy(isPublicProfile = true, showPlanPath = newValue)) }
                                            3 -> { showChallengesPrivacy = newValue; onProfileUpdate(userProfile.copy(isPublicProfile = true, showChallenges = newValue)) }
                                            4 -> { showFollowersPrivacy = newValue; onProfileUpdate(userProfile.copy(isPublicProfile = true, showFollowers = newValue)) }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 6. Equipment
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth().clickable { showEquipmentDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Equipment", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (userProfile.equipment.isEmpty()) "None" else "${userProfile.equipment.size} items",
                            style = MaterialTheme.typography.bodyMedium, color = TextPrimary.copy(alpha = 0.7f)
                        )
                        Icon(Icons.Filled.KeyboardArrowRight, null, tint = TextPrimary)
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Text("My Account Info", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextPrimary)
            Spacer(Modifier.height(12.dp))

            // 7. Info & Support
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Info & Support", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                    Spacer(Modifier.height(12.dp))
                    NavigationRow("Privacy Policy", TextPrimary) { onNavigateToPrivacyPolicy() }
                    NavigationRow("Terms of Service", TextPrimary) { onNavigateToTermsOfService() }
                    NavigationRow("Contact", TextPrimary) { onNavigateToContact() }
                    NavigationRow("About", TextPrimary) { onNavigateToAbout() }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 8. Personal Data
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Personal Data", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = TextPrimary)
                        TextButton(onClick = {
                            if (isEditingPersonal) onProfileUpdate(editedProfile)
                            isEditingPersonal = !isEditingPersonal
                        }) {
                            Text(if (isEditingPersonal) "Save" else "Edit", color = PrimaryBlue)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    if (isEditingPersonal) {
                        listOf(
                            "Username" to editedProfile.username,
                            "First Name" to editedProfile.firstName,
                            "Last Name" to editedProfile.lastName,
                            "Address" to editedProfile.address
                        ).forEachIndexed { index, (label, value) ->
                            OutlinedTextField(
                                value = value,
                                onValueChange = { v ->
                                    editedProfile = when (index) {
                                        0 -> editedProfile.copy(username = v)
                                        1 -> editedProfile.copy(firstName = v)
                                        2 -> editedProfile.copy(lastName = v)
                                        3 -> editedProfile.copy(address = v)
                                        else -> editedProfile
                                    }
                                },
                                label = { Text(label) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = PrimaryBlue,
                                    focusedLabelColor = PrimaryBlue
                                )
                            )
                            Spacer(Modifier.height(8.dp))
                        }
                    } else {
                        ProfileDataRow("Username", editedProfile.username.ifBlank { "Not set" }, TextSecondary, TextPrimary)
                        ProfileDataRow("First Name", editedProfile.firstName.ifBlank { "Not set" }, TextSecondary, TextPrimary)
                        ProfileDataRow("Last Name", editedProfile.lastName.ifBlank { "Not set" }, TextSecondary, TextPrimary)
                        ProfileDataRow("Address", editedProfile.address.ifBlank { "Not set" }, TextSecondary, TextPrimary)
                    }
                    Spacer(Modifier.height(8.dp))
                    ProfileDataRow("Email", userProfile.email, TextSecondary, TextPrimary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 9. My Account gumb
            Button(
                onClick = { onNavigateToMyAccount(); onClose() },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray.copy(alpha = 0.2f), contentColor = TextPrimary),
                shape = RoundedCornerShape(12.dp)
            ) { Text("My Account", fontWeight = FontWeight.SemiBold) }

            Spacer(Modifier.height(8.dp))

            // 10. Logout
            var showLogoutDialog by remember { mutableStateOf(false) }
            Button(
                onClick = { showLogoutDialog = true },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue, contentColor = Color.White),
                shape = RoundedCornerShape(12.dp)
            ) { Text("Log out", fontWeight = FontWeight.SemiBold) }

            if (showLogoutDialog) {
                AlertDialog(
                    onDismissRequest = { showLogoutDialog = false },
                    title = { Text("Log out") },
                    text = { Text("Are you sure you want to log out?") },
                    confirmButton = {
                        Button(
                            onClick = { showLogoutDialog = false; onLogout() },
                            colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue)
                        ) { Text("Log out") }
                    },
                    dismissButton = {
                        OutlinedButton(onClick = { showLogoutDialog = false }) { Text("Cancel") }
                    }
                )
            }

            Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars))
        }
    }

    if (showEquipmentDialog) {
        EquipmentSelectionDialog(
            currentSelection = userProfile.equipment,
            onDismiss = { showEquipmentDialog = false },
            onSave = { newEquipment -> onProfileUpdate(userProfile.copy(equipment = newEquipment)); showEquipmentDialog = false },
            isDarkMode = isDarkMode
        )
    }
}

// -----------------------------------------------------------------------
// POMOŽNI COMPOSABLES (za Drawer)
// -----------------------------------------------------------------------

@Composable
fun EquipmentSelectionDialog(
    currentSelection: List<String>,
    onDismiss: () -> Unit,
    onSave: (List<String>) -> Unit,
    isDarkMode: Boolean
) {
    val options = listOf("Dumbbells", "Barbell", "Kettlebell", "Pull-up Bar", "Resistance Bands", "Bench", "Treadmill", "None (Bodyweight)")
    var selection by remember { mutableStateOf(currentSelection) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Equipment") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                            .clickable {
                                selection = if (option == "None (Bodyweight)") {
                                    if (selection.contains(option)) emptyList() else listOf(option)
                                } else {
                                    val s = selection.toMutableSet().also { it.remove("None (Bodyweight)") }
                                    if (s.contains(option)) s.remove(option) else s.add(option)
                                    s.toList()
                                }
                            }
                            .padding(vertical = 4.dp)
                    ) {
                        Checkbox(
                            checked = selection.contains(option),
                            onCheckedChange = { isChecked ->
                                selection = if (option == "None (Bodyweight)") {
                                    if (isChecked) listOf(option) else emptyList()
                                } else {
                                    val s = selection.toMutableSet().also { it.remove("None (Bodyweight)") }
                                    if (isChecked) s.add(option) else s.remove(option)
                                    s.toList()
                                }
                            }
                        )
                        Text(option, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        },
        confirmButton = { Button(onClick = { onSave(selection) }) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ProfileDataRow(
    label: String,
    value: String,
    valueColor: Color = Color.Unspecified,
    labelColor: Color = Color.Unspecified
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = labelColor, style = MaterialTheme.typography.bodyMedium)
        Text(value, color = valueColor, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
    }
}

@Composable
fun NavigationRow(
    label: String,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
            .padding(vertical = 8.dp).clickable { onClick() },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor)
        Icon(Icons.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDropdownRow(
    label: String,
    currentValue: String,
    options: List<String>,
    textColor: Color,
    onValueChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = textColor)
        Box(modifier = Modifier.wrapContentSize(Alignment.TopStart)) {
            Row(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .border(1.dp, textColor.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    .clickable { expanded = true }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(currentValue, style = MaterialTheme.typography.bodyMedium, color = textColor)
                Spacer(modifier = Modifier.width(8.dp))
                Icon(Icons.Filled.ExpandMore, "Expand", tint = textColor)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = {
                            Text(option, style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (option == currentValue) FontWeight.Bold else FontWeight.Normal)
                        },
                        onClick = { expanded = false; onValueChange(option) },
                        contentPadding = PaddingValues(16.dp)
                    )
                }
            }
        }
    }
}

// -----------------------------------------------------------------------
// POMOŽNA FUNKCIJA: izračuna avtomatsko odklenjene badge-e iz userProfile
// Vrne Pair(Set<String> odklenjenih ID-jev, Int skupaj)
// -----------------------------------------------------------------------
fun calculateAutoUnlockedBadgeCount(userProfile: UserProfile): Pair<Set<String>, Int> {
    val ids = mutableSetOf<String>()

    // Treningi
    if (userProfile.totalWorkoutsCompleted >= 1)   ids.add("first_workout")
    if (userProfile.totalWorkoutsCompleted >= 10)  ids.add("committed_10")
    if (userProfile.totalWorkoutsCompleted >= 50)  ids.add("committed_50")
    if (userProfile.totalWorkoutsCompleted >= 100) ids.add("committed_100")
    if (userProfile.totalWorkoutsCompleted >= 250) ids.add("committed_250")
    if (userProfile.totalWorkoutsCompleted >= 500) ids.add("committed_500")

    // Kalorije
    if (userProfile.totalCaloriesBurned >= 1000)  ids.add("calorie_crusher_1k")
    if (userProfile.totalCaloriesBurned >= 5000)  ids.add("calorie_crusher_5k")
    if (userProfile.totalCaloriesBurned >= 10000) ids.add("calorie_crusher_10k")

    // Nivoji
    if (userProfile.level >= 5)  ids.add("level_5")
    if (userProfile.level >= 10) ids.add("level_10")
    if (userProfile.level >= 25) ids.add("level_25")
    if (userProfile.level >= 50) ids.add("level_50")

    // Sledilci
    if (userProfile.followers >= 1)   ids.add("first_follower")
    if (userProfile.followers >= 10)  ids.add("social_butterfly")
    if (userProfile.followers >= 50)  ids.add("influencer")
    if (userProfile.followers >= 100) ids.add("celebrity")

    // Čas vadbe
    if (userProfile.earlyBirdWorkouts >= 5) ids.add("early_bird")
    if (userProfile.nightOwlWorkouts >= 5)  ids.add("night_owl")

    // Streak
    if (userProfile.currentLoginStreak >= 7)   ids.add("week_warrior")
    if (userProfile.currentLoginStreak >= 30)  ids.add("month_master")
    if (userProfile.currentLoginStreak >= 365) ids.add("year_champion")

    // Plani
    if (userProfile.totalPlansCreated >= 1) ids.add("first_plan")
    if (userProfile.totalPlansCreated >= 5) ids.add("plan_master")

    return Pair(ids, ids.size)
}


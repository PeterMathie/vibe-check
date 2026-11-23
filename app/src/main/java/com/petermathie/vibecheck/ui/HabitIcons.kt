package com.petermathie.vibecheck.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsRun
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.*
import androidx.compose.ui.graphics.vector.ImageVector

internal data class HabitIconOption(
    val key: String,
    val label: String,
    val icon: ImageVector,
)

internal object HabitIconCatalog {
    val options = listOf(
        HabitIconOption("habit", "Habit", Icons.Outlined.CheckCircle),
        HabitIconOption("piano", "Music", Icons.Outlined.MusicNote),
        HabitIconOption("meditation", "Meditation", Icons.Outlined.SelfImprovement),
        HabitIconOption("protein", "Food", Icons.Outlined.LocalDining),
        HabitIconOption("mood", "Mood", Icons.Outlined.Mood),
        HabitIconOption("journal", "Journal", Icons.Outlined.EditNote),
        HabitIconOption("reading", "Reading", Icons.Outlined.AutoStories),
        HabitIconOption("wellbeing", "Wellbeing", Icons.Outlined.Favorite),
        HabitIconOption("goal", "Goal", Icons.Outlined.Star),
        HabitIconOption("running", "Running", Icons.AutoMirrored.Outlined.DirectionsRun),
        HabitIconOption("walking", "Walking", Icons.AutoMirrored.Outlined.DirectionsWalk),
        HabitIconOption("cycling", "Cycling", Icons.Outlined.PedalBike),
        HabitIconOption("hiking", "Hiking", Icons.Outlined.Hiking),
        HabitIconOption("swimming", "Swimming", Icons.Outlined.Pool),
        HabitIconOption("rowing", "Rowing", Icons.Outlined.Rowing),
        HabitIconOption("strength", "Strength", Icons.Outlined.FitnessCenter),
        HabitIconOption("basketball", "Basketball", Icons.Outlined.SportsBasketball),
        HabitIconOption("football", "Football", Icons.Outlined.SportsSoccer),
        HabitIconOption("tennis", "Tennis", Icons.Outlined.SportsTennis),
        HabitIconOption("sleep", "Sleep", Icons.Outlined.Bedtime),
        HabitIconOption("morning", "Morning", Icons.Outlined.WbSunny),
        HabitIconOption("spa", "Spa", Icons.Outlined.Spa),
        HabitIconOption("happy", "Happy", Icons.Outlined.SentimentSatisfied),
        HabitIconOption("mind", "Mind", Icons.Outlined.Psychology),
        HabitIconOption("health", "Health", Icons.Outlined.HealthAndSafety),
        HabitIconOption("medicine", "Medicine", Icons.Outlined.MedicalServices),
        HabitIconOption("water", "Water", Icons.Outlined.WaterDrop),
        HabitIconOption("coffee", "Coffee", Icons.Outlined.LocalCafe),
        HabitIconOption("drink", "Drink", Icons.Outlined.LocalDrink),
        HabitIconOption("restaurant", "Restaurant", Icons.Outlined.Restaurant),
        HabitIconOption("egg", "Egg", Icons.Outlined.EggAlt),
        HabitIconOption("headphones", "Headphones", Icons.Outlined.Headphones),
        HabitIconOption("singing", "Singing", Icons.Outlined.Mic),
        HabitIconOption("art", "Art", Icons.Outlined.Palette),
        HabitIconOption("painting", "Painting", Icons.Outlined.Brush),
        HabitIconOption("drawing", "Drawing", Icons.Outlined.Draw),
        HabitIconOption("camera", "Camera", Icons.Outlined.PhotoCamera),
        HabitIconOption("theatre", "Theatre", Icons.Outlined.TheaterComedy),
        HabitIconOption("book", "Book", Icons.AutoMirrored.Outlined.MenuBook),
        HabitIconOption("school", "Study", Icons.Outlined.School),
        HabitIconOption("language", "Language", Icons.Outlined.Language),
        HabitIconOption("code", "Coding", Icons.Outlined.Code),
        HabitIconOption("science", "Science", Icons.Outlined.Science),
        HabitIconOption("maths", "Maths", Icons.Outlined.Calculate),
        HabitIconOption("work", "Work", Icons.Outlined.Work),
        HabitIconOption("task", "Task", Icons.Outlined.TaskAlt),
        HabitIconOption("calendar", "Calendar", Icons.Outlined.EventAvailable),
        HabitIconOption("alarm", "Alarm", Icons.Outlined.Alarm),
        HabitIconOption("timer", "Timer", Icons.Outlined.Timer),
        HabitIconOption("savings", "Savings", Icons.Outlined.Savings),
        HabitIconOption("shopping", "Shopping", Icons.Outlined.ShoppingCart),
        HabitIconOption("cleaning", "Cleaning", Icons.Outlined.CleaningServices),
        HabitIconOption("home", "Home", Icons.Outlined.Home),
        HabitIconOption("garden", "Garden", Icons.Outlined.Yard),
        HabitIconOption("park", "Outdoors", Icons.Outlined.Park),
        HabitIconOption("pets", "Pets", Icons.Outlined.Pets),
        HabitIconOption("group", "People", Icons.Outlined.Group),
        HabitIconOption("childcare", "Childcare", Icons.Outlined.ChildCare),
        HabitIconOption("call", "Call", Icons.Outlined.Call),
        HabitIconOption("chat", "Chat", Icons.Outlined.Forum),
        HabitIconOption("volunteer", "Volunteer", Icons.Outlined.VolunteerActivism),
        HabitIconOption("accessibility", "Mobility", Icons.Outlined.AccessibilityNew),
    )

    val pickerOptions = options.filterNot { it.key == "health" || it.key == "cleaning" }

    fun icon(key: String): ImageVector = options.find { it.key == key }?.icon ?: options.first().icon

    fun option(key: String): HabitIconOption = options.find { it.key == key } ?: options.first()
}

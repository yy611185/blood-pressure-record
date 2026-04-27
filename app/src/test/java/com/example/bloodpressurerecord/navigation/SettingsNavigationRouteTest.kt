package com.example.bloodpressurerecord.navigation

import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsNavigationRouteTest {
    @Test
    fun settings_sub_routes_are_defined() {
        val routes = setOf(
            AppDestination.Settings.route,
            AppDestination.SettingsProfile.route,
            AppDestination.SettingsReminder.route,
            AppDestination.SettingsDisplay.route,
            AppDestination.SettingsDataManagement.route,
            AppDestination.SettingsInfo.route,
            AppDestination.SettingsInfoAppGuide.route,
            AppDestination.SettingsInfoReleaseNotes.route,
            AppDestination.SettingsDisclaimer.route
        )
        assertTrue(routes.size == 9)
    }
}

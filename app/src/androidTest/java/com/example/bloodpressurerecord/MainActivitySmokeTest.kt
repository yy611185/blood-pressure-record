package com.example.bloodpressurerecord

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloodpressurerecord.navigation.AppDestination
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createEmptyComposeRule()

    @Test
    fun mainActivityLaunchesAndReachesResumedState() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertEquals(Lifecycle.State.RESUMED, scenario.state)
        }
    }

    @Test
    fun deepLinkAddMeasurementConsumesOnceAndDoesNotReplayAfterExitAndRecreate() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val intent = Intent(context, MainActivity::class.java).apply {
            putExtra(MainActivity.EXTRA_OPEN_DESTINATION, AppDestination.AddMeasurement.route)
        }
        ActivityScenario.launch<MainActivity>(intent).use { scenario ->
            composeRule.waitForIdle()
            composeRule.onNodeWithText("记一次血压").assertIsDisplayed()
            scenario.onActivity { activity ->
                assertFalse(
                    "Consumed deep-link extra must be removed from Activity intent",
                    activity.intent.hasExtra(MainActivity.EXTRA_OPEN_DESTINATION)
                )
            }

            // 退出录入页回到首页
            composeRule.onNodeWithContentDescription(context.getString(R.string.back)).performClick()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("记一次血压").assertDoesNotExist()
            composeRule.onNodeWithTag("dashboardScreen").assertIsDisplayed()

            // 模拟旋转重建：不得重放已消费的 open_destination=record/add
            scenario.recreate()
            composeRule.waitForIdle()
            composeRule.onNodeWithText("记一次血压").assertDoesNotExist()
            composeRule.onNodeWithTag("dashboardScreen").assertIsDisplayed()
        }
    }
}

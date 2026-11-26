package com.example.bloodpressurerecord

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.example.bloodpressurerecord.navigation.AppDestination
import com.example.bloodpressurerecord.reminder.MedicationReminders
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun warmMedicationRequestsQueueAndRetainMeasurementDraft() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val repo = (context.applicationContext as BloodPressureApplication).appContainer.medicationRepository
        org.junit.Assume.assumeTrue(
            "Fixture requires an empty measurement draft",
            (context.applicationContext as BloodPressureApplication).appContainer.sessionDraftRepository.load("add_session").getOrThrow() == null
        )
        val date = LocalDate.now()
        val testTag = System.nanoTime()
        val names = listOf("测试药品甲_$testTag", "测试药品乙_$testTag")
        val ids = mutableListOf<Long>()
        try {
            runBlocking {
                names.forEach { ids += repo.addMedication(it, "5mg", listOf("08:00")).getOrThrow() }
            }
            val slots = runBlocking { repo.getSlotsForDay(date).filter { it.medicationId in ids } }
            val first = slots.single { it.medicationId == ids[0] }
            val second = slots.single { it.medicationId == ids[1] }
            val add = Intent(context, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_DESTINATION, AppDestination.AddMeasurement.route)
            ActivityScenario.launch<MainActivity>(add).use { scenario ->
                val reading = composeRule.onNodeWithContentDescription("第 1 组高压（收缩压）")
                reading.performTextInput("122")
                scenario.onActivity { activity ->
                    activity.startActivity(MedicationReminders.createOpenConfirmationIntent(
                        context, first.medicationId, first.timeId, date.toEpochDay()
                    ))
                }
                val inDialog = hasAnyAncestor(hasTestTag("medicationConfirmDialog"))
                composeRule.waitUntil(5_000) {
                    composeRule.onAllNodes(hasText(first.name) and inDialog).fetchSemanticsNodes().isNotEmpty()
                }
                scenario.onActivity { activity ->
                    activity.startActivity(MedicationReminders.createOpenConfirmationIntent(
                        context, second.medicationId, second.timeId, date.toEpochDay()
                    ))
                }
                composeRule.waitForIdle()
                composeRule.onNode(hasText(first.name) and inDialog).assertIsDisplayed()
                composeRule.onNodeWithTag("medicationConfirmButton").performSemanticsAction(SemanticsActions.OnClick)
                composeRule.waitUntil(5_000) {
                    composeRule.onAllNodes(hasText(second.name) and inDialog).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithTag("medicationDismissButton").performClick()
                reading.assertTextContains("122")
                val taken = runBlocking { repo.getSlotsForDay(date).filter { it.medicationId in ids && it.taken } }
                assertEquals(listOf(first.timeId), taken.map { it.timeId })
                reading.performTextClearance()
            }
        } finally {
            runBlocking { ids.forEach { repo.deleteMedication(it).getOrThrow() } }
        }
    }

    @Test
    fun medicationNotificationConfirmationIdentifiesSpecificDrugAndHandlesCancelConfirmAndReplay() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as BloodPressureApplication
        val medRepo = app.appContainer.medicationRepository
        val today = LocalDate.now(ZoneId.systemDefault())
        val uniqueName = "测试氨氯地平_${System.currentTimeMillis()}"
        val medId = runBlocking {
            medRepo.addMedication(uniqueName, "5mg", listOf("08:00")).getOrThrow()
        }
        val slot = runBlocking {
            medRepo.getSlotsForDay(today).first { it.medicationId == medId && it.timeText == "08:00" }
        }
        try {
            val intent = MedicationReminders.createOpenConfirmationIntent(
                context = context,
                medicationId = medId,
                timeId = slot.timeId,
                dateEpochDay = today.toEpochDay()
            )
            // 1. 首次打开显示具体药品、剂量与时间；点「暂不打卡」不写入打卡，重建不重放
            ActivityScenario.launch<MainActivity>(intent).use { scenario ->
                val inDialog = hasAnyAncestor(hasTestTag("medicationConfirmDialog"))
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.onAllNodes(hasText(uniqueName) and inDialog)
                        .fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNode(hasText("确认服药打卡") and inDialog).assertIsDisplayed()
                composeRule.onNode(hasText(uniqueName) and inDialog).assertIsDisplayed()
                composeRule.onNode(hasText("每次用量：5mg") and inDialog).assertIsDisplayed()
                composeRule.onNode(hasText("计划时间：今天 08:00") and inDialog).assertIsDisplayed()

                composeRule.onNodeWithTag("medicationDismissButton").performClick()
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("medicationConfirmDialog").assertDoesNotExist()
                assertFalse(
                    runBlocking {
                        medRepo.getSlotsForDay(today).first { it.timeId == slot.timeId }.taken
                    }
                )

                scenario.recreate()
                composeRule.waitForIdle()
                composeRule.onNodeWithTag("medicationConfirmDialog").assertDoesNotExist()
            }

            // 2. 再次打开并点「确认已打卡」，写入打卡记录
            ActivityScenario.launch<MainActivity>(intent).use {
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.onAllNodes(hasText("确认已打卡")).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithTag("medicationConfirmButton").performClick()
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    runBlocking {
                        medRepo.getSlotsForDay(today).first { it.timeId == slot.timeId }.taken
                    }
                }
                assertTrue(
                    runBlocking {
                        medRepo.getSlotsForDay(today).first { it.timeId == slot.timeId }.taken
                    }
                )
            }

            // 3. 重复点击旧通知：提示已打卡，不重复报错
            ActivityScenario.launch<MainActivity>(intent).use {
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.onAllNodes(hasText("该服药计划已打卡")).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText("该服药计划已打卡").assertIsDisplayed()
                composeRule.onNodeWithText("知道了").performClick()
                composeRule.waitForIdle()
            }

            // 4. 删除药品后点击旧通知：提示提醒已失效，不崩溃
            runBlocking { medRepo.deleteMedication(medId).getOrThrow() }
            ActivityScenario.launch<MainActivity>(intent).use {
                composeRule.waitUntil(timeoutMillis = 5_000) {
                    composeRule.onAllNodes(hasText("提醒已失效")).fetchSemanticsNodes().isNotEmpty()
                }
                composeRule.onNodeWithText("提醒已失效").assertIsDisplayed()
                composeRule.onNodeWithText("知道了").performClick()
                composeRule.waitForIdle()
            }
        } finally {
            runBlocking {
                runCatching { medRepo.deleteMedication(medId) }
            }
        }
    }
}

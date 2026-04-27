package com.example.bloodpressurerecord.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel
) {
    val uiState by viewModel.uiState.collectAsState()

    if (uiState.showClearConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissClearAll,
            title = { Text("Clear all data") },
            text = { Text("This action cannot be undone.") },
            confirmButton = { TextButton(onClick = viewModel::confirmClearAll) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = viewModel::dismissClearAll) { Text("Cancel") } }
        )
    }

    if (uiState.showInfoDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showInfoDialog(false) },
            title = { Text("Info") },
            text = { Text("Local-only blood pressure tracker for family use.") },
            confirmButton = { TextButton(onClick = { viewModel.showInfoDialog(false) }) { Text("OK") } }
        )
    }

    if (uiState.showDisclaimerDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.showDisclaimerDialog(false) },
            title = { Text("Disclaimer") },
            text = { Text("This app is for record keeping and does not provide diagnosis.") },
            confirmButton = { TextButton(onClick = { viewModel.showDisclaimerDialog(false) }) { Text("Close") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SectionCard("User profile") {
            OutlinedTextField(value = uiState.name, onValueChange = viewModel::updateName, label = { Text("Name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = uiState.ageText, onValueChange = viewModel::updateAgeText, label = { Text("Age") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = uiState.gender, onValueChange = viewModel::updateGender, label = { Text("Gender") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            OutlinedTextField(value = uiState.targetSystolicText, onValueChange = viewModel::updateTargetSystolicText, label = { Text("Target systolic") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            OutlinedTextField(value = uiState.targetDiastolicText, onValueChange = viewModel::updateTargetDiastolicText, label = { Text("Target diastolic") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            Button(onClick = viewModel::saveUserProfile, modifier = Modifier.fillMaxWidth()) { Text("Save profile") }
        }

        SectionCard("Reminder") {
            SettingSwitch("Morning reminder", uiState.morningReminderEnabled, viewModel::setMorningReminderEnabled)
            OutlinedTextField(value = uiState.morningReminderTime, onValueChange = viewModel::updateMorningTime, label = { Text("Morning time HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            SettingSwitch("Evening reminder", uiState.eveningReminderEnabled, viewModel::setEveningReminderEnabled)
            OutlinedTextField(value = uiState.eveningReminderTime, onValueChange = viewModel::updateEveningTime, label = { Text("Evening time HH:mm") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Button(onClick = viewModel::saveReminderTimes, modifier = Modifier.fillMaxWidth()) { Text("Save reminder") }
        }

        SectionCard("Display") {
            SettingSwitch("Large text", uiState.isLargeTextEnabled, viewModel::setLargeTextEnabled)
            SettingSwitch("Show trend chart", uiState.showTrendChart, viewModel::setShowTrendChart)
            SettingSwitch("Enable high risk alert", uiState.highRiskAlertEnabled, viewModel::setHighRiskAlertEnabled)
        }

        SectionCard("Data management") {
            Button(onClick = viewModel::exportCsv, modifier = Modifier.fillMaxWidth()) { Text("Export CSV") }
            Button(onClick = viewModel::exportXlsx, modifier = Modifier.fillMaxWidth()) { Text("Export XLSX") }
            Button(onClick = viewModel::importCsv, modifier = Modifier.fillMaxWidth()) { Text("Import CSV") }
            Button(onClick = viewModel::importXlsx, modifier = Modifier.fillMaxWidth()) { Text("Import XLSX") }
            Button(onClick = viewModel::requestClearAll, modifier = Modifier.fillMaxWidth()) { Text("Clear all data") }
        }

        SectionCard("About") {
            Button(onClick = { viewModel.showInfoDialog(true) }, modifier = Modifier.fillMaxWidth()) { Text("Info") }
            Button(onClick = { viewModel.showDisclaimerDialog(true) }, modifier = Modifier.fillMaxWidth()) { Text("Disclaimer") }
        }

        if (uiState.message.isNotBlank()) {
            val success = uiState.message.contains("success", true) || uiState.message.contains("成功")
            Text(uiState.message, color = if (success) Color(0xFF1B5E20) else Color(0xFFB71C1C))
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun SettingSwitch(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(title, modifier = Modifier.padding(top = 10.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

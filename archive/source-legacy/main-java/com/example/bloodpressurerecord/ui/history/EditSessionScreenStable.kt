package com.example.bloodpressurerecord.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.ui.common.SessionReadingInputUi

private val editScenes = listOf("Morning", "Evening", "Other")
private val editSymptoms = listOf("None", "Headache", "Dizzy", "Palpitation", "Chest pain", "Blurred vision", "Other")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditSessionScreen(
    viewModel: EditSessionViewModel,
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    if (uiState.showAbnormalConfirmDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissAbnormalDialog,
            title = { Text("Abnormal value") },
            text = { Text(uiState.abnormalConfirmMessage) },
            confirmButton = { TextButton(onClick = viewModel::confirmAbnormalAndContinue) { Text("Continue") } },
            dismissButton = { TextButton(onClick = viewModel::dismissAbnormalDialog) { Text("Back") } }
        )
    }
    if (uiState.showHighRiskDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissHighRiskDialog,
            title = { Text("High risk alert") },
            text = { Text("Reading exceeds 180/120. Continue saving?") },
            confirmButton = { TextButton(onClick = viewModel::confirmHighRiskAndSave) { Text("Confirm") } },
            dismissButton = { TextButton(onClick = viewModel::dismissHighRiskDialog) { Text("Back") } }
        )
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Edit Session", style = MaterialTheme.typography.headlineMedium)
        if (uiState.loading) {
            Text("Loading...")
            return@Column
        }
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = uiState.measuredAtText,
                    onValueChange = viewModel::updateMeasuredAtText,
                    label = { Text("Time (yyyy-MM-dd HH:mm)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    editScenes.forEach { scene ->
                        FilterChip(selected = uiState.scene == scene, onClick = { viewModel.updateScene(scene) }, label = { Text(scene) })
                    }
                }
                EditReadingBlock("Reading 1", uiState.reading1, viewModel::updateReading1Systolic, viewModel::updateReading1Diastolic, viewModel::updateReading1Pulse)
                EditReadingBlock("Reading 2", uiState.reading2, viewModel::updateReading2Systolic, viewModel::updateReading2Diastolic, viewModel::updateReading2Pulse)
                if (uiState.showThirdReading) {
                    EditReadingBlock("Reading 3 (optional)", uiState.reading3, viewModel::updateReading3Systolic, viewModel::updateReading3Diastolic, viewModel::updateReading3Pulse)
                    TextButton(onClick = { viewModel.toggleThirdReading(false) }) { Text("Hide third reading") }
                } else {
                    TextButton(onClick = { viewModel.toggleThirdReading(true) }) { Text("Add third reading") }
                }
                OutlinedTextField(
                    value = uiState.note,
                    onValueChange = viewModel::updateNote,
                    label = { Text("Note") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    editSymptoms.forEach { symptom ->
                        FilterChip(
                            selected = uiState.selectedSymptoms.contains(symptom),
                            onClick = { viewModel.toggleSymptom(symptom) },
                            label = { Text(symptom) }
                        )
                    }
                }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("Computed", style = MaterialTheme.typography.titleMedium)
                        Text("Avg SBP: ${uiState.avgSystolic ?: "--"}")
                        Text("Avg DBP: ${uiState.avgDiastolic ?: "--"}")
                        Text("Avg Pulse: ${uiState.avgPulse ?: "--"}")
                        Text("Category: ${uiState.categoryLabel}")
                    }
                }
                Button(onClick = viewModel::onSaveClicked, modifier = Modifier.fillMaxWidth()) { Text("Save changes") }
                if (uiState.message.isNotBlank()) {
                    val ok = uiState.message.contains("success", true) || uiState.message.contains("成功")
                    Text(uiState.message, color = if (ok) Color(0xFF1B5E20) else Color(0xFFB71C1C))
                }
            }
        }
    }
}

@Composable
private fun EditReadingBlock(
    title: String,
    input: SessionReadingInputUi,
    onSystolic: (String) -> Unit,
    onDiastolic: (String) -> Unit,
    onPulse: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(value = input.systolic, onValueChange = onSystolic, label = { Text("Systolic") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = input.diastolic, onValueChange = onDiastolic, label = { Text("Diastolic") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        OutlinedTextField(value = input.pulse, onValueChange = onPulse, label = { Text("Pulse (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
    }
}

package com.example.bloodpressurerecord.ui.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.bloodpressurerecord.R
import com.example.bloodpressurerecord.ui.common.AppPrimaryButton
import com.example.bloodpressurerecord.ui.theme.AppDimensions
import com.example.bloodpressurerecord.ui.theme.AppSpacing

/** 表单只展示草稿，解析、校验、保存与错误状态均由 ViewModel 处理。 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BpZoneTargetSheet(
    state: BpZoneTargetEditorUiState,
    onChange: (BpZoneAxis, BpZoneField, String) -> Unit,
    onSave: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true, confirmValueChange = { !state.saving })
    ) {
        BoxWithConstraints {
            val compactFields = maxWidth < 360.dp || LocalDensity.current.fontScale >= 1.3f
            Column(
                Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState())
                    .padding(horizontal = AppSpacing.xLarge).padding(bottom = AppSpacing.xLarge),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.medium)
            ) {
                Text(stringResource(R.string.bp_zone_config_title), style = MaterialTheme.typography.titleLarge)
                Text(stringResource(R.string.bp_zone_config_placeholder_note), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.bp_zone_config_help), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                BpZoneAxis.entries.forEach { axis ->
                    val axisLabel = stringResource(if (axis == BpZoneAxis.SYS) R.string.bp_zone_config_sys else R.string.bp_zone_config_dia)
                    Text(axisLabel, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    val fieldRows = if (compactFields) BpZoneField.entries.map { listOf(it) } else listOf(
                        listOf(BpZoneField.MIN, BpZoneField.MAX),
                        listOf(BpZoneField.LOW_CUT, BpZoneField.NORMAL_CUT, BpZoneField.HIGH_CUT)
                    )
                    fieldRows.forEach { fields ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.small)) {
                            fields.forEach { field ->
                                val fieldLabel = stringResource(field.labelRes())
                                val description = stringResource(R.string.bp_zone_config_field_description, axisLabel, fieldLabel)
                                OutlinedTextField(
                                    value = state.draft.value(axis, field),
                                    onValueChange = { onChange(axis, field, it) },
                                    modifier = Modifier.weight(1f).semantics { contentDescription = description },
                                    label = { Text(fieldLabel, style = MaterialTheme.typography.labelSmall) },
                                    singleLine = true,
                                    enabled = !state.saving,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    isError = state.errorRes == R.string.bp_zone_config_invalid
                                )
                            }
                        }
                    }
                }
                state.errorRes?.let { error ->
                    Text(stringResource(error), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
                AppPrimaryButton(
                    text = stringResource(if (state.saving) R.string.bp_zone_config_saving else R.string.bp_zone_config_save),
                    onClick = onSave,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !state.saving
                )
                TextButton(onClick = onDismiss, enabled = !state.saving,
                    modifier = Modifier.fillMaxWidth().heightIn(min = AppDimensions.minimumTouchTarget)) {
                    Text(stringResource(R.string.bp_zone_config_cancel))
                }
            }
        }
    }
}

private fun BpZoneField.labelRes(): Int = when (this) {
    BpZoneField.MIN -> R.string.bp_zone_config_field_min
    BpZoneField.LOW_CUT -> R.string.bp_zone_config_field_low_cut
    BpZoneField.NORMAL_CUT -> R.string.bp_zone_config_field_normal_cut
    BpZoneField.HIGH_CUT -> R.string.bp_zone_config_field_high_cut
    BpZoneField.MAX -> R.string.bp_zone_config_field_max
}

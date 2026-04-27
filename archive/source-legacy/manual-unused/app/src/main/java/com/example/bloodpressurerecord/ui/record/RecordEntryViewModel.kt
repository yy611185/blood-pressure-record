package com.example.bloodpressurerecord.ui.record

import androidx.lifecycle.ViewModel

data class RecordEntryUiState(
    val message: String = "旧入口已保留，后续将并入“新增测量”页。"
)

class RecordEntryViewModel : ViewModel()

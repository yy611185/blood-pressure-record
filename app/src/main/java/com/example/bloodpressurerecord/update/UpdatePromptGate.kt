package com.example.bloodpressurerecord.update

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 只暴露本地操作是否忙碌，不接触操作内容或健康数据。 */
class UpdatePromptGate {
    private val leases = mutableSetOf<Any>()
    private val mutableBusy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = mutableBusy.asStateFlow()

    fun hold(): () -> Unit {
        val token = Any()
        synchronized(leases) {
            leases.add(token)
            mutableBusy.value = true
        }
        return {
            synchronized(leases) {
                leases.remove(token)
                mutableBusy.value = leases.isNotEmpty()
            }
        }
    }
}

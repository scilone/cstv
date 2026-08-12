package com.cstv.app.data.remote

import com.cstv.app.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CstvSessionStateHolder @Inject constructor() {
    private val mutableState = MutableStateFlow<CstvSessionState>(CstvSessionState.Unknown)
    val state: StateFlow<CstvSessionState> = mutableState.asStateFlow()
    fun publish(value: CstvSessionState) { mutableState.value = value }
}

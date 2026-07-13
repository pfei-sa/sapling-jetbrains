package io.github.pfeisa.sapling.settings

import com.intellij.openapi.components.SerializablePersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

@Service
@State(name = "SaplingSettings", storages = [Storage("sapling.xml")])
class SaplingSettings : SerializablePersistentStateComponent<SaplingSettings.State>(State()) {

    data class State(
        @JvmField var executablePath: String = "sl",
        @JvmField var autoOpenIsl: Boolean = false,
    )

    var executablePath: String
        get() = state.executablePath
        set(value) { updateState { it.copy(executablePath = value) } }

    var autoOpenIsl: Boolean
        get() = state.autoOpenIsl
        set(value) { updateState { it.copy(autoOpenIsl = value) } }

    companion object {
        fun getInstance(): SaplingSettings = service()
    }
}

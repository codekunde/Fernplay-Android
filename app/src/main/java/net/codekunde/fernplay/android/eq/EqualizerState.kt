package net.codekunde.fernplay.android.eq

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import net.codekunde.fernplay.android.settings.SettingsStore

/**
 * The one EQ of the app process. The EQ screen changes it, the player's
 * [EqualizerProcessor] reads [settings] on the audio thread, and changes are
 * saved a moment after the last one (a slider drag is many changes).
 */
@OptIn(FlowPreview::class)
class EqualizerState private constructor(private val store: SettingsStore) {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _settings = MutableStateFlow(EqSettings())
    private val _presets = MutableStateFlow<List<EqPreset>>(emptyList())
    @Volatile private var touched = false

    val settings: StateFlow<EqSettings> = _settings
    /** The user's own presets, in the order saved. */
    val presets: StateFlow<List<EqPreset>> = _presets

    init {
        scope.launch {
            val saved = store.equalizer.first()
            // A change made in the few milliseconds before the read wins over the saved value.
            if (!touched) _settings.value = saved.settings
            _presets.value = saved.presets
            _settings.drop(1).debounce(SAVE_AFTER_MS).collect { store.setEqualizer(it) }
        }
    }

    fun setEnabled(on: Boolean) = change { it.copy(enabled = on) }
    fun setBand(index: Int, db: Double) = change { it.withBand(index, db) }
    fun setPreamp(db: Double) = change { it.withPreamp(db) }
    fun apply(preset: EqPreset) = change { it.apply(preset) }

    /** Saves the curve as it is now under [name] (replacing an own preset of that name). Check [UserPresets.nameProblem] first. */
    fun savePreset(name: String) {
        val list = UserPresets.save(_presets.value, EqPreset.from(name, _settings.value))
        _presets.value = list
        scope.launch { store.setEqPresets(list) }
    }

    fun deletePreset(name: String) {
        val list = UserPresets.delete(_presets.value, name)
        _presets.value = list
        scope.launch { store.setEqPresets(list) }
    }

    private inline fun change(f: (EqSettings) -> EqSettings) {
        touched = true
        _settings.update(f)
    }

    companion object {
        private const val SAVE_AFTER_MS = 400L

        @Volatile private var instance: EqualizerState? = null

        fun get(context: Context): EqualizerState =
            instance ?: synchronized(this) {
                instance ?: EqualizerState(SettingsStore(context.applicationContext)).also { instance = it }
            }
    }
}

package io.github.juliegaunt.chainone.ui.counter

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import io.github.juliegaunt.chainone.R
import io.github.juliegaunt.chainone.appContainer
import io.github.juliegaunt.chainone.data.db.Counter
import io.github.juliegaunt.chainone.data.repository.CounterRepository
import io.github.juliegaunt.chainone.data.settings.AppSettings
import io.github.juliegaunt.chainone.ui.navigation.ARG_PATTERN_ID
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class CounterUiState(
    val counters: List<Counter> = emptyList(),
    val canUndo: Boolean = false,
    val countWithVolumeKeys: Boolean = false,
    val keepScreenOn: Boolean = true,
    val isLoading: Boolean = true,
) {
    /** The first counter: the one on the counter bar and the volume buttons. */
    val main: Counter? get() = counters.firstOrNull()
}

/** The counters of the pattern whose page is open. */
class CounterViewModel(
    savedStateHandle: SavedStateHandle,
    private val repository: CounterRepository,
    private val settings: AppSettings,
    /** Name for the counter made automatically, "Row". */
    private val defaultName: String,
) : ViewModel() {

    private val patternId: Long = checkNotNull(savedStateHandle[ARG_PATTERN_ID])

    val uiState: StateFlow<CounterUiState> = combine(
        repository.observeCounters(patternId),
        repository.observeCanUndo(patternId),
        settings.countWithVolumeKeys,
        settings.keepScreenOn,
    ) { counters, canUndo, volumeKeys, keepScreenOn ->
        CounterUiState(counters, canUndo, volumeKeys, keepScreenOn, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CounterUiState())

    /** Called when the Counter tab opens: a pattern with no counters gets a "Row" counter. */
    fun ensureCounter() {
        viewModelScope.launch { repository.mainCounter(patternId, defaultName) }
    }

    fun increment(counterId: Long) = launch { repository.increment(counterId) }

    fun decrement(counterId: Long) = launch { repository.decrement(counterId) }

    /** For the counter bar and volume buttons, which work even before a counter exists. */
    fun incrementMain() = launch { repository.increment(repository.mainCounter(patternId, defaultName).id) }

    fun decrementMain() = launch { repository.decrement(repository.mainCounter(patternId, defaultName).id) }

    /** A row tapped in the instructions: the main counter moves there and follows that section. */
    fun jumpToRow(value: Int, section: Int) = launch { repository.jumpToRow(patternId, defaultName, value, section) }

    fun setValue(counterId: Long, value: Int) = launch { repository.setValue(counterId, value) }

    fun reset(counterId: Long) = launch { repository.reset(counterId) }

    fun undo() = launch { repository.undo(patternId) }

    fun add(name: String, repeatLength: Int?, repeatTimes: Int?) = launch { repository.add(patternId, name, repeatLength, repeatTimes) }

    fun update(counterId: Long, name: String, repeatLength: Int?, repeatTimes: Int?) =
        launch { repository.updateDetails(counterId, name, repeatLength, repeatTimes) }

    fun delete(counterId: Long) = launch { repository.delete(counterId) }

    fun move(counterId: Long, by: Int) = launch { repository.move(counterId, by) }

    fun setCountWithVolumeKeys(enabled: Boolean) = settings.setCountWithVolumeKeys(enabled)

    fun setKeepScreenOn(enabled: Boolean) = settings.setKeepScreenOn(enabled)

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val container = appContainer
                CounterViewModel(
                    createSavedStateHandle(),
                    container.counterRepository,
                    container.settings,
                    defaultName = checkNotNull(this[APPLICATION_KEY]).getString(R.string.counter_default_name),
                )
            }
        }
    }
}

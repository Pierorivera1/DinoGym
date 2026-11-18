package io.github.pierorivera1.dinogym.ui.share

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.share.PublishResult
import io.github.pierorivera1.dinogym.data.share.ShareRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What is being shared. */
sealed interface ShareTarget {
    val id: String

    data class Routine(override val id: String) : ShareTarget
    data class Program(override val id: String) : ShareTarget
}

/** The share sheet (mock `ShareFlow.dc.html`). Null: no sheet. */
data class ShareUiState(
    val target: ShareTarget,
    val step: Step,
    /** Set in [Step.DONE]. */
    val url: String? = null,
    /** True once, right after the link is made: the screen opens Android's share sheet. */
    val sendNow: Boolean = false,
) {
    enum class Step { WORKING, DONE, SIGNED_OUT, OFFLINE, TOO_MANY, TOO_BIG, EMPTY, FAILED }
}

/**
 * Publishing a routine or program from its editor (task 23, decision 103). One per editor
 * entry; the editor's own ViewModel is not involved, since sharing reads what Room holds.
 */
@HiltViewModel
class ShareViewModel @Inject constructor(
    private val repository: ShareRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<ShareUiState?>(null)
    val state: StateFlow<ShareUiState?> = _state.asStateFlow()
    private var job: Job? = null

    fun share(target: ShareTarget) {
        if (_state.value?.step == ShareUiState.Step.WORKING) return
        _state.value = ShareUiState(target, ShareUiState.Step.WORKING)
        job = viewModelScope.launch {
            val result = when (target) {
                is ShareTarget.Routine -> repository.publishRoutine(target.id)
                is ShareTarget.Program -> repository.publishProgram(target.id)
            }
            _state.value = when (result) {
                is PublishResult.Shared -> ShareUiState(target, ShareUiState.Step.DONE, url = result.url, sendNow = true)
                PublishResult.SignedOut -> ShareUiState(target, ShareUiState.Step.SIGNED_OUT)
                PublishResult.Offline -> ShareUiState(target, ShareUiState.Step.OFFLINE)
                PublishResult.TooMany -> ShareUiState(target, ShareUiState.Step.TOO_MANY)
                PublishResult.TooBig -> ShareUiState(target, ShareUiState.Step.TOO_BIG)
                PublishResult.Empty -> ShareUiState(target, ShareUiState.Step.EMPTY)
                PublishResult.Failed -> ShareUiState(target, ShareUiState.Step.FAILED)
            }
        }
    }

    fun retry() {
        _state.value?.target?.let { share(it) }
    }

    /** The screen opened Android's share sheet. */
    fun sent() {
        _state.value = _state.value?.copy(sendNow = false)
    }

    /** Closing while the link is being made leaves the request to finish, unseen. */
    fun close() {
        job?.cancel()
        _state.value = null
    }
}

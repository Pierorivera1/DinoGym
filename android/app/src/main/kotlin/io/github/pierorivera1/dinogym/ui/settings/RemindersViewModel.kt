package io.github.pierorivera1.dinogym.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import io.github.pierorivera1.dinogym.data.account.SessionManager
import io.github.pierorivera1.dinogym.reminders.ReminderNotifier
import io.github.pierorivera1.dinogym.reminders.ReminderRepository
import io.github.pierorivera1.dinogym.reminders.ReminderRules
import io.github.pierorivera1.dinogym.reminders.ReminderSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject

/** What Settings › REMINDERS shows (mock `Reminders.dc.html`). */
sealed interface RemindersUiState {
    /** Before the account is read: nothing shows, so the card does not flash. */
    data object Loading : RemindersUiState

    /** Reminders need an account (decision 104). */
    data object SignedOut : RemindersUiState

    data class SignedIn(
        val settings: ReminderSettings,
        /** False when Android blocks DinoGym's notifications: the card says so first. */
        val notificationsAllowed: Boolean = true,
        /** The time sheet is open. */
        val editingTime: Boolean = false,
        /** This phone's zone's city, for the sheet's note: `Lima`. */
        val zoneCity: String = "",
    ) : RemindersUiState
}

/**
 * Settings › REMINDERS: the two switches and the time, stored as synced preferences
 * (decision 105). Whether notifications are allowed is checked again each time the screen
 * resumes, since the person may have changed it in Android settings.
 */
@HiltViewModel
class RemindersViewModel @Inject constructor(
    sessions: SessionManager,
    private val repository: ReminderRepository,
    private val notifier: ReminderNotifier,
) : ViewModel() {

    private val allowed = MutableStateFlow(notifier.areNotificationsAllowed())
    private val editingTime = MutableStateFlow(false)

    val state: StateFlow<RemindersUiState> =
        combine(sessions.userId, repository.settings, allowed, editingTime) { userId, settings, isAllowed, editing ->
            if (userId == null) {
                RemindersUiState.SignedOut
            } else {
                RemindersUiState.SignedIn(settings, isAllowed, editing, ReminderRules.zoneCity(ZoneId.systemDefault().id))
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RemindersUiState.Loading)

    fun refreshPermission() {
        allowed.value = notifier.areNotificationsAllowed()
    }

    fun setWorkoutDay(on: Boolean) {
        viewModelScope.launch { repository.setWorkoutDay(on) }
    }

    fun setWeekly(on: Boolean) {
        viewModelScope.launch { repository.setWeekly(on) }
    }

    fun openTime() {
        editingTime.value = true
    }

    fun closeTime() {
        editingTime.value = false
    }

    fun saveTime(time: LocalTime) {
        editingTime.value = false
        viewModelScope.launch { repository.setTime(time) }
    }
}

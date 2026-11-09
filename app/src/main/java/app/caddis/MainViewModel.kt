package app.caddis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.caddis.data.SettingsRepository
import app.caddis.ui.Messenger
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository, val messenger: Messenger) : ViewModel() {
    /** null until DataStore answers, so the first frame never flashes the wrong screen. */
    val onboarded: StateFlow<Boolean?> = settings.settings.map { it.onboarded }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A route handed in by a notification or the share sheet, opened once the app shell is up. */
    val pendingRoute = MutableStateFlow<String?>(null)
}

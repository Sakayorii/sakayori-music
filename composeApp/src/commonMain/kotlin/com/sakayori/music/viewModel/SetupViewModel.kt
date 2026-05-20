package com.sakayori.music.viewModel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.music.expect.ui.clearWebViewCacheAndCookies
import com.sakayori.music.utils.DeviceId
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class SetupStep(val order: Int) {
    Welcome(0),
    Permissions(1),
    Appearance(2),
    Identity(3),
    Account(4),
    Done(5);

    companion object {
        val LAST_ORDER = entries.maxOf { it.order }

        fun fromOrder(order: Int): SetupStep =
            entries.first { it.order == order.coerceIn(0, LAST_ORDER) }
    }
}

data class NameConflict(
    val requested: String,
    val suggested: String,
)

data class SetupUiState(
    val currentStep: SetupStep = SetupStep.Welcome,
    val notificationsGranted: Boolean = false,
    val batteryOptIgnored: Boolean = false,
    val systemThemeFollow: Boolean = true,
    val themeMode: String = DataStoreManager.THEME_MODE_SYSTEM,
    val systemLanguageFollow: Boolean = true,
    val languageCode: String = "en-US",
    val userName: String = "",
    val resolvedDisplayId: String = "",
    val isCheckingName: Boolean = false,
    val nameConflict: NameConflict? = null,
    val googleSignedIn: Boolean = false,
    val isRestoring: Boolean = false,
    val isCompleting: Boolean = false,
    val showUpgradePopup: Boolean = false,
)

sealed interface SetupEvent {
    data class Toast(val message: String) : SetupEvent
    data object SetupComplete : SetupEvent
    data class RestoreFinished(val success: Boolean, val message: String) : SetupEvent
}

class SetupViewModel(
    private val dataStoreManager: DataStoreManager,
) : ViewModel() {

    private val _state = MutableStateFlow(SetupUiState())
    val state = _state.asStateFlow()

    private val _events = MutableSharedFlow<SetupEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    init {
        viewModelScope.launch {
            try {
                if (dataStoreManager.initialSetupDone.first() != DataStoreManager.TRUE) {
                    clearWebViewCacheAndCookies()
                    val priorName = dataStoreManager.userName.first()
                    val priorOpens = dataStoreManager.openAppTime.first()
                    val isExisting = priorName.isNotBlank() || priorOpens > 5
                    if (isExisting) {
                        _state.update { it.copy(showUpgradePopup = true) }
                    }
                }
            } catch (_: Throwable) {
            }
        }
        viewModelScope.launch {
            dataStoreManager.getString(com.sakayori.common.SELECTED_LANGUAGE).collect { savedLang ->
                val resolved = savedLang?.takeIf { it.isNotBlank() }
                if (resolved != null) {
                    _state.update { it.copy(languageCode = resolved) }
                }
            }
        }
        viewModelScope.launch {
            combine(
                dataStoreManager.themeMode,
                dataStoreManager.systemThemeFollow,
                dataStoreManager.systemLanguageFollow,
                dataStoreManager.userName,
                dataStoreManager.userDisplayId,
            ) { theme, systemTheme, systemLang, name, displayId ->
                Prefs(
                    theme = theme,
                    systemTheme = systemTheme == DataStoreManager.TRUE,
                    systemLang = systemLang == DataStoreManager.TRUE,
                    name = name,
                    displayId = displayId,
                )
            }.collect { prefs ->
                _state.update {
                    it.copy(
                        themeMode = prefs.theme,
                        systemThemeFollow = prefs.systemTheme,
                        systemLanguageFollow = prefs.systemLang,
                        userName = prefs.name,
                        resolvedDisplayId = prefs.displayId,
                    )
                }
            }
        }
    }

    private data class Prefs(
        val theme: String,
        val systemTheme: Boolean,
        val systemLang: Boolean,
        val name: String,
        val displayId: String,
    )

    fun goToStep(step: SetupStep) {
        _state.update { it.copy(currentStep = step) }
    }

    fun dismissUpgradePopup() {
        _state.update { it.copy(showUpgradePopup = false) }
    }

    fun goNext() {
        val current = _state.value.currentStep
        val target = SetupStep.fromOrder(current.order + 1)
        _state.update { it.copy(currentStep = target) }
    }

    fun goBack() {
        val current = _state.value.currentStep
        val target = SetupStep.fromOrder(current.order - 1)
        _state.update { it.copy(currentStep = target) }
    }

    fun setNotificationsGranted(granted: Boolean) {
        _state.update { it.copy(notificationsGranted = granted) }
    }

    fun setBatteryOptIgnored(ignored: Boolean) {
        _state.update { it.copy(batteryOptIgnored = ignored) }
    }

    fun setThemeMode(mode: String) {
        val followSystem = mode == DataStoreManager.THEME_MODE_SYSTEM
        viewModelScope.launch {
            dataStoreManager.setThemeMode(mode)
            dataStoreManager.setSystemThemeFollow(followSystem)
        }
    }

    fun setLanguage(code: String, followSystem: Boolean) {
        viewModelScope.launch {
            dataStoreManager.setSystemLanguageFollow(followSystem)
            if (!followSystem) {
                dataStoreManager.putString(com.sakayori.common.SELECTED_LANGUAGE, code)
            }
        }
        _state.update { it.copy(languageCode = code, systemLanguageFollow = followSystem) }
    }

    fun onUserNameChange(name: String) {
        _state.update { it.copy(userName = name, nameConflict = null) }
    }

    fun finalizeIdentity(onDone: () -> Unit = {}) {
        viewModelScope.launch {
            _state.update { it.copy(isCheckingName = true) }
            val typed = _state.value.userName.trim()
            val display = DeviceId.resolveUserDisplayId(typed, conflictSuffix = null)
            dataStoreManager.setUserName(typed)
            dataStoreManager.setUserDisplayId(display)
            _state.update {
                it.copy(
                    isCheckingName = false,
                    resolvedDisplayId = display,
                )
            }
            onDone()
        }
    }

    fun applyNameConflictSuggestion() {
        val conflict = _state.value.nameConflict ?: return
        viewModelScope.launch {
            dataStoreManager.setUserName(conflict.requested)
            dataStoreManager.setUserDisplayId(conflict.suggested)
            _state.update {
                it.copy(
                    nameConflict = null,
                    resolvedDisplayId = conflict.suggested,
                )
            }
        }
    }

    fun setGoogleSignedIn(signedIn: Boolean) {
        _state.update { it.copy(googleSignedIn = signedIn) }
    }

    fun beginRestore() {
        _state.update { it.copy(isRestoring = true) }
    }

    fun finishRestore(success: Boolean, message: String) {
        _state.update { it.copy(isRestoring = false) }
        viewModelScope.launch {
            _events.emit(SetupEvent.RestoreFinished(success, message))
        }
    }

    fun completeSetup() {
        if (_state.value.isCompleting) return
        viewModelScope.launch {
            _state.update { it.copy(isCompleting = true) }
            val typed = _state.value.userName.trim()
            val existingDisplay = dataStoreManager.userDisplayId.first()
            if (existingDisplay.isBlank()) {
                val display = DeviceId.resolveUserDisplayId(typed, conflictSuffix = null)
                if (typed.isNotEmpty()) dataStoreManager.setUserName(typed)
                dataStoreManager.setUserDisplayId(display)
                _state.update { it.copy(resolvedDisplayId = display) }
            }
            dataStoreManager.setHelpBuildLyricsDatabase(true)
            dataStoreManager.setContributorLyricsDatabase(
                if (typed.isNotBlank()) typed to "" else null,
            )
            dataStoreManager.setCrashReportingEnabled(true)
            dataStoreManager.setInitialSetupDone(true)
            _state.update { it.copy(isCompleting = false) }
            _events.emit(SetupEvent.SetupComplete)
        }
    }
}

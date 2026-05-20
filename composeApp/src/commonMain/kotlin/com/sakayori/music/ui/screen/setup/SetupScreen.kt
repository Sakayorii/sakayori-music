package com.sakayori.music.ui.screen.setup

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sakayori.domain.manager.DataStoreManager
import com.sakayori.music.Platform
import com.sakayori.music.expect.SetupPlatform
import com.sakayori.music.getPlatform
import com.sakayori.music.ui.screen.setup.component.StepScaffold
import com.sakayori.music.viewModel.NameConflict
import com.sakayori.music.viewModel.SetupEvent
import com.sakayori.music.viewModel.SetupStep
import com.sakayori.music.viewModel.SetupViewModel
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import com.sakayori.music.generated.resources.Res
import com.sakayori.music.generated.resources.setup_account_google
import com.sakayori.music.generated.resources.setup_account_google_desc
import com.sakayori.music.generated.resources.setup_account_help_body
import com.sakayori.music.generated.resources.setup_account_help_close
import com.sakayori.music.generated.resources.setup_account_help_title
import com.sakayori.music.generated.resources.setup_account_later
import com.sakayori.music.generated.resources.setup_account_signed_in
import com.sakayori.music.generated.resources.setup_account_signin
import com.sakayori.music.generated.resources.setup_account_subtitle
import com.sakayori.music.generated.resources.setup_account_title
import com.sakayori.music.generated.resources.setup_appearance_subtitle
import com.sakayori.music.generated.resources.setup_appearance_title
import com.sakayori.music.generated.resources.setup_back
import com.sakayori.music.generated.resources.setup_done_display_format
import com.sakayori.music.generated.resources.setup_done_finishing
import com.sakayori.music.generated.resources.setup_done_start
import com.sakayori.music.generated.resources.setup_done_subtitle
import com.sakayori.music.generated.resources.setup_done_title
import com.sakayori.music.generated.resources.setup_get_started
import com.sakayori.music.generated.resources.setup_identity_accept_suggestion
import com.sakayori.music.generated.resources.setup_identity_checking
import com.sakayori.music.generated.resources.setup_identity_conflict_format
import com.sakayori.music.generated.resources.setup_identity_continue
import com.sakayori.music.generated.resources.setup_identity_hint
import com.sakayori.music.generated.resources.setup_identity_placeholder
import com.sakayori.music.generated.resources.setup_identity_subtitle
import com.sakayori.music.generated.resources.setup_identity_title
import com.sakayori.music.generated.resources.setup_ios_done_note
import com.sakayori.music.generated.resources.setup_lang_label
import com.sakayori.music.generated.resources.setup_lang_more_hint
import com.sakayori.music.generated.resources.setup_lang_system_format
import com.sakayori.music.generated.resources.setup_new_badge
import com.sakayori.music.generated.resources.setup_next
import com.sakayori.music.generated.resources.setup_perm_battery_desc
import com.sakayori.music.generated.resources.setup_perm_battery_ignored
import com.sakayori.music.generated.resources.setup_perm_battery_open
import com.sakayori.music.generated.resources.setup_perm_battery_title
import com.sakayori.music.generated.resources.setup_perm_notif_allow
import com.sakayori.music.generated.resources.setup_perm_notif_desc
import com.sakayori.music.generated.resources.setup_perm_notif_granted
import com.sakayori.music.generated.resources.setup_perm_notif_title
import com.sakayori.music.generated.resources.setup_perm_nothing
import com.sakayori.music.generated.resources.setup_perm_subtitle
import com.sakayori.music.generated.resources.setup_perm_title
import com.sakayori.music.generated.resources.setup_restore_desc
import com.sakayori.music.generated.resources.setup_restore_pick
import com.sakayori.music.generated.resources.setup_restore_progress
import com.sakayori.music.generated.resources.setup_restore_title
import com.sakayori.music.generated.resources.setup_skip
import com.sakayori.music.generated.resources.setup_theme_dark
import com.sakayori.music.generated.resources.setup_theme_label
import com.sakayori.music.generated.resources.setup_theme_light
import com.sakayori.music.generated.resources.setup_theme_oled
import com.sakayori.music.generated.resources.setup_theme_system
import com.sakayori.music.generated.resources.setup_upgrade_body
import com.sakayori.music.generated.resources.setup_upgrade_continue
import com.sakayori.music.generated.resources.setup_upgrade_title
import com.sakayori.music.generated.resources.setup_welcome_body
import com.sakayori.music.generated.resources.setup_welcome_subtitle
import com.sakayori.music.generated.resources.setup_welcome_title

private const val TOTAL_STEPS = 6
private const val STEP_TRANSITION_MS = 350

@Composable
fun SetupScreen(
    viewModel: SetupViewModel = koinInject(),
    onSetupComplete: () -> Unit,
) {
    val state by viewModel.state.collectAsState()

    LaunchedEffect(state.currentStep) {
        if (state.currentStep == SetupStep.Permissions) {
            viewModel.setNotificationsGranted(SetupPlatform.isNotificationGranted())
            viewModel.setBatteryOptIgnored(SetupPlatform.isBatteryOptIgnored())
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SetupEvent.SetupComplete -> onSetupComplete()
                else -> Unit
            }
        }
    }

    val backText = stringResource(Res.string.setup_back)
    val nextText = stringResource(Res.string.setup_next)
    val skipText = stringResource(Res.string.setup_skip)

    if (state.showUpgradePopup) {
        AlertDialog(
            onDismissRequest = viewModel::dismissUpgradePopup,
            title = { Text(stringResource(Res.string.setup_upgrade_title)) },
            text = { Text(stringResource(Res.string.setup_upgrade_body)) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissUpgradePopup) {
                    Text(stringResource(Res.string.setup_upgrade_continue))
                }
            },
        )
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        AnimatedContent(
            targetState = state.currentStep,
            transitionSpec = {
                val forward = targetState.order > initialState.order
                val direction = if (forward) 1 else -1
                val enterSlide = spring(
                    dampingRatio = 0.82f,
                    stiffness = Spring.StiffnessMediumLow,
                    visibilityThreshold = androidx.compose.ui.unit.IntOffset(1, 1),
                )
                val enterFade = tween<Float>(420, easing = FastOutSlowInEasing)
                val enterScale = tween<Float>(420, easing = FastOutSlowInEasing)
                val exitSlide = tween<androidx.compose.ui.unit.IntOffset>(260, easing = FastOutSlowInEasing)
                val exitFade = tween<Float>(180)
                val exitScale = tween<Float>(260, easing = FastOutSlowInEasing)
                (slideInHorizontally(
                    animationSpec = enterSlide,
                    initialOffsetX = { (it * 0.85f * direction).toInt() },
                ) + fadeIn(enterFade) + scaleIn(
                    animationSpec = enterScale,
                    initialScale = 0.94f,
                )).togetherWith(
                    slideOutHorizontally(
                        animationSpec = exitSlide,
                        targetOffsetX = { -it / 5 * direction },
                    ) + fadeOut(exitFade) + scaleOut(
                        animationSpec = exitScale,
                        targetScale = 0.93f,
                    ),
                )
            },
            label = "SetupStepTransition",
        ) { step ->
            when (step) {
                SetupStep.Welcome -> WelcomeStepContent(
                    onNext = viewModel::goNext,
                    backText = backText,
                )

                SetupStep.Permissions -> PermissionsStepContent(
                    notificationsGranted = state.notificationsGranted,
                    batteryOptIgnored = state.batteryOptIgnored,
                    onRequestNotification = {
                        SetupPlatform.requestNotificationPermission { granted ->
                            viewModel.setNotificationsGranted(granted)
                        }
                    },
                    onRequestBattery = {
                        SetupPlatform.requestIgnoreBatteryOptimizations()
                    },
                    onRecheck = {
                        viewModel.setNotificationsGranted(SetupPlatform.isNotificationGranted())
                        viewModel.setBatteryOptIgnored(SetupPlatform.isBatteryOptIgnored())
                    },
                    onNext = viewModel::goNext,
                    onBack = viewModel::goBack,
                    onSkip = viewModel::goNext,
                    backText = backText,
                    nextText = nextText,
                    skipText = skipText,
                )

                SetupStep.Appearance -> AppearanceStepContent(
                    themeMode = state.themeMode,
                    onThemeSelected = viewModel::setThemeMode,
                    languageTagSystem = SetupPlatform.systemLanguageTag(),
                    followSystemLanguage = state.systemLanguageFollow,
                    onLanguageSelected = viewModel::setLanguage,
                    onNext = viewModel::goNext,
                    onBack = viewModel::goBack,
                    backText = backText,
                    nextText = nextText,
                )

                SetupStep.Identity -> IdentityStepContent(
                    name = state.userName,
                    onNameChange = viewModel::onUserNameChange,
                    isChecking = state.isCheckingName,
                    conflict = state.nameConflict,
                    onAcceptConflictSuggestion = viewModel::applyNameConflictSuggestion,
                    onNext = { viewModel.finalizeIdentity { viewModel.goNext() } },
                    onBack = viewModel::goBack,
                    backText = backText,
                )

                SetupStep.Account -> AccountStepContent(
                    signedIn = state.googleSignedIn,
                    onSignIn = { viewModel.setGoogleSignedIn(false) },
                    onSkip = viewModel::goNext,
                    onNext = viewModel::goNext,
                    onBack = viewModel::goBack,
                    backText = backText,
                    nextText = nextText,
                    skipText = skipText,
                )

                SetupStep.Done -> DoneStepContent(
                    isRestoring = state.isRestoring,
                    isCompleting = state.isCompleting,
                    resolvedDisplayId = state.resolvedDisplayId,
                    onPickRestore = { viewModel.beginRestore() },
                    onComplete = viewModel::completeSetup,
                    onBack = viewModel::goBack,
                    backText = backText,
                )
            }
        }
    }
}

@Composable
private fun WelcomeStepContent(
    onNext: () -> Unit,
    backText: String,
) {
    StepScaffold(
        stepOrder = 0,
        totalSteps = TOTAL_STEPS,
        title = stringResource(Res.string.setup_welcome_title),
        subtitle = stringResource(Res.string.setup_welcome_subtitle),
        canGoBack = false,
        primaryButtonText = stringResource(Res.string.setup_get_started),
        onPrimary = onNext,
        onBack = {},
        backButtonText = backText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(32.dp))
                    .background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.MusicNote,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(64.dp),
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.setup_welcome_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
private fun PermissionsStepContent(
    notificationsGranted: Boolean,
    batteryOptIgnored: Boolean,
    onRequestNotification: () -> Unit,
    onRequestBattery: () -> Unit,
    onRecheck: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onSkip: () -> Unit,
    backText: String,
    nextText: String,
    skipText: String,
) {
    val allowSkip = !SetupPlatform.needsNotificationPermission || notificationsGranted
    StepScaffold(
        stepOrder = 1,
        totalSteps = TOTAL_STEPS,
        title = stringResource(Res.string.setup_perm_title),
        subtitle = stringResource(Res.string.setup_perm_subtitle),
        canGoBack = true,
        primaryButtonText = nextText,
        onPrimary = {
            onRecheck()
            onNext()
        },
        onBack = onBack,
        backButtonText = backText,
        secondaryButtonText = if (!allowSkip) skipText else null,
        onSecondary = if (!allowSkip) onSkip else null,
    ) {
        LifecycleResumeEffect(Unit) {
            onRecheck()
            onPauseOrDispose { }
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (SetupPlatform.needsNotificationPermission) {
                PermissionCard(
                    icon = Icons.Filled.Notifications,
                    title = stringResource(Res.string.setup_perm_notif_title),
                    description = stringResource(Res.string.setup_perm_notif_desc),
                    granted = notificationsGranted,
                    actionLabel = if (notificationsGranted)
                        stringResource(Res.string.setup_perm_notif_granted)
                    else stringResource(Res.string.setup_perm_notif_allow),
                    onAction = onRequestNotification,
                )
            }
            if (SetupPlatform.needsBatteryOptOut) {
                PermissionCard(
                    icon = Icons.Filled.PowerSettingsNew,
                    title = stringResource(Res.string.setup_perm_battery_title),
                    description = stringResource(Res.string.setup_perm_battery_desc),
                    granted = batteryOptIgnored,
                    actionLabel = if (batteryOptIgnored)
                        stringResource(Res.string.setup_perm_battery_ignored)
                    else stringResource(Res.string.setup_perm_battery_open),
                    onAction = onRequestBattery,
                )
            }
            if (!SetupPlatform.needsNotificationPermission && !SetupPlatform.needsBatteryOptOut) {
                Text(
                    text = stringResource(Res.string.setup_perm_nothing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    description: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (granted) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!granted) {
                OutlinedButton(
                    onClick = onAction,
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.align(Alignment.End),
                ) {
                    Text(actionLabel)
                }
            } else {
                Row(
                    modifier = Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = actionLabel,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

@Composable
private fun AppearanceStepContent(
    themeMode: String,
    onThemeSelected: (String) -> Unit,
    languageTagSystem: String,
    followSystemLanguage: Boolean,
    onLanguageSelected: (String, Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    backText: String,
    nextText: String,
) {
    StepScaffold(
        stepOrder = 2,
        totalSteps = TOTAL_STEPS,
        title = stringResource(Res.string.setup_appearance_title),
        subtitle = stringResource(Res.string.setup_appearance_subtitle),
        canGoBack = true,
        primaryButtonText = nextText,
        onPrimary = onNext,
        onBack = onBack,
        backButtonText = backText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                stringResource(Res.string.setup_theme_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            ThemeRow(themeMode = themeMode, onSelected = onThemeSelected)
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(Res.string.setup_lang_label),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            FilterChip(
                selected = followSystemLanguage,
                onClick = { onLanguageSelected(languageTagSystem, true) },
                label = { Text(stringResource(Res.string.setup_lang_system_format, languageTagSystem)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
            Text(
                stringResource(Res.string.setup_lang_more_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ThemeRow(themeMode: String, onSelected: (String) -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        ThemeChip(
            label = stringResource(Res.string.setup_theme_system),
            selected = themeMode == DataStoreManager.THEME_MODE_SYSTEM,
            onClick = { onSelected(DataStoreManager.THEME_MODE_SYSTEM) },
        )
        ThemeChip(
            label = stringResource(Res.string.setup_theme_light),
            selected = themeMode == DataStoreManager.THEME_MODE_LIGHT,
            onClick = { onSelected(DataStoreManager.THEME_MODE_LIGHT) },
        )
        ThemeChip(
            label = stringResource(Res.string.setup_theme_dark),
            selected = themeMode == DataStoreManager.THEME_MODE_DARK,
            onClick = { onSelected(DataStoreManager.THEME_MODE_DARK) },
        )
        ThemeChip(
            label = stringResource(Res.string.setup_theme_oled),
            selected = themeMode == DataStoreManager.THEME_MODE_OLED,
            onClick = { onSelected(DataStoreManager.THEME_MODE_OLED) },
            newBadge = true,
        )
    }
}

@Composable
private fun ThemeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    newBadge: Boolean = false,
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label)
                if (newBadge) {
                    Spacer(Modifier.width(6.dp))
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = MaterialTheme.colorScheme.tertiary,
                    ) {
                        Text(
                            stringResource(Res.string.setup_new_badge),
                            modifier = Modifier.padding(horizontal = 4.dp),
                            fontSize = 9.sp,
                            color = MaterialTheme.colorScheme.onTertiary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@Composable
private fun IdentityStepContent(
    name: String,
    onNameChange: (String) -> Unit,
    isChecking: Boolean,
    conflict: NameConflict?,
    onAcceptConflictSuggestion: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    backText: String,
) {
    StepScaffold(
        stepOrder = 3,
        totalSteps = TOTAL_STEPS,
        title = stringResource(Res.string.setup_identity_title),
        subtitle = stringResource(Res.string.setup_identity_subtitle),
        canGoBack = true,
        primaryButtonText = if (isChecking)
            stringResource(Res.string.setup_identity_checking)
        else stringResource(Res.string.setup_identity_continue),
        onPrimary = onNext,
        onBack = onBack,
        backButtonText = backText,
        isPrimaryEnabled = !isChecking,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                singleLine = true,
                placeholder = { Text(stringResource(Res.string.setup_identity_placeholder)) },
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
                ),
                shape = RoundedCornerShape(14.dp),
            )
            Text(
                stringResource(Res.string.setup_identity_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (conflict != null) {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            stringResource(
                                Res.string.setup_identity_conflict_format,
                                conflict.requested,
                                conflict.suggested,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Row(
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            TextButton(onClick = onAcceptConflictSuggestion) {
                                Text(stringResource(Res.string.setup_identity_accept_suggestion))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountStepContent(
    signedIn: Boolean,
    onSignIn: () -> Unit,
    onSkip: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    backText: String,
    nextText: String,
    skipText: String,
) {
    var helpOpen by remember { mutableStateOf(false) }
    StepScaffold(
        stepOrder = 4,
        totalSteps = TOTAL_STEPS,
        title = stringResource(Res.string.setup_account_title),
        subtitle = stringResource(Res.string.setup_account_subtitle),
        canGoBack = true,
        primaryButtonText = if (signedIn) nextText else skipText,
        onPrimary = if (signedIn) onNext else onSkip,
        onBack = onBack,
        backButtonText = backText,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(Res.string.setup_account_google),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { helpOpen = true }) {
                            Icon(
                                Icons.AutoMirrored.Filled.HelpOutline,
                                contentDescription = stringResource(Res.string.setup_account_help_title),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Text(
                        stringResource(Res.string.setup_account_google_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onSignIn,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            if (signedIn) stringResource(Res.string.setup_account_signed_in)
                            else stringResource(Res.string.setup_account_signin),
                        )
                    }
                }
            }
            Text(
                stringResource(Res.string.setup_account_later),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    if (helpOpen) {
        AlertDialog(
            onDismissRequest = { helpOpen = false },
            title = { Text(stringResource(Res.string.setup_account_help_title)) },
            text = { Text(stringResource(Res.string.setup_account_help_body)) },
            confirmButton = {
                TextButton(onClick = { helpOpen = false }) {
                    Text(stringResource(Res.string.setup_account_help_close))
                }
            },
        )
    }
}

@Composable
private fun DoneStepContent(
    isRestoring: Boolean,
    isCompleting: Boolean,
    resolvedDisplayId: String,
    onPickRestore: () -> Unit,
    onComplete: () -> Unit,
    onBack: () -> Unit,
    backText: String,
) {
    val isIos = remember { getPlatform() == Platform.iOS }
    StepScaffold(
        stepOrder = 5,
        totalSteps = TOTAL_STEPS,
        title = stringResource(Res.string.setup_done_title),
        subtitle = stringResource(Res.string.setup_done_subtitle),
        canGoBack = true,
        primaryButtonText = if (isCompleting)
            stringResource(Res.string.setup_done_finishing)
        else stringResource(Res.string.setup_done_start),
        onPrimary = onComplete,
        onBack = onBack,
        backButtonText = backText,
        isPrimaryEnabled = !isCompleting && !isRestoring,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isIos) {
                Text(
                    text = stringResource(Res.string.setup_ios_done_note),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Restore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            stringResource(Res.string.setup_restore_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    Text(
                        stringResource(Res.string.setup_restore_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = onPickRestore,
                        enabled = !isRestoring,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.align(Alignment.End),
                    ) {
                        Text(
                            if (isRestoring) stringResource(Res.string.setup_restore_progress)
                            else stringResource(Res.string.setup_restore_pick),
                        )
                    }
                }
            }
            if (resolvedDisplayId.isNotBlank()) {
                Text(
                    text = stringResource(Res.string.setup_done_display_format, resolvedDisplayId),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

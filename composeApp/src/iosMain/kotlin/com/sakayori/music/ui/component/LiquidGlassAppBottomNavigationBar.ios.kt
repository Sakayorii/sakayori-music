package com.sakayori.music.ui.component

import androidx.compose.runtime.Composable
import androidx.navigation.NavController
import com.sakayori.music.expect.ui.PlatformBackdrop
import com.sakayori.music.viewModel.SharedViewModel
import kotlin.reflect.KClass

@Composable
actual fun LiquidGlassAppBottomNavigationBar(
    startDestination: Any,
    navController: NavController,
    backdrop: PlatformBackdrop,
    viewModel: SharedViewModel,
    isScrolledToTop: Boolean,
    onOpenNowPlaying: () -> Unit,
    reloadDestinationIfNeeded: (KClass<*>) -> Unit,
) {}

package com.sakayori.music.ui.navigation.graph

import androidx.compose.foundation.layout.PaddingValues
import androidx.navigation.NavController
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import androidx.navigation.toRoute
import com.sakayori.music.ui.navigation.destination.home.AnalyticsDestination
import com.sakayori.music.ui.navigation.destination.home.CreditDestination
import com.sakayori.music.ui.navigation.destination.home.EqualizerDestination
import com.sakayori.music.ui.navigation.destination.home.MoodDestination
import com.sakayori.music.ui.navigation.destination.home.NotificationDestination
import com.sakayori.music.ui.navigation.destination.home.RecentlySongsDestination
import com.sakayori.music.ui.navigation.destination.home.SettingsDestination
import com.sakayori.music.ui.screen.home.MoodScreen
import com.sakayori.music.ui.screen.home.NotificationScreen
import com.sakayori.music.ui.screen.home.RecentlySongsScreen
import com.sakayori.music.ui.screen.home.EqualizerScreen
import com.sakayori.music.ui.screen.home.SettingScreen
import com.sakayori.music.ui.screen.home.analytics.AnalyticsScreen
import com.sakayori.music.ui.screen.other.CreditScreen

fun NavGraphBuilder.homeScreenGraph(
    innerPadding: PaddingValues,
    navController: NavController,
) {
    composable<CreditDestination> {
        CreditScreen(
            paddingValues = innerPadding,
            navController = navController,
        )
    }
    composable<MoodDestination> { entry ->
        val params = entry.toRoute<MoodDestination>().params
        MoodScreen(
            navController = navController,
            params = params,
        )
    }
    composable<NotificationDestination> {
        NotificationScreen(
            navController = navController,
        )
    }
    composable<RecentlySongsDestination> {
        RecentlySongsScreen(
            navController = navController,
            innerPadding = innerPadding,
        )
    }
    composable<SettingsDestination> {
        SettingScreen(
            navController = navController,
            innerPadding = innerPadding,
        )
    }
    composable<AnalyticsDestination> {
        AnalyticsScreen(
            navController = navController,
            innerPadding = innerPadding,
        )
    }
    composable<EqualizerDestination> {
        EqualizerScreen(
            navController = navController,
        )
    }
}

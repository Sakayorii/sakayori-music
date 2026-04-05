package com.sakayori.music

import android.Manifest
import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.lifecycleScope
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.eygraber.uri.toKmpUriOrNull
import com.sakayori.common.FIRST_TIME_MIGRATION
import com.sakayori.common.SELECTED_LANGUAGE
import com.sakayori.common.STATUS_DONE
import com.sakayori.common.SUPPORTED_LANGUAGE
import com.sakayori.common.SUPPORTED_LOCATION
import com.sakayori.domain.data.model.intent.GenericIntent
import com.sakayori.domain.mediaservice.handler.MediaPlayerHandler
import com.sakayori.domain.mediaservice.handler.ToastType
import com.sakayori.logger.Logger
import com.sakayori.media3.di.setServiceActivitySession
import com.sakayori.music.di.viewModelModule
import com.sakayori.music.service.test.notification.NotifyWork
import com.sakayori.music.utils.ComposeResUtils
import com.sakayori.music.utils.VersionManager
import com.sakayori.music.viewModel.SharedViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import org.koin.core.context.loadKoinModules
import org.koin.core.context.unloadKoinModules
import org.koin.dsl.module
import com.sakayori.music.crashlytics.pushPlayerError
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import pub.devrel.easypermissions.EasyPermissions
import java.util.Locale
import java.util.concurrent.TimeUnit

@Suppress("DEPRECATION")
class MainActivity : AppCompatActivity() {
    val viewModel: SharedViewModel by inject()
    val mediaPlayerHandler by inject<MediaPlayerHandler>()

    private var mBound = false
    private var shouldUnbind = false
    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                setServiceActivitySession(this@MainActivity, MainActivity::class.java, service)
                Logger.w("MainActivity", "onServiceConnected: ")
                mBound = true
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                Logger.w("MainActivity", "onServiceDisconnected: ")
                mBound = false
            }
        }

    override fun onStart() {
        super.onStart()
        startMusicService()
    }

    override fun onStop() {
        super.onStop()
        if (shouldUnbind) {
            unbindService(serviceConnection)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        Logger.d("MainActivity", "onNewIntent: $intent")
        viewModel.setIntent(
            GenericIntent(
                action = intent.action,
                data = (intent.data ?: intent.getStringExtra(Intent.EXTRA_TEXT)?.toUri())?.toKmpUriOrNull(),
                type = intent.type,
            ),
        )
    }

    @ExperimentalFoundationApi
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        loadKoinModules(
            module {
                single<AppCompatActivity> { this@MainActivity }
            },
        )
        VersionManager.initialize()
        checkForUpdate()
        if (viewModel.recreateActivity.value) {
            viewModel.activityRecreateDone()
        }
        Logger.d("MainActivity", "onCreate: ")
        val data = (intent?.data ?: intent?.getStringExtra(Intent.EXTRA_TEXT)?.toUri())?.toKmpUriOrNull()
        if (data != null) {
            viewModel.setIntent(
                GenericIntent(
                    action = intent.action,
                    data = data,
                    type = intent.type,
                ),
            )
        }
        Logger.d("Italy", "Key: ${Locale.ITALY.toLanguageTag()}")

        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            if (getString(FIRST_TIME_MIGRATION) != STATUS_DONE) {
                Logger.d("Locale Key", "onCreate: ${Locale.getDefault().toLanguageTag()}")
                if (SUPPORTED_LANGUAGE.codes.contains(Locale.getDefault().toLanguageTag())) {
                    Logger.d(
                        "Contains",
                        "onCreate: ${
                            SUPPORTED_LANGUAGE.codes.contains(
                                Locale.getDefault().toLanguageTag(),
                            )
                        }",
                    )
                    putString(SELECTED_LANGUAGE, Locale.getDefault().toLanguageTag())
                    if (SUPPORTED_LOCATION.items.contains(Locale.getDefault().country)) {
                        putString("location", Locale.getDefault().country)
                    } else {
                        putString("location", "US")
                    }
                } else {
                    putString(SELECTED_LANGUAGE, "en-US")
                }
                getString(SELECTED_LANGUAGE)?.let {
                    Logger.d("Locale Key", "getString: $it")
                    val localeList = LocaleListCompat.forLanguageTags(it)
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        AppCompatDelegate.setApplicationLocales(localeList)
                    }
                    putString(FIRST_TIME_MIGRATION, STATUS_DONE)
                }
            }
            if (AppCompatDelegate.getApplicationLocales().toLanguageTags() !=
                getString(
                    SELECTED_LANGUAGE,
                )
            ) {
                Logger.d(
                    "Locale Key",
                    "onCreate: ${AppCompatDelegate.getApplicationLocales().toLanguageTags()}",
                )
                putString(SELECTED_LANGUAGE, AppCompatDelegate.getApplicationLocales().toLanguageTags())
            }
        }

        enableEdgeToEdge(
            navigationBarStyle =
                SystemBarStyle.dark(
                    scrim = Color.Transparent.toArgb(),
                ),
            statusBarStyle =
                SystemBarStyle.dark(
                    scrim = Color.Transparent.toArgb(),
                ),
        )
        setContent {
            App(viewModel)
        }

        viewModel.checkIsRestoring()
        val request =
            PeriodicWorkRequestBuilder<NotifyWork>(
                12L,
                TimeUnit.HOURS,
            ).addTag("Worker Test")
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                ).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "Artist Worker",
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )

        if (!EasyPermissions.hasPermissions(this, Manifest.permission.POST_NOTIFICATIONS)) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                lifecycleScope.launch {
                    val msg = ComposeResUtils.getResString(ComposeResUtils.StringType.NOTIFICATION_REQUEST)
                    EasyPermissions.requestPermissions(
                        this@MainActivity,
                        msg,
                        1,
                        Manifest.permission.POST_NOTIFICATIONS,
                    )
                }
            }
        }
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            viewModel.getLocation()
        }
    }

    override fun onDestroy() {
        val shouldStopMusicService = viewModel.shouldStopMusicService()

        if (shouldStopMusicService && shouldUnbind && isFinishing) {
            viewModel.isServiceRunning = false
        }
        if (isFinishing) {
            unloadKoinModules(viewModelModule)
        }
        super.onDestroy()
    }

    override fun onRestart() {
        super.onRestart()
        viewModel.activityRecreate()
    }

    private fun startMusicService() {
        com.sakayori.media3.di
            .startService(this@MainActivity, serviceConnection)
        mediaPlayerHandler.pushPlayerError = { it ->
            pushPlayerError(it)
        }
        mediaPlayerHandler.showToast = { type ->
            lifecycleScope.launch {
                val message = when (type) {
                    is ToastType.ExplicitContent -> {
                        ComposeResUtils.getResString(ComposeResUtils.StringType.EXPLICIT_CONTENT_BLOCKED)
                    }

                    is ToastType.PlayerError -> {
                        ComposeResUtils.getResString(ComposeResUtils.StringType.TIME_OUT_ERROR, type.error)
                    }
                }
                viewModel.makeToast(message)
            }
        }
        viewModel.isServiceRunning = true
        shouldUnbind = true
        Logger.d("Service", "Service started")
    }

    private fun checkForUpdate() {
        if (viewModel.shouldCheckForUpdate()) {
            viewModel.checkForUpdate()
        }
    }

    private fun putString(
        key: String,
        value: String,
    ) {
        viewModel.putString(key, value)
    }

    private fun getString(key: String): String? = viewModel.getString(key)

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        viewModel.activityRecreate()
    }
}

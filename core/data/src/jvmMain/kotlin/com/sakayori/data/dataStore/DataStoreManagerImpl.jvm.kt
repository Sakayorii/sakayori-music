package com.sakayori.data.dataStore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.sakayori.common.SETTINGS_FILENAME
import com.sakayori.data.io.getHomeFolderPath
import createDataStore
import java.io.File

actual fun createDataStoreInstance(): DataStore<Preferences> = createDataStore(
    producePath = {
        val file = File(getHomeFolderPath(listOf(".SakayoriMusic")), "$SETTINGS_FILENAME.preferences_pb")
        file.absolutePath
    }
)

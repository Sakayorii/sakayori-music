package com.sakayori.data.db

import androidx.room.Room
import androidx.room.RoomDatabase
import com.sakayori.common.DB_NAME
import com.sakayori.data.io.getHomeFolderPath
import java.io.File

actual fun getDatabaseBuilder(
    converters: Converters
): RoomDatabase.Builder<MusicDatabase> {
    return Room.databaseBuilder<MusicDatabase>(
        name = getDatabasePath()
    ).addTypeConverter(converters)
}

actual fun getDatabasePath(): String {
    val dbFile = File(getHomeFolderPath(listOf(".SakayoriMusic", "db")), DB_NAME)
    return dbFile.absolutePath
}

package com.sakayori.music.expect

import java.io.File

expect fun updateDownloadDir(): File

expect fun isValidPendingUpdate(path: String): Boolean

expect fun deletePendingUpdate(path: String)

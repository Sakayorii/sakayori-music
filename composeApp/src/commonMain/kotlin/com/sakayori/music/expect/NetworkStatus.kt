package com.sakayori.music.expect

import kotlinx.coroutines.flow.Flow

expect fun networkStatusFlow(): Flow<Boolean>

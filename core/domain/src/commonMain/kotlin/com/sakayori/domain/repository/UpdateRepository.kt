package com.sakayori.domain.repository

import com.sakayori.domain.data.model.update.UpdateData
import com.sakayori.domain.utils.Resource
import kotlinx.coroutines.flow.Flow

interface UpdateRepository {
    fun checkForGithubReleaseUpdate(): Flow<Resource<UpdateData>>
}

package com.sakayori.data.repository

import com.sakayori.domain.data.model.update.UpdateData
import com.sakayori.domain.repository.UpdateRepository
import com.sakayori.domain.utils.Resource
import com.sakayori.kotlinytmusicscraper.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

internal class UpdateRepositoryImpl(
    private val youTube: YouTube,
) : UpdateRepository {
    override fun checkForGithubReleaseUpdate(): Flow<Resource<UpdateData>> =
        flow {
            youTube
                .checkForGithubReleaseUpdate()
                .onSuccess { response ->
                    val assets = response.assets?.filterNotNull().orEmpty().map { asset ->
                        UpdateData.AssetInfo(
                            name = asset.name ?: "",
                            downloadUrl = asset.browserDownloadUrl ?: "",
                            sizeBytes = asset.size?.toLong() ?: 0L,
                        )
                    }
                    emit(
                        Resource.Success(
                            UpdateData(
                                tagName = response.tagName ?: "",
                                releaseTime = response.publishedAt ?: "",
                                body = response.body ?: "",
                                assets = assets,
                            ),
                        ),
                    )
                }.onFailure {
                    emit(Resource.Error<UpdateData>(it.message ?: "Unknown error"))
                }
        }.flowOn(Dispatchers.IO)
}

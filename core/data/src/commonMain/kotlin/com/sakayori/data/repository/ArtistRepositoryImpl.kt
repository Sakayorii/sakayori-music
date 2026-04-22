package com.sakayori.data.repository

import com.sakayori.data.db.datasource.LocalDataSource
import com.sakayori.data.extension.getFullDataFromDB
import com.sakayori.data.parser.parseArtistData
import com.sakayori.domain.data.entities.ArtistEntity
import com.sakayori.domain.data.model.browse.artist.ArtistBrowse
import com.sakayori.domain.repository.ArtistRepository
import com.sakayori.domain.utils.Resource
import com.sakayori.kotlinytmusicscraper.YouTube
import com.sakayori.logger.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.datetime.LocalDateTime

internal class ArtistRepositoryImpl(
    private val localDataSource: LocalDataSource,
    private val youTube: YouTube,
) : ArtistRepository {
    override fun getAllArtists(limit: Int): Flow<List<ArtistEntity>> =
        flow {
            emit(localDataSource.getAllArtists(limit))
        }.flowOn(Dispatchers.IO)

    override fun getArtistById(id: String): Flow<ArtistEntity?> =
        flow {
            emit(localDataSource.getArtist(id))
        }.flowOn(Dispatchers.IO)

    override suspend fun insertArtist(artistEntity: ArtistEntity) =
        withContext(Dispatchers.IO) {
            localDataSource.insertArtist(artistEntity)
        }

    override suspend fun updateArtistImage(
        channelId: String,
        thumbnail: String,
    ) = withContext(
        Dispatchers.IO,
    ) {
        localDataSource.updateArtistImage(
            channelId,
            thumbnail,
        )
    }

    override suspend fun updateFollowedStatus(
        channelId: String,
        followedStatus: Int,
    ) = withContext(
        Dispatchers.IO,
    ) { localDataSource.updateFollowed(followedStatus, channelId) }

    override fun getFollowedArtists(): Flow<List<ArtistEntity>> =
        flow {
            emit(
                getFullDataFromDB { limit, offset ->
                    localDataSource.getFollowedArtists(limit, offset)
                },
            )
        }.flowOn(Dispatchers.IO)

    override suspend fun updateArtistInLibrary(
        inLibrary: LocalDateTime,
        channelId: String,
    ) = withContext(Dispatchers.IO) {
        localDataSource.updateArtistInLibrary(
            inLibrary,
            channelId,
        )
    }

    override fun getArtistData(channelId: String): Flow<Resource<ArtistBrowse>> =
        flow {
            runCatching {
                youTube
                    .artist(channelId)
                    .onSuccess { result ->
                        emit(Resource.Success<ArtistBrowse>(parseArtistData(result)))
                    }.onFailure { e ->
                        Logger.d("Artist", "Error: ${e.message}")
                        emit(Resource.Error<ArtistBrowse>(e.message.toString()))
                    }
            }
        }.flowOn(Dispatchers.IO)
}

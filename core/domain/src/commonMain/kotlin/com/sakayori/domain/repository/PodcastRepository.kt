package com.sakayori.domain.repository

import com.sakayori.domain.data.entities.EpisodeEntity
import com.sakayori.domain.data.entities.PodcastWithEpisodes
import com.sakayori.domain.data.entities.PodcastsEntity
import com.sakayori.domain.data.model.podcast.PodcastBrowse
import com.sakayori.domain.utils.Resource
import kotlinx.coroutines.flow.Flow

interface PodcastRepository {
    fun getPodcastData(podcastId: String): Flow<Resource<PodcastBrowse>>

    fun insertPodcast(podcastsEntity: PodcastsEntity): Flow<Long>

    fun insertEpisodes(episodes: List<EpisodeEntity>): Flow<List<Long>>

    fun getPodcastWithEpisodes(podcastId: String): Flow<PodcastWithEpisodes?>

    fun getAllPodcasts(limit: Int): Flow<List<PodcastsEntity>>

    fun getAllPodcastWithEpisodes(): Flow<List<PodcastWithEpisodes>>

    fun getPodcast(podcastId: String): Flow<PodcastsEntity?>

    fun getEpisode(videoId: String): Flow<EpisodeEntity?>

    fun deletePodcast(podcastId: String): Flow<Int>

    fun favoritePodcast(
        podcastId: String,
        favorite: Boolean,
    ): Flow<Boolean>

    fun getPodcastEpisodes(podcastId: String): Flow<List<EpisodeEntity>>

    fun getFavoritePodcasts(): Flow<List<PodcastsEntity>>

    fun updatePodcastInLibraryNow(id: String): Flow<Int>
}

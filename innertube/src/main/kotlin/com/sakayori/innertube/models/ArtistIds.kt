package com.sakayori.innertube.models

fun List<YTItem>.missingArtistNames(): Set<String> = buildSet {
    for (item in this@missingArtistNames) {
        val artists = when (item) {
            is SongItem -> item.artists
            is AlbumItem -> item.artists.orEmpty()
            is PlaylistItem -> listOfNotNull(item.author)
            is EpisodeItem -> listOfNotNull(item.author)
            is PodcastItem -> listOfNotNull(item.author)
            is ArtistItem -> emptyList()
        }
        artists.filter { it.id == null && it.name.isNotBlank() }.forEach { add(it.name) }
    }
}

/** Only fills missing IDs. Track facts, ordering, and already-known artists remain authoritative. */
fun List<YTItem>.withResolvedArtistIds(ids: Map<String, String>): List<YTItem> {
    if (ids.isEmpty()) return this
    fun Artist.resolve(): Artist = if (id == null) ids[name]?.let { copy(id = it) } ?: this else this

    var changed = false
    val resolved = map { item ->
        val replacement = when (item) {
            is SongItem -> {
                val artists = item.artists.map { it.resolve() }
                if (artists == item.artists) item else item.copy(artists = artists)
            }
            is AlbumItem -> {
                val artists = item.artists?.map { it.resolve() }
                if (artists == item.artists) item else item.copy(artists = artists)
            }
            is PlaylistItem -> item.author?.resolve().let { if (it == item.author) item else item.copy(author = it) }
            is EpisodeItem -> item.author?.resolve().let { if (it == item.author) item else item.copy(author = it) }
            is PodcastItem -> item.author?.resolve().let { if (it == item.author) item else item.copy(author = it) }
            is ArtistItem -> item
        }
        if (replacement !== item) changed = true
        replacement
    }
    return if (changed) resolved else this
}

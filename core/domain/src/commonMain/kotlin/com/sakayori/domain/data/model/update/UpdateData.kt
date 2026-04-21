package com.sakayori.domain.data.model.update

data class UpdateData(
    val tagName: String,
    val releaseTime: String?,
    val body: String,
    val assets: List<AssetInfo> = emptyList(),
) {
    data class AssetInfo(
        val name: String,
        val downloadUrl: String,
        val sizeBytes: Long,
    )

    fun pickAssetFor(preferredNames: List<String>): AssetInfo? =
        preferredNames.firstNotNullOfOrNull { wanted ->
            assets.firstOrNull { it.name == wanted }
        }
}

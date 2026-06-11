package com.melodyflow.app.data

import com.google.gson.annotations.SerializedName
import com.melodyflow.app.model.SearchResult
import retrofit2.http.GET
import retrofit2.http.Query

data class SongUrlResponse(
    @SerializedName("url") val url: String = ""
)

data class LyricResponse(
    @SerializedName("lrc") val lrc: String = "",
    @SerializedName("lyric") val lyric: String = ""
)

data class NeteaseLyricWrapper(
    @SerializedName("lrc") val lrc: NeteaseLyricInner? = null
)

data class NeteaseLyricInner(
    @SerializedName("lyric") val lyric: String = ""
)

interface MusicApiService {

    @GET("meting/")
    suspend fun search(
        @Query("server") server: String = "netease",
        @Query("type") type: String = "search",
        @Query("id") keyword: String,
        @Query("limit") limit: Int = 30
    ): List<SearchResult>

    @GET("meting/")
    suspend fun searchArtist(
        @Query("server") server: String = "netease",
        @Query("type") type: String = "artist",
        @Query("id") keyword: String,
        @Query("limit") limit: Int = 30
    ): List<SearchResult>

    @GET("meting/")
    suspend fun searchAlbum(
        @Query("server") server: String = "netease",
        @Query("type") type: String = "album",
        @Query("id") keyword: String,
        @Query("limit") limit: Int = 30
    ): List<SearchResult>

    @GET("meting/")
    suspend fun getPlaylist(
        @Query("server") server: String = "netease",
        @Query("type") type: String = "playlist",
        @Query("id") id: String,
        @Query("limit") limit: Int = 100
    ): List<SearchResult>

    @GET("meting/")
    suspend fun getSongInfo(
        @Query("server") server: String = "netease",
        @Query("type") type: String = "song",
        @Query("id") id: String
    ): List<SearchResult>

    @GET("meting/")
    suspend fun getUrl(
        @Query("server") server: String = "netease",
        @Query("type") type: String = "url",
        @Query("id") id: String
    ): String

    @GET("meting/")
    suspend fun getLrc(
        @Query("server") server: String = "netease",
        @Query("type") type: String = "lrc",
        @Query("id") id: String
    ): String
}
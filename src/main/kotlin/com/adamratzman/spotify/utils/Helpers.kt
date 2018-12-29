/* Created by Adam Ratzman (2018) */
@file:Suppress("UNCHECKED_CAST")

package com.adamratzman.spotify.utils

import com.adamratzman.spotify.main.SpotifyAPI
import com.adamratzman.spotify.main.SpotifyRestAction
import com.google.gson.Gson
import org.json.JSONArray
import org.json.JSONObject
import java.io.InvalidObjectException
import java.net.URLEncoder
import java.util.Base64
import java.util.function.Supplier

data class Cursor(val after: String)

class CursorBasedPagingObject<T>(
    href: String,
    items: List<T>,
    limit: Int,
    next: String?,
    val cursors: Cursor,
    total: Int,
    endpoint: SpotifyEndpoint
)
    : PagingObject<T>(href, items, limit, next, 0, null, total, endpoint)

open class PagingObject<T>(
    val href: String,
    val items: List<T>,
    val limit: Int,
    val next: String? = null,
    val offset: Int = 0,
    val previous: String? = null,
    val total: Int,
    var endpoint: SpotifyEndpoint
) {
    lateinit var tClazz: Class<T>
    fun getNext(): SpotifyRestAction<PagingObject<T>?> = endpoint.toAction(
            Supplier {
                catch {
                    if (this is CursorBasedPagingObject) next?.let { endpoint.get(it).toCursorBasedPagingObject(tClazz = tClazz, endpoint = endpoint) }
                    else next?.let { endpoint.get(it).toPagingObject<T>(tClazz = tClazz, endpoint = endpoint) }
                }
            })

    fun getPrevious(): SpotifyRestAction<PagingObject<T>?> = endpoint.toAction(
            Supplier {
                catch {
                    previous?.let { endpoint.get(it).toPagingObject(tClazz = tClazz, endpoint = endpoint) }
                }
            })

    fun getAll(): SpotifyRestAction<List<PagingObject<T>>> {
        return endpoint.toAction(
                Supplier {
                    if (this is CursorBasedPagingObject) {
                        val pagingObjects = mutableListOf(this)
                        var next = getNext().complete()
                        while (next != null) {
                            pagingObjects.add(next as CursorBasedPagingObject<T>)
                            next = getNext().complete()
                        }
                        pagingObjects.toList()
                    } else {
                        val pagingObjects = mutableListOf<PagingObject<T>>()
                        var prev = previous?.let { getPrevious().complete() }
                        while (prev != null) {
                            pagingObjects.add(prev)
                            prev = prev.previous?.let { prev?.getPrevious()?.complete() }
                        }
                        pagingObjects.reverse() // closer we are to current, the further we are from the start

                        pagingObjects.add(this)

                        var nxt = next?.let { getNext().complete() }
                        while (nxt != null) {
                            pagingObjects.add(nxt)
                            nxt = nxt.next?.let { nxt?.getNext()?.complete() }
                        }
                        // we don't need to reverse here, as it's in order
                        pagingObjects.toList()
                    }
                })
    }

    fun getAllItems(): SpotifyRestAction<List<T>> {
        return endpoint.toAction(Supplier {
            getAll().complete().asSequence().map { it.items }.toList().flatten()
        })
    }
}

data class LinkedResult<out T>(val href: String, val items: List<T>) {
    fun toPlaylist(): PlaylistURI {
        if (href.startsWith("https://api.spotify.com/v1/users/")) {
            val split = href.removePrefix("https://api.spotify.com/v1/users/").split("/playlists/")
            if (split.size == 2) return PlaylistURI(split[1].split("/")[0])
        }
        throw InvalidObjectException("This object is not linked to a playlist")
    }

    fun getArtist(): ArtistURI {
        if (href.startsWith("https://api.spotify.com/v1/artists/")) {
            return ArtistURI(href.removePrefix("https://api.spotify.com/v1/artists/").split("/")[0])
        }
        throw InvalidObjectException("This object is not linked to an artist")
    }

    fun getAlbum(): AlbumURI {
        if (href.startsWith("https://api.spotify.com/v1/albums/")) {
            return AlbumURI(href.removePrefix("https://api.spotify.com/v1/albums/").split("/")[0])
        }
        throw InvalidObjectException("This object is not linked to an album")
    }
}

abstract class RelinkingAvailableResponse(val linkedTrack: LinkedTrack?) : Linkable() {
    fun isRelinked() = linkedTrack != null
}

internal fun String.byteEncode(): String {
    return String(Base64.getEncoder().encode(toByteArray()))
}

internal fun String.encode() = URLEncoder.encode(this, "UTF-8")!!

internal fun <T> Any.toObject(o: Any, tClazz: Class<T>): T {
    val obj = ((o as? SpotifyAPI)?.gson ?: (o as? Gson)
    ?: throw IllegalArgumentException("Parameter must be a SpotifyAPI or Gson instance"))
            .fromJson(this as String, tClazz)
    if (o is SpotifyAPI) {
        if (obj is Linkable) obj.api = o
        obj?.instantiatePagingObjects(o)
    }
    return obj
}

internal fun Any.instantiatePagingObjects(spotifyAPI: SpotifyAPI) = when {
    this is FeaturedPlaylists -> this.playlists
    this is Album -> this.tracks
    this is Playlist -> this.tracks
    else -> null
}.let { it?.endpoint = spotifyAPI.tracks; this }

internal fun <T> String.toPagingObject(innerObjectName: String? = null, endpoint: SpotifyEndpoint, tClazz: Class<T>): PagingObject<T> {
    val jsonObject = if (innerObjectName != null) JSONObject(this).getJSONObject(innerObjectName) else JSONObject(this)
    val jsonArray: JSONArray = jsonObject.getJSONArray("items")
    val list = ArrayList<T>()
    for(i in 0 until jsonArray.length()) {
        list.add(jsonArray.get(i).toString().toObject(endpoint.api, tClazz))
    }
    val pagingObject = PagingObject(
            jsonObject.getString("href"),
            list,
            jsonObject.getInt("limit"),
            jsonObject.get("next") as? String,
            jsonObject.get("offset") as Int,
            jsonObject.get("previous") as? String,
            jsonObject.getInt("total"),
            endpoint)
    pagingObject.tClazz = tClazz
    return pagingObject
}

internal fun <T> String.toCursorBasedPagingObject(innerObjectName: String? = null, endpoint: SpotifyEndpoint, tClazz: Class<T>): CursorBasedPagingObject<T> {
    val jsonObject = if (innerObjectName != null) JSONObject(this).getJSONObject(innerObjectName) else JSONObject(this)
    val cursorBasedPagingObject = CursorBasedPagingObject(
            jsonObject.getString("href"),
            jsonObject.getJSONArray("items").map { it.toString().toObject(endpoint.api, tClazz) },
            jsonObject.getInt("limit"),
            jsonObject.get("next") as? String,
            endpoint.api.gson.fromJson(jsonObject.getJSONObject("cursors").toString(), Cursor::class.java),
            if (jsonObject.keySet().contains("total")) jsonObject.getInt("total") else -1,
            endpoint)
    cursorBasedPagingObject.tClazz = tClazz
    return cursorBasedPagingObject
}

internal fun <T> String.toLinkedResult(api: SpotifyAPI, tClazz: Class<T>): LinkedResult<T> {
    val jsonObject = JSONObject(this)
    return LinkedResult(
            jsonObject.getString("href"),
            jsonObject.getJSONArray("items").map { it.toString().toObject(api, tClazz) })
}

internal fun <T> String.toInnerObject(innerName: String, api: SpotifyAPI, tClazz: Class<T>): List<T> {
    return JSONObject(this).getJSONArray(innerName).map { it.toString().toObject(api, tClazz) }
}

internal fun <T> catch(function: () -> T): T? {
    return try {
        function()
    } catch (e: BadRequestException) {
        null
    }
}

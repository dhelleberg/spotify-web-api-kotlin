package com.adamratzman.spotify.endpoints.public

import com.adamratzman.spotify.main.SpotifyAPI
import com.adamratzman.spotify.main.SpotifyRestAction
import com.adamratzman.spotify.main.SpotifyRestPagingAction
import com.adamratzman.spotify.utils.*
import java.util.function.Supplier

/**
 * Endpoints for retrieving information about a user’s playlists
 */
open class PlaylistsAPI(api: SpotifyAPI) : SpotifyEndpoint(api) {
    /**
     * Get a list of the playlists owned or followed by a Spotify user. Lookups for non-existant users return empty [PagingObject]s
     * (blame Spotify)
     *
     * @param userId The user’s Spotify user ID.
     * @param limit The number of album objects to return. Default: 20. Minimum: 1. Maximum: 50.
     * @param offset The index of the first album to return. Default: 0 (i.e., the first album). Use with limit to get the next set of albums.
     *
     * @return [PagingObject] of [SimplePlaylist]s **ONLY if** the user can be found. Otherwise, an empty paging object is returned.
     * This does not have the detail of full [Playlist] objects.
     *
     */
    fun getPlaylists(userId: String, limit: Int? = null, offset: Int? = null): SpotifyRestPagingAction<SimplePlaylist, PagingObject<SimplePlaylist>> {
        return toPagingObjectAction(Supplier {
            get(EndpointBuilder("/users/${userId.encode()}/playlists").with("limit", limit).with("offset", offset)
                    .toString()).toPagingObject(endpoint = this, tClazz = SimplePlaylist::class.java)
        })
    }

    /**
     * Get a playlist owned by a Spotify user.
     *
     * @param userId The user’s Spotify user ID.
     * @param playlistId The Spotify ID for the playlist.
     * @param market Provide this parameter if you want to apply [Track Relinking](https://github.com/adamint/spotify-web-api-kotlin/blob/master/README.md#track-relinking)
     *
     * @throws BadRequestException if the playlist is not found
     */
    fun getPlaylist(playlistId: String, market: Market? = null): SpotifyRestAction<Playlist?> {
        return toAction(Supplier {
            catch {
                get(EndpointBuilder("/playlists/${playlistId.encode()}")
                        .with("market", market?.code).toString()).toObject(api, Playlist::class.java)
            }
        })
    }

    /**
     * Get full details of the tracks of a playlist owned by a Spotify user.
     *
     * @param userId The user’s Spotify user ID.
     * @param playlistId The Spotify ID for the playlist.
     * @param market Provide this parameter if you want to apply [Track Relinking](https://github.com/adamint/spotify-web-api-kotlin/blob/master/README.md#track-relinking)
     * @param limit The number of track objects to return. Default: 20. Minimum: 1. Maximum: 50.
     * @param offset The index of the first track to return. Default: 0 (i.e., the first album). Use with limit to get the next set of albums.
     *
     * @throws BadRequestException if the playlist cannot be found
     */
    fun getPlaylistTracks(playlistId: String, limit: Int? = null, offset: Int? = null, market: Market? = null): SpotifyRestAction<LinkedResult<PlaylistTrack>> {
        return toAction(Supplier {
            get(EndpointBuilder("/playlists/${playlistId.encode()}/tracks").with("limit", limit)
                    .with("offset", offset).with("market", market?.code).toString()).toLinkedResult(api, PlaylistTrack::class.java)
        })

    }

    /**
     * Get the current image associated with a specific playlist.
     * @param userId The user’s Spotify user ID.
     * @param playlistId The Spotify ID for the playlist.
     *
     * @throws BadRequestException if the playlist cannot be found
     */
    fun getPlaylistCovers(playlistId: String): SpotifyRestAction<List<SpotifyImage>> {
        return toAction(Supplier {
            get(EndpointBuilder("/playlists/${playlistId.encode()}/images").toString()).toObject(api, mutableListOf<SpotifyImage>().javaClass).toList()
        })
    }
}
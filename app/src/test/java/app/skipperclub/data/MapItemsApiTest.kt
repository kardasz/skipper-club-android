package app.skipperclub.data

import java.util.Locale
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MapItemsApiTest {
    @Test
    fun listRequestIncludesAuthorizationLanguageAndViewportBoundsOnly() {
        val request = MapItemsApi.listRequest(
            accessToken = "access-token",
            bounds = MapViewportBounds(
                north = 54.49,
                south = 54.39,
                east = 18.63,
                west = 18.50,
            ),
        )

        assertEquals("GET", request.method)
        assertEquals("https://api.skipperclub.app/v1/map/items", "${request.url.scheme}://${request.url.host}${request.url.encodedPath}")
        assertEquals("Bearer access-token", request.header("Authorization"))
        assertEquals(Locale.getDefault().toLanguageTag(), request.header("Accept-Language"))
        assertEquals("54.49", request.url.queryParameter("north"))
        assertEquals("54.39", request.url.queryParameter("south"))
        assertEquals("18.63", request.url.queryParameter("east"))
        assertEquals("18.5", request.url.queryParameter("west"))
        assertNull(request.url.queryParameter("types"))
        assertNull(request.url.queryParameter("detail"))
        assertNull(request.url.queryParameter("limit"))
    }

    @Test
    fun decodeResponseMapsItemsAndClusters() {
        val decoded = MapItemsApi.decodeResponse(
            """
                {
                  "data": [
                    {
                      "kind": "item",
                      "type": "spot",
                      "id": "spot-1",
                      "name": "Sopot Marina",
                      "coordinates": { "lat": 54.441, "lng": 18.567 },
                      "geometry": { "type": "Point", "coordinates": [18.567, 54.441] },
                      "attributes": {
                        "hasPhoneContacts": true,
                        "hasRadioChannels": true,
                        "phoneContactsCount": 2,
                        "radioChannelsCount": 1
                      }
                    },
                    {
                      "kind": "cluster",
                      "id": "cluster:u3wge:5",
                      "name": "24 items",
                      "coordinates": { "lat": 54.44, "lng": 18.56 },
                      "geometry": { "type": "Point", "coordinates": [18.56, 54.44] },
                      "count": 24,
                      "types": { "post": 12, "spot": 8, "check_in": 4 },
                      "bounds": { "north": 54.49, "south": 54.39, "east": 18.63, "west": 18.5 }
                    }
                  ],
                  "meta": {
                    "mode": "bounds",
                    "detail": "auto",
                    "totalItems": 248,
                    "returnedItems": 248,
                    "topLevelEntries": 42,
                    "hasMoreDetail": true,
                    "checkInFreshnessHours": 24,
                    "appliedLimit": 200
                  }
                }
            """.trimIndent(),
        )

        assertEquals(2, decoded.entries.size)
        assertEquals(MapEntryKind.Item, decoded.entries[0].kind)
        assertEquals(MapEntryType.Spot, decoded.entries[0].type)
        assertEquals("Sopot Marina", decoded.entries[0].name)
        assertEquals(54.441, decoded.entries[0].coordinates.lat, 0.0)
        assertEquals(MapEntryKind.Cluster, decoded.entries[1].kind)
        assertEquals("24 items", decoded.entries[1].name)
        assertEquals(24, decoded.entries[1].count)
        assertTrue(decoded.meta.hasMoreDetail)
    }

    @Test
    fun decodeResponseMapsPostAttributes() {
        val decoded = MapItemsApi.decodeResponse(
            """
                {
                  "data": [
                    {
                      "kind": "item",
                      "type": "post",
                      "id": "019dfd19-ddd8-7d23-a1f4-06b96c16a36d",
                      "name": "Sopot Pier",
                      "coordinates": { "lat": 54.441, "lng": 18.567 },
                      "geometry": { "type": "Point", "coordinates": [18.567, 54.441] },
                      "attributes": {
                        "contentKeys": ["media"],
                        "alertCategory": null,
                        "status": "published",
                        "author": { "id": "u1", "displayName": "Jan K.", "avatarUrl": "https://a/1.jpg" },
                        "publishedAt": "2030-01-01T12:00:00Z",
                        "expiresAt": null,
                        "mediaPreview": { "id": "m1", "kind": "image", "url": "https://m/1.jpg", "thumbnailUrl": null },
                        "commentsCount": 4,
                        "bookmarked": true
                      }
                    }
                  ],
                  "meta": { "hasMoreDetail": false }
                }
            """.trimIndent(),
        )

        val entry = decoded.entries.single()
        val attributes = entry.attributes as MapEntryAttributes.Post
        assertEquals(MapEntryType.Post, entry.type)
        assertTrue(attributes.hasMedia)
        assertEquals(false, attributes.hasAlert)
        assertNull(attributes.alertCategory)
        assertEquals("Jan K.", attributes.author.displayName)
        assertEquals("2030-01-01T12:00:00Z", attributes.publishedAt)
        assertNull(attributes.expiresAt)
        assertEquals(4, attributes.commentsCount)
        assertEquals(true, attributes.bookmarked)
        assertEquals("https://m/1.jpg", attributes.mediaPreview?.url)
        assertEquals(false, attributes.mediaPreview?.isVideo)
    }

    @Test
    fun decodeResponseMapsAlertPostAttributes() {
        val decoded = MapItemsApi.decodeResponse(
            """
                {
                  "data": [
                    {
                      "kind": "item",
                      "type": "post",
                      "id": "019eac4a-3e2d-7c11-8761-f9d85d6e6419",
                      "name": "Weather alert",
                      "coordinates": { "lat": 54.49, "lng": 18.55 },
                      "geometry": { "type": "Point", "coordinates": [18.55, 54.49] },
                      "attributes": {
                        "contentKeys": ["alert"],
                        "alertCategory": "weather",
                        "status": "published",
                        "author": { "id": "u1", "displayName": "Jan Kowalski", "avatarUrl": null },
                        "publishedAt": "2030-01-01T12:00:00Z",
                        "expiresAt": "2030-01-02T12:00:00Z",
                        "commentsCount": 0,
                        "bookmarked": false
                      }
                    }
                  ],
                  "meta": { "hasMoreDetail": false }
                }
            """.trimIndent(),
        )

        val entry = decoded.entries.single()
        val attributes = entry.attributes as MapEntryAttributes.Post
        assertEquals(MapEntryType.Post, entry.type)
        assertTrue(attributes.hasAlert)
        assertEquals(false, attributes.hasMedia)
        assertEquals(AlertCategory.Weather, attributes.alertCategory)
        assertEquals("Jan Kowalski", attributes.author.displayName)
        assertEquals("2030-01-02T12:00:00Z", attributes.expiresAt)
    }

    @Test
    fun decodeResponseParsesAlertPostPolygonGeometryAndClusterBounds() {
        val decoded = MapItemsApi.decodeResponse(
            """
                {
                  "data": [
                    {
                      "kind": "item",
                      "type": "post",
                      "id": "alert-1",
                      "name": "Obstruction",
                      "coordinates": { "lat": 54.45, "lng": 18.6 },
                      "geometry": {
                        "type": "Polygon",
                        "coordinates": [[[18.59,54.44],[18.61,54.44],[18.61,54.46],[18.59,54.46],[18.59,54.44]]]
                      },
                      "attributes": {
                        "contentKeys": ["alert"],
                        "alertCategory": "obstruction",
                        "status": "published",
                        "author": { "id": "u1", "displayName": "Jan K.", "avatarUrl": null },
                        "publishedAt": "2030-01-01T12:00:00Z"
                      }
                    },
                    {
                      "kind": "cluster",
                      "id": "cluster:u3wge:5",
                      "name": "24 items",
                      "coordinates": { "lat": 54.44, "lng": 18.56 },
                      "geometry": { "type": "Point", "coordinates": [18.56, 54.44] },
                      "count": 24,
                      "bounds": { "north": 54.49, "south": 54.39, "east": 18.63, "west": 18.5 }
                    }
                  ],
                  "meta": { "hasMoreDetail": true }
                }
            """.trimIndent(),
        )

        val alert = decoded.entries[0]
        val attributes = alert.attributes as MapEntryAttributes.Post
        assertTrue(attributes.hasAlert)
        assertEquals(AlertCategory.Obstruction, attributes.alertCategory)
        val polygon = alert.geometry as MapGeometry.Polygon
        assertEquals(1, polygon.rings.size)
        assertEquals(5, polygon.rings.first().size)
        assertEquals(54.44, polygon.rings.first().first().lat, 0.0)
        assertEquals(18.59, polygon.rings.first().first().lng, 0.0)

        val clusterBounds = decoded.entries[1].bounds!!
        assertEquals(54.49, clusterBounds.north, 0.0)
        assertEquals(18.5, clusterBounds.west, 0.0)
    }

    @Test
    fun decodeResponseMapsSpotAttributes() {
        val decoded = MapItemsApi.decodeResponse(
            """
                {
                  "data": [
                    {
                      "kind": "item",
                      "type": "spot",
                      "id": "019dfd19-ddd8-7d23-a1f4-06b96c16a36d",
                      "name": "Sopot Marina",
                      "coordinates": { "lat": 54.441, "lng": 18.567 },
                      "geometry": { "type": "Point", "coordinates": [18.567, 54.441] },
                      "attributes": {
                        "hasPhoneContacts": true,
                        "hasRadioChannels": false,
                        "phoneContactsCount": 2,
                        "radioChannelsCount": 0
                      }
                    }
                  ],
                  "meta": { "hasMoreDetail": false }
                }
            """.trimIndent(),
        )

        val entry = decoded.entries.single()
        val attributes = entry.attributes as MapEntryAttributes.Spot
        assertEquals(MapEntryType.Spot, entry.type)
        assertEquals("Sopot Marina", entry.name)
        assertTrue(attributes.hasPhoneContacts)
        assertEquals(2, attributes.phoneContactsCount)
        assertEquals(false, attributes.hasRadioChannels)
        assertEquals(0, attributes.radioChannelsCount)
    }

    @Test
    fun decodeResponseMapsCheckInAttributes() {
        val decoded = MapItemsApi.decodeResponse(
            """
                {
                  "data": [
                    {
                      "kind": "item",
                      "type": "check_in",
                      "id": "019eac4a-3e2d-7c11-8761-f9d85d6e6419",
                      "name": "Krzysztof",
                      "coordinates": {
                        "lat": 43.939826948,
                        "lng": 15.441865213
                      },
                      "geometry": {
                        "type": "Point",
                        "coordinates": [15.441865213, 43.939826948]
                      },
                      "attributes": {
                        "user": {
                          "id": "01985af0-b793-7d54-a10f-a0d18100b4a0",
                          "displayName": "Krzysztof",
                          "avatarUrl": "https://media.skipperclub.app/avatars/krzysztof.jpeg"
                        },
                        "checkedInAt": "2026-06-09T12:10:07.274Z",
                        "locationName": "Marina Kornati"
                      },
                      "distanceMeters": 26130
                    }
                  ],
                  "meta": {
                    "hasMoreDetail": false
                  }
                }
            """.trimIndent(),
        )

        val entry = decoded.entries.single()
        val attributes = entry.attributes as MapEntryAttributes.CheckIn
        assertEquals(MapEntryType.CheckIn, entry.type)
        assertEquals("Krzysztof", entry.name)
        assertEquals("01985af0-b793-7d54-a10f-a0d18100b4a0", attributes.user.id)
        assertEquals("Krzysztof", attributes.user.displayName)
        assertEquals("https://media.skipperclub.app/avatars/krzysztof.jpeg", attributes.user.avatarUrl)
        assertEquals("2026-06-09T12:10:07.274Z", attributes.checkedInAt)
        assertEquals("Marina Kornati", attributes.locationName)
    }

    @Test
    fun unauthorizedProblemMapsToAuthenticationRequired() {
        val error = response(
            code = 401,
            body = """{"title":"Unauthorized","detail":"Token expired"}""",
        ).toMapItemsErrorForTest()

        assertTrue(error is MapItemsError.AuthenticationRequired)
        assertEquals("Token expired", error.message)
    }

    @Test
    fun validationProblemMapsToValidationError() {
        val error = response(
            code = 422,
            body = """{"title":"Invalid Spatial Mode","detail":"Bounds are required"}""",
        ).toMapItemsErrorForTest()

        assertTrue(error is MapItemsError.Validation)
        assertEquals("Bounds are required", error.message)
    }

    private fun response(code: Int, body: String): Response =
        Response.Builder()
            .request(Request.Builder().url("https://api.skipperclub.app/test").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .body(body.toResponseBody("application/problem+json".toMediaType()))
            .build()

    private fun Response.toMapItemsErrorForTest(): MapItemsError =
        MapItemsApi.run { toMapItemsError() }
}

package app.skipperclub.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RegionsAndGeocoderApiTest {

    @Test
    fun regionsListRequestIsPublicAndSortedByPopularity() {
        val request = RegionsApi.listRequest()

        assertEquals("GET", request.method)
        assertEquals("/v1/regions", request.url.encodedPath)
        assertEquals("popularity", request.url.queryParameter("sort"))
        assertNull(request.header("Authorization"))
    }

    @Test
    fun regionsResponseDecodesLocalizedFields() {
        val payload = """
            {
              "regions": [
                {
                  "code": "HR",
                  "slug": "croatia",
                  "name": "Croatia",
                  "path": "mediterranean-sea/croatia",
                  "localizedName": "Chorwacja",
                  "localizedParents": ["Morze Śródziemne"],
                  "localizedPath": "morze-srodziemne/chorwacja",
                  "parent": "MED",
                  "popularity": 0.95,
                  "order": 1,
                  "level": 1
                }
              ]
            }
        """.trimIndent()

        val regions = RegionsApi.decodeResponse(payload)

        assertEquals(1, regions.size)
        assertEquals("HR", regions[0].code)
        assertEquals("Chorwacja", regions[0].localizedName)
        assertEquals(listOf("Morze Śródziemne"), regions[0].localizedParents)
        assertEquals(1, regions[0].level)
    }

    @Test
    fun geocoderSearchRequestIncludesQueryAndAuth() {
        val request = GeocoderApi.searchRequest("token", "marina split", limit = 5)

        assertEquals("/v1/geocoder/search", request.url.encodedPath)
        assertEquals("marina split", request.url.queryParameter("query"))
        assertEquals("5", request.url.queryParameter("limit"))
        assertEquals("Bearer token", request.header("Authorization"))
    }

    @Test
    fun geocoderResponsePrefersPlaceNameForDisplay() {
        val payload = """
            {
              "data": [
                {
                  "name": "ACI Marina Split",
                  "formattedAddress": "Uvala Baluni 8, Split, Croatia",
                  "coordinates": {"lat": 43.5, "lng": 16.43}
                },
                {
                  "formattedAddress": "Split, Croatia",
                  "coordinates": {"lat": 43.51, "lng": 16.44}
                }
              ]
            }
        """.trimIndent()

        val locations = GeocoderApi.decodeResponse(payload)

        assertEquals(2, locations.size)
        assertEquals("ACI Marina Split", locations[0].displayName)
        assertEquals("Split, Croatia", locations[1].displayName)
        assertEquals(43.5, locations[0].coordinates.lat, 0.0)
    }
}

package app.skipperclub.data

import org.junit.Assert.assertEquals
import org.junit.Test

/** RegionsApi was removed in the v8.0.0 cutover; only the geocoder remains here. */
class RegionsAndGeocoderApiTest {

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

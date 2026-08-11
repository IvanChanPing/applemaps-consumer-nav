package com.example.applemaps.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AppleBrowseClientTest {
    @Test fun categoryParserKeepsOnlyValidPlaceResults() {
        val raw = """
            {"status":"STATUS_SUCCESS","mapsResult":[
              {"resultType":"MAPS_RESULT_TYPE_GUIDE"},
              {"resultType":"MAPS_RESULT_TYPE_PLACE","place":{
                "muid":"42","mapsId":{"shardedId":{"center":{"lat":40.7,"lng":-73.9}}},
                "component":[
                  {"type":"COMPONENT_TYPE_ENTITY","value":[{"entity":{"name":[{"stringValue":"Cafe One"}],
                    "localizedCategory":[{"level":2,"localizedName":[{"stringValue":"Coffee Shop"}]}]}}]},
                  {"type":"COMPONENT_TYPE_RESULT_SNIPPET","value":[{"resultSnippet":{"category":"Cafe","locationString":"Brooklyn"}}]},
                  {"type":"COMPONENT_TYPE_ADDRESS_OBJECT","value":[{"addressObject":{"shortAddress":"1 Main St"}}]}
                ]}}
            ]}
        """.trimIndent()
        val result = AppleBrowseClient.parseCategoryResponse("Coffee", raw)
        assertEquals(1, result?.places?.size)
        assertEquals("Cafe One", result?.places?.first()?.place?.name)
        assertEquals("Cafe", result?.places?.first()?.place?.category)
        assertEquals("Brooklyn", result?.places?.first()?.place?.locality)
    }

    @Test fun categoryParserRejectsMissingCoordinates() {
        val raw = """{"status":"STATUS_SUCCESS","mapsResult":[{"resultType":"MAPS_RESULT_TYPE_PLACE","place":{"component":[]}}]}"""
        assertNull(AppleBrowseClient.parseCategoryResponse("Parks", raw))
    }

    @Test fun guideParserReadsNativeTrayFieldsAndRegularHero() {
        val shell = """
          {"initialState":{"placeCache":{"77":{
            "longTitleLines":["A Real Guide"],"descriptionLines":["Guide description"],
            "publisher":{"name":"Publisher"},
            "photos":[{"photo":{"photoVersions":[{"url":"https://example.com/hero.jpg","urlType":"URL_TYPE_REGULAR"}]}}],
            "items":[{"descriptionLines":["Item note"],"placeData":{
              "muid":"9","mapsId":{"shardedId":{"center":{"lat":1.0,"lng":2.0}}},
              "component":[
                {"type":"COMPONENT_TYPE_ENTITY","value":[{"entity":{"name":[{"stringValue":"Place Nine"}]}}]},
                {"type":"COMPONENT_TYPE_ADDRESS_OBJECT","value":[{"addressObject":{"getDisplayLocality":"Town"}}]}
              ]}}]
          }}}}
        """.trimIndent()
        val html = """<script id="shell-props" type="application/json">$shell</script>"""
        val result = AppleBrowseClient.parseGuideHtml("77", html)
        assertEquals("A Real Guide", result?.title)
        assertEquals("Publisher", result?.publisher)
        assertEquals("https://example.com/hero.jpg", result?.heroUrl)
        assertEquals("Item note", result?.places?.first()?.note)
    }
}

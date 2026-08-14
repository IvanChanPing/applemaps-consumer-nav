package com.example.applemaps.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RestaurantMenuClientTest {
    @Test
    fun parsesSemanticSectionsPricesAndPathMatchedPhotos() {
        val html = """
            <div class="action page-link flex-container" data-url="/menu/sisters/item/fried-chicken-sandwich">
              <img src="https://s3-media0.fl.yelpcdn.com/bphoto/photo-id/ms.jpg">
            </div>
            <script type="application/ld+json">
              {"@context":"https://schema.org","@type":"Menu","name":"Sisters Menu","hasMenuSection":[
                {"@type":"MenuSection","name":"Lunch","hasMenuItem":[
                  {"@type":"MenuItem","name":"Fried Chicken Sandwich","url":"/menu/sisters/item/fried-chicken-sandwich",
                   "description":"Cheddar, pickled slaw, sriracha aioli, and fries or salad.",
                   "offers":{"@type":"Offer","price":"22","priceCurrency":"USD"}}
                ]}
              ]}
            </script>
        """.trimIndent()

        val menu = RestaurantMenuClient.parse("https://www.yelp.com/menu/sisters", html)!!

        assertEquals("Sisters Menu", menu.name)
        assertEquals("Yelp", menu.sourceName)
        assertEquals("Lunch", menu.sections.single().name)
        assertEquals("\$22.00", menu.sections.single().items.single().price)
        assertEquals(
            "https://s3-media0.fl.yelpcdn.com/bphoto/photo-id/300s.jpg",
            menu.sections.single().items.single().imageUrl,
        )
    }

    @Test
    fun acceptsObjectFormsAndMissingOptionalFields() {
        val html = """
            <script type='application/ld+json'>
              {"@graph":[{"@type":"Menu","hasMenuSection":{"name":"Dinner","hasMenuItem":
                {"name":"Market Fish","offers":{"price":"Market Price"}}}}]}
            </script>
        """.trimIndent()

        val item = RestaurantMenuClient.parse("https://restaurant.example/menu", html)!!
            .sections.single().items.single()

        assertEquals("Market Fish", item.name)
        assertEquals("Market Price", item.price)
        assertNull(item.description)
        assertNull(item.imageUrl)
    }

    @Test
    fun rejectsDocumentsWithoutActualMenuItems() {
        assertNull(RestaurantMenuClient.parse("https://restaurant.example/menu", "<html>No menu JSON</html>"))
        assertNull(RestaurantMenuClient.parse(
            "https://restaurant.example/menu",
            "<script type=\"application/ld+json\">{\"@type\":\"Menu\",\"hasMenuSection\":[]}</script>",
        ))
    }
}

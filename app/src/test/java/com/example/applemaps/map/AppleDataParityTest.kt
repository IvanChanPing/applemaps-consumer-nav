package com.example.applemaps.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import streetlevel.GroundMetadataTileOuterClass

/** Pure JVM contracts for the former box's Apple photo, coverage-protobuf, and face-signing behavior. */
class AppleDataParityTest {
    @Test fun photoParserNormalizesEscapedAppleUrlsAndUpsizesThem() {
        val blob = """{\"url\":\"https:\\/\\/is1-ssl.mzstatic.com\\/image\\/thumb\\/abc\\/240x240bb.jpg\\u0026x=1\"}"""
        assertEquals(
            listOf("https://is1-ssl.mzstatic.com/image/thumb/abc/1200x1200bb.jpg&x=1"),
            ApplePlaceClient.parsePhotos(blob),
        )
    }

    @Test fun placeParserMapsAirportPhotoCategoriesDescriptionAndNestedPlaces() {
        val shell = """
          {"initialState":{"placeCache":{"airport":{"component":[
            {"type":"COMPONENT_TYPE_ENTITY","value":[{"entity":{"name":[{"stringValue":"Helsinki Airport"}],"localizedCategory":[{"level":2,"localizedName":[{"stringValue":"Airport"}]}]}}]},
            {"type":"COMPONENT_TYPE_PLACE_INFO","value":[{"placeInfo":{"center":{"lat":60.3172,"lng":24.9633}}}]},
            {"type":"COMPONENT_TYPE_CATEGORIZED_PHOTOS","value":[
              {"categorizedPhotos":{"categoryName":[{"stringValue":"Exterior"}],"photo":[{"photo":{"photoVersion":[{"url":"https://is1-ssl.mzstatic.com/image/thumb/exterior/{w}x{h}bb.{f}","urlType":"URL_TYPE_AMP_TEMPLATE"}]}}]}},
              {"categorizedPhotos":{"categoryName":[{"stringValue":"Interior"}],"photo":[{"photo":{"photoVersion":[{"url":"https://is1-ssl.mzstatic.com/image/thumb/interior/320x320bb.jpg","urlType":"URL_TYPE_REGULAR"}]}}]}},
              {"categorizedPhotos":{"categoryName":[{"stringValue":"All Photos"}],"photo":[{"photo":{"photoVersion":[{"url":"https://is1-ssl.mzstatic.com/image/thumb/all/320x320bb.jpg","urlType":"URL_TYPE_REGULAR"}]}}]}}
            ]},
            {"type":"COMPONENT_TYPE_TEXT_BLOCK","value":[{"textBlock":{"title":"Wikipedia","text":"Finland's primary international airport.","attributionUrl":"https://en.wikipedia.org/wiki/Helsinki_Airport"}}]},
            {"type":"COMPONENT_TYPE_AMENITIES","value":[{"amenities":{"amenityV2":[
              {"amenityPresent":true,"name":[{"stringValue":"Reservations"}],"symbolImageName":"reservations"},
              {"amenityPresent":true,"name":[{"stringValue":"Free Wi-Fi"}],"symbolImageName":"wifi"}
            ]}}]},
            {"type":"COMPONENT_TYPE_TEMPLATE_PLACE","value":[{"templatePlace":{"templateData":[
              {"mapsId":{"shardedId":{"muid":"1"}},"title":[{"stringValue":"Hilton Helsinki Airport"}],"footer":{"ratingData":{"vendorName":"Yelp","rating":[{"score":4.2,"maxScore":5,"numRatingsUsedForScore":20}]}}},
              {"mapsId":{"shardedId":{"muid":"2"}},"title":[{"stringValue":"P3 Parking"}]}
            ]}}]},
            {"type":"COMPONENT_TYPE_BOUNDS","value":[{"bounds":{"mapRegion":{"southLat":60.30,"westLng":24.90,"northLat":60.34,"eastLng":25.00}}}]},
            {"type":"COMPONENT_TYPE_BROWSE_CATEGORIES","value":[{"browseCategories":{"browseCategory":[
              {"displayString":"Gates","popularDisplayToken":"Gates"},{"displayString":"Food","popularDisplayToken":"Food","subCategory":[{"displayString":"Coffee Shops"}]}
            ]}}]},
            {"type":"COMPONENT_TYPE_VENUE_INFO","value":[{"venueInfo":{"featureValue":{"featureVenue":{
              "venueContainer":{"label":{"nameShort":"HEL"}},
              "building":[{"label":{"name":"Terminal 2"},"levelId":["level-arrivals","level-departures"]}],
              "level":[{"levelId":"level-arrivals","label":{"name":"Arrivals"}},{"levelId":"level-departures","label":{"name":"Departures"}}]
            }},"itemList":{"item":["Finnair","SAS"]}}}]},
            {"type":"COMPONENT_TYPE_ROAD_ACCESS_INFO","value":[{"accessInfo":{"roadAccessPoint":[
              {"location":{"lat":60.310,"lng":24.950},"walkingDirection":"ENTRY_EXIT"},
              {"location":{"lat":60.320,"lng":24.970},"drivingDirection":"ENTRY"}
            ]}}]},
            {"type":"COMPONENT_TYPE_FACTOID","value":[{"factoid":{"entryType":"ELEVATION","number":55.0}}]},
            {"type":"COMPONENT_TYPE_ACTION_DATA","value":[
              {"actionData":{"categoryId":"quicklinks.restaurant_reservation","winningAdamId":"111","actionLink":[{"appAdamId":"222","link":[{"quickLinkParams":{"url":"https://wrong.example/reserve"}}]},{"appAdamId":"111","link":[{"quickLinkParams":{"url":"https://reserve.example/airport"}}]}]}},
              {"actionData":{"categoryId":"quicklinks.restaurant_order_food","actionLink":[]}}
            ]},
            {"type":"COMPONENT_TYPE_QUICK_LINK","value":[{"quickLink":{"quickLinkItem":[
              {"title":"Reserve","url":"https://fallback.example/reserve"},
              {"title":"Order","url":"https://order.example/airport"},
              {"title":"Menu","url":"https://restaurant.example/airport-menu"}
            ]}}]}
          ]}}}}
        """.trimIndent()
        val place = ApplePlaceClient.parsePlace(
            "Airport",
            0.0,
            0.0,
            """<script id="shell-props" type="application/json">$shell</script>""",
        )
        assertEquals("Helsinki Airport", place?.name)
        assertEquals("Finland's primary international airport.", place?.description)
        assertEquals(listOf("Exterior", "Interior", "All Photos"), place?.photoLabels)
        assertEquals(
            listOf(
                "https://is1-ssl.mzstatic.com/image/thumb/exterior/1200x1200bb.jpg",
                "https://is1-ssl.mzstatic.com/image/thumb/interior/320x320bb.jpg",
                "https://is1-ssl.mzstatic.com/image/thumb/all/320x320bb.jpg",
            ),
            place?.photoUrls,
        )
        assertEquals(listOf("Hilton Helsinki Airport", "P3 Parking"), place?.alsoHere)
        assertEquals(3, place?.photoAlbums?.size)
        assertEquals("Wikipedia", place?.aboutAttribution?.text)
        assertEquals("https://en.wikipedia.org/wiki/Helsinki_Airport", place?.aboutAttribution?.uri)
        assertEquals(listOf("Reservations", "Free Wi-Fi"), place?.amenities)
        assertEquals("reservations", place?.amenityDetails?.first()?.symbolName)
        assertEquals(listOf("I1", "I2"), place?.relatedPlaces?.map { it.id })
        assertEquals(4.2, place?.relatedPlaces?.first()?.rating ?: 0.0, 0.0)
        assertEquals("HEL", place?.airportDetails?.code)
        assertEquals(listOf("Arrivals", "Departures"), place?.airportDetails?.terminals?.single()?.levels)
        assertEquals(listOf("Finnair", "SAS"), place?.airportDetails?.airlines)
        assertEquals(listOf("Gates", "Food"), place?.airportDetails?.browseCategories?.map { it.label })
        assertEquals(listOf("Coffee Shops"), place?.airportDetails?.browseCategories?.last()?.subcategories)
        assertEquals(2, place?.airportDetails?.accessPoints?.size)
        assertEquals(55.0, place?.airportDetails?.elevationMeters ?: 0.0, 0.0)
        assertEquals(listOf(PlaceActionKind.RESERVE, PlaceActionKind.ORDER), place?.placeActions?.map { it.kind })
        assertEquals(
            listOf("https://reserve.example/airport", "https://order.example/airport"),
            place?.placeActions?.map { it.url },
        )
        assertEquals("https://restaurant.example/airport-menu", place?.menuUrl)
    }

    @Test fun movieTheaterTicketActionIsLabeledAsShowtimes() {
        val shell = """
          {"initialState":{"placeCache":{"cinema":{"component":[
            {"type":"COMPONENT_TYPE_ENTITY","value":[{"entity":{"name":[{"stringValue":"AMC Empire 25"}],"localizedCategory":[{"level":2,"localizedName":[{"stringValue":"Movie Theater"}]}]}}]},
            {"type":"COMPONENT_TYPE_ACTION_DATA","value":[{"actionData":{"categoryId":"quicklinks.buy_tickets","actionLink":[{"link":[{"quickLinkParams":{"url":"https://cinema.example/showtimes"}}]}]}}]}
          ]}}}}
        """.trimIndent()
        val place = ApplePlaceClient.parsePlace(
            "AMC Empire 25",
            0.0,
            0.0,
            """<script id="shell-props" type="application/json">$shell</script>""",
        )
        assertEquals(PlaceActionKind.SHOWTIMES, place?.placeActions?.single()?.kind)
        assertEquals("https://cinema.example/showtimes", place?.placeActions?.single()?.url)
    }

    @Test fun coverageParserPreservesUnsignedIdsCoordinatesAndSixCalibrations() {
        val lens = GroundMetadataTileOuterClass.CameraMetadata.LensProjection.newBuilder()
            .setFovS(2.1).setFovH(1.8).setCy(0.3).build()
        val cameras = (0..5).map { index ->
            GroundMetadataTileOuterClass.CameraMetadata.newBuilder()
                .setCameraNumber(index).setLensProjection(lens)
                .setPosition(GroundMetadataTileOuterClass.CameraMetadata.OrientedPosition.newBuilder().setYaw(index.toDouble()))
                .build()
        }
        val tile = GroundMetadataTileOuterClass.GroundMetadataTile.newBuilder()
            .setTileCoordinate(GroundMetadataTileOuterClass.TileCoordinate.newBuilder().setX(38598).setY(49263).setZ(17))
            .addBuildTable(GroundMetadataTileOuterClass.GroundMetadataTile.GroundDataBuild.newBuilder().setBuildId(2_147_486_047L))
            .addAllCameraMetadata(cameras)
            .addPano(
                GroundMetadataTileOuterClass.GroundMetadataTile.PhotoPosition.newBuilder()
                    .setPanoid(java.lang.Long.parseUnsignedLong("15122779243262790715"))
                    .setBuildTableIdx(0).addAllCameraMetadataIdx((0..5).toList())
                    .setTilePosition(
                        GroundMetadataTileOuterClass.GroundMetadataTile.PhotoPosition.OrientedTilePosition.newBuilder()
                            .setX(0).setY(0),
                    ),
            ).build()
        val parsed = AppleLookAroundClient.parseCoverageTile(tile.toByteArray()).single()
        assertEquals("15122779243262790715", parsed.panoId)
        assertEquals("2147486047", parsed.buildId)
        assertEquals(6, parsed.cameras.size)
        assertTrue(parsed.lat.isFinite() && parsed.lon.isFinite())
    }

    @Test fun faceSignerMatchesTheReferenceAlgorithmByteForByte() {
        val raw = "https://gspe72-ssl.ls.apple.com/mnn_us/1512/2779/2432/6279/0715/2147486047/t/0/7"
        val sid = "1".repeat(40)
        val expected = raw + "?sid=" + sid +
            "&accessKey=1700000000_AAAAAAAAAAAAAAAA_TO6Q60bYc%2BW1TWEI9pO1tP4iSAHfZw39dnUcaLfT7ED%2F3k0mS8D1m%2BkiwSwzGHk2mfyuskwjovfT1%2FS7emHUQT6ODUJZyzfVzERuZkyIPmqkkB1sksUa6ggt4Yiwk%2BlCN81ysMsjblbQo7xF9rbPqCrIpBI6BeHrspt3vEZGnM8%3D"
        assertEquals(expected, AppleLookAroundClient.authenticateUrl(raw, sid, "A".repeat(16), 1_700_000_000L))
    }
}

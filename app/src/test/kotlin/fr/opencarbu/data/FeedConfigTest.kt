package fr.opencarbu.data

import kotlin.test.Test
import kotlin.test.assertEquals

class FeedConfigTest {
    @Test
    fun displaysOnlyTheDomainName() {
        assertEquals("data.economie.gouv.fr", FeedConfig.hostOf(FeedConfig.url))
        assertEquals("data.gouv.fr", FeedConfig.hostOf(FeedConfig.NAMES_URL))
        assertEquals("example.org", FeedConfig.hostOf("https://example.org:8080/a?b=c"))
    }
}

package de.astronarren.allsky

import de.astronarren.allsky.network.AllskyClient.Companion.stripWebsitePath
import org.junit.Assert.assertEquals
import org.junit.Test

class WebsitePathTest {
    @Test
    fun hostNamedAllskyIsKept() {
        assertEquals("http://allsky", stripWebsitePath("http://allsky"))
        assertEquals("http://allsky", stripWebsitePath("http://allsky/"))
        assertEquals("http://allsky.local:8080", stripWebsitePath("http://allsky.local:8080"))
    }

    @Test
    fun websitePathIsStripped() {
        assertEquals("http://pi", stripWebsitePath("http://pi/allsky"))
        assertEquals("http://allsky", stripWebsitePath("http://allsky/allsky/"))
        assertEquals("https://example.com/sky", stripWebsitePath("https://example.com/sky/allsky"))
    }
}

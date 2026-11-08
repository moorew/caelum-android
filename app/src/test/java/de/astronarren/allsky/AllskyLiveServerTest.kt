package de.astronarren.allsky

import de.astronarren.allsky.data.AllskyRepository
import de.astronarren.allsky.data.MediaCategory
import de.astronarren.allsky.network.AllskyClient
import de.astronarren.allsky.network.InMemoryCookieJar
import kotlinx.coroutines.runBlocking
import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.nio.file.Files

/**
 * End-to-end checks against a real Allsky install. Skipped unless
 * ALLSKY_TEST_URL is set, e.g.:
 *
 *   ALLSKY_TEST_URL=http://allsky ALLSKY_TEST_USER=… ALLSKY_TEST_PASS=… \
 *     ./gradlew testDebugUnitTest --tests '*AllskyLiveServerTest*'
 */
class AllskyLiveServerTest {
    private val url = System.getenv("ALLSKY_TEST_URL").orEmpty()
    private val user = System.getenv("ALLSKY_TEST_USER").orEmpty()
    private val pass = System.getenv("ALLSKY_TEST_PASS").orEmpty()

    @Before
    fun requireServer() = assumeTrue("ALLSKY_TEST_URL not set", url.isNotEmpty())

    private fun newRepo(u: String = user, p: String = pass): Pair<AllskyClient, AllskyRepository> {
        val client = AllskyClient(InMemoryCookieJar()) { AllskyClient.Config(url, u, p) }
        val dir = Files.createTempDirectory("allsky-test").toFile()
        return client to AllskyRepository(dir, client)
    }

    private fun status(client: AllskyClient, url: String): Int =
        client.httpClient.newCall(Request.Builder().url(url).head().build()).execute().use { it.code }

    @Test
    fun listsEveryCategoryWithThumbnails() = runBlocking {
        val (client, repo) = newRepo()
        for (c in listOf(MediaCategory.KEOGRAMS, MediaCategory.STARTRAILS, MediaCategory.TIMELAPSES, MediaCategory.METEORS)) {
            val t0 = System.nanoTime()
            val items = repo.fetch(c)
            val ms = (System.nanoTime() - t0) / 1_000_000
            println("${c.key}: ${items.size} items in ${ms}ms; first=${items.firstOrNull()}")
            if (c == MediaCategory.METEORS) continue
            assertTrue("${c.key} empty", items.isNotEmpty())
            val first = items.first()
            assertNotNull("${c.key} has no thumbnail", first.thumbUrl)
            assertEquals(200, status(client, first.url))
            assertEquals(200, status(client, first.thumbUrl!!))
            // Newest first.
            assertTrue(items.zipWithNext().all { (a, b) -> a.url >= b.url })
            // Cached copy is identical.
            assertEquals(items, repo.cached(c))
        }
    }

    @Test
    fun listsLatestNightsFrames() = runBlocking {
        val (client, repo) = newRepo()
        val days = repo.fetchDays()
        println("days: $days")
        assertTrue(days.isNotEmpty())
        val t0 = System.nanoTime()
        val frames = repo.fetch(MediaCategory.IMAGES)
        println("images: ${frames.size} in ${(System.nanoTime() - t0) / 1_000_000}ms; first=${frames.firstOrNull()}")
        assertTrue(frames.isNotEmpty())
        assertTrue(frames.first().url.contains("/images/${days.first()}/"))
        assertEquals(200, status(client, frames.first().thumbUrl!!))
        assertEquals(200, status(client, frames.first().url))
    }

    @Test
    fun reLoginsWhenSessionIsLost() = runBlocking {
        val (client, repo) = newRepo()
        assertTrue(repo.fetch(MediaCategory.KEOGRAMS).isNotEmpty())
        client.clearSession() // simulates expired PHP session + remember cookie
        assertTrue(repo.fetch(MediaCategory.STARTRAILS).isNotEmpty())
    }

    @Test
    fun wrongPasswordGivesClearError() = runBlocking {
        // Dummy username so we never count towards the real user's login throttle.
        val (_, repo) = newRepo(u = "caelum-test-nobody", p = "definitely-wrong-password")
        try {
            repo.fetch(MediaCategory.KEOGRAMS)
            fail("expected login failure")
        } catch (e: AllskyClient.LoginRequiredException) {
            println("wrong password -> ${e.message}")
        }
    }
}

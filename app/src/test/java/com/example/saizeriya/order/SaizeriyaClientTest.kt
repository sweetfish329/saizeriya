package com.example.saizeriya.order

import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SaizeriyaClientTest {

    private lateinit var mockEngine: MockEngine
    private lateinit var client: SaizeriyaClient

    @Before
    fun setup() {
        mockEngine = MockEngine { request ->
            val path = request.url.encodedPath
            when {
                path == "/saizeriya3/qr" -> {
                    respond(
                        content = "",
                        status = HttpStatusCode.Found,
                        headers = headersOf(HttpHeaders.Location, "http://example.com/saizeriya3/entry?shop=1&table=2")
                    )
                }
                path == "/saizeriya3/entry" && request.method == HttpMethod.Get -> {
                    val html = """
                        <form id="frm_ctrl" class="entry-page" action="?actionId123">
                        <input id="shop-id" value="123">
                        <input id="table-no" value="456">
                        <input name="token" value="token123">
                        <input id="session-id" value="session123">
                        </form>
                    """.trimIndent()
                    respond(
                        content = html,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html")
                    )
                }
                request.method == HttpMethod.Post -> {
                    val html = """
                        <form id="frm_ctrl" class="menu-page" action="?actionId456">
                        <input name="token" value="token456">
                        <input id="session-id" value="session123">
                        </form>
                    """.trimIndent()
                    respond(
                        content = html,
                        status = HttpStatusCode.OK,
                        headers = headersOf(HttpHeaders.ContentType, "text/html")
                    )
                }
                else -> respondBadRequest()
            }
        }
        client = SaizeriyaClient(mockEngine)
    }

    @After
    fun teardown() {
        client.close()
    }

    @Test
    fun `session creation successfully fetches redirect and parses details`() = runTest {
        val session = client.createSession("http://example.com/saizeriya3/qr", 2)
        assertEquals("123", session.shopId)
        assertEquals("456", session.tableNo)
        assertEquals("token456", session.token)
        assertEquals("session123", session.sessionId)
        assertEquals("actionId456", session.nextId)
        assertEquals("menu", session.pageKind)
        assertEquals(2, session.peopleCount)
        assertEquals("http://example.com/saizeriya3/entry", session.baseUrl)
    }

    @Test
    fun `addItem adds item to cart`() = runTest {
        client.createSession("http://example.com/saizeriya3/qr", 2)
        val cart = client.addItem("session123", "1120", 2)
        assertEquals(1, cart.size)
        assertEquals("1120", cart[0].code)
        assertEquals(2, cart[0].count)
    }

    @Test
    fun `submitOrder fails with empty cart`() = runTest {
        val session = client.createSession("http://example.com/saizeriya3/qr", 2)
        try {
            client.submitOrder(session.sessionId)
            fail("Expected exception")
        } catch (e: Exception) {
            assertEquals("Cannot submit an empty cart", e.message)
        }
    }

    @Test
    fun `submitOrder succeeds when cart has items`() = runTest {
        val session = client.createSession("http://example.com/saizeriya3/qr", 2)
        client.addItem(session.sessionId, "1120", 2)

        val newSession = client.submitOrder(session.sessionId)
        assertTrue(newSession.cart.isEmpty())
        assertEquals("menu", newSession.pageKind)
    }
}

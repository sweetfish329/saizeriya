package com.example.saizeriya.order

import com.example.saizeriya.data.model.*
import io.ktor.client.*
import io.ktor.client.engine.okhttp.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.client.request.forms.*
import io.ktor.client.statement.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import java.net.URI
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SaizeriyaClient(
    engine: io.ktor.client.engine.HttpClientEngine = OkHttp.create()
) {
    private val httpClient = HttpClient(engine) {
        install(ContentNegotiation) {
            json(Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        followRedirects = false // we handle qr redirect manually
    }

    private val mutex = Mutex()
    private var currentSession: OrderSession? = null

    private fun getNextActionId(html: String): String {
        val regexWithQuestionMark = Regex("""<form[^>]*id="frm_ctrl"[^>]*action="[^"]*\?([^"]+)"""")
        return regexWithQuestionMark.find(html)?.groupValues?.get(1) ?: ""
    }

    private fun getInputValue(html: String, idOrName: String): String? {
        val regex = Regex("""<input[^>]*(?:id|name)="${idOrName}"[^>]*value="([^"]*)"""")
        return regex.find(html)?.groupValues?.get(1)
    }

    private fun getPageKind(html: String): String {
        val regex = Regex("""<form[^>]*id="frm_ctrl"[^>]*class="([^"]*)"""")
        val matchResult = regex.find(html)
        val classes = matchResult?.groupValues?.get(1)?.split(Regex("\\s+")) ?: return "unknown"
        val pageClass = classes.find { it.endsWith("-page") }
        return pageClass?.removeSuffix("-page") ?: "unknown"
    }

    private fun nowOrderTime(): String {
        val dateFormat = SimpleDateFormat("yyyy/MM/dd,HH:mm:ss", Locale.US)
        return dateFormat.format(Date())
    }

    suspend fun createSession(qrUrl: String, peopleCount: Int): OrderSession = mutex.withLock {
        // 1. Resolve QR URL redirect
        val qrResponse = httpClient.get(qrUrl)
        val redirectUrl = qrResponse.headers[HttpHeaders.Location] ?: throw Exception("No redirect location found")
        val redirectUri = URI(redirectUrl)

        val baseUri = if (redirectUri.isAbsolute) {
            redirectUrl.substringBefore("?")
        } else {
            val originalUri = URI(qrUrl)
            "${originalUri.scheme}://${originalUri.host}${if (originalUri.port != -1) ":" + originalUri.port else ""}${redirectUrl.substringBefore("?")}"
        }

        // 2. Fetch the redirected page
        val pageResponse = httpClient.get(baseUri) {
            url {
                if (redirectUrl.contains("?")) {
                    parameters.appendAll(io.ktor.http.parseQueryString(redirectUrl.substringAfter("?")))
                }
            }
        }
        val html = pageResponse.bodyAsText()

        // 3. Parse initial state
        val nextId = getNextActionId(html)
        val shopId = getInputValue(html, "shop-id") ?: "0"
        val tableNo = getInputValue(html, "table-no") ?: "0"
        val pageKind = getPageKind(html)
        val token = getInputValue(html, "token")
        val sessionId = getInputValue(html, "session-id") ?: ""

        var session = OrderSession(
            shopId = shopId,
            tableNo = tableNo,
            sessionId = sessionId,
            pageKind = pageKind,
            peopleCount = peopleCount,
            nextId = nextId,
            token = token,
            baseUrl = baseUri,
            cart = emptyList()
        )

        // 4. Set people count
        if (pageKind != "number") {
            // we skip explicit forced number page transition for simplicity and assume standard flow
        }

        val tokenForNumber = session.token ?: ""
        val submitNumberResponse = httpClient.submitForm(
            url = "${session.baseUrl}?${session.nextId}",
            formParameters = Parameters.build {
                append("proc", "menu")
                append("ctrl", "number")
                append("sub_ctrl", "")
                append("cur_lang", "1")
                append("message", "")
                append("number", peopleCount.toString())
                if (tokenForNumber.isNotEmpty()) {
                    append("token", tokenForNumber)
                }
            }
        )
        val newHtml = submitNumberResponse.bodyAsText()

        session = session.copy(
            nextId = getNextActionId(newHtml),
            pageKind = getPageKind(newHtml),
            token = getInputValue(newHtml, "token") ?: session.token,
            sessionId = getInputValue(newHtml, "session-id") ?: session.sessionId
        )

        currentSession = session
        return session
    }

    suspend fun addItem(sessionId: String, code: String, count: Int = 1): List<CartItem> = mutex.withLock {
        val session = currentSession ?: throw Exception("Session not initialized")
        val token = session.token ?: throw Exception("Token not found")

        // Assume code exists and is available, normally we'd lookupItem here like saizeriya.js
        // We'll proceed with submitPage directly

        val submitResponse = httpClient.submitForm(
            url = "${session.baseUrl}?${session.nextId}",
            formParameters = Parameters.build {
                append("proc", "main")
                append("ctrl", "add")
                append("sub_ctrl", "")
                append("cur_lang", "1")
                append("message", "")
                append("token", token)
                append("ord-drkbar-cnt", "0")
                append("is_reorder", "0")
                append("order-time", nowOrderTime())
                append("code", code)
                append("amount", count.toString())
                append("mod_code", "")
                append("mod_amount", "0")
            }
        )
        val newHtml = submitResponse.bodyAsText()

        val updatedCart = session.cart.toMutableList()
        updatedCart.add(CartItem(code = code, count = count))

        currentSession = session.copy(
            nextId = getNextActionId(newHtml),
            pageKind = getPageKind(newHtml),
            token = getInputValue(newHtml, "token") ?: session.token,
            sessionId = getInputValue(newHtml, "session-id") ?: session.sessionId,
            cart = updatedCart
        )

        return updatedCart
    }

    suspend fun submitOrder(sessionId: String): OrderSession = mutex.withLock {
        val session = currentSession ?: throw Exception("Session not initialized")
        val token = session.token ?: throw Exception("Token not found")

        if (session.cart.isEmpty()) {
            throw Exception("Cannot submit an empty cart")
        }

        val submitResponse = httpClient.submitForm(
            url = "${session.baseUrl}?${session.nextId}",
            formParameters = Parameters.build {
                append("proc", "order")
                append("ctrl", "remember")
                append("sub_ctrl", "")
                append("cur_lang", "1")
                append("message", "")
                append("token", token)
                append("code", "")
                append("drinkbar-cnt", "0")
                append("alcohol-cnt", "0")
                append("ord-drkbar-cnt", "0")

                session.cart.forEach { item ->
                    append("item[id][]", item.code)
                    append("item[reorder][]", item.reorder.toString())
                    append("item[count][]", item.count.toString())
                    append("item[mod_id][]", "")
                    append("item[mod_count][]", "0")
                }
            }
        )
        val newHtml = submitResponse.bodyAsText()

        currentSession = session.copy(
            nextId = getNextActionId(newHtml),
            pageKind = getPageKind(newHtml),
            token = getInputValue(newHtml, "token") ?: session.token,
            sessionId = getInputValue(newHtml, "session-id") ?: session.sessionId,
            cart = emptyList() // Clear cart after order
        )

        return currentSession!!
    }

    fun close() {
        httpClient.close()
    }
}

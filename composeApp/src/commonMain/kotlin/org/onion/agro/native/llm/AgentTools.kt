package org.onion.agro.native.llm

import com.dokar.quickjs.quickJs
import com.fleeksoft.ksoup.Ksoup
import io.ktor.client.HttpClient
import io.ktor.client.request.header
import io.ktor.client.request.request
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpMethod
import io.ktor.http.URLBuilder
import io.ktor.http.Url
import io.ktor.http.contentLength
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

class AgentTools : KoinComponent, AgentToolExecutor {

    private companion object {
        const val DEFAULT_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        const val MAX_JS_CHARS = 20_000
    }

    private val httpClient: HttpClient by lazy {
        try {
            get<HttpClient>()
        } catch (e: Exception) {
            HttpClient()
        }
    }

    private val tools: Map<String, AgentTool> by lazy {
        listOf(
            //LocalJsTool(),
            AnalyzeUrlTool(),
            SearchWebTool()
        ).associateBy { it.definition.name }
    }

    /**
     * Returns a JSON array of all tool definitions for the LiteRT LM model.
     * The schema and execution registry share the same source of truth.
     */
    fun getToolsDescriptionJson(): String {
        return buildJsonArray {
            tools.values.forEach { add(it.definition.toJson()) }
        }.toString()
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun executeTool(name: String, arguments: JsonObject): ToolExecutionResult {
        val tool = tools[name]
        if (tool == null) {
            val now = Clock.System.now().toEpochMilliseconds()
            return ToolExecutionResult(
                toolName = name,
                success = false,
                data = JsonNull,
                error = "Tool '$name' is not registered or is disabled.",
                startedAtMillis = now,
                completedAtMillis = now
            )
        }

        return try {
            tool.execute(arguments)
        } catch (e: Exception) {
            val now = Clock.System.now().toEpochMilliseconds()
            ToolExecutionResult(
                toolName = name,
                success = false,
                data = JsonNull,
                error = e.message ?: "Tool execution failed.",
                startedAtMillis = now,
                completedAtMillis = now
            )
        }
    }

    private inner class LocalJsTool : AgentTool {
        override val definition = AgentToolDefinition(
            name = "runJs",
            description = "Runs a small JavaScript expression using the local QuickJS engine.",
            parameters = objectSchema(
                required = listOf("data"),
                properties = mapOf(
                    "data" to stringSchema(
                        "The JavaScript source to execute. Keep it deterministic and self-contained."
                    )
                )
            ),
            concurrencySafe = false
        )

        override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
            return runMeasured(definition.name) {
                val data = arguments.stringArgument("data")
                if (data.isBlank()) {
                    return@runMeasured buildJsonObject {
                        put("success", false)
                        put("error", "JavaScript source cannot be blank.")
                    }
                }
                if (data.length > MAX_JS_CHARS) {
                    return@runMeasured buildJsonObject {
                        put("success", false)
                        put("error", "JavaScript source exceeds $MAX_JS_CHARS characters.")
                    }
                }

                val result = quickJs {
                    evaluate<Any?>(data)
                }
                buildJsonObject {
                    put("success", true)
                    put("result", result?.toString() ?: "null")
                }
            }
        }
    }

    private inner class AnalyzeUrlTool : AgentTool {
        override val definition = AgentToolDefinition(
            name = "analyzeUrl",
            description = "Performs an HTTP request and returns structured URL, request, response, and content preview data.",
            parameters = objectSchema(
                required = listOf("url"),
                properties = mapOf(
                    "url" to stringSchema("The HTTP or HTTPS URL to request and analyze."),
                    "method" to stringSchema("The HTTP method to use: GET, POST, PUT, DELETE, or HEAD. Default is GET."),
                    "headers" to stringSchema("Optional JSON object string containing request headers."),
                    "body" to stringSchema("Optional request body string.")
                )
            ),
            concurrencySafe = true
        )

        override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
            return runMeasured(definition.name) {
                analyzeUrl(
                    urlString = arguments.stringArgument("url"),
                    methodStr = arguments.stringArgumentOrNull("method"),
                    headersJson = arguments.stringArgumentOrNull("headers"),
                    bodyStr = arguments.stringArgumentOrNull("body")
                )
            }
        }
    }

    private inner class SearchWebTool : AgentTool {
        override val definition = AgentToolDefinition(
            name = "searchWeb",
            description = "Searches current web content with Bing and returns structured, indexable results.",
            parameters = objectSchema(
                required = listOf("query"),
                properties = mapOf(
                    "query" to stringSchema("The search query."),
                    "count" to integerSchema("Number of search results to return. Default is 5, maximum is 10."),
                    "includeContent" to booleanSchema("Whether to fetch each result page and include cleaned text. Default is false."),
                    "maxContentChars" to integerSchema("Maximum cleaned text characters per fetched page. Default is 4000.")
                )
            ),
            concurrencySafe = true
        )

        override suspend fun execute(arguments: JsonObject): ToolExecutionResult {
            return runMeasured(definition.name) {
                searchWeb(
                    query = arguments.stringArgument("query"),
                    count = arguments.intArgument("count"),
                    includeContent = arguments.booleanArgument("includeContent"),
                    maxContentChars = arguments.intArgument("maxContentChars")
                )
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun runMeasured(
        toolName: String,
        block: suspend () -> JsonElement
    ): ToolExecutionResult {
        val startedAt = Clock.System.now().toEpochMilliseconds()
        val data = try {
            block()
        } catch (e: Exception) {
            val completedAt = Clock.System.now().toEpochMilliseconds()
            return ToolExecutionResult(
                toolName = toolName,
                success = false,
                data = JsonNull,
                error = e.message ?: "Tool execution failed.",
                startedAtMillis = startedAt,
                completedAtMillis = completedAt
            )
        }
        val completedAt = Clock.System.now().toEpochMilliseconds()
        val success = data.successFlagOrDefault(default = true)
        val error = if (success) null else data.errorMessageOrNull()
        return ToolExecutionResult(
            toolName = toolName,
            success = success,
            data = data,
            error = error,
            startedAtMillis = startedAt,
            completedAtMillis = completedAt
        )
    }

    private fun objectSchema(
        required: List<String>,
        properties: Map<String, JsonObject>
    ): JsonObject = buildJsonObject {
        put("type", "object")
        put("properties", buildJsonObject {
            properties.forEach { (name, schema) -> put(name, schema) }
        })
        put("required", buildJsonArray {
            required.forEach { add(JsonPrimitive(it)) }
        })
    }

    private fun stringSchema(description: String): JsonObject = buildJsonObject {
        put("type", "string")
        put("description", description)
    }

    private fun integerSchema(description: String): JsonObject = buildJsonObject {
        put("type", "integer")
        put("description", description)
    }

    private fun booleanSchema(description: String): JsonObject = buildJsonObject {
        put("type", "boolean")
        put("description", description)
    }

    private fun JsonObject.stringArgument(name: String): String {
        return stringArgumentOrNull(name).orEmpty()
    }

    private fun JsonObject.stringArgumentOrNull(name: String): String? {
        return (this[name] as? JsonPrimitive)?.contentOrNull
    }

    private fun JsonObject.intArgument(name: String): Int? {
        return (this[name] as? JsonPrimitive)?.intOrNull
    }

    private fun JsonObject.booleanArgument(name: String): Boolean? {
        return (this[name] as? JsonPrimitive)?.booleanOrNull
    }

    private fun JsonElement.successFlagOrDefault(default: Boolean): Boolean {
        return (this as? JsonObject)
            ?.get("success")
            ?.jsonPrimitive
            ?.booleanOrNull
            ?: default
    }

    private fun JsonElement.errorMessageOrNull(): String? {
        return (this as? JsonObject)
            ?.get("error")
            ?.jsonPrimitive
            ?.contentOrNull
    }

    private fun sanitizeUrl(urlString: String): String {
        var url = urlString.trim()
        val wrappers = listOf("<|\\\"|>", "<|'|>", "<|", "|>", "\\\"", "\\'", "\"", "'", "`")
        var modified = true
        while (modified) {
            modified = false
            for (wrapper in wrappers) {
                if (url.startsWith(wrapper)) {
                    url = url.substring(wrapper.length)
                    modified = true
                }
                if (url.endsWith(wrapper)) {
                    url = url.substring(0, url.length - wrapper.length)
                    modified = true
                }
            }
        }
        url = url.trim()
        url = url.replace("<|\\\"|>", "")
            .replace("<|'|>", "")
            .replace("<|", "")
            .replace("|>", "")
            .replace("\\\"", "")
            .replace("\\'", "")
            .replace("\"", "")
            .replace("'", "")
            .replace("`", "")
        return url.trim()
    }

    private fun cleanHtml(html: String, baseUri: String = ""): String {
        return try {
            val document = Ksoup.parse(html = html, baseUri = baseUri)
            document.select("script, style, svg, noscript, iframe, nav, footer, form").remove()
            document.body().text().normalizeWhitespace()
        } catch (e: Exception) {
            html.replace(Regex("<script[^>]*?>[\\s\\S]*?</script>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<style[^>]*?>[\\s\\S]*?</style>", RegexOption.IGNORE_CASE), "")
                .replace(Regex("<[^>]*?>"), " ")
                .normalizeWhitespace()
        }
    }

    private fun String.normalizeWhitespace(): String {
        return replace(Regex("\\s+"), " ").trim()
    }

    private fun String.truncateForTool(maxChars: Int): String {
        val boundedMax = maxChars.coerceIn(200, 20_000)
        return if (length > boundedMax) {
            substring(0, boundedMax) + "... [truncated]"
        } else {
            this
        }
    }

    private fun applyBrowserHeaders(headers: MutableMap<String, String>) {
        val hasUserAgent = headers.keys.any { it.equals("User-Agent", ignoreCase = true) }
        if (!hasUserAgent) {
            headers["User-Agent"] = DEFAULT_USER_AGENT
        }
        val hasAccept = headers.keys.any { it.equals("Accept", ignoreCase = true) }
        if (!hasAccept) {
            headers["Accept"] = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8"
        }
        val hasAcceptLanguage = headers.keys.any { it.equals("Accept-Language", ignoreCase = true) }
        if (!hasAcceptLanguage) {
            headers["Accept-Language"] = "en-US,en;q=0.9,zh-CN;q=0.8,zh;q=0.7"
        }
    }

    private suspend fun executeRequest(
        urlObj: Url,
        method: HttpMethod,
        headers: Map<String, String>,
        bodyStr: String?
    ): HttpResponse {
        return httpClient.request(urlObj) {
            this.method = method
            headers.forEach { (key, value) ->
                header(key, value)
            }
            if (!bodyStr.isNullOrBlank()) {
                setBody(bodyStr)
            }
        }
    }

    private fun buildBingSearchUrl(query: String, count: Int): Url {
        return URLBuilder("https://www.bing.com/search").apply {
            parameters.append("q", query)
            parameters.append("count", count.toString())
            parameters.append("mkt", "en-US")
        }.build()
    }

    private fun resolveSearchResultUrl(rawUrl: String): String {
        val href = rawUrl.trim()
        return when {
            href.startsWith("http://") || href.startsWith("https://") -> href
            href.startsWith("//") -> "https:$href"
            href.startsWith("/") -> "https://www.bing.com$href"
            else -> href
        }
    }

    private suspend fun fetchSearchResultContent(url: String, maxContentChars: Int): JsonObject {
        return try {
            val targetUrl = Url(sanitizeUrl(url))
            val headers = mutableMapOf<String, String>()
            applyBrowserHeaders(headers)
            val response = executeRequest(targetUrl, HttpMethod.Get, headers, null)
            val contentType = response.headers["Content-Type"] ?: ""
            val rawBody = response.bodyAsText()
            val cleanedBody = if (contentType.contains("html", ignoreCase = true)) {
                cleanHtml(rawBody, targetUrl.toString())
            } else {
                rawBody.normalizeWhitespace()
            }
            buildJsonObject {
                put("contentFetched", true)
                put("statusCode", response.status.value)
                put("contentType", contentType)
                put("content", cleanedBody.truncateForTool(maxContentChars))
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("contentFetched", false)
                put("contentError", e.message ?: "Unable to fetch result content.")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun searchWeb(
        query: String,
        count: Int?,
        includeContent: Boolean?,
        maxContentChars: Int?
    ): JsonObject {
        return try {
            val normalizedQuery = query.trim()
            if (normalizedQuery.isBlank()) {
                return buildJsonObject {
                    put("success", false)
                    put("error", "Search query cannot be blank.")
                }
            }

            val resultLimit = (count ?: 5).coerceIn(1, 10)
            val shouldFetchContent = includeContent ?: false
            val contentLimit = (maxContentChars ?: 4000).coerceIn(500, 20_000)
            val startedAt = Clock.System.now().toString()
            val searchUrl = buildBingSearchUrl(normalizedQuery, resultLimit)
            val headers = mutableMapOf<String, String>()
            applyBrowserHeaders(headers)

            val response = executeRequest(searchUrl, HttpMethod.Get, headers, null)
            val html = response.bodyAsText()
            val document = Ksoup.parse(html = html, baseUri = "https://www.bing.com")
            val resultItems = document.select("li.b_algo")
            val parsedResults = mutableListOf<JsonObject>()

            for (item in resultItems) {
                if (parsedResults.size >= resultLimit) break

                val link = item.select("h2 a").first() ?: continue
                val rawUrl = link.absUrl("href").ifBlank { link.attr("href") }
                val url = resolveSearchResultUrl(rawUrl)
                if (url.isBlank()) continue

                val title = link.text().normalizeWhitespace()
                val snippet = (item.select(".b_caption p").first() ?: item.select("p").first())
                    ?.text()
                    ?.normalizeWhitespace()
                    .orEmpty()
                val displayUrl = item.select("cite").first()?.text()?.normalizeWhitespace().orEmpty()

                val pageContent = if (shouldFetchContent) {
                    fetchSearchResultContent(url, contentLimit)
                } else {
                    buildJsonObject { put("contentFetched", false) }
                }

                parsedResults += buildJsonObject {
                    put("rank", parsedResults.size + 1)
                    put("title", title)
                    put("url", url)
                    put("displayUrl", displayUrl)
                    put("snippet", snippet)
                    pageContent.forEach { (key, value) -> put(key, value) }
                }
            }

            buildJsonObject {
                put("success", true)
                put("provider", "bing")
                put("query", normalizedQuery)
                put("requestedCount", resultLimit)
                put("resultCount", parsedResults.size)
                put("fetchedAt", startedAt)
                put("searchUrl", searchUrl.toString())
                put("results", buildJsonArray {
                    parsedResults.forEach { add(it) }
                })
                if (parsedResults.isEmpty()) {
                    put("warning", "No standard Bing search results were parsed from the response.")
                }
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("success", false)
                put("provider", "bing")
                put("query", query)
                put("error", "Search failed: ${e.message}")
            }
        }
    }

    @OptIn(ExperimentalTime::class)
    private suspend fun analyzeUrl(
        urlString: String,
        methodStr: String?,
        headersJson: String?,
        bodyStr: String?
    ): JsonObject {
        return try {
            val startTime = Clock.System.now().toEpochMilliseconds()
            val sanitizedUrlString = sanitizeUrl(urlString)
            val targetUrlObj = try {
                Url(sanitizedUrlString)
            } catch (e: Exception) {
                return buildJsonObject {
                    put("success", false)
                    put("error", "Invalid URL: ${e.message} (original input was: $urlString)")
                }
            }

            if (targetUrlObj.protocol.name !in setOf("http", "https")) {
                return buildJsonObject {
                    put("success", false)
                    put("error", "Only HTTP and HTTPS URLs are supported.")
                }
            }

            val method = when (methodStr?.uppercase()) {
                "POST" -> HttpMethod.Post
                "PUT" -> HttpMethod.Put
                "DELETE" -> HttpMethod.Delete
                "HEAD" -> HttpMethod.Head
                else -> HttpMethod.Get
            }

            val parsedHeaders = mutableMapOf<String, String>()
            if (!headersJson.isNullOrBlank()) {
                try {
                    val element = Json.parseToJsonElement(headersJson)
                    if (element is JsonObject) {
                        element.forEach { (key, value) ->
                            parsedHeaders[key] = value.jsonPrimitive.content
                        }
                    }
                } catch (e: Exception) {
                    return buildJsonObject {
                        put("success", false)
                        put("error", "Invalid headers JSON: ${e.message}")
                    }
                }
            }

            applyBrowserHeaders(parsedHeaders)

            val response = executeRequest(targetUrlObj, method, parsedHeaders, bodyStr)
            val endTime = Clock.System.now().toEpochMilliseconds()
            val latency = endTime - startTime

            val contentType = response.headers["Content-Type"] ?: ""
            val isHtml = contentType.contains("text/html", ignoreCase = true)

            val rawBody = try {
                response.bodyAsText()
            } catch (e: Exception) {
                "[Unable to read body: ${e.message}]"
            }

            val cleanedBody = if (isHtml) {
                cleanHtml(rawBody, targetUrlObj.toString())
            } else {
                rawBody
            }

            val responseHeaders = buildJsonObject {
                response.headers.entries().forEach { entry ->
                    put(entry.key, entry.value.joinToString(","))
                }
            }

            val queryParams = buildJsonObject {
                targetUrlObj.parameters.entries().forEach { entry ->
                    put(entry.key, entry.value.joinToString(","))
                }
            }

            buildJsonObject {
                put("success", true)
                put("urlAnalysis", buildJsonObject {
                    put("originalUrl", urlString)
                    put("requestedUrl", targetUrlObj.toString())
                    put("protocol", targetUrlObj.protocol.name)
                    put("host", targetUrlObj.host)
                    put("port", targetUrlObj.port)
                    put("path", targetUrlObj.encodedPath)
                    put("queryParameters", queryParams)
                })
                put("request", buildJsonObject {
                    put("method", method.value)
                    put("headers", buildJsonObject {
                        parsedHeaders.forEach { (key, value) -> put(key, value) }
                    })
                })
                put("response", buildJsonObject {
                    put("statusCode", response.status.value)
                    put("statusDescription", response.status.description)
                    put("headers", responseHeaders)
                    put("responseTimeMs", latency)
                    put("isHtmlCleaned", isHtml)
                    put("contentLength", response.contentLength() ?: cleanedBody.length.toLong())
                    put("contentPreview", cleanedBody.truncateForTool(2000))
                })
            }
        } catch (e: Exception) {
            buildJsonObject {
                put("success", false)
                put("error", "Request failed: ${e.message}")
            }
        }
    }
}

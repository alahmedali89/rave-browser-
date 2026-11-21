package com.example

import java.io.BufferedInputStream
import java.io.Closeable
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Deterministic multi-route local HTTP/1.1 fixture server bound to 127.0.0.1:0
 * for Phase 7 real Chromium WebView instrumentation tests.
 * Supports distinct ports per instance to emulate real cross-origin boundaries,
 * HTTP 302 redirects, form submissions, dynamic/delayed fixtures, and SPA routes.
 */
class LocalWebFixtureServer : Closeable {

    data class HttpRequest(
        val method: String,
        val path: String,
        val rawPathWithQuery: String,
        val queryParams: Map<String, String>,
        val headers: Map<String, String>,
        val body: String
    )

    data class HttpResponse(
        val statusCode: Int = 200,
        val statusText: String = "OK",
        val contentType: String = "text/html; charset=utf-8",
        val body: String = "",
        val headers: Map<String, String> = emptyMap()
    )

    private val serverSocket = ServerSocket(0, 32, InetAddress.getByName("127.0.0.1"))
    private val running = AtomicBoolean(true)
    private val executor: ExecutorService = Executors.newCachedThreadPool()
    private val routes = ConcurrentHashMap<String, (HttpRequest) -> HttpResponse>()
    private val recordedRequests = CopyOnWriteArrayList<HttpRequest>()

    val port: Int get() = serverSocket.localPort
    val origin: String get() = "http://127.0.0.1:$port"

    init {
        executor.execute {
            while (running.get() && !serverSocket.isClosed) {
                val client = try {
                    serverSocket.accept()
                } catch (_: SocketException) {
                    break
                } catch (_: Throwable) {
                    continue
                }
                executor.execute { handleClient(client) }
            }
        }
    }

    fun urlFor(path: String): String {
        val clean = if (path.startsWith("/")) path else "/$path"
        return "$origin$clean"
    }

    fun registerHtml(path: String, html: String): LocalWebFixtureServer {
        val clean = if (path.startsWith("/")) path else "/$path"
        routes[clean] = { HttpResponse(statusCode = 200, statusText = "OK", body = html) }
        return this
    }

    fun registerRedirect(path: String, locationUrl: String, statusCode: Int = 302): LocalWebFixtureServer {
        val clean = if (path.startsWith("/")) path else "/$path"
        routes[clean] = {
            HttpResponse(
                statusCode = statusCode,
                statusText = if (statusCode == 301) "Moved Permanently" else "Found",
                contentType = "text/html; charset=utf-8",
                body = "<html><body>Redirecting to $locationUrl</body></html>",
                headers = mapOf("Location" to locationUrl)
            )
        }
        return this
    }

    fun registerDynamic(path: String, handler: (HttpRequest) -> HttpResponse): LocalWebFixtureServer {
        val clean = if (path.startsWith("/")) path else "/$path"
        routes[clean] = handler
        return this
    }

    fun requests(): List<HttpRequest> = recordedRequests.toList()

    fun clearRequests() {
        recordedRequests.clear()
    }

    private fun handleClient(socket: Socket) {
        socket.use { sock ->
            sock.soTimeout = 5_000
            val input = BufferedInputStream(sock.getInputStream())
            val output = sock.getOutputStream()
            val req = readHttpRequest(input) ?: return
            recordedRequests.add(req)

            val handler = routes[req.path]
            val response = if (handler != null) {
                runCatching { handler(req) }.getOrElse { err ->
                    HttpResponse(
                        statusCode = 500,
                        statusText = "Internal Server Error",
                        body = "<html><body>Handler error: ${err.message}</body></html>"
                    )
                }
            } else {
                HttpResponse(
                    statusCode = 404,
                    statusText = "Not Found",
                    body = "<html><body><h1>404 Not Found</h1><p>${req.path}</p></body></html>"
                )
            }
            writeHttpResponse(output, response)
        }
    }

    private fun readHttpRequest(input: BufferedInputStream): HttpRequest? {
        val headerBuf = StringBuilder()
        var prev = -1
        var matched = false
        while (true) {
            val b = input.read()
            if (b == -1) break
            headerBuf.append(b.toChar())
            val len = headerBuf.length
            if (len >= 4 &&
                headerBuf[len - 4] == '\r' &&
                headerBuf[len - 3] == '\n' &&
                headerBuf[len - 2] == '\r' &&
                headerBuf[len - 1] == '\n'
            ) {
                matched = true
                break
            }
            if (prev == '\n'.code && b == '\n'.code) {
                matched = true
                break
            }
            prev = b
            if (len > 32 * 1024) break
        }
        if (!matched && headerBuf.isEmpty()) return null

        val lines = headerBuf.toString().split("\r\n", "\n")
        val requestLine = lines.firstOrNull()?.trim().orEmpty()
        if (requestLine.isEmpty()) return null
        val parts = requestLine.split(' ')
        val method = parts.getOrNull(0)?.uppercase() ?: "GET"
        val rawPath = parts.getOrNull(1) ?: "/"

        val qIdx = rawPath.indexOf('?')
        val pathOnly = if (qIdx >= 0) rawPath.substring(0, qIdx) else rawPath
        val queryString = if (qIdx >= 0) rawPath.substring(qIdx + 1) else ""
        val queryParams = parseQuery(queryString)

        val headers = LinkedHashMap<String, String>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val colon = line.indexOf(':')
            if (colon > 0) {
                val k = line.substring(0, colon).trim().lowercase()
                val v = line.substring(colon + 1).trim()
                headers[k] = v
            }
        }

        val contentLength = headers["content-length"]?.toIntOrNull()?.coerceIn(0, 512 * 1024) ?: 0
        val bodyBytes = ByteArray(contentLength)
        var readTotal = 0
        while (readTotal < contentLength) {
            val n = input.read(bodyBytes, readTotal, contentLength - readTotal)
            if (n <= 0) break
            readTotal += n
        }
        val body = String(bodyBytes, 0, readTotal, Charsets.UTF_8)

        return HttpRequest(
            method = method,
            path = pathOnly,
            rawPathWithQuery = rawPath,
            queryParams = queryParams,
            headers = headers,
            body = body
        )
    }

    private fun parseQuery(query: String): Map<String, String> {
        if (query.isBlank()) return emptyMap()
        val result = LinkedHashMap<String, String>()
        query.split('&').forEach { pair ->
            if (pair.isBlank()) return@forEach
            val eq = pair.indexOf('=')
            val key = URLDecoder.decode(if (eq >= 0) pair.substring(0, eq) else pair, "UTF-8")
            val value = if (eq >= 0) URLDecoder.decode(pair.substring(eq + 1), "UTF-8") else ""
            result[key] = value
        }
        return result
    }

    private fun writeHttpResponse(output: OutputStream, response: HttpResponse) {
        val bodyBytes = response.body.toByteArray(Charsets.UTF_8)
        val headerSb = StringBuilder()
        headerSb.append("HTTP/1.1 ${response.statusCode} ${response.statusText}\r\n")
        headerSb.append("Content-Type: ${response.contentType}\r\n")
        headerSb.append("Content-Length: ${bodyBytes.size}\r\n")
        headerSb.append("Cache-Control: no-store, no-cache, must-revalidate\r\n")
        headerSb.append("Connection: close\r\n")
        response.headers.forEach { (k, v) ->
            headerSb.append("$k: $v\r\n")
        }
        headerSb.append("\r\n")
        output.write(headerSb.toString().toByteArray(Charsets.US_ASCII))
        output.write(bodyBytes)
        output.flush()
    }

    override fun close() {
        running.set(false)
        runCatching { serverSocket.close() }
        executor.shutdownNow()
    }
}

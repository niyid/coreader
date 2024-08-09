package com.techducat.coreader

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.wss
import io.ktor.http.HttpMethod
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.security.KeyStore
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.TrustManagerFactory
import javax.net.ssl.X509TrustManager

class RelayClient private constructor() {

    private val notificationInterfaces: MutableList<NotificationInterface> = mutableListOf()

    companion object {
        const val HOST = "8.222.202.85"
        const val PORT = 8765
        private const val TAG = "RelayClient"

        private const val MAX_RETRY_DELAY = 60000
        private const val MAX_RETRY_COUNT = 5
        private const val RETRY_DELAY = 5000
        @SuppressLint("StaticFieldLeak")
        private var instance: RelayClient? = null

        fun getInstance(): RelayClient {
            if (instance == null) {
                instance = RelayClient()
            }
            return instance!!
        }
    }

    fun addNotificationInterface(notificationInterface: NotificationInterface) {
        notificationInterfaces.add(notificationInterface)
    }

    fun removeNotificationInterface(notificationInterface: NotificationInterface) {
        notificationInterfaces.remove(notificationInterface)
    }

    private fun notifyAllInterfaces(message: String) {
        for (notificationInterface in notificationInterfaces) {
            notificationInterface.notify(message)
        }
    }

    private lateinit var session: DefaultClientWebSocketSession
    private val connectionCompleted = CompletableDeferred<Unit>()

    init {
        CoroutineScope(Dispatchers.IO).launch {
            connect()
        }
    }

    //TODO Make more secure
    private val sslContext = SSLContext.getInstance("TLS").apply {
        init(null, arrayOf<TrustManager>(@SuppressLint("CustomX509TrustManager")
        object : X509TrustManager {
            override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            @SuppressLint("TrustAllX509TrustManager")
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
        }), null)
    }

    private val client = HttpClient(OkHttp) {
        install(WebSockets)
        engine {
            addInterceptor { chain ->
                val request = chain.request()
                val newRequest = request.newBuilder()
                    .build()
                chain.proceed(newRequest)
            }
            config {
                sslSocketFactory(sslContext.socketFactory, TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm()).apply {
                    init(null as KeyStore?)
                }.trustManagers[0] as X509TrustManager)
                hostnameVerifier { _, _ -> true }
            }
        }
    }

    private suspend fun ensureConnection() {
        connectionCompleted.await()
    }

    private suspend fun connect() {
        var retryCount = 0
        var connected = false

        while (!connected && retryCount < MAX_RETRY_COUNT) {
            try {
                client.wss(
                    method = HttpMethod.Get,
                    host = HOST,
                    port = PORT,
                    path = "/",
                ) {
                    session = this
                    connectionCompleted.complete(Unit)
                    connected = true

                    Log.i(TAG, "session => $session")
                    //TODO Handle relays from server here
                    // Listen for incoming messages
                    try {
                        for (frame in incoming) {
                            if (frame is Frame.Text) {
                                val message = frame.readText()
                                Log.i(TAG, "message=$message")
                                val jsonObject = Gson().fromJson(message, Map::class.java)

                                if (jsonObject["type"] == "turn_paragraph") {
                                    Log.i(TAG, "turn_paragraph received")
                                    handleSessionUpdateResponse(jsonObject)
                                } else
                                    if (jsonObject["type"] == "turn_page") {
                                        Log.i(TAG, "turn_page received")
                                        handleSessionUpdateResponse(jsonObject)
                                    }
                                else
                                    if(jsonObject["type"] == "list_sessions_response") {
                                        Log.i(TAG, "list_sessions_response received")
                                        handleSessionsListResponse(message)
                                    }
                                else
                                    if(jsonObject["type"] == "create_session_response") {
                                        Log.i(TAG, "create_session_response received")
                                        handleSessionCreateResponse(jsonObject)
                                    }
                                else
                                    if(jsonObject["type"] == "peer_update") {
                                        Log.i(TAG, "peer_update received")
                                        handlePeerUpdateResponse(jsonObject)
                                    }
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error receiving message: ${e.message}")
                        e.printStackTrace()
                    } finally {
                        connected = false
//                        activity.restartActivity()
                    }
                }
            } catch (e: Exception) {
                // Silent retry
//                Log.e(TAG, "Connection attempt failed: ${e.message}")
//                e.printStackTrace()
                // Retry with exponential backoff
                val delay = minOf(RETRY_DELAY * (1 shl retryCount), MAX_RETRY_DELAY)
                delay(delay.toLong())
                retryCount++
            }
        }

        if (!connected) {
            Log.e(TAG, "Failed to establish connection after $MAX_RETRY_COUNT attempts")
        }
    }

    private fun handleSessionsListResponse(response: String) {
        Log.i(TAG, "handleSessionCreateResponse - $response")
        val sessionResponse = Gson().fromJson(response, SessionResponse::class.java)
        for (notificationInterface in notificationInterfaces) {
            if(notificationInterface is MainActivity) {
                notificationInterface.runOnUiThread {
                    notificationInterface.onSessionsUpdated(sessionResponse.sessions)
                }
            }
        }
    }

    private fun handleSessionCreateResponse(response: Map<*, *>) {
        Log.i(TAG, "handleSessionCreateResponse - $response")
        val newSession = Session(response["session_id"] as String, response["title"] as String)
        val sessionList = mutableListOf<Session>()
        sessionList.add(newSession)
        for (notificationInterface in notificationInterfaces) {
            notificationInterface.onSessionsUpdated(sessionList)
            if(notificationInterface is MainActivity) {
                notificationInterface.runOnUiThread {
                    notificationInterface.launchCoreadActivity(response["title"] as String)
                }
            }
        }
    }

    private fun handlePeerUpdateResponse(response: Map<*, *>) {
        Log.i(TAG, "handlePeerUpdateResponse - $response")
        val count = response["count"] as Int

        for (notificationInterface in notificationInterfaces) {
            if(notificationInterface is CoreadActivity) {
                notificationInterface.runOnUiThread {
                    notificationInterface.onSessionsUpdated(count)
                }
            }
        }
    }

    private fun handleSessionUpdateResponse(response: Map<*, *>) {
        Log.i(TAG, "handleSessionUpdateResponse - $response")
        val count = response["count"] as Double
        val page = response["current_page"] as Double
        val pageCount = response["page_count"] as Double
        val pageHtml = response["page"] as String

        for (notificationInterface in notificationInterfaces) {
            if(notificationInterface is CoreadActivity) {
                notificationInterface.runOnUiThread {
                    notificationInterface.onSessionsUpdated(
                        count.toInt(),
                        page.toInt(),
                        pageCount.toInt(),
                        pageHtml
                    )
                }
            }
        }

    }

    suspend fun listSessions() {
        Log.i(TAG, "Function listSessions")
        val message = """{"type": "list_sessions"}"""
        ensureConnection()
        session.send(Frame.Text(message))
    }

    suspend fun joinSession(deviceId: String, sessionId: String) {
        Log.i(TAG, "Function joinSession - $deviceId, $sessionId")
        val message = """{"type": "join_session", "device_id": "$deviceId", "session_id": "$sessionId"}"""
        ensureConnection()
        session.send(Frame.Text(message))
    }

    suspend fun setReady(deviceId: String, sessionId: String, isReady: Boolean) {
        Log.i(TAG, "Function setReady - $deviceId, $sessionId, $isReady")
        val message = """{"type": "ready", "device_id": "$deviceId", "session_id": "$sessionId", "ready": $isReady}"""
        ensureConnection()
        session.send(Frame.Text(message))
    }

    suspend fun unregister() {
        Log.i(TAG, "Function unregister - ")
        val message = """{"type": "unregister"}"""
        ensureConnection()
        session.send(Frame.Text(message))
    }

    suspend fun createSession(deviceId: String, sessionId: String, title: String, pdfUri: Uri) {
        Log.i(TAG, "Function uploadPdf - $deviceId, $sessionId, $title, ${pdfUri.path}")
        val bufferSize = 1024 * 4  // 4KB
        val buffer = ByteArray(bufferSize)
        val activity = notificationInterfaces[0] as Activity
        val fileInputStream = withContext(Dispatchers.IO) {
            getInputStreamFromUri(activity, pdfUri)
        }

        var bytesRead: Int
        var chunkIndex = 0

        while (withContext(Dispatchers.IO) {
                fileInputStream!!.read(buffer)
            }.also { bytesRead = it } != -1) {
            val base64Chunk = Base64.encodeToString(buffer, 0, bytesRead, Base64.NO_WRAP)
            val message = """{
                "type": "upload_chunk", 
                "device_id": "$deviceId", 
                "session_id": "$sessionId", 
                "title": "$title", 
                "chunk_index": $chunkIndex, 
                "chunk_data": "$base64Chunk"
            }"""
            ensureConnection()
            session.send(Frame.Text(message))
            chunkIndex++
            Log.i(TAG, "Function uploadPdf - $chunkIndex")
        }

        // Notify the server that the upload is complete
        val completeMessage = """{
            "type": "upload_complete", 
            "device_id": "$deviceId",
             "title": "$title", 
            "session_id": "$sessionId" 
        }"""
        session.send(Frame.Text(completeMessage))
        Log.i(TAG, "PDF upload completed - $deviceId, $sessionId")
    }

    private fun getInputStreamFromUri(context: Context, uri: Uri): InputStream? {
        return context.contentResolver.openInputStream(uri)
    }

    private fun getParagraphText(page: Int, paragraph: Int): String {
        // Placeholder method to fetch paragraph text
        return "Page $page, Paragraph $paragraph"
    }

    fun close() {
        client.close()
    }
}



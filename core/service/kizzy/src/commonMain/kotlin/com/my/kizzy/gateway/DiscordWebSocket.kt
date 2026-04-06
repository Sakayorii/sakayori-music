package com.my.kizzy.gateway

import com.sakayori.logger.Logger
import com.my.kizzy.gateway.entities.Heartbeat
import com.my.kizzy.gateway.entities.Identify.Companion.toIdentifyPayload
import com.my.kizzy.gateway.entities.Payload
import com.my.kizzy.gateway.entities.Ready
import com.my.kizzy.gateway.entities.Resume
import com.my.kizzy.gateway.entities.op.OpCode
import com.my.kizzy.gateway.entities.presence.Presence
import io.ktor.client.HttpClient
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.websocket.CloseReason
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import io.ktor.websocket.readText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration.Companion.milliseconds

private const val TAG = "Kizzy"
open class DiscordWebSocket(
    private val token: String,
) : CoroutineScope {

    private val gatewayUrl = "wss://gateway.discord.gg/?v=10&encoding=json"
    private var websocket: DefaultClientWebSocketSession? = null
    private var sequence = 0
    private var sessionId: String? = null
    private var heartbeatInterval = 0L
    private var resumeGatewayUrl: String? = null
    private var heartbeatJob: Job? = null
    private var connected = false
    private var client: HttpClient = HttpClient {
        install(WebSockets)
    }
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + Dispatchers.Default

    suspend fun connect() {
        launch {
            try {
                Logger.i("Gateway","Connect called")
                val url = resumeGatewayUrl ?: gatewayUrl
                websocket = client.webSocketSession(url)

                val ws = websocket ?: return@launch
                ws.incoming.receiveAsFlow()
                    .collect {
                        when (it) {
                            is Frame.Text -> {
                                val jsonString = it.readText()
                                onMessage(json.decodeFromString(jsonString))
                            }
                            else -> {}
                        }
                    }
                handleClose()
            } catch (e: Exception) {
                Logger.e(TAG, "Gateway ${e.message}", e)
                close()
            }
        }
    }

    private suspend fun handleClose(){
        heartbeatJob?.cancel()
        connected = false
        val close = websocket?.closeReason?.await()
        Logger.w(TAG,"Closed with code: ${close?.code}, " +
            "reason: ${close?.message}, " +
            "can_reconnect: ${close?.code?.toInt() == 4000}")
        if (close?.code?.toInt() == 4000) {
            delay(200.milliseconds)
            connect()
        } else
            close()
    }

    private suspend fun onMessage(payload: Payload) {
        Logger.d(TAG,"Received op:${payload.op}, seq:${payload.s}, event :${payload.t}")

        payload.s?.let {
            sequence = it
        }
        when (payload.op) {
            OpCode.DISPATCH -> payload.handleDispatch()
            OpCode.HEARTBEAT -> sendHeartBeat()
            OpCode.RECONNECT -> reconnectWebSocket()
            OpCode.INVALID_SESSION -> handleInvalidSession()
            OpCode.HELLO -> payload.handleHello()
            else -> {}
        }
    }

    open fun Payload.handleDispatch() {
        try {
            when (this.t.toString()) {
                "READY" -> {
                    val d = this.d ?: return
                    val ready = json.decodeFromJsonElement<Ready>(d)
                    sessionId = ready.sessionId
                    resumeGatewayUrl = ready.resumeGatewayUrl + "/?v=10&encoding=json"
                    Logger.i(TAG, "resume_gateway_url updated to $resumeGatewayUrl")
                    Logger.i(TAG, "session_id updated to $sessionId")
                    connected = true
                    return
                }

                "RESUMED" -> {
                    Logger.i(TAG, "Session Resumed")
                }

                else -> {}
            }
        } catch (e: Exception) {
            Logger.e(TAG, "Handle dispatch", e)
        }
    }

    private suspend inline fun handleInvalidSession() {
        Logger.i(TAG,"Handling Invalid Session")
        Logger.d(TAG,"Sending Identify after 150ms")
        delay(150)
        sendIdentify()
    }

    private suspend inline fun Payload.handleHello() {
        if (sequence > 0 && !sessionId.isNullOrBlank()) {
            sendResume()
        } else {
            sendIdentify()
        }
        val d = this.d ?: return
        heartbeatInterval = json.decodeFromJsonElement<Heartbeat>(d).heartbeatInterval
        Logger.i(TAG,"Setting heartbeatInterval= $heartbeatInterval")
        startHeartbeatJob(heartbeatInterval)
    }

    private suspend fun sendHeartBeat() {
        Logger.i(TAG,"Sending ${OpCode.HEARTBEAT} with seq: $sequence")
        send(
            op = OpCode.HEARTBEAT,
            d = if (sequence == 0) "null" else sequence.toString(),
        )
    }

    private suspend inline fun reconnectWebSocket() {
        websocket?.close(
            CloseReason(
                code = 4000,
                message = "Attempting to reconnect"
            )
        )
    }

    private suspend fun sendIdentify() {
        Logger.i(TAG,"Sending ${OpCode.IDENTIFY}")
        send(
            op = OpCode.IDENTIFY,
            d = token.toIdentifyPayload()
        )
    }

    private suspend fun sendResume() {
        Logger.i(TAG,"Sending ${OpCode.RESUME}")
        send(
            op = OpCode.RESUME,
            d = Resume(
                seq = sequence,
                sessionId = sessionId,
                token = token
            )
        )
    }

    private fun startHeartbeatJob(interval: Long) {
        heartbeatJob?.cancel()
        heartbeatJob = launch {
            while (isActive) {
                sendHeartBeat()
                delay(interval)
            }
        }
    }

    private fun isSocketConnectedToAccount(): Boolean {
        return connected && websocket?.isActive == true
    }

    @OptIn(ExperimentalCoroutinesApi::class, DelicateCoroutinesApi::class)
    fun isWebSocketConnected(): Boolean {
        return websocket?.incoming != null && websocket?.outgoing?.isClosedForSend == false
    }

    private suspend inline fun <reified T> send(op: OpCode, d: T?) {
        if (websocket?.isActive == true) {
            val payload = json.encodeToString(
                Payload(
                    op = op,
                    d= json.encodeToJsonElement<T?>(d),
                )
            )
            websocket?.send(Frame.Text(payload))
        }
    }

    fun close() {
        heartbeatJob?.cancel()
        heartbeatJob = null
        this.cancel()
        resumeGatewayUrl = null
        sessionId = null
        connected = false
        runBlocking {
            websocket?.close()
            Logger.e(TAG,"Connection to gateway closed")
        }
    }

    suspend fun sendActivity(presence: Presence) {
        while (!isSocketConnectedToAccount()){
            delay(10.milliseconds)
        }
        Logger.i(TAG,"Sending ${OpCode.PRESENCE_UPDATE}")
        send(
            op = OpCode.PRESENCE_UPDATE,
            d = presence
        )
    }
}

package hal.health.check.v1.app.processors

import com.rabbitmq.client.*
import hal.health.check.v1.app.config.EventConfig
import hal.health.check.v1.app.config.QueueConfig
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.http4k.client.JavaHttpClient
import org.http4k.core.Request
import org.http4k.core.Method
import org.http4k.core.Status
import org.http4k.core.HttpHandler
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.format.DateTimeFormatter
import kotlin.collections.MutableMap

@Serializable
data class HealthCheckRequestPayload(
    val urls: List<String>,
)
@Serializable
data class HealthCheckResponsePayload(
    val responses: List<HealthCheckResultPayload>
)
@Serializable
data class HealthCheckResultPayload(
    val url: String,
    val status: String,
    val date: String,
)


class HealthCheckProcessor {
    fun process(channel: Channel, message: String) = runBlocking {
        val responseList = mutableListOf<HealthCheckResultPayload>()

        sendRequests(message, responseList)

        var success = true
        for (payload in responseList) {
            if (payload.status != Status.OK.code.toString()) {
                success = false
            }
        }
        println("Success: $success")
        val messagePayload = Json.encodeToString(HealthCheckResponsePayload(responseList))
        println(messagePayload)
        val headers: MutableMap<String, Any> = HashMap()
        headers["eventType"] = if (success) { EventConfig.healthCheck.success } else { EventConfig.healthCheck.error }
        val headerString = AMQP.BasicProperties.Builder()
            .headers(headers)
            .build()
        channel.basicPublish(QueueConfig.publishExchange, "", headerString, messagePayload.toByteArray(StandardCharsets.UTF_8))
    }

    private suspend fun sendRequests(message: String, responseList: MutableList<HealthCheckResultPayload>) = coroutineScope {
        val payload: HealthCheckRequestPayload = Json.decodeFromString(message)
        val requestClient: HttpHandler = JavaHttpClient()

        for (url in payload.urls) {
            launch {
                delay(1000L) // non-blocking delay for 1 second (default time unit is ms)
                val request = Request(Method.GET, url)
                    .header("Content-Type", "application/json")
                val response = requestClient(request)
                val responseDate = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
                val status = response.status.code.toString()
                val responseData = HealthCheckResultPayload(
                    url,
                    status,
                    responseDate
                )
                println("Checking ${url}")
                responseList.add(responseData)
            }
        }
    }
}
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
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.route53.Route53Client
import software.amazon.awssdk.services.route53.model.Change
import software.amazon.awssdk.services.route53.model.ChangeAction
import software.amazon.awssdk.services.route53.model.ChangeBatch
import software.amazon.awssdk.services.route53.model.ChangeResourceRecordSetsRequest
import software.amazon.awssdk.services.route53.model.RRType
import software.amazon.awssdk.services.route53.model.ResourceRecord
import software.amazon.awssdk.services.route53.model.ResourceRecordSet
import java.net.Inet4Address
import java.net.InetAddress
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.collections.MutableMap

@Serializable
data class HealthCheckRequestPayload(
    val urls: List<HealthCheckUrlRow>,
)

@Serializable
data class HealthCheckUrlRow(
    val url: String,
    val domainCheck: Boolean = false,
    val hostedZoneId: String? = null,
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
    fun process(channel: Channel, message: String, headers: Map<String, Any>) = runBlocking {
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
        val newHeaders: MutableMap<String, Any> = HashMap()
        newHeaders["event"] = if (success) {
            EventConfig.healthCheck.success
        } else {
            EventConfig.healthCheck.error
        }
        println(newHeaders["event"])
        newHeaders["trace-id"] = headers["trace-id"].toString()
        newHeaders["date"] = OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).toString()
        val headerString = AMQP.BasicProperties.Builder()
            .headers(newHeaders)
            .build()
        channel.basicPublish(
            QueueConfig.publishExchange,
            "",
            headerString,
            messagePayload.toByteArray(StandardCharsets.UTF_8)
        )
    }

    private suspend fun sendRequests(message: String, responseList: MutableList<HealthCheckResultPayload>) = coroutineScope {
        val payload: HealthCheckRequestPayload = Json.decodeFromString(message)
        val requestClient: HttpHandler = JavaHttpClient()

        for (urlObj in payload.urls) {
            launch {
                delay(1000L) // non-blocking delay for 1 second (default time unit is ms)
                val request = Request(Method.GET, urlObj.url)
                    .header("Content-Type", "application/json")
                val response = requestClient(request)
                val responseDate = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
                val status = response.status.code.toString()
                val responseData = HealthCheckResultPayload(
                    urlObj.url,
                    status,
                    responseDate
                )
                println("Checking ${urlObj.url}")
                responseList.add(responseData)
                if (urlObj.domainCheck) {
                    if (urlObj.hostedZoneId == null) {
                        throw Exception("No Hosted Zone found" +
                                "")
                    }
                    val uri = URI.create(urlObj.url)

                    checkDomainResolution(uri, urlObj.hostedZoneId)
                }
            }
        }
    }

    private fun checkDomainResolution(uri: URI, hostedZoneId: String) {
        try {
            val addresses = InetAddress.getAllByName(uri.host)
            val ipv4 = addresses.firstOrNull { it is Inet4Address }
            if (ipv4 != null) {
                println("IPv4 Address: ${ipv4.hostAddress}")
                updateDomainRecords(uri, hostedZoneId)
            } else {
                println("No IPv4 address found for ${uri.host}")
            }
        } catch (e: Exception) {
            println("Error resolving domain: ${e.message}")
        }
    }

    private fun updateDomainRecords(uri: URI, hostedZoneId: String) {
        val publicIp = this.getPublicIp()
        if (publicIp == null) {
            println("Could not fetch public IP, skipping checks.")
            return
        }
        val currentIp = this.getDomainIPv4(uri)
        println("Current IP address of URL ${uri}: ${currentIp}")
        println("Current Public IP of server: ${publicIp}")
        if (currentIp != publicIp) {
            updateRoute53(hostedZoneId, "$uri.", publicIp)
        } else {
            println("Current IP is up to date for $uri.")
        }
    }

    private fun getPublicIp(): String? {
        return try {
            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("https://checkip.amazonaws.com"))
                .GET()
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            response.body().trim()
        } catch (e: Exception) {
            println("Error fetching public IP: ${e.message}")
            null
        }
    }

    private fun getDomainIPv4(uri: URI): String? =
        try {
            InetAddress.getAllByName(uri.host)
                .firstOrNull { it is Inet4Address }
                ?.hostAddress
        } catch (e: Exception) { null }

    private fun updateRoute53(hostedZoneId: String, domain: String, ip: String) {
        val client = Route53Client.builder()
            .region(Region.AWS_GLOBAL)
            .build()

        try {
            val recordSet = ResourceRecordSet.builder()
                .name(domain)
                .type(RRType.A)
                .ttl(300)
                .resourceRecords(ResourceRecord.builder().value(ip).build())
                .build()

            val change = Change.builder()
                .action(ChangeAction.UPSERT)
                .resourceRecordSet(recordSet)
                .build()

            val request = ChangeResourceRecordSetsRequest.builder()
                .hostedZoneId(hostedZoneId)
                .changeBatch(ChangeBatch.builder().changes(change).build())
                .build()

            val response = client.changeResourceRecordSets(request)
            println("Route 53 update submitted for $domain → ${response.changeInfo().id()}")
        } finally {
            client.close()
        }
    }


}
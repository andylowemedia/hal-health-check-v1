package hal.health.check.v1.app.functional

import hal.health.check.v1.app.config.QueueConfig as QueueConfigProd
import hal.health.check.v1.app.config.EventConfig as EventConfigProd
import com.rabbitmq.client.AMQP
import com.rabbitmq.client.BuiltinExchangeType
import com.rabbitmq.client.CancelCallback
import com.rabbitmq.client.Channel
import com.rabbitmq.client.Connection
import com.rabbitmq.client.ConnectionFactory
import com.rabbitmq.client.DeliverCallback
import com.rabbitmq.client.Delivery
import hal.health.check.v1.app.config.EventConfig
import hal.health.check.v1.app.functional.config.QueueConfig
import hal.health.check.v1.app.processors.HealthCheckProcessor
import hal.health.check.v1.app.processors.HealthCheckRequestPayload
import hal.health.check.v1.app.processors.HealthCheckResponsePayload
import hal.health.check.v1.app.processors.HealthCheckUrlRow
import hal.health.check.v1.app.queue.exceptionHandler
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import java.lang.Thread.sleep
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.test.BeforeTest
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WorkerTest {
    private var channel: Channel? = null

    @BeforeTest
    fun loadEnvironment() {
        println("Loading environment")
        this.channel = buildChannel()
    }

    @AfterTest
    fun tearDownTestEnvironment() {
        println("Tearing down Indivdual test Environment")
        this.channel?.close()
        this.channel = null
    }

    @Test
    fun sendMessageAndSuccessMessageIsSentIntoEventExchange() {
        println("Running tests")
        if (this.channel == null) {
            throw Exception("Channel connection should be set and active")
        }

        val payload = HealthCheckRequestPayload(
            urls = listOf(
                HealthCheckUrlRow("http://test-site", false)
            )
        )

        val messagePayload = Json.encodeToString(payload)

        val headers: MutableMap<String, Any> = HashMap()
        headers["event"] = EventConfigProd.healthCheck.started
        headers["date"] = OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).toString()
        headers["trace-id"] = UUID.randomUUID().toString()
        println("Trace ID: ${headers["trace-id"]}")
        val headerString = AMQP.BasicProperties.Builder()
            .headers(headers)
            .build()

        this.channel!!.basicPublish("",QueueConfigProd.readingQueue,headerString, messagePayload.toByteArray(StandardCharsets.UTF_8))

        sleep(5000)

        println("Publishing message ${QueueConfig.redirectQueue}")
        println("message processed ${QueueConfig.redirectQueue}")
        val delivery = this.channel!!.basicGet(QueueConfig.redirectQueue, true)
        val message = String(delivery.body, java.nio.charset.StandardCharsets.UTF_8)

        val messageHeaders: Map<String, Any> = delivery.props.headers as Map<String, Any>
        println(messageHeaders.entries)
        assertTrue(messageHeaders.containsKey("event"))
        assertEquals(EventConfigProd.healthCheck.success, messageHeaders.get("event").toString())
        assertTrue(messageHeaders.containsKey("date"))
        assertTrue(messageHeaders.containsKey("trace-id"))
        assertEquals(headers.get("trace-id").toString(), messageHeaders.get("trace-id").toString())

        println("Synchronous check: ${message}")
        val responsePayload: HealthCheckResponsePayload = Json.decodeFromString(message)
        assertEquals(1,responsePayload.responses.size)
        for (response in responsePayload.responses) {
            assertEquals("http://test-site", response.url)
            assertEquals("200", response.status)
            assertIs<String>(response.date)
        }
    }


    @Test
    fun sendMessageAndErrorMessageIsSentIntoEventExchange() {
        println("Running tests")
//        this.channel!!.basicConsume(QueueConfig.redirectQueue, false, this.loopingErrorMessages(), this.cancelCallback())
        if (this.channel == null) {
            throw Exception("Channel connection should be set and active")
        }

        val payload = HealthCheckRequestPayload(
            urls = listOf(
                HealthCheckUrlRow("http://test-site1", false),
            )
        )

        val messagePayload = Json.encodeToString(payload)

        val headers: MutableMap<String, Any> = HashMap()
        headers["event"] = EventConfigProd.healthCheck.started
        headers["date"] = OffsetDateTime.of(LocalDateTime.now(), ZoneOffset.UTC).toString()
        headers["trace-id"] = UUID.randomUUID().toString()
        val headerString = AMQP.BasicProperties.Builder()
            .headers(headers)
            .build()

        this.channel!!.basicPublish("",QueueConfigProd.readingQueue,headerString, messagePayload.toByteArray(StandardCharsets.UTF_8))
        sleep(5000)


        println("Publishing message ${QueueConfig.redirectQueue}")
        println("message processed ${QueueConfig.redirectQueue}")
        val delivery = this.channel!!.basicGet(QueueConfig.redirectQueue, true)
        val message = String(delivery.body, java.nio.charset.StandardCharsets.UTF_8)

        val messageHeaders: Map<String, Any> = delivery.props.headers as Map<String, Any>
        println("Synchronous check: ${message} headers processed ${messageHeaders}")
        assertTrue(messageHeaders.containsKey("event"))
        assertEquals(EventConfigProd.healthCheck.error, messageHeaders.get("event").toString())
        assertTrue(messageHeaders.containsKey("date"))
        assertTrue(messageHeaders.containsKey("trace-id"))
        assertEquals(headers.get("trace-id").toString(), messageHeaders.get("trace-id").toString())

        val responsePayload: HealthCheckResponsePayload = Json.decodeFromString(message)
        assertEquals(1,responsePayload.responses.size)
        for (response in responsePayload.responses) {
            assertEquals("http://test-site1", response.url)
            assertEquals("503", response.status)
            assertIs<String>(response.date)
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun setup() {
            println("build queues and exchanges")
            val channel = buildChannel()
//            channel.queueDeclare(
//                QueueConfig.readingQueue,
//                true,   // durable
//                false,  // exclusive
//                false,  // autoDelete
//                null    // arguments
//            )
            channel.queueDeclare(
                QueueConfig.redirectQueue,
                true,   // durable
                false,  // exclusive
                false,  // autoDelete
                null    // arguments
            )

            channel.exchangeDeclare(
                QueueConfig.publishExchange,
                BuiltinExchangeType.HEADERS,
                true   // durable
            )

            // 3. Bind the queue to the exchange with headers
            val headers = HashMap<String, Any>()
            headers["x-match"] = "all" // "all" = all headers must match, "any" = any header matches

            channel.queueBind(QueueConfig.redirectQueue, QueueConfig.publishExchange, "", headers)
            channel.close()
        }

        @JvmStatic
        @AfterAll
        fun teardown() {
            println("destroy queues and exchanges")
//            val channel = buildChannel()
//            channel.queueDelete(QueueConfig.redirectQueue)
//            println("Queue '${QueueConfig.redirectQueue}' deleted.")
//
//            // 2. Delete the exchange
//            channel.exchangeDelete(QueueConfig.publishExchange)
//            println("Exchange '${QueueConfig.publishExchange}' deleted.")
        }
    }
}


fun buildChannel(): Channel {
    val factory = ConnectionFactory()

    factory.isAutomaticRecoveryEnabled = false

    factory.host = QueueConfig.queueHost
    factory.port = QueueConfig.queuePort
    factory.username = QueueConfig.queueUser
    factory.password = QueueConfig.queueUser

    if (QueueConfig.queueSsl) {
        factory.useSslProtocol()
    }

    val connection: Connection = factory.newConnection()
    return connection.createChannel()
}
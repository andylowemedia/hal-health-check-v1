package hal.health.check.v1.app.queue

import com.rabbitmq.client.*
import com.rabbitmq.client.impl.DefaultExceptionHandler
import hal.health.check.v1.app.processors.HealthCheckProcessor


val exceptionHandler: ExceptionHandler = object : DefaultExceptionHandler() {
    override fun handleConsumerException(
        channel: Channel,
        exception: Throwable?,
        consumer: Consumer?,
        consumerTag: String?,
        methodName: String?
    ) {
        if (exception is Throwable) {
            println(exception.message)
            channel.close()
            java.lang.System.exit(0)
        }
    }
}

class RabbitMqQueue (
    private val host: String,
    private val port: Int,
    private val user: String,
    private val pass: String,
    private val ssl: Boolean,
    private val queueName: String
) {
    fun process(processor: HealthCheckProcessor) {
        java.time.format.DateTimeFormatter.ISO_INSTANT.format(java.time.Instant.now())
        val factory = ConnectionFactory()

        factory.isAutomaticRecoveryEnabled = false
        factory.setExceptionHandler(exceptionHandler)

        val queueName = this.queueName
        factory.host = this.host
        factory.port = this.port
        factory.username = this.user
        factory.password = this.pass

        if (this.ssl) {
            factory.useSslProtocol()
        }

        val connection: Connection = factory.newConnection()
        val channel: Channel = connection.createChannel()
        channel.queueDeclare(queueName, true, false, false, null)
        println(" [*] Waiting for messages. To exit press CTRL+C")
        println("")
        channel.basicQos(1)
        channel.basicConsume(queueName, false, this.loopingMessages(channel, processor), this.cancelCallback())
    }

    private fun loopingMessages(channel: Channel, processor: HealthCheckProcessor) : DeliverCallback {
        return DeliverCallback { _: String?, delivery: Delivery ->
            val message = String(delivery.body, java.nio.charset.StandardCharsets.UTF_8)
            val headers: Map<String, Any> = delivery.properties.headers as Map<String, Any>
            val startedDate = java.time.LocalDateTime.now().toString()
            println("************************************************")
            println("Event: ${headers.get("eventType")}")
            println("${startedDate} [x] Received")
            processor.process(channel, message)
            channel.basicAck(delivery.envelope.deliveryTag, false)
            val endedDate = java.time.LocalDateTime.now().toString()
            println("${endedDate} [x] Processed")
            println("************************************************")
            println("")
        }

    }

    private fun cancelCallback() : CancelCallback {
        return CancelCallback { consumerTag: String? ->
            println("[$consumerTag] was canceled")
        }
    }
}
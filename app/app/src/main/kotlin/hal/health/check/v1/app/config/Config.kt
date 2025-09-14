package hal.health.check.v1.app.config

import io.github.cdimascio.dotenv.dotenv

val dotenv = dotenv{
    ignoreIfMissing = true
}
val rabbitMqPort = dotenv["RABBITMQ_PORT"] ?: "5672";
val rabbitMqSsl = dotenv["RABBITMQ_SSL"] ?: "false";


object AppConfig {

}

object QueueConfig {
    val queueHost: String = dotenv["RABBITMQ_HOST"] ?: "rabbitmq"
    val queuePort: Int = rabbitMqPort.toInt()
    val queueUser: String = dotenv["RABBITMQ_USERNAME"] ?: "guest"
    val queuePass: String = dotenv["RABBITMQ_PASSWORD"] ?: "guest"
    val queueSsl: Boolean = (rabbitMqSsl.lowercase() == "true")
    val readingQueue: String = dotenv["RABBITMQ_QUEUE"] ?: "hal-health-check"
    val publishExchange: String = dotenv["RABBITMQ_EXCHANGE"] ?: "hal-health-check-exchange"
}

object EventConfig {
    val healthCheck = HealthCheck
}

object HealthCheck {
//    val started = "health-check:started",
    val success = "health-check:success"
    val error = "health-check:error"
}
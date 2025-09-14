package hal.health.check.v1.app

import hal.health.check.v1.app.config.QueueConfig
import hal.health.check.v1.app.processors.HealthCheckProcessor
import hal.health.check.v1.app.queue.RabbitMqQueue

fun main() {
    RabbitMqQueue(
        QueueConfig.queueHost,
        QueueConfig.queuePort,
        QueueConfig.queueUser,
        QueueConfig.queuePass,
        QueueConfig.queueSsl,
        QueueConfig.readingQueue
    ).process(HealthCheckProcessor())
}

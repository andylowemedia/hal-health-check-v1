plugins {
    // Kotlin JVM
    alias(libs.plugins.kotlin.jvm)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.0"
    application
}

repositories {
    mavenCentral()
}

dependencies {
    // http4k
    implementation(platform("org.http4k:http4k-bom:6.17.0.0"))
    implementation("org.http4k:http4k-core")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Serialization JSON
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")

    // Dotenv
    implementation("io.github.cdimascio:dotenv-kotlin:6.5.1")

    // RabbitMQ
    implementation("com.rabbitmq:amqp-client:5.26.0")

    // Guava
    implementation(libs.guava)

    // Kotlin Test / JUnit 5
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation(libs.junit.jupiter.engine)
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

application {
    mainClass.set("hal.health.check.v1.app.AppKt")
}

tasks.named<Test>("test") {
    useJUnitPlatform()
}

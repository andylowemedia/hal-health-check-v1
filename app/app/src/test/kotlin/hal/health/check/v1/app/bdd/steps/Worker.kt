package hal.health.check.v1.app.bdd.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals

class Worker {
    @Given("message has been sent to queue")
    fun message_has_been_sent_to_queue() {
        println("Soemthing")
    }

    @When("the health check has been performed")
    fun health_check_has_been_performed() {
        println("something else")
    }

    @Then("a message was sent to exhcnage and queue with results")
    fun a_message_was_sent_to_exhcnage_and_queue_with_results() {
        println("something else more")
    }
}
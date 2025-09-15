package hal.health.check.v1.app.bdd.steps

import io.cucumber.java.en.Given
import io.cucumber.java.en.Then
import io.cucumber.java.en.When
import kotlin.test.assertEquals

class Sample {
    private var message: String = ""

    @Given("I have a working Kotlin Cucumber setup")
    fun i_have_a_working_setup() {
        message = "Hello Cucumber"
    }

    @When("I run the tests")
    fun i_run_the_tests() {
        // no-op
    }

    @Then("I should see them execute")
    fun i_should_see_them_execute() {
        assertEquals("Hello Cucumber", message)
    }
}
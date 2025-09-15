package hal.health.check.v1.app.bdd

import org.junit.platform.suite.api.*

@Suite
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
class RunCucumberTest
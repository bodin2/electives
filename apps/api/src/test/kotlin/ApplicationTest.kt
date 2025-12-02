package th.ac.bodin2.electives

import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.server.testing.*
import th.ac.bodin2.electives.api.module
import kotlin.test.Test
import kotlin.test.assertEquals

class ApplicationTest {

    @Test
    fun testRoot() = testApplication {
        application {
            module()
        }

        client.head("/status").apply {
            assertEquals(HttpStatusCode.OK, status)
        }
    }

}

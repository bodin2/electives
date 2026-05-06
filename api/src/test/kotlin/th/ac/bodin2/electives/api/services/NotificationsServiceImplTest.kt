package th.ac.bodin2.electives.api.services

import io.ktor.client.plugins.websocket.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.jetbrains.exposed.v1.jdbc.update
import th.ac.bodin2.electives.api.ApplicationTest
import th.ac.bodin2.electives.api.SessionUserMocks.janeSessionUser
import th.ac.bodin2.electives.api.SessionUserMocks.johnSessionUser
import th.ac.bodin2.electives.api.TestConstants.Electives
import th.ac.bodin2.electives.api.TestConstants.Students
import th.ac.bodin2.electives.api.TestConstants.Subjects
import th.ac.bodin2.electives.api.TestConstants.Teams
import th.ac.bodin2.electives.api.TestConstants.TestData.CLIENT_NAME
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.utils.send
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.models.StudentTeams
import th.ac.bodin2.electives.proto.api.NotificationsService
import java.time.LocalDateTime
import kotlin.test.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class, Transactional::class)
class NotificationsServiceImplTest : ApplicationTest() {
    private val ApplicationTestBuilder.usersService: UsersService
        get() {
            val service: UsersService by application.dependencies
            return service
        }

    private val ApplicationTestBuilder.electiveSelectionService: ElectiveSelectionService
        get() {
            val service: ElectiveSelectionService by application.dependencies
            return service
        }

    private val ApplicationTestBuilder.notificationsService: NotificationsServiceImpl
        get() {
            val service: th.ac.bodin2.electives.api.services.NotificationsService by application.dependencies
            return service as NotificationsServiceImpl
        }

    val serviceConfig = NotificationsServiceImpl.Config(
        maxSubjectSubscriptionsPerClient = 5,
        bulkUpdateInterval = 500.milliseconds,
        bulkUpdatesEnabled = false
    )

    private suspend fun ApplicationTestBuilder.studentToken(): String {
        startApplication()
        return usersService.createSession(
            Students.JOHN_ID,
            Students.JOHN_PASSWORD,
            CLIENT_NAME
        )
    }

    private suspend fun ApplicationTestBuilder.webSocket(
        token: String,
        block: suspend DefaultClientWebSocketSession.() -> Unit
    ) {
        createWSClient().webSocket("/notifications") {
            send(
                NotificationsService.Envelope(
                    identify = NotificationsService.Identify(token = token)
                )
            )

            block()
        }
    }

    private suspend fun ApplicationTestBuilder.webSocket(block: suspend DefaultClientWebSocketSession.() -> Unit) {
        webSocket(
            token = studentToken(),
            block = block
        )
    }

    override fun runTest(block: suspend ApplicationTestBuilder.() -> Unit) {
        super.runTest(
            {
                application {
                    dependencies.provide<th.ac.bodin2.electives.api.services.NotificationsService> {
                        NotificationsServiceImpl(serviceConfig, resolve<UsersService>())
                    }
                }
            },
            block
        )
    }

    @Test
    fun `websocket connect and disconnect`() = runTest {
        webSocket {
            close()
        }
    }

    @Test
    fun `websocket subscription request acknowledged`() = runTest {
        webSocket {
            val envelope = NotificationsService.Envelope(
                message_id = 1,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Electives.SCIENCE_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = listOf(Subjects.PHYSICS_ID.toInt())
                                    )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = NotificationsService.Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(1, ackEnvelope.message_id, "Expected message ID to match")
            assertTrue(ackEnvelope.acknowledged != null, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription to multiple subjects`() = runTest {
        webSocket {
            val envelope = NotificationsService.Envelope(
                message_id = 2,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Electives.SCIENCE_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = listOf(
                                            Subjects.PHYSICS_ID.toInt(),
                                            Subjects.CHEMISTRY_ID.toInt()
                                        )
                                    )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = NotificationsService.Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(2, ackEnvelope.message_id, "Expected message ID to match")
            assertTrue(ackEnvelope.acknowledged != null, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription exceeds max subjects limit`() = runTest {
        webSocket {
            val envelope = NotificationsService.Envelope(
                message_id = 3,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Electives.SCIENCE_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = (0 until 10).toList()
                                    )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))
            assertFailsWith<ClosedReceiveChannelException>("Expected connection to be closed by server") {
                incoming.receive()
            }
        }
    }

    @Test
    fun `websocket subscription with empty subject list`() = runTest {
        webSocket {
            val envelope = NotificationsService.Envelope(
                message_id = 4,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Electives.SCIENCE_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription()
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = NotificationsService.Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(4, ackEnvelope.message_id, "Expected message ID to match")
            assertTrue(ackEnvelope.acknowledged != null, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket send text frame returns bad frame`() = runTest {
        webSocket {
            outgoing.send(Frame.Text("invalid data"))
            assertFailsWith<ClosedReceiveChannelException>("Expected connection to be closed by server") {
                incoming.receive()
            }
        }
    }

    @Test
    fun `websocket send invalid binary data`() = runTest {
        webSocket {
            outgoing.send(Frame.Binary(true, byteArrayOf(1, 2, 3)))
            assertFailsWith<ClosedReceiveChannelException>("Expected connection to be closed by server") {
                incoming.receive()
            }
        }
    }

    @Test
    fun `websocket send server-only payload`() = runTest {
        webSocket {
            val envelope = NotificationsService.Envelope(
                subject_enrollment_update = NotificationsService.SubjectEnrollmentUpdate(
                    elective_id = Electives.SCIENCE_ID.toInt(),
                    subject_id = Subjects.PHYSICS_ID.toInt(),
                    enrolled_count = 10
                )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))
            assertFailsWith<ClosedReceiveChannelException>("Expected connection to be closed by server") {
                incoming.receive()
            }
        }
    }

    @Test
    fun `websocket subscription to nonexistent elective`() = runTest {
        webSocket {
            val envelope = NotificationsService.Envelope(
                message_id = 5,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            UNUSED_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = listOf(Subjects.PHYSICS_ID.toInt())
                                    )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = NotificationsService.Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(5, ackEnvelope.message_id, "Expected message ID to match")
            assertTrue(ackEnvelope.acknowledged != null, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription with negative ids`() = runTest {
        webSocket {
            val envelope = NotificationsService.Envelope(
                message_id = 6,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            -1 to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = listOf(-1)
                                    )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(10.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = NotificationsService.Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(6, ackEnvelope.message_id, "Expected message ID to match")
            assertTrue(ackEnvelope.acknowledged != null, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket multiple subscription requests`() = runTest {
        webSocket {
            repeat(3) { i ->
                val envelope = NotificationsService.Envelope(
                    message_id = (i + 1).toLong(),
                    subject_enrollment_update_subscription_request =
                        NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                            subscriptions = mapOf(
                                Electives.SCIENCE_ID.toInt() to
                                        NotificationsService.SubjectEnrollmentUpdateSubscription(
                                            subject_ids = listOf(Subjects.PHYSICS_ID.toInt())
                                        )
                            )
                        )
                )

                outgoing.send(Frame.Binary(true, envelope.encode()))

                val response = withTimeout(5.seconds) {
                    incoming.receive() as Frame.Binary
                }

                val ackEnvelope = NotificationsService.Envelope.ADAPTER.decode(response.readBytes())
                assertEquals((i + 1).toLong(), ackEnvelope.message_id, "Expected message ID to match")
                assertTrue(ackEnvelope.acknowledged != null, "Expected acknowledged response")
            }

            close()
        }
    }

    @Test
    fun `websocket subscription without message id`() = runTest {
        webSocket {
            val envelope = NotificationsService.Envelope(
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Electives.SCIENCE_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = listOf(Subjects.PHYSICS_ID.toInt())
                                    )
                        )
                    )
            )

            send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = NotificationsService.Envelope.ADAPTER.decode(response.readBytes())
            assertTrue(ackEnvelope.acknowledged != null, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription at exact max limit`() = runTest {
        webSocket {
            val envelope = NotificationsService.Envelope(
                message_id = 7,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Electives.SCIENCE_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = (0 until 5).toList()
                                    )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = NotificationsService.Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(7, ackEnvelope.message_id, "Expected message ID to match")
            assertTrue(ackEnvelope.acknowledged != null, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket multiple electives within limit`() = runTest {
        webSocket {
            val envelope = NotificationsService.Envelope(
                message_id = 8,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            1 to NotificationsService.SubjectEnrollmentUpdateSubscription(
                                subject_ids = listOf(101, 102)
                            ),
                            2 to NotificationsService.SubjectEnrollmentUpdateSubscription(
                                subject_ids = listOf(201, 202, 203)
                            )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = NotificationsService.Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(8, ackEnvelope.message_id, "Expected message ID to match")
            assertTrue(ackEnvelope.acknowledged != null, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket receives bulk enrollment updates for all electives`() = runTest {
        webSocket {
            serviceConfig.bulkUpdatesEnabled = true

            val electives = transaction { Elective.getAllActiveIds() }
            assertTrue(electives.isNotEmpty(), "Test requires at least one elective")

            val received = mutableMapOf<UInt, NotificationsService.Envelope>()

            // Trigger a selection
            electiveSelectionService.setStudentSelection(
                johnSessionUser,
                Students.JOHN_ID,
                Electives.SCIENCE_ID,
                Subjects.PHYSICS_ID,
            )

            withTimeout(15.seconds) {
                while (received.size < electives.size || received.values.all {
                        it.bulk_subject_enrollment_update!!.subject_enrolled_counts.values.all { count -> count == 0 }
                    }) {
                    val frame = incoming.receive() as Frame.Binary
                    val envelope = NotificationsService.Envelope.ADAPTER.decode(frame.readBytes())

                    envelope.bulk_subject_enrollment_update?.let {
                        received[it.elective_id.toUInt()] = envelope
                    }
                }
            }

            // Received exactly one bulk update per elective (eventually with data)
            assertEquals(
                electives.toSet(),
                received.keys,
                "Did not receive bulk updates for all electives"
            )

            // Is the payload correct?
            val scienceUpdate = received[Electives.SCIENCE_ID]!!.bulk_subject_enrollment_update!!
            assertEquals(
                1,
                scienceUpdate.subject_enrolled_counts[Subjects.PHYSICS_ID.toInt()],
                "Expected 1 student in Physics"
            )

            serviceConfig.bulkUpdatesEnabled = false

            close()
        }
    }

    @Test
    fun `bulk update flow is removed when elective becomes inactive`() = runTest {
        webSocket {
            serviceConfig.bulkUpdatesEnabled = true

            val electives = transaction { Elective.getAllActiveIds() }
            assertTrue(electives.contains(Electives.SCIENCE_ID), "Science elective should be active")

            // Wait for at least one bulk update to arrive so the flow is created
            withTimeout(15.seconds) {
                while (true) {
                    val frame = incoming.receive() as Frame.Binary
                    val envelope = NotificationsService.Envelope.ADAPTER.decode(frame.readBytes())
                    if (envelope.bulk_subject_enrollment_update?.elective_id == Electives.SCIENCE_ID.toInt()) break
                }
            }

            assertTrue(
                notificationsService.bulkUpdateFlows.value.containsKey(Electives.SCIENCE_ID),
                "Expected bulk update flow to exist for science elective"
            )

            // Make the elective inactive by setting its end date to the past
            transaction {
                th.ac.bodin2.electives.db.models.Electives.update(
                    { th.ac.bodin2.electives.db.models.Electives.id eq Electives.SCIENCE_ID }
                ) {
                    it[endDate] = LocalDateTime.now().minusDays(1)
                }
            }

            // Wait for the bulk update loop to clear the inactive elective's flow
            withTimeout(15.seconds) {
                while (notificationsService.bulkUpdateFlows.value.containsKey(Electives.SCIENCE_ID)) {
                    delay(100.milliseconds)
                }
            }

            assertFalse(
                notificationsService.bulkUpdateFlows.value.containsKey(Electives.SCIENCE_ID),
                "Expected bulk update flow to be removed for inactive elective"
            )

            serviceConfig.bulkUpdatesEnabled = false

            close()
        }
    }

    @Test
    fun `websocket disconnects if a new instance connects`() = runTest {
        webSocket {
            webSocket {}

            assertFailsWith<ClosedReceiveChannelException>("Expected first connection to be closed") {
                incoming.receive()
            }
        }
    }

    @Test
    fun `websocket client subscribes and receives subject enrollment update`() = runTest {
        webSocket {
            // Subscribe to physics subject
            val subscriptionEnvelope = NotificationsService.Envelope(
                message_id = 100,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Electives.SCIENCE_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = listOf(Subjects.PHYSICS_ID.toInt())
                                    )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, subscriptionEnvelope.encode()))

            // Wait for acknowledgement
            val ackResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val ackEnvelope = NotificationsService.Envelope.ADAPTER.decode(ackResponse.readBytes())
            assertTrue(ackEnvelope.acknowledged != null, "Expected acknowledged response")

            // Trigger a selection that affects the subscribed subject
            transaction {
                // Add Jane to Team 1 so they can enroll in Physics
                StudentTeams.insert {
                    it[student] = Students.JANE_ID
                    it[team] = Teams.TEAM_1_ID
                }
            }

            assertIs<ElectiveSelectionService.ModifySelectionResult.Success>(
                electiveSelectionService.setStudentSelection(
                    janeSessionUser,
                    Students.JANE_ID,
                    Electives.SCIENCE_ID,
                    Subjects.PHYSICS_ID
                )
            )

            // Should receive an update notification
            val updateResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val updateEnvelope = NotificationsService.Envelope.ADAPTER.decode(updateResponse.readBytes())
            assertTrue(
                updateEnvelope.subject_enrollment_update != null,
                "Expected subjectEnrollmentUpdate notification"
            )

            val update = updateEnvelope.subject_enrollment_update!!
            assertEquals(Electives.SCIENCE_ID.toInt(), update.elective_id)
            assertEquals(Subjects.PHYSICS_ID.toInt(), update.subject_id)
            assertTrue(update.enrolled_count > 0, "Enrolled count should be greater than 0")

            close()
        }
    }

    @Test
    fun `websocket client subscribes to multiple subjects and receives updates`() = runTest {
        webSocket {
            // Subscribe to both physics and chemistry
            val subscriptionEnvelope = NotificationsService.Envelope(
                message_id = 101,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Electives.SCIENCE_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = listOf(
                                            Subjects.PHYSICS_ID.toInt(),
                                            Subjects.CHEMISTRY_ID.toInt()
                                        )
                                    )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, subscriptionEnvelope.encode()))

            // Wait for acknowledgement
            val ackResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val ackEnvelope = NotificationsService.Envelope.ADAPTER.decode(ackResponse.readBytes())
            assertTrue(ackEnvelope.acknowledged != null, "Expected acknowledged response")

            // Enroll John in Physics
            assertIs<ElectiveSelectionService.ModifySelectionResult.Success>(
                electiveSelectionService.setStudentSelection(
                    johnSessionUser,
                    Students.JOHN_ID,
                    Electives.SCIENCE_ID,
                    Subjects.PHYSICS_ID
                )
            )

            // Should receive update for Physics
            val physicsUpdateResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val physicsUpdateEnvelope = NotificationsService.Envelope.ADAPTER.decode(physicsUpdateResponse.readBytes())
            assertTrue(physicsUpdateEnvelope.subject_enrollment_update != null)
            assertEquals(Subjects.PHYSICS_ID.toInt(), physicsUpdateEnvelope.subject_enrollment_update!!.subject_id)

            close()
        }
    }

    @Test
    fun `websocket client updates subscription and receives new updates`() = runTest {
        webSocket {
            // Initial subscription to physics only
            val initialSubscription = NotificationsService.Envelope(
                message_id = 102,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Electives.SCIENCE_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = listOf(Subjects.PHYSICS_ID.toInt())
                                    )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, initialSubscription.encode()))

            val ack1 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertTrue(NotificationsService.Envelope.ADAPTER.decode(ack1.readBytes()).acknowledged != null)

            // Update subscription to chemistry only
            val updatedSubscription = NotificationsService.Envelope(
                message_id = 103,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Electives.SCIENCE_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = listOf(Subjects.CHEMISTRY_ID.toInt())
                                    )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, updatedSubscription.encode()))

            val ack2 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertTrue(NotificationsService.Envelope.ADAPTER.decode(ack2.readBytes()).acknowledged != null)

            // Trigger update for chemistry
            assertIs<ElectiveSelectionService.ModifySelectionResult.Success>(
                electiveSelectionService.setStudentSelection(
                    johnSessionUser,
                    Students.JOHN_ID,
                    Electives.SCIENCE_ID,
                    Subjects.CHEMISTRY_ID,
                )
            )

            // Should receive update for chemistry
            val updateResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val updateEnvelope = NotificationsService.Envelope.ADAPTER.decode(updateResponse.readBytes())
            assertTrue(updateEnvelope.subject_enrollment_update != null)
            assertEquals(Subjects.CHEMISTRY_ID.toInt(), updateEnvelope.subject_enrollment_update!!.subject_id)

            close()
        }
    }

    @Test
    fun `websocket client unsubscribes and stops receiving updates`() = runTest {
        webSocket {
            // Subscribe to physics
            val subscriptionEnvelope = NotificationsService.Envelope(
                message_id = 104,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Electives.SCIENCE_ID.toInt() to
                                    NotificationsService.SubjectEnrollmentUpdateSubscription(
                                        subject_ids = listOf(Subjects.PHYSICS_ID.toInt())
                                    )
                        )
                    )
            )

            outgoing.send(Frame.Binary(true, subscriptionEnvelope.encode()))

            val ack1 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertTrue(NotificationsService.Envelope.ADAPTER.decode(ack1.readBytes()).acknowledged != null)

            // Unsubscribe (send empty subscription)
            val unsubscribeEnvelope = NotificationsService.Envelope(
                message_id = 105,
                subject_enrollment_update_subscription_request =
                    NotificationsService.SubjectEnrollmentUpdateSubscriptionRequest(
                        // Empty subscriptions map means unsubscribe from all
                    )
            )

            outgoing.send(Frame.Binary(true, unsubscribeEnvelope.encode()))

            val ack2 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertTrue(NotificationsService.Envelope.ADAPTER.decode(ack2.readBytes()).acknowledged != null)

            close()
        }
    }

    @Test
    fun `websocket disconnects when new user session is created (re-login)`() = runTest {
        webSocket {
            // Create a new session for the same user, which should invalidate the current session
            usersService.createSession(
                Students.JOHN_ID,
                Students.JOHN_PASSWORD,
                CLIENT_NAME
            )

            assertFailsWith<ClosedReceiveChannelException>("Expected connection to be closed by server") {
                incoming.receive()
            }
        }
    }

    @Test
    fun `admin websocket connection fails with invalid token`() = runTest {
        assertFailsWith<CancellationException> {
            try {
                createWSClient().webSocket("/notifications") {
                    send(
                        NotificationsService.Envelope(
                            identify = NotificationsService.Identify(token = "invalid-token")
                        )
                    )

                    // Connection should be closed due to authentication failure
                    incoming.receive()
                }
            } catch (e: ClosedReceiveChannelException) {
                throw CancellationException("Connection closed by server", e)
            }
        }
    }

    @Test
    fun `admin websocket connection fails without identify message`() = runTest {
        assertFailsWith<ClosedReceiveChannelException> {
            createWSClient().webSocket("/notifications") {
                withTimeout(15.seconds) {
                    incoming.receive()
                }
            }
        }
    }
}

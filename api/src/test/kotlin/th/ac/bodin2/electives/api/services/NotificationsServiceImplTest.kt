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
import th.ac.bodin2.electives.api.TestConstants.Enrollments
import th.ac.bodin2.electives.api.TestConstants.Groups
import th.ac.bodin2.electives.api.TestConstants.Students
import th.ac.bodin2.electives.api.TestConstants.Subjects
import th.ac.bodin2.electives.api.TestConstants.TestData.CLIENT_NAME
import th.ac.bodin2.electives.api.annotations.Transactional
import th.ac.bodin2.electives.api.services.mock.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.utils.send
import th.ac.bodin2.electives.db.Enrollment
import th.ac.bodin2.electives.db.models.StudentGroups
import th.ac.bodin2.electives.proto.api.NotificationsService.*
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

    private val ApplicationTestBuilder.enrollmentSelectionService: EnrollmentSelectionService
        get() {
            val service: EnrollmentSelectionService by application.dependencies
            return service
        }

    private val ApplicationTestBuilder.notificationsService: NotificationsServiceImpl
        get() {
            val service: NotificationsService by application.dependencies
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
            send(Envelope(identify = Identify(token = token)))

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
                    dependencies.provide<NotificationsService> {
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
            val envelope = Envelope(
                message_id = 1L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(Subjects.PHYSICS_ID)
                        )
                    )
                )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(1L, ackEnvelope.message_id, "Expected message ID to match")
            assertNotNull(ackEnvelope.acknowledged, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription to multiple subjects`() = runTest {
        webSocket {
            val envelope = Envelope(
                message_id = 2L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(Subjects.PHYSICS_ID, Subjects.CHEMISTRY_ID)
                        )
                    )
                )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(2L, ackEnvelope.message_id, "Expected message ID to match")
            assertNotNull(ackEnvelope.acknowledged, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription exceeds max subjects limit`() = runTest {
        webSocket {
            val envelope = Envelope(
                message_id = 3L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription(
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
            val envelope = Envelope(
                message_id = 4L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription()
                    )
                )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(4L, ackEnvelope.message_id, "Expected message ID to match")
            assertNotNull(ackEnvelope.acknowledged, "Expected acknowledged response")

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
            val envelope = Envelope(
                subject_enrollment_update = SubjectEnrollmentUpdate(
                    enrollment_id = Enrollments.SCIENCE_ID,
                    subject_id = Subjects.PHYSICS_ID,
                    enrolled_count = 10,
                )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))
            assertFailsWith<ClosedReceiveChannelException>("Expected connection to be closed by server") {
                incoming.receive()
            }
        }
    }

    @Test
    fun `websocket subscription to nonexistent enrollment`() = runTest {
        webSocket {
            val envelope = Envelope(
                message_id = 5L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        UNUSED_ID to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(Subjects.PHYSICS_ID)
                        )
                    )
                )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(5L, ackEnvelope.message_id, "Expected message ID to match")
            assertNotNull(ackEnvelope.acknowledged, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription with negative ids`() = runTest {
        webSocket {
            val envelope = Envelope(
                message_id = 6L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        -1 to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(-1)
                        )
                    )
                )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(10.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(6L, ackEnvelope.message_id, "Expected message ID to match")
            assertNotNull(ackEnvelope.acknowledged, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket multiple subscription requests`() = runTest {
        webSocket {
            repeat(3) { i ->
                val envelope = Envelope(
                    message_id = (i + 1).toLong(),
                    subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                        subscriptions = mapOf(
                            Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription(
                                subject_ids = listOf(Subjects.PHYSICS_ID)
                            )
                        )
                    )
                )

                outgoing.send(Frame.Binary(true, envelope.encode()))

                val response = withTimeout(5.seconds) {
                    incoming.receive() as Frame.Binary
                }

                val ackEnvelope = Envelope.ADAPTER.decode(response.readBytes())
                assertEquals((i + 1).toLong(), ackEnvelope.message_id, "Expected message ID to match")
                assertNotNull(ackEnvelope.acknowledged, "Expected acknowledged response")
            }

            close()
        }
    }

    @Test
    fun `websocket subscription without message id`() = runTest {
        webSocket {
            val envelope = Envelope(
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(Subjects.PHYSICS_ID)
                        )
                    )
                )
            )

            send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.ADAPTER.decode(response.readBytes())
            assertNotNull(ackEnvelope.acknowledged, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription at exact max limit`() = runTest {
        webSocket {
            val envelope = Envelope(
                message_id = 7L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription(
                            subject_ids = (0 until 5).toList()
                        )
                    )
                )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(7L, ackEnvelope.message_id, "Expected message ID to match")
            assertNotNull(ackEnvelope.acknowledged, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket multiple electives within limit`() = runTest {
        webSocket {
            val envelope = Envelope(
                message_id = 8L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        1 to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(101, 102)
                        ),
                        2 to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(201, 202, 203)
                        )
                    )
                )
            )

            outgoing.send(Frame.Binary(true, envelope.encode()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.ADAPTER.decode(response.readBytes())
            assertEquals(8L, ackEnvelope.message_id, "Expected message ID to match")
            assertNotNull(ackEnvelope.acknowledged, "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket receives bulk enrollment updates for all enrollments`() = runTest {
        webSocket {
            serviceConfig.bulkUpdatesEnabled = true

            val enrollments = transaction { Enrollment.getAllActiveIds() }
            assertTrue(enrollments.isNotEmpty(), "Test requires at least one enrollment")

            val received = mutableMapOf<Int, Envelope>()

            // Trigger a selection
            enrollmentSelectionService.setStudentSelection(
                johnSessionUser,
                Students.JOHN_ID,
                Enrollments.SCIENCE_ID,
                Subjects.PHYSICS_ID,
            )

            withTimeout(15.seconds) {
                while (received.size < enrollments.size || received.values.all {
                        it.bulk_subject_enrollment_update!!.subject_enrolled_counts.values.all { count -> count == 0 }
                    }) {
                    val frame = incoming.receive() as Frame.Binary
                    val envelope = Envelope.ADAPTER.decode(frame.readBytes())

                    if (envelope.bulk_subject_enrollment_update != null) {
                        val bulk = envelope.bulk_subject_enrollment_update!!
                        received[bulk.enrollment_id] = envelope
                    }
                }
            }

            // Received exactly one bulk update per enrollment (eventually with data)
            assertEquals(
                enrollments.toSet(),
                received.keys,
                "Did not receive bulk updates for all enrollments"
            )

            // Is the payload correct?
            val scienceUpdate = received[Enrollments.SCIENCE_ID]!!.bulk_subject_enrollment_update!!
            assertEquals(
                1,
                scienceUpdate.subject_enrolled_counts[Subjects.PHYSICS_ID],
                "Expected 1 student in Physics"
            )

            serviceConfig.bulkUpdatesEnabled = false

            close()
        }
    }

    @Test
    fun `bulk update flow is removed when enrollment becomes inactive`() = runTest {
        webSocket {
            serviceConfig.bulkUpdatesEnabled = true

            val enrollments = transaction { Enrollment.getAllActiveIds() }
            assertTrue(enrollments.contains(Enrollments.SCIENCE_ID), "Science enrollment should be active")

            // Wait for at least one bulk update to arrive so the flow is created
            withTimeout(15.seconds) {
                while (true) {
                    val frame = incoming.receive() as Frame.Binary
                    val envelope = Envelope.ADAPTER.decode(frame.readBytes())
                    if (envelope.bulk_subject_enrollment_update != null &&
                        envelope.bulk_subject_enrollment_update!!.enrollment_id == Enrollments.SCIENCE_ID
                    ) break
                }
            }

            assertTrue(
                notificationsService.bulkUpdateFlows.value.containsKey(Enrollments.SCIENCE_ID),
                "Expected bulk update flow to exist for science enrollment"
            )

            // Make the enrollment inactive by setting its end date to the past
            transaction {
                th.ac.bodin2.electives.db.models.Enrollments.update(
                    { th.ac.bodin2.electives.db.models.Enrollments.id eq Enrollments.SCIENCE_ID }
                ) {
                    it[endDate] = LocalDateTime.now().minusDays(1)
                }
            }

            // Wait for the bulk update loop to clear the inactive enrollment's flow
            withTimeout(15.seconds) {
                while (notificationsService.bulkUpdateFlows.value.containsKey(Enrollments.SCIENCE_ID)) {
                    delay(100.milliseconds)
                }
            }

            assertFalse(
                notificationsService.bulkUpdateFlows.value.containsKey(Enrollments.SCIENCE_ID),
                "Expected bulk update flow to be removed for inactive enrollment"
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
            val subscriptionEnvelope = Envelope(
                message_id = 100L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(Subjects.PHYSICS_ID)
                        )
                    )
                )
            )

            outgoing.send(Frame.Binary(true, subscriptionEnvelope.encode()))

            // Wait for acknowledgement
            val ackResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val ackEnvelope = Envelope.ADAPTER.decode(ackResponse.readBytes())
            assertNotNull(ackEnvelope.acknowledged, "Expected acknowledged response")

            // Trigger a selection that affects the subscribed subject
            transaction {
                // Add Jane to Group 1 so they can enroll in Physics
                StudentGroups.insert {
                    it[student] = Students.JANE_ID
                    it[group] = Groups.GROUP_1_ID
                }
            }

            assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(
                enrollmentSelectionService.setStudentSelection(
                    janeSessionUser,
                    Students.JANE_ID,
                    Enrollments.SCIENCE_ID,
                    Subjects.PHYSICS_ID
                )
            )

            // Should receive an update notification
            val updateResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val updateEnvelope = Envelope.ADAPTER.decode(updateResponse.readBytes())
            assertNotNull(
                updateEnvelope.subject_enrollment_update,
                "Expected subjectEnrollmentUpdate notification"
            )

            val update = updateEnvelope.subject_enrollment_update!!
            assertEquals(Enrollments.SCIENCE_ID, update.enrollment_id)
            assertEquals(Subjects.PHYSICS_ID, update.subject_id)
            assertTrue(update.enrolled_count > 0, "Enrolled count should be greater than 0")

            close()
        }
    }

    @Test
    fun `websocket client subscribes to multiple subjects and receives updates`() = runTest {
        webSocket {
            // Subscribe to both physics and chemistry
            val subscriptionEnvelope = Envelope(
                message_id = 101L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(Subjects.PHYSICS_ID, Subjects.CHEMISTRY_ID)
                        )
                    )
                )
            )

            outgoing.send(Frame.Binary(true, subscriptionEnvelope.encode()))

            // Wait for acknowledgement
            val ackResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val ackEnvelope = Envelope.ADAPTER.decode(ackResponse.readBytes())
            assertNotNull(ackEnvelope.acknowledged, "Expected acknowledged response")

            // Enroll John in Physics
            assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(
                enrollmentSelectionService.setStudentSelection(
                    johnSessionUser,
                    Students.JOHN_ID,
                    Enrollments.SCIENCE_ID,
                    Subjects.PHYSICS_ID
                )
            )

            // Should receive update for Physics
            val physicsUpdateResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val physicsUpdateEnvelope = Envelope.ADAPTER.decode(physicsUpdateResponse.readBytes())
            assertNotNull(physicsUpdateEnvelope.subject_enrollment_update)
            assertEquals(Subjects.PHYSICS_ID, physicsUpdateEnvelope.subject_enrollment_update!!.subject_id)

            close()
        }
    }

    @Test
    fun `websocket client updates subscription and receives new updates`() = runTest {
        webSocket {
            // Initial subscription to physics only
            val initialSubscription = Envelope(
                message_id = 102L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(Subjects.PHYSICS_ID)
                        )
                    )
                )
            )

            outgoing.send(Frame.Binary(true, initialSubscription.encode()))

            val ack1 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertNotNull(Envelope.ADAPTER.decode(ack1.readBytes()).acknowledged)

            // Update subscription to chemistry only
            val updatedSubscription = Envelope(
                message_id = 103L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(Subjects.CHEMISTRY_ID)
                        )
                    )
                )
            )

            outgoing.send(Frame.Binary(true, updatedSubscription.encode()))

            val ack2 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertNotNull(Envelope.ADAPTER.decode(ack2.readBytes()).acknowledged)

            // Trigger update for chemistry
            assertIs<EnrollmentSelectionService.ModifySelectionResult.Success>(
                enrollmentSelectionService.setStudentSelection(
                    johnSessionUser,
                    Students.JOHN_ID,
                    Enrollments.SCIENCE_ID,
                    Subjects.CHEMISTRY_ID,
                )
            )

            // Should receive update for chemistry
            val updateResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val updateEnvelope = Envelope.ADAPTER.decode(updateResponse.readBytes())
            assertNotNull(updateEnvelope.subject_enrollment_update)
            assertEquals(Subjects.CHEMISTRY_ID, updateEnvelope.subject_enrollment_update!!.subject_id)

            close()
        }
    }

    @Test
    fun `websocket client unsubscribes and stops receiving updates`() = runTest {
        webSocket {
            // Subscribe to physics
            val subscriptionEnvelope = Envelope(
                message_id = 104L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    subscriptions = mapOf(
                        Enrollments.SCIENCE_ID to SubjectEnrollmentUpdateSubscription(
                            subject_ids = listOf(Subjects.PHYSICS_ID)
                        )
                    )
                )
            )

            outgoing.send(Frame.Binary(true, subscriptionEnvelope.encode()))

            val ack1 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertNotNull(Envelope.ADAPTER.decode(ack1.readBytes()).acknowledged)

            // Unsubscribe (send empty subscription)
            val unsubscribeEnvelope = Envelope(
                message_id = 105L,
                subject_enrollment_update_subscription_request = SubjectEnrollmentUpdateSubscriptionRequest(
                    // Empty subscriptions map means unsubscribe from all
                )
            )

            outgoing.send(Frame.Binary(true, unsubscribeEnvelope.encode()))

            val ack2 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertNotNull(Envelope.ADAPTER.decode(ack2.readBytes()).acknowledged)

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
                    send(Envelope(identify = Identify(token = "invalid-token")))

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

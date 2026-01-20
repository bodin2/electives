package th.ac.bodin2.electives.api

import io.ktor.client.plugins.websocket.*
import io.ktor.server.plugins.di.*
import io.ktor.server.testing.*
import io.ktor.websocket.*
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.withTimeout
import org.jetbrains.exposed.v1.jdbc.insert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import th.ac.bodin2.electives.api.TestConstants.Electives
import th.ac.bodin2.electives.api.TestConstants.Students
import th.ac.bodin2.electives.api.TestConstants.Subjects
import th.ac.bodin2.electives.api.TestConstants.Teams
import th.ac.bodin2.electives.api.TestConstants.TestData.CLIENT_NAME
import th.ac.bodin2.electives.api.annotations.CreatesTransaction
import th.ac.bodin2.electives.api.services.ElectiveSelectionService
import th.ac.bodin2.electives.api.services.NotificationsService
import th.ac.bodin2.electives.api.services.NotificationsServiceImpl
import th.ac.bodin2.electives.api.services.TestServiceConstants.UNUSED_ID
import th.ac.bodin2.electives.api.services.UsersService
import th.ac.bodin2.electives.api.utils.send
import th.ac.bodin2.electives.db.Elective
import th.ac.bodin2.electives.db.models.StudentTeams
import th.ac.bodin2.electives.proto.api.NotificationsService.Envelope
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.envelope
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.identify
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.subjectEnrollmentUpdate
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.subjectEnrollmentUpdateSubscription
import th.ac.bodin2.electives.proto.api.NotificationsServiceKt.subjectEnrollmentUpdateSubscriptionRequest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@OptIn(DelicateCoroutinesApi::class, CreatesTransaction::class)
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

    val serviceConfig = NotificationsServiceImpl.Config(
        maxSubjectSubscriptionsPerClient = 5,
        bulkUpdateInterval = 100.milliseconds,
        bulkUpdatesEnabled = false
    )

    private suspend fun ApplicationTestBuilder.studentToken(): String {
        startApplication()
        return transaction {
            usersService.createSession(
                Students.JOHN_ID,
                Students.JOHN_PASSWORD,
                CLIENT_NAME
            )
        }
    }

    private suspend fun ApplicationTestBuilder.webSocket(
        token: String,
        block: suspend DefaultClientWebSocketSession.() -> Unit
    ) {
        createWSClient().webSocket("/notifications") {
            send(envelope {
                identify = identify {
                    this.token = token
                }
            })

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
            val envelope = envelope {
                messageId = 1
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            Electives.SCIENCE_ID,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(Subjects.PHYSICS_ID)
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, envelope.toByteArray()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.parseFrom(response.readBytes())
            assertEquals(1, ackEnvelope.messageId, "Expected message ID to match")
            assertTrue(ackEnvelope.hasAcknowledged(), "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription to multiple subjects`() = runTest {
        webSocket {
            val envelope = envelope {
                messageId = 2
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            Electives.SCIENCE_ID,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(Subjects.PHYSICS_ID)
                                subjectIds.add(Subjects.CHEMISTRY_ID)
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, envelope.toByteArray()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.parseFrom(response.readBytes())
            assertEquals(2, ackEnvelope.messageId, "Expected message ID to match")
            assertTrue(ackEnvelope.hasAcknowledged(), "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription exceeds max subjects limit`() = runTest {
        webSocket {
            val envelope = envelope {
                messageId = 3
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            Electives.SCIENCE_ID,
                            subjectEnrollmentUpdateSubscription {
                                repeat(10) { subjectIds.add(it) }
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, envelope.toByteArray()))
            assertFailsWith<ClosedReceiveChannelException>("Expected connection to be closed by server") {
                incoming.receive()
            }
        }
    }

    @Test
    fun `websocket subscription with empty subject list`() = runTest {
        webSocket {
            val envelope = envelope {
                messageId = 4
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            Electives.SCIENCE_ID,
                            subjectEnrollmentUpdateSubscription {}
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, envelope.toByteArray()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.parseFrom(response.readBytes())
            assertEquals(4, ackEnvelope.messageId, "Expected message ID to match")
            assertTrue(ackEnvelope.hasAcknowledged(), "Expected acknowledged response")

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
            val envelope = envelope {
                subjectEnrollmentUpdate = subjectEnrollmentUpdate {
                    electiveId = Electives.SCIENCE_ID
                    subjectId = Subjects.PHYSICS_ID
                    enrolledCount = 10
                }
            }

            outgoing.send(Frame.Binary(true, envelope.toByteArray()))
            assertFailsWith<ClosedReceiveChannelException>("Expected connection to be closed by server") {
                incoming.receive()
            }
        }
    }

    @Test
    fun `websocket subscription to nonexistent elective`() = runTest {
        webSocket {
            val envelope = envelope {
                messageId = 5
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            UNUSED_ID,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(Subjects.PHYSICS_ID)
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, envelope.toByteArray()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.parseFrom(response.readBytes())
            assertEquals(5, ackEnvelope.messageId, "Expected message ID to match")
            assertTrue(ackEnvelope.hasAcknowledged(), "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription with negative ids`() = runTest {
        webSocket {
            val envelope = envelope {
                messageId = 6
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            -1,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(-1)
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, envelope.toByteArray()))

            val response = withTimeout(10.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.parseFrom(response.readBytes())
            assertEquals(6, ackEnvelope.messageId, "Expected message ID to match")
            assertTrue(ackEnvelope.hasAcknowledged(), "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket multiple subscription requests`() = runTest {
        webSocket {
            repeat(3) { i ->
                val envelope = envelope {
                    messageId = (i + 1).toLong()
                    subjectEnrollmentUpdateSubscriptionRequest =
                        subjectEnrollmentUpdateSubscriptionRequest {
                            subscriptions.put(
                                Electives.SCIENCE_ID,
                                subjectEnrollmentUpdateSubscription {
                                    subjectIds.add(Subjects.PHYSICS_ID)
                                }
                            )
                        }
                }

                outgoing.send(Frame.Binary(true, envelope.toByteArray()))

                val response = withTimeout(5.seconds) {
                    incoming.receive() as Frame.Binary
                }

                val ackEnvelope = Envelope.parseFrom(response.readBytes())
                assertEquals((i + 1).toLong(), ackEnvelope.messageId, "Expected message ID to match")
                assertTrue(ackEnvelope.hasAcknowledged(), "Expected acknowledged response")
            }

            close()
        }
    }

    @Test
    fun `websocket subscription without message id`() = runTest {
        webSocket {
            val envelope = envelope {
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            Electives.SCIENCE_ID,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(Subjects.PHYSICS_ID)
                            }
                        )
                    }
            }

            send(Frame.Binary(true, envelope.toByteArray()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.parseFrom(response.readBytes())
            assertTrue(ackEnvelope.hasAcknowledged(), "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket subscription at exact max limit`() = runTest {
        webSocket {
            val envelope = envelope {
                messageId = 7
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            Electives.SCIENCE_ID,
                            subjectEnrollmentUpdateSubscription {
                                repeat(5) { subjectIds.add(it) }
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, envelope.toByteArray()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.parseFrom(response.readBytes())
            assertEquals(7, ackEnvelope.messageId, "Expected message ID to match")
            assertTrue(ackEnvelope.hasAcknowledged(), "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket multiple electives within limit`() = runTest {
        webSocket {
            val envelope = envelope {
                messageId = 8
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            1,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(101)
                                subjectIds.add(102)
                            }
                        )
                        subscriptions.put(
                            2,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(201)
                                subjectIds.add(202)
                                subjectIds.add(203)
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, envelope.toByteArray()))

            val response = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }

            val ackEnvelope = Envelope.parseFrom(response.readBytes())
            assertEquals(8, ackEnvelope.messageId, "Expected message ID to match")
            assertTrue(ackEnvelope.hasAcknowledged(), "Expected acknowledged response")

            close()
        }
    }

    @Test
    fun `websocket receives bulk enrollment updates for all electives`() = runTest {
        webSocket {
            serviceConfig.bulkUpdatesEnabled = true

            val electives = transaction { Elective.allReferences() }
            assertTrue(electives.isNotEmpty(), "Test requires at least one elective")

            val received = mutableMapOf<Int, Envelope>()

            // Trigger a selection
            electiveSelectionService.setStudentSelection(
                Students.JOHN_ID,
                Students.JOHN_ID,
                Electives.SCIENCE_ID,
                Subjects.PHYSICS_ID,
            )

            withTimeout(10.seconds) {
                while (received.size < electives.size) {
                    val frame = incoming.receive() as Frame.Binary
                    val envelope =
                        Envelope.parseFrom(frame.readBytes())

                    assertTrue(
                        envelope.hasBulkSubjectEnrollmentUpdate(),
                        "Expected bulkSubjectEnrollmentUpdate envelope"
                    )

                    val bulk = envelope.bulkSubjectEnrollmentUpdate
                    received[bulk.electiveId] = envelope
                }
            }

            // Received exactly one bulk update per elective
            assertEquals(
                electives.map { it.id }.toSet(),
                received.keys,
                "Did not receive bulk updates for all electives"
            )

            // Is the payload correct?
            for ((electiveId, envelope) in received) {
                val bulk = envelope.bulkSubjectEnrollmentUpdate

                assertEquals(electiveId, bulk.electiveId)
                assertTrue(
                    bulk.subjectEnrolledCountsMap.isNotEmpty(),
                    "Bulk update should contain enrolled counts"
                )
            }

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
            val subscriptionEnvelope = envelope {
                messageId = 100
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            Electives.SCIENCE_ID,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(Subjects.PHYSICS_ID)
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, subscriptionEnvelope.toByteArray()))

            // Wait for acknowledgement
            val ackResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val ackEnvelope = Envelope.parseFrom(ackResponse.readBytes())
            assertTrue(ackEnvelope.hasAcknowledged(), "Expected acknowledged response")

            // Trigger a selection that affects the subscribed subject
            transaction {
                // Add Jane to Team 1 so they can enroll in Physics
                StudentTeams.insert {
                    it[student] = Students.JANE_ID
                    it[team] = Teams.TEAM_1_ID
                }
            }

            electiveSelectionService.setStudentSelection(
                Students.JANE_ID,
                Students.JANE_ID,
                Electives.SCIENCE_ID,
                Subjects.PHYSICS_ID
            )

            // Should receive an update notification
            val updateResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val updateEnvelope = Envelope.parseFrom(updateResponse.readBytes())
            assertTrue(
                updateEnvelope.hasSubjectEnrollmentUpdate(),
                "Expected subjectEnrollmentUpdate notification"
            )

            val update = updateEnvelope.subjectEnrollmentUpdate
            assertEquals(Electives.SCIENCE_ID, update.electiveId)
            assertEquals(Subjects.PHYSICS_ID, update.subjectId)
            assertTrue(update.enrolledCount > 0, "Enrolled count should be greater than 0")

            close()
        }
    }

    @Test
    fun `websocket client subscribes to multiple subjects and receives updates`() = runTest {
        webSocket {
            // Subscribe to both physics and chemistry
            val subscriptionEnvelope = envelope {
                messageId = 101
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            Electives.SCIENCE_ID,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(Subjects.PHYSICS_ID)
                                subjectIds.add(Subjects.CHEMISTRY_ID)
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, subscriptionEnvelope.toByteArray()))

            // Wait for acknowledgement
            val ackResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val ackEnvelope = Envelope.parseFrom(ackResponse.readBytes())
            assertTrue(ackEnvelope.hasAcknowledged(), "Expected acknowledged response")

            // Enroll John in Physics
            electiveSelectionService.setStudentSelection(
                Students.JOHN_ID,
                Students.JOHN_ID,
                Electives.SCIENCE_ID,
                Subjects.PHYSICS_ID
            )

            // Should receive update for Physics
            val physicsUpdateResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val physicsUpdateEnvelope = Envelope.parseFrom(physicsUpdateResponse.readBytes())
            assertTrue(physicsUpdateEnvelope.hasSubjectEnrollmentUpdate())
            assertEquals(Subjects.PHYSICS_ID, physicsUpdateEnvelope.subjectEnrollmentUpdate.subjectId)

            close()
        }
    }

    @Test
    fun `websocket client updates subscription and receives new updates`() = runTest {
        webSocket {
            // Initial subscription to physics only
            val initialSubscription = envelope {
                messageId = 102
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            Electives.SCIENCE_ID,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(Subjects.PHYSICS_ID)
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, initialSubscription.toByteArray()))

            val ack1 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertTrue(Envelope.parseFrom(ack1.readBytes()).hasAcknowledged())

            // Update subscription to chemistry only
            val updatedSubscription = envelope {
                messageId = 103
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            Electives.SCIENCE_ID,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(Subjects.CHEMISTRY_ID)
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, updatedSubscription.toByteArray()))

            val ack2 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertTrue(Envelope.parseFrom(ack2.readBytes()).hasAcknowledged())

            // Trigger update for chemistry
            electiveSelectionService.setStudentSelection(
                Students.JOHN_ID,
                Students.JOHN_ID,
                Electives.SCIENCE_ID,
                Subjects.CHEMISTRY_ID,
            )

            // Should receive update for chemistry
            val updateResponse = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            val updateEnvelope = Envelope.parseFrom(updateResponse.readBytes())
            assertTrue(updateEnvelope.hasSubjectEnrollmentUpdate())
            assertEquals(Subjects.CHEMISTRY_ID, updateEnvelope.subjectEnrollmentUpdate.subjectId)

            close()
        }
    }

    @Test
    fun `websocket client unsubscribes and stops receiving updates`() = runTest {
        webSocket {
            // Subscribe to physics
            val subscriptionEnvelope = envelope {
                messageId = 104
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        subscriptions.put(
                            Electives.SCIENCE_ID,
                            subjectEnrollmentUpdateSubscription {
                                subjectIds.add(Subjects.PHYSICS_ID)
                            }
                        )
                    }
            }

            outgoing.send(Frame.Binary(true, subscriptionEnvelope.toByteArray()))

            val ack1 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertTrue(Envelope.parseFrom(ack1.readBytes()).hasAcknowledged())

            // Unsubscribe (send empty subscription)
            val unsubscribeEnvelope = envelope {
                messageId = 105
                subjectEnrollmentUpdateSubscriptionRequest =
                    subjectEnrollmentUpdateSubscriptionRequest {
                        // Empty subscriptions map means unsubscribe from all
                    }
            }

            outgoing.send(Frame.Binary(true, unsubscribeEnvelope.toByteArray()))

            val ack2 = withTimeout(5.seconds) {
                incoming.receive() as Frame.Binary
            }
            assertTrue(Envelope.parseFrom(ack2.readBytes()).hasAcknowledged())

            close()
        }
    }
}

package th.ac.bodin2.electives.api.utils

import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*

suspend inline fun <reified T : MessageLite> ApplicationCall.parse(): T {
    val bytes = receive<ByteArray>()
    val parserField = T::class.java.getDeclaredField("PARSER")

    @Suppress("UNCHECKED_CAST")
    val parser = parserField.get(null) as Parser<T>
    return parser.parseFrom(bytes)
}

suspend fun ApplicationCall.respondMessage(
    message: MessageLite,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondBytes(
        message.toByteArray(),
        contentType = ContentType("application", "x-protobuf"),
        status = status
    )
}
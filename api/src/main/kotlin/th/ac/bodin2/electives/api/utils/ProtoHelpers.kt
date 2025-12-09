package th.ac.bodin2.electives.api.utils

import com.google.protobuf.InvalidProtocolBufferException
import com.google.protobuf.MessageLite
import com.google.protobuf.Parser
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.websocket.*
import java.util.concurrent.ConcurrentHashMap

private val parserCache = ConcurrentHashMap<Class<*>, Parser<*>>()

fun <T : MessageLite> getParser(clazz: Class<T>): Parser<T> {
    @Suppress("UNCHECKED_CAST")
    return parserCache.getOrPut(clazz) {
        val parserField = clazz.getDeclaredField("PARSER")
        parserField.get(null) as Parser<T>
    } as Parser<T>
}

suspend inline fun <reified T : MessageLite> ApplicationCall.parseOrNull(): T? {
    val bytes = receive<ByteArray>()
    return parseProtoOrNull<T>(bytes)
}

inline fun <reified T : MessageLite> Frame.Binary.parseOrNull(): T? {
    val bytes = readBytes()
    return parseProtoOrNull<T>(bytes)
}

inline fun <reified T : MessageLite> parseProtoOrNull(bytes: ByteArray): T? {
    val parser = getParser(T::class.java)
    return try {
        parser.parseFrom(bytes)
    } catch (_: InvalidProtocolBufferException) {
        null
    }
}

suspend inline fun WebSocketSession.send(message: MessageLite) {
    send(message.toByteArray())
}

suspend inline fun ApplicationCall.respond(
    message: MessageLite,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondBytes(
        message.toByteArray(),
        contentType = ContentType("application", "x-protobuf"),
        status = status
    )
}
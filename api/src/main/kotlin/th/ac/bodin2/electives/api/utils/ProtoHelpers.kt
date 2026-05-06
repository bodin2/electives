package th.ac.bodin2.electives.api.utils

import com.squareup.wire.Message
import com.squareup.wire.ProtoAdapter
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.websocket.*
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

private val adapterCache = ConcurrentHashMap<Class<*>, ProtoAdapter<*>>()

fun <T : Message<T, *>> getAdapter(clazz: Class<T>): ProtoAdapter<T> {
    @Suppress("UNCHECKED_CAST")
    return adapterCache.getOrPut(clazz) {
        val field = clazz.getDeclaredField("ADAPTER")
        field.get(null) as ProtoAdapter<T>
    } as ProtoAdapter<T>
}

suspend inline fun <reified T : Message<T, *>> ApplicationCall.parseOrNull(): T? {
    val bytes = receive<ByteArray>()
    return parseProtoOrNull<T>(bytes)
}

inline fun <reified T : Message<T, *>> Frame.Binary.parseOrNull(): T? {
    val bytes = readBytes()
    return parseProtoOrNull<T>(bytes)
}

inline fun <reified T : Message<T, *>> parseProtoOrNull(bytes: ByteArray): T? {
    val adapter = getAdapter(T::class.java)
    return try {
        adapter.decode(bytes)
    } catch (_: IOException) {
        null
    }
}

suspend inline fun WebSocketSession.send(message: Message<*, *>) {
    send(message.encode())
}

suspend inline fun ApplicationCall.respond(
    message: Message<*, *>,
    status: HttpStatusCode = HttpStatusCode.OK
) {
    respondBytes(
        message.encode(),
        contentType = ContentType("application", "x-protobuf"),
        status = status
    )
}

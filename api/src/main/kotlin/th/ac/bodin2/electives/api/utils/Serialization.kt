package th.ac.bodin2.electives.api.utils

import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object UIntStringSerializer : KSerializer<UInt> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("UIntString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: UInt) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): UInt {
        return decoder.decodeString().toUInt()
    }
}
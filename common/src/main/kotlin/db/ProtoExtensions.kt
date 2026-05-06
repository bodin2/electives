package th.ac.bodin2.electives.db

import th.ac.bodin2.electives.proto.api.Elective as ProtoElective
import th.ac.bodin2.electives.proto.api.Subject as ProtoSubject
import th.ac.bodin2.electives.proto.api.Team as ProtoTeam
import th.ac.bodin2.electives.proto.api.User as ProtoUser

val ProtoUser.uId inline get() = id.toUInt()
val ProtoTeam.uId inline get() = id.toUInt()
val ProtoElective.uId inline get() = id.toUInt()
val ProtoSubject.uId inline get() = id.toUInt()

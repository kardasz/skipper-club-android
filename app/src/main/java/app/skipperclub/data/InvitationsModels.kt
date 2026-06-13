package app.skipperclub.data

import kotlinx.serialization.Serializable

/**
 * Lifecycle state of an invitation. Wire values follow `docs/api/openapi.yaml`
 * (`InvitationStatus`). [Unknown] is a forward-compatible fallback so a future
 * server status never drops the row from the list.
 */
enum class InvitationStatus(val wireValue: String) {
    Pending("pending"),
    Accepted("accepted"),
    Expired("expired"),
    Unknown(""),
    ;

    companion object {
        fun fromWire(value: String): InvitationStatus =
            entries.firstOrNull { it.wireValue == value } ?: Unknown
    }
}

/** The admin who sent an invitation (`Invitation.inviter`). */
data class Inviter(
    val id: String,
    val name: String,
)

/** A single invitation as rendered by the UI. */
data class Invitation(
    val id: String,
    val email: String,
    val status: InvitationStatus,
    val expiresAt: String,
    val createdAt: String,
    val inviter: Inviter,
)

/** Query parameters for `GET /v1/invitations`. */
data class InvitationListQuery(
    val status: InvitationStatus? = null,
    val limit: Int = 20,
    val offset: Int = 0,
)

data class InvitationsPage(
    val invitations: List<Invitation>,
    val total: Int,
    val limit: Int,
    val offset: Int,
) {
    val hasMore: Boolean
        get() = offset + invitations.size < total
}

@Serializable
internal data class SendInvitationRequest(
    val email: String,
)

@Serializable
internal data class InviterDto(
    val id: String,
    val name: String,
) {
    fun toDomain(): Inviter = Inviter(id = id, name = name)
}

@Serializable
internal data class InvitationDto(
    val id: String,
    val email: String,
    val status: String,
    val expiresAt: String,
    val createdAt: String,
    val inviter: InviterDto,
) {
    fun toDomain(): Invitation = Invitation(
        id = id,
        email = email,
        status = InvitationStatus.fromWire(status),
        expiresAt = expiresAt,
        createdAt = createdAt,
        inviter = inviter.toDomain(),
    )
}

@Serializable
internal data class InvitationListDto(
    val invitations: List<InvitationDto> = emptyList(),
    val total: Int = 0,
    val limit: Int = 0,
    val offset: Int = 0,
) {
    fun toDomain(): InvitationsPage = InvitationsPage(
        invitations = invitations.map { it.toDomain() },
        total = total,
        limit = limit,
        offset = offset,
    )
}

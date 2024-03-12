package no.nav.syfo.domain

import no.nav.syfo.util.nowUTC
import java.time.OffsetDateTime
import java.util.*

data class Vurdering private constructor(
    val uuid: UUID,
    val personident: PersonIdent,
    val createdAt: OffsetDateTime,
    val veilederident: String,
    val type: VurderingType,
    val begrunnelse: String,
    val varsel: Varsel?,
    val document: List<DocumentComponent>,
    val journalpostId: String?,
    val publishedAt: OffsetDateTime?,

) {
    fun journalfor(journalpostId: String): Vurdering = this.copy(journalpostId = journalpostId)

    fun publish(): Vurdering = this.copy(publishedAt = nowUTC())

    companion object {
        fun createForhandsvarsel(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            svarfristDager: Long,
        ) = Vurdering(
            uuid = UUID.randomUUID(),
            personident = personident,
            createdAt = nowUTC(),
            veilederident = veilederident,
            type = VurderingType.FORHANDSVARSEL,
            begrunnelse = begrunnelse,
            document = document,
            journalpostId = null,
            varsel = Varsel(svarfristDager),
            publishedAt = null,
        )

        fun createFromDatabase(
            uuid: UUID,
            personident: PersonIdent,
            createdAt: OffsetDateTime,
            veilederident: String,
            type: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            journalpostId: String?,
            varsel: Varsel?,
            publishedAt: OffsetDateTime?,
        ) = Vurdering(
            uuid = uuid,
            personident = personident,
            createdAt = createdAt,
            veilederident = veilederident,
            type = VurderingType.valueOf(type),
            begrunnelse = begrunnelse,
            document = document,
            journalpostId = journalpostId,
            varsel = varsel,
            publishedAt = publishedAt,
        )
    }
}

enum class VurderingType {
    FORHANDSVARSEL, OPPFYLT, AVSLAG
}

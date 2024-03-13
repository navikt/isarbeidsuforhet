package no.nav.syfo.domain

import no.nav.syfo.infrastructure.clients.dokarkiv.dto.BrevkodeType
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.JournalpostType
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
    val journalpostId: JournalpostId?,
    val publishedAt: OffsetDateTime?,
) {

    constructor(
        personident: PersonIdent,
        veilederident: String,
        begrunnelse: String,
        document: List<DocumentComponent>,
        type: VurderingType,
    ) : this(
        uuid = UUID.randomUUID(),
        personident = personident,
        createdAt = nowUTC(),
        veilederident = veilederident,
        type = type,
        begrunnelse = begrunnelse,
        document = document,
        journalpostId = null,
        varsel = if (type == VurderingType.FORHANDSVARSEL) Varsel() else null,
        publishedAt = null,
    )

    fun journalfor(journalpostId: JournalpostId): Vurdering = this.copy(journalpostId = journalpostId)

    fun publish(): Vurdering = this.copy(publishedAt = nowUTC())

    fun shouldJournalfores(): Boolean = when (type) {
        VurderingType.FORHANDSVARSEL, VurderingType.OPPFYLT, VurderingType.AVSLAG -> true
    }

    fun isForhandsvarsel(): Boolean = type == VurderingType.FORHANDSVARSEL

    companion object {

        fun createFromDatabase(
            uuid: UUID,
            personident: PersonIdent,
            createdAt: OffsetDateTime,
            veilederident: String,
            type: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            journalpostId: JournalpostId?,
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

fun VurderingType.getDokumentTittel(): String = when (this) {
    VurderingType.FORHANDSVARSEL -> "Forhåndsvarsel om avslag på sykepenger"
    VurderingType.OPPFYLT, VurderingType.AVSLAG -> "Vurdering av §8-4 arbeidsuførhet"
}

fun VurderingType.getBrevkode(): BrevkodeType = when (this) {
    VurderingType.FORHANDSVARSEL -> BrevkodeType.ARBEIDSUFORHET_FORHANDSVARSEL
    VurderingType.OPPFYLT, VurderingType.AVSLAG -> BrevkodeType.ARBEIDSUFORHET_VURDERING
}

fun VurderingType.getJournalpostType(): JournalpostType = when (this) {
    VurderingType.FORHANDSVARSEL -> JournalpostType.UTGAAENDE
    VurderingType.OPPFYLT, VurderingType.AVSLAG -> JournalpostType.NOTAT
}

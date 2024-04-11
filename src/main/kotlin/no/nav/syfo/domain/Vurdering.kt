package no.nav.syfo.domain

import no.nav.syfo.infrastructure.clients.dokarkiv.dto.BrevkodeType
import no.nav.syfo.infrastructure.clients.dokarkiv.dto.JournalpostType
import no.nav.syfo.util.nowUTC
import java.time.LocalDate
import java.time.OffsetDateTime
import java.util.*

sealed interface Vurdering {
    val uuid: UUID
    val personident: PersonIdent
    val createdAt: OffsetDateTime
    val veilederident: String
    val type: VurderingType
    val begrunnelse: String
    val varsel: Varsel?
    val document: List<DocumentComponent>
    val journalpostId: JournalpostId?
    val publishedAt: OffsetDateTime?
    val gjelderFom: LocalDate?

    fun journalfor(journalpostId: JournalpostId): Vurdering = when (this) {
        is Forhandsvarsel -> this.copy(journalpostId = journalpostId)
        is Oppfylt -> this.copy(journalpostId = journalpostId)
        is Avslag -> this.copy(journalpostId = journalpostId)
    }

    fun publish(): Vurdering = when (this) {
        is Forhandsvarsel -> this.copy(publishedAt = nowUTC())
        is Oppfylt -> this.copy(publishedAt = nowUTC())
        is Avslag -> this.copy(publishedAt = nowUTC())
    }

    fun shouldJournalfores(): Boolean = true
    fun isExpiredForhandsvarsel(): Boolean = this is Forhandsvarsel && this.varsel.isExpired()

    data class Forhandsvarsel internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val begrunnelse: String,
        override val document: List<DocumentComponent>,
        override val varsel: Varsel = Varsel(),
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null
    ) : Vurdering {
        override val type: VurderingType = VurderingType.FORHANDSVARSEL
        override val gjelderFom: LocalDate? = null

        constructor(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            journalpostId = null,
            publishedAt = null,
        )
    }

    data class Oppfylt internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val begrunnelse: String,
        override val document: List<DocumentComponent>,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null
    ) : Vurdering {
        override val type: VurderingType = VurderingType.OPPFYLT
        override val varsel: Varsel? = null
        override val gjelderFom: LocalDate? = null

        constructor(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            journalpostId = null,
            publishedAt = null,
        )
    }

    data class Avslag internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val begrunnelse: String,
        override val document: List<DocumentComponent>,
        override val gjelderFom: LocalDate,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null
    ) : Vurdering {
        override val type: VurderingType = VurderingType.AVSLAG
        override val varsel: Varsel? = null

        constructor(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            gjelderFom: LocalDate,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            gjelderFom = gjelderFom,
            journalpostId = null,
            publishedAt = null,
        )

        override fun shouldJournalfores(): Boolean = false
    }

    companion object {
        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            personident: PersonIdent,
            veilederident: String,
            type: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            journalpostId: JournalpostId?,
            varsel: Varsel?,
            publishedAt: OffsetDateTime?,
            gjelderFom: LocalDate?
        ): Vurdering {
            return when (VurderingType.valueOf(type)) {
                VurderingType.FORHANDSVARSEL -> Forhandsvarsel(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    begrunnelse = begrunnelse,
                    document = document,
                    varsel = varsel!!,
                    journalpostId = journalpostId,
                    publishedAt = publishedAt
                )

                VurderingType.OPPFYLT -> Oppfylt(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    begrunnelse = begrunnelse,
                    document = document,
                    journalpostId = journalpostId,
                    publishedAt = publishedAt
                )

                VurderingType.AVSLAG -> Avslag(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    begrunnelse = begrunnelse,
                    document = document,
                    gjelderFom = gjelderFom!!,
                    journalpostId = journalpostId,
                    publishedAt = publishedAt
                )
            }
        }
    }
}

enum class VurderingType {
    FORHANDSVARSEL, OPPFYLT, AVSLAG
}

fun VurderingType.getDokumentTittel(): String = when (this) {
    VurderingType.FORHANDSVARSEL -> "Forhåndsvarsel om avslag på sykepenger"
    VurderingType.OPPFYLT -> "Vurdering av §8-4 arbeidsuførhet"
    VurderingType.AVSLAG -> throw IllegalStateException("Should not journalfore type $this")
}

fun VurderingType.getBrevkode(): BrevkodeType = when (this) {
    VurderingType.FORHANDSVARSEL -> BrevkodeType.ARBEIDSUFORHET_FORHANDSVARSEL
    VurderingType.OPPFYLT -> BrevkodeType.ARBEIDSUFORHET_VURDERING
    VurderingType.AVSLAG -> throw IllegalStateException("Should not journalfore type $this")
}

fun VurderingType.getJournalpostType(): JournalpostType = when (this) {
    VurderingType.FORHANDSVARSEL -> JournalpostType.UTGAAENDE
    VurderingType.OPPFYLT -> JournalpostType.NOTAT
    VurderingType.AVSLAG -> throw IllegalStateException("Should not journalfore type $this")
}

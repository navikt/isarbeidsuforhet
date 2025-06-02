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
    val arsak: VurderingArsak?

    fun journalfor(journalpostId: JournalpostId): Vurdering = when (this) {
        is Forhandsvarsel -> this.copy(journalpostId = journalpostId)
        is Oppfylt -> this.copy(journalpostId = journalpostId)
        is OppfyltUtenForhandsvarsel -> this.copy(journalpostId = journalpostId)
        is Avslag -> this.copy(journalpostId = journalpostId)
        is AvslagUtenForhandsvarsel -> this.copy(journalpostId = journalpostId)
        is IkkeAktuell -> this.copy(journalpostId = journalpostId)
    }

    fun publish(): Vurdering = when (this) {
        is Forhandsvarsel -> this.copy(publishedAt = nowUTC())
        is Oppfylt -> this.copy(publishedAt = nowUTC())
        is OppfyltUtenForhandsvarsel -> this.copy(publishedAt = nowUTC())
        is Avslag -> this.copy(publishedAt = nowUTC())
        is AvslagUtenForhandsvarsel -> this.copy(publishedAt = nowUTC())
        is IkkeAktuell -> this.copy(publishedAt = nowUTC())
    }

    fun isExpiredForhandsvarsel(): Boolean = this is Forhandsvarsel && this.varsel.isExpired()

    data class Forhandsvarsel internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val begrunnelse: String,
        override val document: List<DocumentComponent>,
        override val varsel: Varsel,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null
    ) : Vurdering {
        override val type: VurderingType = VurderingType.FORHANDSVARSEL
        override val gjelderFom: LocalDate? = null
        override val arsak: VurderingArsak? = null

        constructor(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            svarfrist: LocalDate,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            journalpostId = null,
            publishedAt = null,
            varsel = Varsel(svarfrist = svarfrist),
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
        override val arsak: VurderingArsak? = null

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

    data class OppfyltUtenForhandsvarsel internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val begrunnelse: String,
        override val document: List<DocumentComponent>,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null,
        override val arsak: VurderingArsak,
    ) : Vurdering {
        override val type: VurderingType = VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL
        override val varsel: Varsel? = null
        override val gjelderFom: LocalDate? = null

        constructor(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            arsak: VurderingArsak,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            journalpostId = null,
            publishedAt = null,
            arsak = arsak,
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
        override val arsak: VurderingArsak? = null

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
    }

    data class AvslagUtenForhandsvarsel internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val begrunnelse: String,
        override val document: List<DocumentComponent>,
        override val gjelderFom: LocalDate,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null,
        override val arsak: VurderingArsak,
    ) : Vurdering {
        override val type: VurderingType = VurderingType.AVSLAG_UTEN_FORHANDSVARSEL
        override val varsel: Varsel? = null

        constructor(
            personident: PersonIdent,
            veilederident: String,
            begrunnelse: String,
            document: List<DocumentComponent>,
            gjelderFom: LocalDate,
            arsak: VurderingArsak,
        ) : this(
            personident = personident,
            veilederident = veilederident,
            begrunnelse = begrunnelse,
            document = document,
            gjelderFom = gjelderFom,
            journalpostId = null,
            publishedAt = null,
            arsak = arsak,
        )
    }

    data class IkkeAktuell internal constructor(
        override val uuid: UUID = UUID.randomUUID(),
        override val createdAt: OffsetDateTime = nowUTC(),
        override val personident: PersonIdent,
        override val veilederident: String,
        override val arsak: VurderingArsak,
        override val document: List<DocumentComponent>,
        override val journalpostId: JournalpostId? = null,
        override val publishedAt: OffsetDateTime? = null
    ) : Vurdering {
        override val begrunnelse = ""
        override val type: VurderingType = VurderingType.IKKE_AKTUELL
        override val varsel: Varsel? = null
        override val gjelderFom: LocalDate? = null

        constructor(
            arsak: VurderingArsak,
            personident: PersonIdent,
            veilederident: String,
            document: List<DocumentComponent>,
        ) : this(
            arsak = arsak,
            personident = personident,
            veilederident = veilederident,
            document = document,
            journalpostId = null,
            publishedAt = null,
        )
    }

    companion object {
        fun createFromDatabase(
            uuid: UUID,
            createdAt: OffsetDateTime,
            personident: PersonIdent,
            veilederident: String,
            type: String,
            arsak: String?,
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

                VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL -> OppfyltUtenForhandsvarsel(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    arsak = VurderingArsak.valueOf(arsak!!),
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

                VurderingType.AVSLAG_UTEN_FORHANDSVARSEL -> AvslagUtenForhandsvarsel(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    arsak = VurderingArsak.valueOf(arsak!!),
                    begrunnelse = begrunnelse,
                    document = document,
                    gjelderFom = gjelderFom!!,
                    journalpostId = journalpostId,
                    publishedAt = publishedAt
                )

                VurderingType.IKKE_AKTUELL -> IkkeAktuell(
                    uuid = uuid,
                    createdAt = createdAt,
                    personident = personident,
                    veilederident = veilederident,
                    arsak = VurderingArsak.valueOf(arsak!!),
                    document = document,
                    journalpostId = journalpostId,
                    publishedAt = publishedAt
                )
            }
        }
    }
}

enum class VurderingType(val isFinal: Boolean) {
    FORHANDSVARSEL(false),
    OPPFYLT(true),
    OPPFYLT_UTEN_FORHANDSVARSEL(true),
    AVSLAG(true),
    AVSLAG_UTEN_FORHANDSVARSEL(true),
    IKKE_AKTUELL(true);
}

fun VurderingType.getDokumentTittel(): String = when (this) {
    VurderingType.FORHANDSVARSEL -> "Forhåndsvarsel om avslag på sykepenger"
    VurderingType.OPPFYLT, VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL, VurderingType.IKKE_AKTUELL -> "Vurdering av §8-4 arbeidsuførhet"
    VurderingType.AVSLAG, VurderingType.AVSLAG_UTEN_FORHANDSVARSEL -> "Innstilling om avslag"
}

fun VurderingType.getBrevkode(): BrevkodeType = when (this) {
    VurderingType.FORHANDSVARSEL -> BrevkodeType.ARBEIDSUFORHET_FORHANDSVARSEL
    VurderingType.OPPFYLT, VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL, VurderingType.IKKE_AKTUELL -> BrevkodeType.ARBEIDSUFORHET_VURDERING
    VurderingType.AVSLAG, VurderingType.AVSLAG_UTEN_FORHANDSVARSEL -> BrevkodeType.ARBEIDSUFORHET_AVSLAG
}

fun VurderingType.getJournalpostType(): JournalpostType = when (this) {
    VurderingType.FORHANDSVARSEL, VurderingType.OPPFYLT, VurderingType.OPPFYLT_UTEN_FORHANDSVARSEL, VurderingType.IKKE_AKTUELL -> JournalpostType.UTGAAENDE
    VurderingType.AVSLAG, VurderingType.AVSLAG_UTEN_FORHANDSVARSEL -> JournalpostType.NOTAT
}

enum class VurderingArsak {
    FRISKMELDT,
    FRISKMELDING_TIL_ARBEIDSFORMIDLING,
    SYKEPENGER_IKKE_UTBETALT,
    NY_VURDERING_NAY,
}

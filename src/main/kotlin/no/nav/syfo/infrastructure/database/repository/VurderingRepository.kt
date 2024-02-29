package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.domain.Vurdering
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.*

class VurderingRepository(private val database: DatabaseInterface) : IVurderingRepository {
    override fun getForhandsvarsel(
        personident: PersonIdent,
    ): List<Vurdering> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_VURDERING).use {
                it.setString(1, personident.value)
                it.executeQuery().toList { toPVurdering() }
            }.map {
                it.toVurdering(
                    varsel = connection.getVarselForVurdering(it)
                )
            }
        }

    override fun createForhandsvarsel(
        pdf: ByteArray,
        vurdering: Vurdering,
    ) {
        if (vurdering.varsel == null) throw IllegalStateException("Vurdering should have Varsel when creating forhåndsvarsel")

        database.connection.use { connection ->
            val pVurdering = connection.createVurdering(vurdering)
            val pVarsel = connection.createVarsel(
                vurderingId = pVurdering.id,
                varsel = vurdering.varsel,
            )
            connection.createPdf(
                varselId = pVarsel.id,
                pdf = pdf,
            )
            connection.commit()
        }
    }

    private fun Connection.createVurdering(
        vurdering: Vurdering,
    ): PVurdering {
        val now = OffsetDateTime.now()

        return prepareStatement(CREATE_VURDERING).use {
            it.setString(1, vurdering.uuid.toString())
            it.setString(2, vurdering.personident.value)
            it.setObject(3, vurdering.createdAt)
            it.setObject(4, now)
            it.setString(5, vurdering.veilederident)
            it.setString(6, vurdering.type.name)
            it.setString(7, vurdering.begrunnelse)
            it.executeQuery().toList { toPVurdering() }
        }.single()
    }

    private fun Connection.createVarsel(vurderingId: Int, varsel: Varsel): PVarsel {
        val now = OffsetDateTime.now()

        return prepareStatement(CREATE_VARSEL).use {
            it.setString(1, varsel.uuid.toString())
            it.setObject(2, varsel.createdAt)
            it.setObject(3, now)
            it.setInt(4, vurderingId)
            it.setObject(5, mapper.writeValueAsString(varsel.document))
            it.setObject(6, varsel.svarfrist)
            it.setObject(7, varsel.svarfristExpiredPublishedAt)
            it.executeQuery().toList { toPVarsel() }.single()
        }
    }

    private fun Connection.createPdf(varselId: Int, pdf: ByteArray): PVarselPdf =
        prepareStatement(CREATE_VARSEL_PDF).use {
            it.setString(1, UUID.randomUUID().toString())
            it.setObject(2, OffsetDateTime.now())
            it.setInt(3, varselId)
            it.setBytes(4, pdf)
            it.executeQuery().toList { toPVarselPdf() }.single()
        }

    private fun Connection.getVarselForVurdering(vurdering: PVurdering): Varsel? =
        prepareStatement(GET_VARSEL_FOR_VURDERING).use {
            it.setInt(1, vurdering.id)
            it.executeQuery().toList { toPVarsel() }
        }.map { it.toVarsel() }.firstOrNull()

    companion object {
        private val mapper = configuredJacksonMapper()

        private const val GET_VURDERING =
            """
                SELECT * FROM VURDERING WHERE personident=? ORDER BY created_at DESC
            """

        private const val GET_VARSEL_FOR_VURDERING =
            """
                SELECT * FROM VARSEL WHERE vurdering_id = ?
            """

        private const val CREATE_VURDERING =
            """
            INSERT INTO VURDERING (
                id,
                uuid,
                personident,
                created_at,
                updated_at,
                veilederident,
                type,
                begrunnelse
            ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?)
            RETURNING *
            """

        private const val CREATE_VARSEL =
            """
            INSERT INTO VARSEL (
                id,
                uuid,
                created_at,
                updated_at,
                vurdering_id,
                document,
                svarfrist,
                svarfrist_expired_published_at             
            ) values (DEFAULT, ?, ?, ?, ?, ?::jsonb, ?, ?)
            RETURNING *
            """

        private const val CREATE_VARSEL_PDF =
            """
            INSERT INTO VARSEL_PDF (
                id,
                uuid,
                created_at,
                varsel_id,
                pdf
            ) values (DEFAULT, ?, ?, ?, ?)
            RETURNING *
            """
    }
}

internal fun ResultSet.toPVurdering(): PVurdering = PVurdering(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    personident = PersonIdent(getString("personident")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    veilederident = getString("veilederident"),
    type = getString("type"),
    begrunnelse = getString("begrunnelse")
)

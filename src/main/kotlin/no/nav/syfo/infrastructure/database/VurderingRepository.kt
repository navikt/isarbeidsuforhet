package no.nav.syfo.infrastructure.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.util.configuredJacksonMapper
import java.sql.Connection
import java.sql.ResultSet
import java.time.OffsetDateTime
import java.util.UUID

class VurderingRepository(private val database: DatabaseInterface) : IVurderingRepository {
    override fun createForhandsvarsel(
        pdf: ByteArray,
        document: List<DocumentComponent>,
        personIdent: PersonIdent,
        veileder: String,
        type: String,
        begrunnelse: String
    ) {
        database.connection.use { connection ->
            val vurdering = connection.createVurdering(
                personIdent = personIdent,
                veileder = veileder,
                type = type,
                begrunnelse = begrunnelse
            )
            val varsel = connection.createVarsel(
                vurderingId = vurdering.id,
                document = document
            )
            connection.createPdf(
                varselId = varsel.id,
                pdf = pdf
            )
            connection.commit()
        }
    }

    private fun Connection.createVurdering(
        personIdent: PersonIdent,
        veileder: String,
        type: String,
        begrunnelse: String
    ): PVurdering {
        val now = OffsetDateTime.now()

        return prepareStatement(CREATE_VURDERING).use {
            it.setString(1, UUID.randomUUID().toString())
            it.setString(2, personIdent.value)
            it.setObject(3, now)
            it.setObject(4, now)
            it.setString(5, veileder)
            it.setString(6, type)
            it.setString(7, begrunnelse)
            it.executeQuery().toList { toPVurdering() }
        }.single()
    }

    private fun Connection.createVarsel(vurderingId: Int, document: List<DocumentComponent>): PVarsel {
        val now = OffsetDateTime.now()

        return prepareStatement(CREATE_VARSEL).use {
            it.setString(1, UUID.randomUUID().toString())
            it.setObject(2, now)
            it.setObject(3, now)
            it.setInt(4, vurderingId)
            it.setObject(5, mapper.writeValueAsString(document))
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

    private fun ResultSet.toPVurdering(): PVurdering = PVurdering(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        personident = PersonIdent(getString("personident")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        veilederident = getString("veilederident"),
        type = getString("type"),
        begrunnelse = getString("begrunnelse")
    )

    private fun ResultSet.toPVarsel(): PVarsel = PVarsel(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        updatedAt = getObject("updated_at", OffsetDateTime::class.java),
        vurderingId = getInt("vurdering_id"),
        document = mapper.readValue(
            getString("document"),
            object : TypeReference<List<DocumentComponent>>() {}
        ),
        journalpostId = getString("journalpost_id"),
    )

    private fun ResultSet.toPVarselPdf(): PVarselPdf = PVarselPdf(
        id = getInt("id"),
        uuid = UUID.fromString(getString("uuid")),
        createdAt = getObject("created_at", OffsetDateTime::class.java),
        varselId = getInt("varsel_id"),
        pdf = getBytes("pdf"),
    )

    companion object {
        private val mapper = configuredJacksonMapper()

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
                document
            ) values (DEFAULT, ?, ?, ?, ?, ?::jsonb)
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

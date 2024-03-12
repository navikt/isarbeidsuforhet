package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.nowUTC
import java.sql.Connection
import java.sql.Date
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.*

private val mapper = configuredJacksonMapper()

class VurderingRepository(private val database: DatabaseInterface) : IVurderingRepository {
    override fun getVurderinger(
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

    override fun getUnpublishedVurderinger(): List<Vurdering> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNPUBLISHED_VURDERING).use {
                it.executeQuery().toList { toPVurdering() }
            }.map {
                it.toVurdering(
                    varsel = connection.getVarselForVurdering(it)
                )
            }
        }

    override fun createVurdering(vurdering: Vurdering, pdf: ByteArray?): Vurdering {
        database.connection.use { connection ->
            val pVurdering = connection.createVurdering(vurdering)
            if (pdf != null) {
                connection.createPdf(
                    vurderingId = pVurdering.id,
                    pdf = pdf,
                )
            }
            connection.commit()

            return pVurdering.toVurdering(
                varsel = connection.getVarselForVurdering(pVurdering)
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
            connection.createVarsel(
                vurderingId = pVurdering.id,
                varsel = vurdering.varsel,
            )
            connection.createPdf(
                vurderingId = pVurdering.id,
                pdf = pdf,
            )
            connection.commit()
        }
    }

    override fun update(vurdering: Vurdering) = database.connection.use { connection ->
        connection.prepareStatement(UPDATE_VURDERING).use {
            it.setString(1, vurdering.journalpostId?.value)
            it.setObject(2, nowUTC())
            it.setObject(3, vurdering.publishedAt)
            it.setString(4, vurdering.uuid.toString())
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
    }

    override fun getNotJournalforteVurderinger(): List<Pair<Vurdering, ByteArray>> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_NOT_JOURNALFORT_VURDERING).use {
                it.executeQuery()
                    .toList {
                        Pair(
                            toPVurdering(),
                            getBytes("pdf"),
                        )
                    }
            }.map { (pVurdering, pdf) -> Pair(pVurdering.toVurdering(null), pdf) }
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
            it.setObject(8, mapper.writeValueAsString(vurdering.document))
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
            it.setDate(5, Date.valueOf(varsel.svarfrist))
            it.setObject(6, varsel.svarfristExpiredPublishedAt)
            it.executeQuery().toList { toPVarsel() }.single()
        }
    }

    private fun Connection.createPdf(vurderingId: Int, pdf: ByteArray): PVurderingPdf =
        prepareStatement(CREATE_VURDERING_PDF).use {
            it.setString(1, UUID.randomUUID().toString())
            it.setObject(2, OffsetDateTime.now())
            it.setInt(3, vurderingId)
            it.setBytes(4, pdf)
            it.executeQuery().toList { toPVurderingPdf() }.single()
        }

    private fun Connection.getVarselForVurdering(vurdering: PVurdering): Varsel? =
        prepareStatement(GET_VARSEL_FOR_VURDERING).use {
            it.setInt(1, vurdering.id)
            it.executeQuery().toList { toPVarsel() }
        }.map { it.toVarsel() }.firstOrNull()

    companion object {
        private const val GET_VURDERING =
            """
                SELECT * FROM VURDERING WHERE personident=? ORDER BY created_at DESC
            """

        private const val GET_UNPUBLISHED_VURDERING =
            """
                SELECT * FROM VURDERING WHERE published_at IS NULL ORDER BY created_at ASC
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
                begrunnelse,
                document
            ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?::jsonb)
            RETURNING *
            """

        private const val UPDATE_VURDERING =
            """
                UPDATE VURDERING SET journalpost_id=?, updated_at=?, published_at=? WHERE uuid=?
            """

        private const val CREATE_VARSEL =
            """
            INSERT INTO VARSEL (
                id,
                uuid,
                created_at,
                updated_at,
                vurdering_id,
                svarfrist,
                svarfrist_expired_published_at             
            ) values (DEFAULT, ?, ?, ?, ?, ?, ?)
            RETURNING *
            """

        private const val CREATE_VURDERING_PDF =
            """
            INSERT INTO VURDERING_PDF (
                id,
                uuid,
                created_at,
                vurdering_id,
                pdf
            ) values (DEFAULT, ?, ?, ?, ?)
            RETURNING *
            """

        private const val GET_NOT_JOURNALFORT_VURDERING =
            """
                 SELECT vu.*, vup.pdf
                 FROM vurdering vu
                 INNER JOIN vurdering_pdf vup ON vu.id = vup.vurdering_id
                 WHERE vu.journalpost_id IS NULL
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
    begrunnelse = getString("begrunnelse"),
    document = mapper.readValue(
        getString("document"),
        object : TypeReference<List<DocumentComponent>>() {}
    ),
    journalpostId = getString("journalpost_id"),
    publishedAt = getObject("published_at", OffsetDateTime::class.java),
)

internal fun ResultSet.toPVurderingPdf(): PVurderingPdf = PVurderingPdf(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    vurderingId = getInt("vurdering_id"),
    pdf = getBytes("pdf"),
)

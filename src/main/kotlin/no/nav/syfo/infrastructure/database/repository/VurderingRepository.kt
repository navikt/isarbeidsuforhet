package no.nav.syfo.infrastructure.database.repository

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IVurderingRepository
import no.nav.syfo.domain.*
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.nowUTC
import java.sql.*
import java.sql.Date
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

    override fun getLatestVurderingForPersoner(
        personidenter: List<PersonIdent>,
    ): Map<PersonIdent, Vurdering> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_VURDERINGER).use { preparedStatement ->
                preparedStatement.setString(1, personidenter.joinToString(",") { it.value })
                preparedStatement.executeQuery().toList {
                    toPVurdering().toVurdering(
                        if (getString("varsel_uuid") != null) {
                            PVarsel(
                                id = getInt("varsel_id"),
                                uuid = UUID.fromString(getString("varsel_uuid")),
                                createdAt = getObject("varsel_created_at", OffsetDateTime::class.java),
                                updatedAt = getObject("varsel_updated_at", OffsetDateTime::class.java),
                                vurderingId = getInt("id"),
                                publishedAt = getObject("varsel_published_at", OffsetDateTime::class.java),
                                svarfrist = getDate("varsel_svarfrist").toLocalDate(),
                            ).toVarsel()
                        } else null
                    )
                }
            }.associateBy {
                // Den nyeste vurderingen blir valgt her siden lista er sortert med den nyeste til slutt
                it.personident
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

    override fun createVurdering(vurdering: Vurdering, pdf: ByteArray): Vurdering {
        database.connection.use { connection ->
            val pVurdering = connection.createVurdering(vurdering)

            connection.createPdf(
                vurderingId = pVurdering.id,
                pdf = pdf,
            )

            if (vurdering is Vurdering.Forhandsvarsel) {
                connection.createVarsel(
                    vurderingId = pVurdering.id,
                    varsel = vurdering.varsel,
                )
            }

            connection.commit()

            return pVurdering.toVurdering(
                varsel = connection.getVarselForVurdering(pVurdering)
            )
        }
    }

    override fun setJournalpostId(vurdering: Vurdering) = database.connection.use { connection ->
        connection.prepareStatement(UPDATE_JOURNALPOST_ID).use {
            it.setString(1, vurdering.journalpostId?.value)
            it.setObject(2, nowUTC())
            it.setString(3, vurdering.uuid.toString())
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
    }

    override fun setPublished(vurdering: Vurdering) = database.connection.use { connection ->
        connection.prepareStatement(UPDATE_PUBLISHED_AT).use {
            it.setObject(1, nowUTC())
            it.setObject(2, vurdering.publishedAt)
            it.setString(3, vurdering.uuid.toString())
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
            }.map { (pVurdering, pdf) ->
                Pair(
                    pVurdering.toVurdering(
                        varsel = connection.getVarselForVurdering(pVurdering)
                    ),
                    pdf
                )
            }
        }

    override fun updatePersonident(nyPersonident: PersonIdent, vurderinger: List<Vurdering>) = database.connection.use { connection ->
        connection.prepareStatement(UPDATE_PERSONIDENT).use {
            vurderinger.forEach { vurdering ->
                it.setString(1, nyPersonident.value)
                it.setString(2, vurdering.uuid.toString())
                val updated = it.executeUpdate()
                if (updated != 1) {
                    throw SQLException("Expected a single row to be updated, got update count $updated")
                }
            }
        }
        connection.commit()
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
            it.setObject(9, vurdering.gjelderFom)
            if (vurdering.arsak() != null) {
                it.setString(10, vurdering.arsak())
            } else {
                it.setNull(10, Types.CHAR)
            }
            it.setObject(11, vurdering.nayOppgaveDato())

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

        private const val GET_VURDERINGER =
            """
                SELECT vu.*,
                    va.id as varsel_id,
                    va.uuid as varsel_uuid,
                    va.created_at as varsel_created_at,
                    va.updated_at as varsel_updated_at,
                    va.svarfrist as varsel_svarfrist,
                    va.published_at as varsel_published_at
                FROM VURDERING vu LEFT OUTER JOIN VARSEL va ON (vu.id = va.vurdering_id) 
                WHERE vu.personident = ANY (string_to_array(?, ',')) 
                ORDER BY vu.created_at ASC
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
                document,
                gjelder_fom,
                arsak,
                nay_oppgave_dato
            ) values (DEFAULT, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
            RETURNING *
            """

        private const val UPDATE_JOURNALPOST_ID =
            """
                UPDATE VURDERING SET journalpost_id=?, updated_at=? WHERE uuid=?
            """

        private const val UPDATE_PUBLISHED_AT =
            """
                UPDATE VURDERING SET updated_at=?, published_at=? WHERE uuid=?
            """

        private const val UPDATE_PERSONIDENT =
            """
                UPDATE VURDERING SET personident=? WHERE uuid=?
            """

        private const val CREATE_VARSEL =
            """
            INSERT INTO VARSEL (
                id,
                uuid,
                created_at,
                updated_at,
                vurdering_id,
                svarfrist             
            ) values (DEFAULT, ?, ?, ?, ?, ?)
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
    arsak = getString("arsak"),
    document = mapper.readValue(
        getString("document"),
        object : TypeReference<List<DocumentComponent>>() {}
    ),
    journalpostId = getString("journalpost_id"),
    publishedAt = getObject("published_at", OffsetDateTime::class.java),
    gjelderFom = getDate("gjelder_fom")?.toLocalDate(),
    nayOppgaveDato = getDate("nay_oppgave_dato")?.toLocalDate(),
)

internal fun ResultSet.toPVurderingPdf(): PVurderingPdf = PVurderingPdf(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    vurderingId = getInt("vurdering_id"),
    pdf = getBytes("pdf"),
)

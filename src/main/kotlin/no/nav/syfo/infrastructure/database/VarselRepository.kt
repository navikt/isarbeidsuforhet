package no.nav.syfo.infrastructure.database

import com.fasterxml.jackson.core.type.TypeReference
import no.nav.syfo.application.IVarselRepository
import no.nav.syfo.domain.DocumentComponent
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.util.configuredJacksonMapper
import no.nav.syfo.util.nowUTC
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.*

private val mapper = configuredJacksonMapper()

class VarselRepository(private val database: DatabaseInterface) : IVarselRepository {

    override fun getUnpublishedVarsler(): List<Pair<PersonIdent, Varsel>> = database.connection.use { connection ->
        connection.prepareStatement(GET_UNPUBLISHED_VARSEL).use {
            it.executeQuery().toList { Pair(PersonIdent(getString("personident")), toPVarsel()) }
        }
    }.map { (personident, pVarsel) -> Pair(personident, pVarsel.toVarsel()) }

    override fun getExpiredVarsler(): List<Pair<PersonIdent, Varsel>> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_EXPIRED_VARSLER).use {
                it.executeQuery().toList { Pair(PersonIdent(getString("personident")), toPVarsel()) }
            }
        }.map { (personident, pVarsel) -> Pair(personident, pVarsel.toVarsel()) }

    override fun update(varsel: Varsel) = database.connection.use { connection ->
        connection.prepareStatement(UPDATE_VARSEL).use {
            it.setObject(1, varsel.publishedAt)
            it.setString(2, varsel.journalpostId)
            it.setObject(3, nowUTC())
            it.setString(4, varsel.uuid.toString())
            val updated = it.executeUpdate()
            if (updated != 1) {
                throw SQLException("Expected a single row to be updated, got update count $updated")
            }
        }
        connection.commit()
    }

    companion object {
        private const val GET_UNPUBLISHED_VARSEL =
            """
                SELECT vu.personident, v.* FROM varsel v
                INNER JOIN vurdering vu
                ON v.vurdering_id = vu.id
                WHERE v.journalpost_id IS NOT NULL AND v.published_at IS NULL
            """

        private const val UPDATE_VARSEL =
            """
                 UPDATE varsel
                 SET published_at = ?, journalpost_id = ?, updated_at = ?
                 WHERE uuid = ?
            """

        private const val GET_EXPIRED_VARSLER =
            """
                SELECT vu.personident, v.*
                FROM varsel v
                INNER JOIN vurdering vu
                ON v.vurdering_id = vu.id
                WHERE published_at < NOW() - interval '3 weeks';
            """
    }
}

internal fun ResultSet.toPVarsel(): PVarsel = PVarsel(
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
    publishedAt = getObject("published_at", OffsetDateTime::class.java),
)

internal fun ResultSet.toPVarselPdf(): PVarselPdf = PVarselPdf(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    varselId = getInt("varsel_id"),
    pdf = getBytes("pdf"),
)

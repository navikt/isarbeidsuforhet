package no.nav.syfo.infrastructure.database.repository

import no.nav.syfo.application.IVarselRepository
import no.nav.syfo.domain.JournalpostId
import no.nav.syfo.domain.PersonIdent
import no.nav.syfo.domain.Varsel
import no.nav.syfo.infrastructure.database.DatabaseInterface
import no.nav.syfo.infrastructure.database.toList
import no.nav.syfo.util.nowUTC
import java.sql.ResultSet
import java.sql.SQLException
import java.time.OffsetDateTime
import java.util.*

class VarselRepository(private val database: DatabaseInterface) : IVarselRepository {

    override fun getUnpublishedVarsler(): List<Triple<PersonIdent, JournalpostId, Varsel>> = database.connection.use { connection ->
        connection.prepareStatement(GET_UNPUBLISHED_VARSEL).use {
            it.executeQuery().toList { Triple(PersonIdent(getString("personident")), JournalpostId(getString("journalpost_id")), toPVarsel()) }
        }
    }.map { (personident, journalpostId, pVarsel) -> Triple(personident, journalpostId, pVarsel.toVarsel()) }

    override fun getUnpublishedExpiredVarsler(): List<Pair<PersonIdent, Varsel>> =
        database.connection.use { connection ->
            connection.prepareStatement(GET_UNPUBLISHED_EXPIRED_VARSLER).use {
                it.executeQuery().toList { Pair(PersonIdent(getString("personident")), toPVarsel()) }
            }
        }.map { (personident, pVarsel) -> Pair(personident, pVarsel.toVarsel()) }

    override fun update(varsel: Varsel) = database.connection.use { connection ->
        connection.prepareStatement(UPDATE_VARSEL).use {
            it.setObject(1, varsel.publishedAt)
            it.setObject(2, nowUTC())
            it.setObject(3, varsel.svarfristExpiredPublishedAt)
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
                SELECT vu.personident, vu.journalpost_id, v.* FROM varsel v
                INNER JOIN vurdering vu
                ON v.vurdering_id = vu.id
                WHERE vu.journalpost_id IS NOT NULL AND v.published_at IS NULL
            """

        private const val UPDATE_VARSEL =
            """
                 UPDATE varsel
                 SET published_at = ?, updated_at = ?, svarfrist_expired_published_at = ?
                 WHERE uuid = ?
            """

        private const val GET_UNPUBLISHED_EXPIRED_VARSLER =
            """
                SELECT vu.personident, v.*
                FROM varsel v
                INNER JOIN vurdering vu
                ON v.vurdering_id = vu.id
                WHERE svarfrist <= NOW() 
                    AND v.published_at IS NOT NULL 
                    AND svarfrist_expired_published_at IS NULL
                    AND NOT EXISTS (
                        SELECT 1
                        FROM vurdering vu2
                        WHERE vu2.personident = vu.personident
                            AND vu2.created_at > v.created_at
                            AND vu2.type = 'OPPFYLT'
                )
            """
    }
}

internal fun ResultSet.toPVarsel(): PVarsel = PVarsel(
    id = getInt("id"),
    uuid = UUID.fromString(getString("uuid")),
    createdAt = getObject("created_at", OffsetDateTime::class.java),
    updatedAt = getObject("updated_at", OffsetDateTime::class.java),
    vurderingId = getInt("vurdering_id"),
    publishedAt = getObject("published_at", OffsetDateTime::class.java),
    svarfrist = getDate("svarfrist").toLocalDate(),
    svarfristExpiredPublishedAt = getObject("svarfrist_expired_published_at", OffsetDateTime::class.java),
)

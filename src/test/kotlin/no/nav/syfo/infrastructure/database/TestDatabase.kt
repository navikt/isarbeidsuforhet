package no.nav.syfo.infrastructure.database

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import no.nav.syfo.infrastructure.database.repository.*
import org.flywaydb.core.Flyway
import java.sql.Connection
import java.util.*

class TestDatabase : DatabaseInterface {
    private val pg: EmbeddedPostgres = try {
        EmbeddedPostgres.start()
    } catch (e: Exception) {
        EmbeddedPostgres.builder().start()
    }

    override val connection: Connection
        get() = pg.postgresDatabase.connection.apply { autoCommit = false }

    init {

        Flyway.configure().run {
            dataSource(pg.postgresDatabase).validateMigrationNaming(true).load().migrate()
        }
    }

    fun stop() {
        pg.close()
    }
}

fun TestDatabase.dropData() {
    val queryList = listOf(
        """
        DELETE FROM VURDERING
        """.trimIndent(),
        """
        DELETE FROM VARSEL
        """.trimIndent(),
        """
        DELETE FROM VARSEL_PDF
        """.trimIndent(),
    )

    this.connection.use { connection ->
        queryList.forEach { query ->
            connection.prepareStatement(query).execute()
        }
        connection.commit()
    }
}

private const val queryGetVurdering =
    """
        SELECT *
        FROM vurdering
        WHERE uuid = ?
    """

fun TestDatabase.getVurdering(
    uuid: UUID,
): PVurdering? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetVurdering).use {
            it.setString(1, uuid.toString())
            it.executeQuery()
                .toList { toPVurdering() }
                .firstOrNull()
        }
    }

private const val queryGetVurderingPdf =
    """
        SELECT pdf.*
        FROM vurdering_pdf pdf INNER JOIN vurdering vu ON vu.id=pdf.vurdering_id 
        WHERE vu.uuid = ?
    """

fun TestDatabase.getVurderingPdf(
    vurderingUuid: UUID,
): PVurderingPdf? =
    this.connection.use { connection ->
        connection.prepareStatement(queryGetVurderingPdf).use {
            it.setString(1, vurderingUuid.toString())
            it.executeQuery()
                .toList { toPVurderingPdf() }
                .firstOrNull()
        }
    }

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")

    fun stop() {
    }
}

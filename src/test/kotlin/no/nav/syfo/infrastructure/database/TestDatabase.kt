package no.nav.syfo.infrastructure.database

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres
import org.flywaydb.core.Flyway
import java.sql.Connection

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

fun DatabaseInterface.dropData() {
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

class TestDatabaseNotResponding : DatabaseInterface {

    override val connection: Connection
        get() = throw Exception("Not working")

    fun stop() {
    }
}

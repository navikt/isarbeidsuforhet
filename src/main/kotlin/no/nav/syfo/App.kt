package no.nav.syfo

import com.typesafe.config.ConfigFactory
import io.ktor.server.application.*
import io.ktor.server.config.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import no.nav.syfo.api.apiModule
import no.nav.syfo.application.service.VurderingService
import no.nav.syfo.application.service.VarselService
import no.nav.syfo.domain.Varsel
import no.nav.syfo.infrastructure.clients.azuread.AzureAdClient
import no.nav.syfo.infrastructure.cronjob.launchCronjobs
import no.nav.syfo.infrastructure.database.applicationDatabase
import no.nav.syfo.infrastructure.database.databaseModule
import no.nav.syfo.infrastructure.database.repository.VarselRepository
import no.nav.syfo.infrastructure.database.repository.VurderingRepository
import no.nav.syfo.infrastructure.clients.dokarkiv.DokarkivClient
import no.nav.syfo.infrastructure.journalforing.JournalforingService
import no.nav.syfo.infrastructure.kafka.esyfovarsel.ArbeidstakerForhandsvarselProducer
import no.nav.syfo.infrastructure.kafka.esyfovarsel.KafkaArbeidstakervarselSerializer
import no.nav.syfo.infrastructure.clients.pdfgen.PdfGenClient
import no.nav.syfo.infrastructure.clients.pdfgen.VurderingPdfService
import no.nav.syfo.infrastructure.clients.pdl.PdlClient
import no.nav.syfo.infrastructure.clients.veiledertilgang.VeilederTilgangskontrollClient
import no.nav.syfo.infrastructure.clients.wellknown.getWellKnown
import no.nav.syfo.infrastructure.kafka.*
import org.apache.kafka.clients.producer.KafkaProducer
import org.slf4j.LoggerFactory
import java.util.concurrent.TimeUnit

const val applicationPort = 8080

fun main() {
    val applicationState = ApplicationState()
    val environment = Environment()
    val logger = LoggerFactory.getLogger("ktor.application")

    val wellKnownInternalAzureAD = getWellKnown(
        wellKnownUrl = environment.azure.appWellKnownUrl
    )
    val azureAdClient = AzureAdClient(
        azureEnvironment = environment.azure
    )
    val pdlClient = PdlClient(
        azureAdClient = azureAdClient,
        pdlEnvironment = environment.clients.pdl,
    )
    val dokarkivClient = DokarkivClient(
        azureAdClient = azureAdClient,
        dokarkivEnvironment = environment.clients.dokarkiv,
    )
    val veilederTilgangskontrollClient =
        VeilederTilgangskontrollClient(
            azureAdClient = azureAdClient,
            clientEnvironment = environment.clients.istilgangskontroll
        )

    val pdfGenClient = PdfGenClient(
        pdfGenBaseUrl = environment.clients.ispdfgen.baseUrl,
    )

    val journalforingService = JournalforingService(
        dokarkivClient = dokarkivClient,
        pdlClient = pdlClient,
    )

    val vurderingPdfService = VurderingPdfService(
        pdfGenClient = pdfGenClient,
        pdlClient = pdlClient,
    )

    val arbeidstakerForhandsvarselProducer = ArbeidstakerForhandsvarselProducer(
        kafkaProducer = KafkaProducer(
            kafkaAivenProducerConfig<KafkaArbeidstakervarselSerializer>(kafkaEnvironment = environment.kafka)
        )
    )
    val vurderingProducer = VurderingProducer(
        producer = KafkaProducer(
            kafkaAivenProducerConfig<VurderingRecordSerializer>(kafkaEnvironment = environment.kafka)
        )
    )

    Varsel.svarfristDager = environment.svarfristDager
    lateinit var vurderingService: VurderingService
    lateinit var varselService: VarselService

    val applicationEngineEnvironment =
        applicationEngineEnvironment {
            log = logger
            config = HoconApplicationConfig(ConfigFactory.load())
            connector {
                port = applicationPort
            }
            module {
                databaseModule(
                    databaseEnvironment = environment.database,
                )

                val vurderingRepository = VurderingRepository(database = applicationDatabase)
                vurderingService = VurderingService(
                    vurderingRepository = vurderingRepository,
                    vurderingPdfService = vurderingPdfService,
                    journalforingService = journalforingService,
                    vurderingProducer = vurderingProducer,
                )
                val varselRepository = VarselRepository(database = applicationDatabase)
                val varselProducer = VarselProducer(
                    arbeidstakerForhandsvarselProducer = arbeidstakerForhandsvarselProducer,
                )
                varselService = VarselService(
                    varselRepository = varselRepository,
                    varselProducer = varselProducer,
                )

                apiModule(
                    applicationState = applicationState,
                    database = applicationDatabase,
                    environment = environment,
                    wellKnownInternalAzureAD = wellKnownInternalAzureAD,
                    veilederTilgangskontrollClient = veilederTilgangskontrollClient,
                    vurderingService = vurderingService,
                )
            }
        }

    applicationEngineEnvironment.monitor.subscribe(ApplicationStarted) {
        applicationState.ready = true
        logger.info("Application is ready, running Java VM ${Runtime.version()}")

        launchCronjobs(
            applicationState = applicationState,
            environment = environment,
            vurderingService = vurderingService,
            varselService = varselService,
        )
    }

    val server = embeddedServer(
        factory = Netty,
        environment = applicationEngineEnvironment
    ) {
        connectionGroupSize = 8
        workerGroupSize = 8
        callGroupSize = 16
    }

    Runtime.getRuntime().addShutdownHook(
        Thread { server.stop(10, 10, TimeUnit.SECONDS) }
    )

    server.start(wait = true)
}

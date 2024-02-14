package no.nav.syfo.infrastructure.metric

import io.micrometer.prometheus.PrometheusConfig
import io.micrometer.prometheus.PrometheusMeterRegistry

const val METRICS_NS = "isarbeidsuforhet"

val METRICS_REGISTRY = PrometheusMeterRegistry(PrometheusConfig.DEFAULT)

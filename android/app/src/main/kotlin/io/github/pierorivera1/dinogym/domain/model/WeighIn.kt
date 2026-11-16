package io.github.pierorivera1.dinogym.domain.model

/** One body weight reading in kilograms, taken at [measuredAt] (epoch milliseconds). */
data class WeighIn(
    val id: String,
    val measuredAt: Long,
    val weightKg: Double,
)

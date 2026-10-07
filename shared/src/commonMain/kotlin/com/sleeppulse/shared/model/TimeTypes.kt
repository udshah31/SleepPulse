package com.sleeppulse.shared.model

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** Interprets a session timestamp in the same local zone used by the platform. */
fun localDateAt(epochMillis: Long, timeZone: TimeZone = TimeZone.currentSystemDefault()): LocalDate =
    Instant.fromEpochMilliseconds(epochMillis).toLocalDateTime(timeZone).date

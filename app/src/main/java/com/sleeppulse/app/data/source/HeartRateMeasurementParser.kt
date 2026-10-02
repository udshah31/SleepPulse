package com.sleeppulse.app.data.source

/** One decoded Heart Rate Measurement (GATT 0x2A37) notification. */
data class HeartRateMeasurement(val bpm: Int, val rrIntervalsMillis: List<Double>)

/**
 * Pure decoder for the Heart Rate Measurement characteristic. Flags bit 0: bpm is UINT16; bit 3:
 * a 2-byte energy-expended field follows the bpm; bit 4: RR-intervals (UINT16, 1/1024 s) follow.
 */
object HeartRateMeasurementParser {

    fun parse(bytes: ByteArray?): HeartRateMeasurement? {
        if (bytes == null || bytes.isEmpty()) return null
        val flags = bytes[0].toInt() and 0xFF
        var i = 1

        val bpm = if (flags and 0x01 != 0) {
            if (bytes.size < i + 2) return null
            u16(bytes, i).also { i += 2 }
        } else {
            if (bytes.size < i + 1) return null
            (bytes[i].toInt() and 0xFF).also { i += 1 }
        }

        if (flags and 0x08 != 0) i += 2

        val rr = mutableListOf<Double>()
        if (flags and 0x10 != 0) {
            while (i + 1 < bytes.size) {
                rr += u16(bytes, i) * 1000.0 / 1024.0
                i += 2
            }
        }
        return HeartRateMeasurement(bpm, rr)
    }

    private fun u16(b: ByteArray, i: Int) = (b[i].toInt() and 0xFF) or ((b[i + 1].toInt() and 0xFF) shl 8)
}

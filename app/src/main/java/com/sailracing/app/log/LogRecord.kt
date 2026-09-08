package com.sailracing.app.log

/**
 * One line of a session log: when it happened, what kind of thing it was, and the values that describe it.
 *
 * It is written as JSON, one object per line ("JSON lines"): a format that can be read by eye, followed
 * with `tail -f`, filtered with `grep`, and parsed by any script. One line at a time also means that the
 * log of a session that never ended - a crash, a flat battery, a phone thrown in the bilge - is still a
 * valid log up to the moment it stopped.
 *
 * @property timeMillis the session clock's time, which is the wall clock unless a simulation is speeding
 *   it up. It is the first field of every line so that a log sorts by time as text.
 */
data class LogRecord(val timeMillis: Long, val type: String, val values: List<Pair<String, Any?>> = emptyList()) {

    fun toJsonLine(): String = buildString {
        append("{\"t\":").append(timeMillis).append(",\"type\":")
        appendJson(type)
        for ((key, value) in values) {
            if (value == null) continue
            append(',')
            appendJson(key)
            append(':')
            appendJson(value)
        }
        append('}')
    }

    companion object {
        fun of(timeMillis: Long, type: String, vararg values: Pair<String, Any?>): LogRecord =
            LogRecord(timeMillis, type, values.toList())

        /** JSON has no NaN and no infinity, and a value the log cannot state is better left out. */
        private fun StringBuilder.appendJson(value: Any) {
            when (value) {
                is Boolean, is Int, is Long -> append(value)
                is Double -> if (value.isFinite()) append(value) else append("null")
                is Float -> if (value.isFinite()) append(value) else append("null")
                is Number -> append(value)
                is Enum<*> -> appendQuoted(value.name)
                else -> appendQuoted(value.toString())
            }
        }

        private fun StringBuilder.appendQuoted(text: String) {
            append('"')
            for (character in text) {
                when (character) {
                    '"' -> append("\\\"")
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    else -> if (character < ' ') append("\\u%04x".format(character.code)) else append(character)
                }
            }
            append('"')
        }
    }
}

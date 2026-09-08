package com.sailracing.app.log

import com.sailracing.domain.timer.Cue
import org.junit.Test
import kotlin.test.assertEquals

/** A log line is JSON, and stays JSON whatever is put in it. */
class LogRecordTest {

    @Test
    fun timeAndTypeComeFirstAndValuesFollow() {
        val record = LogRecord.of(1_700_000_000_000L, "state", "speedMps" to 3.5, "cue" to Cue.MINUTE, "points" to 12L)
        assertEquals(
            """{"t":1700000000000,"type":"state","speedMps":3.5,"cue":"MINUTE","points":12}""",
            record.toJsonLine(),
        )
    }

    @Test
    fun whatCannotBeStatedIsLeftOut() {
        val record = LogRecord.of(
            0L,
            "event",
            "missing" to null,
            "nan" to Double.NaN,
            "infinite" to Double.POSITIVE_INFINITY,
            "float" to Float.NaN,
            "finiteFloat" to 1.5f,
            "flag" to true,
            "count" to 3,
            "big" to java.math.BigDecimal("1.25"),
        )
        assertEquals(
            """{"t":0,"type":"event","nan":null,"infinite":null,"float":null,"finiteFloat":1.5,"flag":true,"count":3,"big":1.25}""",
            record.toJsonLine(),
        )
    }

    @Test
    fun textIsEscaped() {
        // A quote, a backslash, a newline, a tab and a byte no editor shows: all of them survive as JSON.
        val text = "a \"quote\", a \\, a \nline\t and a \u0001 byte"
        assertEquals(
            """{"t":0,"type":"event","text":"a \"quote\", a \\, a \nline\t and a \u0001 byte"}""",
            LogRecord.of(0L, "event", "text" to text).toJsonLine(),
        )
    }
}

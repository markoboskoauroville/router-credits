package com.mantra.routercredits

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplyTest {
    @Test fun balance() {
        val b = Reply.parse("Postovani, do 14.10.2026 u 11:57 iskoristili ste 73062.82 od ukupno 102400.00 MB. " +
            "Vase stanje racuna je 0,00 EUR i bonus 1,50 EUR.")!!
        assertEquals("2026-10-14 11:57", b.at)
        assertEquals(29337.18, b.leftMb, 0.001)
        assertEquals(0.0, b.eur!!, 0.0)
        assertEquals(1.5, b.bonusEur!!, 0.0)
    }

    @Test fun overdrawn() {
        val b = Reply.parse("Postovani, do 7.9.2026 u 8:05 iskoristili ste 2067.00 od ukupno 0.00 MB")!!
        assertEquals("2026-09-07 08:05", b.at)
        assertEquals(-2067.0, b.leftMb, 0.001)
    }

    @Test fun notABalance() {
        assertNull(Reply.parse("Postovani, vas NET2GO 20 GB paket istjece za 3 dana."))
    }
}

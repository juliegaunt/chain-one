package io.github.juliegaunt.chainone.ui.pdf

import io.github.juliegaunt.chainone.data.db.RowMarker
import org.junit.Assert.assertEquals
import org.junit.Test

class RowMarkerStepTest {

    @Test
    fun movesByItsOwnHeight() {
        assertEquals(RowMarker(0, 0.3f, 0.1f), stepped(RowMarker(0, 0.2f, 0.1f), 1, pageCount = 2).rounded())
        assertEquals(RowMarker(0, 0.1f, 0.1f), stepped(RowMarker(0, 0.2f, 0.1f), -1, pageCount = 2).rounded())
    }

    @Test
    fun goesOnToTheNextOrPreviousPageAtAnEdge() {
        assertEquals(RowMarker(1, 0f, 0.1f), stepped(RowMarker(0, 0.85f, 0.1f), 1, pageCount = 2))
        assertEquals(RowMarker(0, 0.9f, 0.1f), stepped(RowMarker(1, 0.05f, 0.1f), -1, pageCount = 2))
    }

    @Test
    fun stopsAtTheEndsOfTheDocument() {
        assertEquals(RowMarker(1, 0.9f, 0.1f), stepped(RowMarker(1, 0.85f, 0.1f), 1, pageCount = 2))
        assertEquals(RowMarker(0, 0f, 0.1f), stepped(RowMarker(0, 0.05f, 0.1f), -1, pageCount = 2))
    }

    private fun RowMarker.rounded() = copy(top = Math.round(top * 1000) / 1000f)
}

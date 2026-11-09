package io.github.yarkoody.scalesync.data

import io.github.yarkoody.scalesync.core.BodyMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class HistoryDeleteTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun weighIn(id: Long, kg: Float = 74f, synced: Boolean = true) =
        WeighIn(id, 1, BodyMetrics(weightKg = kg, impedance = null, bmi = null), synced)

    @Test
    fun deleting_removes_the_row_and_queues_health_connect_removal() {
        val h = History(tmp.root)
        h.upsert(weighIn(1)); h.upsert(weighIn(2))
        h.delete(1)
        assertEquals(listOf(2L), h.all().map { it.sessionId })
        assertTrue(h.isDeleted(1))
        assertEquals(listOf(1L), h.pendingHealthConnectRemovals())
    }

    @Test
    fun a_deleted_weigh_in_never_comes_back() {
        val h = History(tmp.root)
        h.upsert(weighIn(1, synced = false))
        h.delete(1)
        // e.g. the impedance reading for the same session arrives later, or a profile recalculation
        h.upsert(weighIn(1, kg = 74.2f))
        assertTrue(h.all().isEmpty())
    }

    @Test
    fun removal_is_remembered_across_restarts() {
        History(tmp.root).apply { upsert(weighIn(1)); upsert(weighIn(2)); delete(1); delete(2) }
        val reopened = History(tmp.root)
        assertEquals(listOf(1L, 2L), reopened.pendingHealthConnectRemovals())
        reopened.markRemovedFromHealthConnect(1)
        val again = History(tmp.root)
        assertEquals(listOf(2L), again.pendingHealthConnectRemovals())
        assertTrue("still blocked from coming back", again.isDeleted(1))
    }

    @Test
    fun deleting_an_unknown_weigh_in_is_harmless() {
        val h = History(tmp.root)
        h.upsert(weighIn(2))
        h.delete(99)
        assertEquals(listOf(2L), h.all().map { it.sessionId })
        assertFalse(h.isDeleted(2))
    }
}

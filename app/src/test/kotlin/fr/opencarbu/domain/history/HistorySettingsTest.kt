package fr.opencarbu.domain.history

import fr.opencarbu.domain.model.HistorySettings
import kotlin.test.Test
import kotlin.test.assertEquals

class HistorySettingsTest {
    @Test
    fun everythingIsOffByDefaultAndWifiOnly() {
        val s = HistorySettings()
        assertEquals(false, s.historyEnabled)
        assertEquals(false, s.backgroundRefresh)
        assertEquals(true, s.backgroundWifiOnly)
        assertEquals(6, s.retentionMonths)
    }
}

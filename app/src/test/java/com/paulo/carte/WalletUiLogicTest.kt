package com.paulo.carte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletUiLogicTest {
    @Test fun categorizesCommonStores() {
        assertEquals("Supermarchés", WalletUiLogic.categoryForMerchant("Super U"))
        assertEquals("Supermarchés", WalletUiLogic.categoryForMerchant("E.Leclerc"))
        assertEquals("Mode", WalletUiLogic.categoryForMerchant("Zara"))
    }

    @Test fun createsShortMerchantBadge() {
        assertEquals("U", WalletUiLogic.badgeForMerchant("Super U"))
        assertEquals("IM", WalletUiLogic.badgeForMerchant("Intermarché"))
        assertEquals("C", WalletUiLogic.badgeForMerchant("Carrefour"))
    }

    @Test fun validatesAffiliateLinks() {
        assertTrue(WalletUiLogic.isValidWebUrl("https://example.com/offre"))
        assertFalse(WalletUiLogic.isValidWebUrl("javascript:alert(1)"))
        assertFalse(WalletUiLogic.isValidWebUrl("example.com"))
    }
}

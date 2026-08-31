package com.paulo.carte

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletModelsTest {
    @Test
    fun detectsKnownMerchantFromCardText() {
        assertEquals("Super U", MerchantDetector.detect("CARTE U SUPER U LE MANS"))
        assertEquals("Intermarché", MerchantDetector.detect("INTERMARCHE CARTE DE FIDELITE"))
        assertEquals("E.Leclerc", MerchantDetector.detect("E.LECLERC MA CARTE"))
        assertEquals("Carrefour", MerchantDetector.detect("CARREFOUR BONUS"))
    }

    @Test
    fun fallsBackToFirstUsefulLine() {
        assertEquals("Boulangerie Martin", MerchantDetector.detect("Boulangerie Martin\nCarte fidélité\n123456"))
    }

    @Test
    fun affiliateAdRequiresHttpUrlAndTitle() {
        assertTrue(AffiliateAd("Promo jardin", "-10%", "https://example.com/aff", true).isValid())
        assertFalse(AffiliateAd("", "Promo", "https://example.com", true).isValid())
        assertFalse(AffiliateAd("Promo", "Promo", "javascript:alert(1)", true).isValid())
    }
}

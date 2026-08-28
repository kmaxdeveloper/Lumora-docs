package uz.kmax.documents.domain.ads

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import uz.kmax.documents.domain.model.PremiumEntitlement
import uz.kmax.documents.domain.repository.BillingRepository

class AdsManagerTest {

    @Test
    fun `isNoAdsActive returns true when entitlement is PREMIUM`() {
        // Arrange
        val billingRepository = mockk<BillingRepository>()
        every { billingRepository.entitlement } returns MutableStateFlow(PremiumEntitlement.PREMIUM)

        // Act
        val result = AdsManager.isNoAdsActive(billingRepository)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `isNoAdsActive returns false when entitlement is FREE`() {
        // Arrange
        val billingRepository = mockk<BillingRepository>()
        every { billingRepository.entitlement } returns MutableStateFlow(PremiumEntitlement.FREE)

        // Act
        val result = AdsManager.isNoAdsActive(billingRepository)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `isNoAdsActive returns false when entitlement is UNKNOWN`() {
        // Arrange
        val billingRepository = mockk<BillingRepository>()
        every { billingRepository.entitlement } returns MutableStateFlow(PremiumEntitlement.UNKNOWN)

        // Act
        val result = AdsManager.isNoAdsActive(billingRepository)

        // Assert
        assertFalse(result)
    }
}

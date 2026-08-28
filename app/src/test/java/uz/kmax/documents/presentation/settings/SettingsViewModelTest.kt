package uz.kmax.documents.presentation.settings

import app.cash.turbine.test
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import uz.kmax.documents.data.local.PreferenceManager
import uz.kmax.documents.domain.model.PremiumEntitlement
import uz.kmax.documents.domain.repository.BillingRepository
import uz.kmax.documents.domain.repository.DocumentRepository

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    private lateinit var viewModel: SettingsViewModel
    private val documentRepository = mockk<DocumentRepository>()
    private val preferenceManager = mockk<PreferenceManager>(relaxed = true)
    private val billingRepository = mockk<BillingRepository>(relaxed = true)
    
    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        
        every { billingRepository.entitlement } returns MutableStateFlow(PremiumEntitlement.FREE)
        
        viewModel = SettingsViewModel(
            documentRepository,
            preferenceManager,
            billingRepository
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `updateAppearance calls preferenceManager`() {
        // Arrange
        val mode = 1

        // Act
        viewModel.updateAppearance(mode)

        // Assert
        verify { preferenceManager.appearance = mode }
    }

    @Test
    fun `setDebugNoAds calls billingRepository with correct entitlement`() {
        // Act
        viewModel.setDebugNoAds(true)
        
        // Assert
        verify { billingRepository.setDebugEntitlementOverride(PremiumEntitlement.PREMIUM) }

        // Act
        viewModel.setDebugNoAds(false)

        // Assert
        verify { billingRepository.setDebugEntitlementOverride(null) }
    }

    @Test
    fun `entitlement flow correctly reflects billingRepository entitlement`() = runTest {
        // Arrange
        val entitlementFlow = MutableStateFlow(PremiumEntitlement.FREE)
        every { billingRepository.entitlement } returns entitlementFlow
        
        // Re-init to pick up the mocked flow
        viewModel = SettingsViewModel(documentRepository, preferenceManager, billingRepository)

        viewModel.entitlement.test {
            assertEquals(PremiumEntitlement.FREE, awaitItem())
            
            entitlementFlow.value = PremiumEntitlement.PREMIUM
            assertEquals(PremiumEntitlement.PREMIUM, awaitItem())
        }
    }
}

package uz.kmax.documents.presentation.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import uz.kmax.documents.domain.repository.BillingRepository

class PremiumViewModel(
    private val billingRepository: BillingRepository
) : ViewModel() {

    val entitlement = billingRepository.entitlement
    val products = billingRepository.products

    private val _purchaseResult = MutableSharedFlow<Result<Unit>>()
    val purchaseResult = _purchaseResult.asSharedFlow()

    fun purchase(activity: android.app.Activity, productId: String) {
        viewModelScope.launch {
            val result = billingRepository.launchPurchaseFlow(activity, productId)
            _purchaseResult.emit(result)
        }
    }

    fun restore() {
        viewModelScope.launch {
            val result = billingRepository.restorePurchases()
            _purchaseResult.emit(result)
        }
    }

    class Factory(private val billingRepository: BillingRepository) : ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return PremiumViewModel(billingRepository) as T
        }
    }
}

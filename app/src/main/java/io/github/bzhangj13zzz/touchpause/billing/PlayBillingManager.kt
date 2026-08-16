package io.github.bzhangj13zzz.touchpause.billing

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.annotation.StringRes
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import io.github.bzhangj13zzz.touchpause.R

/**
 * Connects the settings screen to TouchPause's single Google Play product.
 *
 * The manager queries current ownership on every connection, caches a valid entitlement for
 * offline use, acknowledges new purchases, and exposes the Play-localized lifetime price.
 */
class PlayBillingManager(
    context: Context,
    private val entitlementStore: EntitlementStore,
    private val listener: Listener
) : PurchasesUpdatedListener {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val billingClient = BillingClient.newBuilder(context.applicationContext)
        .setListener(this)
        .enablePendingPurchases(
            PendingPurchasesParams.newBuilder().enableOneTimeProducts().build()
        )
        .build()

    private var productDetails: ProductDetails? = null
    private var connectionInProgress = false
    private val availabilityTimeout = Runnable {
        connectionInProgress = false
        markProductUnavailable()
    }

    var productQueryComplete: Boolean = false
        private set

    val formattedPrice: String?
        get() = productDetails?.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.formattedPrice

    fun connect() {
        if (billingClient.isReady) {
            refreshPurchasesAndProduct()
            return
        }
        if (connectionInProgress) return

        connectionInProgress = true
        scheduleAvailabilityTimeout()
        billingClient.startConnection(object : BillingClientStateListener {
            override fun onBillingSetupFinished(result: BillingResult) {
                connectionInProgress = false
                if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                    refreshPurchasesAndProduct()
                } else {
                    markProductUnavailable()
                }
            }

            override fun onBillingServiceDisconnected() {
                connectionInProgress = false
                markProductUnavailable()
            }
        })
    }

    /** Starts Google's purchase UI using the current localized product offer. */
    fun launchLifetimePurchase(activity: Activity): Boolean {
        val details = productDetails ?: return false
        val offer = details.oneTimePurchaseOfferDetailsList?.firstOrNull() ?: return false
        val productParamsBuilder = BillingFlowParams.ProductDetailsParams.newBuilder()
            .setProductDetails(details)
        offer.offerToken?.let(productParamsBuilder::setOfferToken)
        val productParams = productParamsBuilder.build()
        val result = billingClient.launchBillingFlow(
            activity,
            BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(listOf(productParams))
                .build()
        )
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            listener.onBillingUnavailable(R.string.billing_unavailable)
            return false
        }
        return true
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: List<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(
                purchases.orEmpty(),
                announcePurchase = true,
                clearWhenMissing = false
            )
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwnedPurchases()
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            else -> listener.onBillingUnavailable(R.string.purchase_failed)
        }
    }

    fun close() {
        mainHandler.removeCallbacks(availabilityTimeout)
        billingClient.endConnection()
    }

    private fun refreshPurchasesAndProduct() {
        productQueryComplete = false
        listener.onBillingStateChanged()
        scheduleAvailabilityTimeout()
        queryOwnedPurchases()
        queryProductDetails()
    }

    private fun queryOwnedPurchases() {
        val params = QueryPurchasesParams.newBuilder()
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                processPurchases(
                    purchases,
                    announcePurchase = false,
                    clearWhenMissing = true
                )
            }
        }
    }

    private fun queryProductDetails() {
        val product = QueryProductDetailsParams.Product.newBuilder()
            .setProductId(LIFETIME_PRODUCT_ID)
            .setProductType(BillingClient.ProductType.INAPP)
            .build()
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(product))
            .build()
        billingClient.queryProductDetailsAsync(params) { result, detailsResult ->
            mainHandler.removeCallbacks(availabilityTimeout)
            productQueryComplete = true
            productDetails = if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                detailsResult.productDetailsList.firstOrNull {
                    it.productId == LIFETIME_PRODUCT_ID
                }
            } else {
                null
            }
            listener.onBillingStateChanged()
        }
    }

    /** Prevents a missing or outdated Play Store from leaving settings in a permanent loading state. */
    private fun scheduleAvailabilityTimeout() {
        mainHandler.removeCallbacks(availabilityTimeout)
        mainHandler.postDelayed(availabilityTimeout, AVAILABILITY_TIMEOUT_MS)
    }

    private fun markProductUnavailable() {
        mainHandler.removeCallbacks(availabilityTimeout)
        productDetails = null
        productQueryComplete = true
        listener.onBillingStateChanged()
    }

    private fun processPurchases(
        purchases: List<Purchase>,
        announcePurchase: Boolean,
        clearWhenMissing: Boolean
    ) {
        val lifetimePurchase = purchases.firstOrNull { purchase ->
            LIFETIME_PRODUCT_ID in purchase.products &&
                purchase.purchaseState == Purchase.PurchaseState.PURCHASED
        }
        if (lifetimePurchase == null) {
            if (clearWhenMissing && entitlementStore.setLifetimeUnlocked(false)) {
                listener.onBillingStateChanged()
            }
            if (purchases.any { it.purchaseState == Purchase.PurchaseState.PENDING }) {
                listener.onBillingUnavailable(R.string.purchase_pending)
            }
            return
        }

        val changed = entitlementStore.setLifetimeUnlocked(true)
        if (changed || announcePurchase) listener.onBillingStateChanged()
        if (announcePurchase) listener.onPurchaseCompleted()

        if (!lifetimePurchase.isAcknowledged) {
            val params = AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(lifetimePurchase.purchaseToken)
                .build()
            billingClient.acknowledgePurchase(params) { result ->
                if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                    listener.onBillingUnavailable(R.string.purchase_acknowledgement_failed)
                }
            }
        }
    }

    interface Listener {
        fun onBillingStateChanged()
        fun onPurchaseCompleted()
        fun onBillingUnavailable(@StringRes message: Int)
    }

    companion object {
        const val LIFETIME_PRODUCT_ID = "lifetime_access"
        private const val AVAILABILITY_TIMEOUT_MS = 5_000L
    }
}

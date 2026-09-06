package com.junchen.jingdu

import android.app.Activity
import android.content.Context
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

internal data class BillingState(
    val unlocked: Boolean,
    val available: Boolean,
    val connected: Boolean,
    val price: String?,
)

internal class BillingManager(
    private val activity: Activity,
    private val onState: (BillingState) -> Unit,
    private val onMessage: (String) -> Unit,
) : PurchasesUpdatedListener, BillingClientStateListener {

    private val preferences = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val errorLog = ProductErrorLog(activity)
    private var unlocked = preferences.getBoolean(KEY_CACHED_PRO, false)
    private var connected = false
    private var productDetails: ProductDetails? = null
    private var offerToken: String? = null
    private var formattedPrice: String? = null

    private val billingClient = BillingClient.newBuilder(activity.applicationContext)
        .setListener(this)
        .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
        .enableAutoServiceReconnection()
        .build()

    fun start() {
        publish()
        if (billingClient.isReady) {
            connected = true
            queryOwned(showResult = false)
            queryProduct()
        } else billingClient.startConnection(this)
    }

    fun close() { if (billingClient.isReady) billingClient.endConnection() }

    fun purchase() {
        if (unlocked) {
            onMessage(activity.getString(R.string.billing_already_unlocked))
            return
        }
        val details = productDetails
        val token = offerToken
        if (!billingClient.isReady || details == null || token.isNullOrEmpty()) {
            errorLog.record(ProductErrorCode.BILLING_UNAVAILABLE, "billing.purchase")
            onMessage(activity.getString(R.string.billing_unavailable))
            return
        }
        val productParams = BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(details).setOfferToken(token).build()
        val result = billingClient.launchBillingFlow(activity, BillingFlowParams.newBuilder().setProductDetailsParamsList(listOf(productParams)).build())
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            errorLog.record(ProductErrorCode.BILLING_LAUNCH_FAILED, "billing.purchase")
            onMessage(activity.getString(R.string.billing_launch_failed, result.debugMessage))
        }
    }

    fun restore() {
        if (!billingClient.isReady) {
            start()
            onMessage(activity.getString(R.string.billing_restoring))
            return
        }
        queryOwned(showResult = true)
    }

    override fun onBillingSetupFinished(result: BillingResult) {
        connected = result.responseCode == BillingClient.BillingResponseCode.OK
        if (!connected) errorLog.record(ProductErrorCode.BILLING_UNAVAILABLE, "billing.connect")
        publish()
        if (!connected) return
        queryOwned(showResult = false)
        queryProduct()
    }

    override fun onBillingServiceDisconnected() {
        connected = false
        publish()
    }

    override fun onPurchasesUpdated(result: BillingResult, purchases: MutableList<Purchase>?) {
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> processPurchases(purchases.orEmpty(), authoritative = false)
            BillingClient.BillingResponseCode.USER_CANCELED -> Unit
            BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED -> queryOwned(showResult = true)
            else -> {
                errorLog.record(ProductErrorCode.BILLING_UPDATE_FAILED, "billing.update")
                onMessage(activity.getString(R.string.billing_incomplete, result.debugMessage))
            }
        }
    }

    private fun queryProduct() {
        val params = QueryProductDetailsParams.newBuilder()
            .setProductList(listOf(QueryProductDetailsParams.Product.newBuilder().setProductId(PRODUCT_ID).setProductType(BillingClient.ProductType.INAPP).build()))
            .build()
        billingClient.queryProductDetailsAsync(params) { result, queryResult ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val details = queryResult.productDetailsList.firstOrNull { it.productId == PRODUCT_ID }
                val offer = details?.oneTimePurchaseOfferDetailsList?.firstOrNull()
                productDetails = details
                offerToken = offer?.offerToken
                formattedPrice = offer?.formattedPrice
            } else {
                errorLog.record(ProductErrorCode.BILLING_PRODUCT_QUERY_FAILED, "billing.product")
                productDetails = null
                offerToken = null
                formattedPrice = null
            }
            publish()
        }
    }

    private fun queryOwned(showResult: Boolean) {
        val params = QueryPurchasesParams.newBuilder().setProductType(BillingClient.ProductType.INAPP).build()
        billingClient.queryPurchasesAsync(params) { result, purchases ->
            if (result.responseCode == BillingClient.BillingResponseCode.OK) {
                val restored = processPurchases(purchases, authoritative = true)
                if (showResult) onMessage(activity.getString(if (restored) R.string.billing_restored else R.string.billing_not_owned))
            } else {
                errorLog.record(ProductErrorCode.BILLING_OWNERSHIP_QUERY_FAILED, "billing.restore")
                if (showResult) onMessage(activity.getString(R.string.billing_restore_failed, result.debugMessage))
            }
        }
    }

    private fun processPurchases(purchases: List<Purchase>, authoritative: Boolean): Boolean {
        val snapshots = purchases.map { purchase ->
            BillingPurchaseSnapshot(
                productIds = purchase.products.toSet(),
                state = when (purchase.purchaseState) {
                    Purchase.PurchaseState.PURCHASED -> BillingPurchaseState.PURCHASED
                    Purchase.PurchaseState.PENDING -> BillingPurchaseState.PENDING
                    else -> BillingPurchaseState.OTHER
                },
            )
        }
        val ownsPro = BillingEntitlementPolicy.owns(PRODUCT_ID, snapshots)
        purchases.forEach { purchase ->
            if (PRODUCT_ID !in purchase.products || purchase.purchaseState != Purchase.PurchaseState.PURCHASED) return@forEach
            if (!purchase.isAcknowledged) {
                val params = AcknowledgePurchaseParams.newBuilder().setPurchaseToken(purchase.purchaseToken).build()
                billingClient.acknowledgePurchase(params) { result ->
                    if (result.responseCode != BillingClient.BillingResponseCode.OK) {
                        errorLog.record(ProductErrorCode.BILLING_ACK_FAILED, "billing.ack")
                        onMessage(activity.getString(R.string.billing_ack_pending))
                    }
                }
            }
        }
        val reconciled = BillingEntitlementPolicy.reconcileCached(
            previous = unlocked,
            authoritativeQuerySucceeded = authoritative,
            ownsNow = ownsPro,
        )
        if (authoritative || ownsPro) setUnlocked(if (authoritative) reconciled else true)
        return ownsPro
    }

    private fun setUnlocked(value: Boolean) {
        unlocked = value
        preferences.edit().putBoolean(KEY_CACHED_PRO, value).apply()
        publish()
    }

    private fun publish() {
        activity.runOnUiThread {
            onState(BillingState(unlocked = unlocked, available = productDetails != null && !offerToken.isNullOrEmpty(), connected = connected, price = formattedPrice))
        }
    }

    companion object {
        const val PRODUCT_ID = "jingdu_pro_lifetime"
        private const val PREFS = "jingdu.billing.v1"
        private const val KEY_CACHED_PRO = "pro.cached"
    }
}

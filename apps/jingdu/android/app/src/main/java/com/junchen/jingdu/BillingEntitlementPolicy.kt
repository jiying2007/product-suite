package com.junchen.jingdu

internal enum class BillingPurchaseState { PURCHASED, PENDING, OTHER }

internal data class BillingPurchaseSnapshot(
    val productIds: Set<String>,
    val state: BillingPurchaseState,
)

/** Pure entitlement policy so purchase-state behavior is unit-testable without Play services. */
internal object BillingEntitlementPolicy {
    fun owns(productId: String, purchases: List<BillingPurchaseSnapshot>): Boolean = purchases.any { purchase ->
        productId in purchase.productIds && purchase.state == BillingPurchaseState.PURCHASED
    }

    /**
     * An authoritative successful Play query replaces the cached answer. A failed/unavailable query
     * must leave the last verified offline entitlement untouched instead of disabling Free/Pro state
     * because of a transient service outage.
     */
    fun reconcileCached(previous: Boolean, authoritativeQuerySucceeded: Boolean, ownsNow: Boolean): Boolean =
        if (authoritativeQuerySucceeded) ownsNow else previous
}

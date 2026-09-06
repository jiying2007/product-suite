package com.junchen.jingdu

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BillingEntitlementPolicyTest {
    @Test
    fun onlyPurchasedMatchingProductUnlocks() {
        assertFalse(BillingEntitlementPolicy.owns(BillingManager.PRODUCT_ID, emptyList()))
        assertFalse(BillingEntitlementPolicy.owns(
            BillingManager.PRODUCT_ID,
            listOf(BillingPurchaseSnapshot(setOf(BillingManager.PRODUCT_ID), BillingPurchaseState.PENDING)),
        ))
        assertFalse(BillingEntitlementPolicy.owns(
            BillingManager.PRODUCT_ID,
            listOf(BillingPurchaseSnapshot(setOf("other_product"), BillingPurchaseState.PURCHASED)),
        ))
        assertTrue(BillingEntitlementPolicy.owns(
            BillingManager.PRODUCT_ID,
            listOf(BillingPurchaseSnapshot(setOf(BillingManager.PRODUCT_ID), BillingPurchaseState.PURCHASED)),
        ))
    }

    @Test
    fun authoritativeNoOwnershipRevokesCachedEntitlement() {
        assertFalse(BillingEntitlementPolicy.reconcileCached(previous = true, authoritativeQuerySucceeded = true, ownsNow = false))
        assertTrue(BillingEntitlementPolicy.reconcileCached(previous = false, authoritativeQuerySucceeded = true, ownsNow = true))
    }

    @Test
    fun billingOutageKeepsLastVerifiedOfflineState() {
        assertTrue(BillingEntitlementPolicy.reconcileCached(previous = true, authoritativeQuerySucceeded = false, ownsNow = false))
        assertFalse(BillingEntitlementPolicy.reconcileCached(previous = false, authoritativeQuerySucceeded = false, ownsNow = true))
    }
}

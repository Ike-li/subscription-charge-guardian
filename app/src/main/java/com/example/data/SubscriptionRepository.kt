package com.example.data

import kotlinx.coroutines.flow.Flow

class SubscriptionRepository(private val subscriptionDao: SubscriptionDao) {

    val allSubscriptions: Flow<List<Subscription>> = subscriptionDao.getAllSubscriptions()

    val activeSubscriptions: Flow<List<Subscription>> = subscriptionDao.getActiveSubscriptions()

    fun getSubscriptionById(id: Long): Flow<Subscription?> = subscriptionDao.getSubscriptionById(id)

    suspend fun getSubscriptionByIdDirect(id: Long): Subscription? = subscriptionDao.getSubscriptionByIdDirect(id)

    suspend fun getAllActiveDirect(): List<Subscription> = subscriptionDao.getAllActiveDirect()

    suspend fun insert(subscription: Subscription): Long = subscriptionDao.insertSubscription(subscription)

    suspend fun update(subscription: Subscription) = subscriptionDao.updateSubscription(subscription)

    suspend fun delete(subscription: Subscription) = subscriptionDao.deleteSubscription(subscription)

    suspend fun deleteById(id: Long) = subscriptionDao.deleteSubscriptionById(id)

    suspend fun setActive(id: Long, isActive: Boolean) = subscriptionDao.setSubscriptionActive(id, isActive)

    suspend fun rollForwardOverdue(today: Long) {
        for (sub in subscriptionDao.getAllActiveDirect()) {
            val rolled = sub.rollForward(today)
            if (rolled != sub) subscriptionDao.updateSubscription(rolled)
        }
    }
}

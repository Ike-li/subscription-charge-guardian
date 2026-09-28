package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.example.data.AppDatabase
import com.example.data.SubscriptionRepository
import com.example.notification.NotificationHelper
import com.example.ui.SubGuardApp
import com.example.ui.SubscriptionViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {

    private val subscriptionViewModel: SubscriptionViewModel by viewModels {
        val repo = (application as? SubGuardApplication)?.repository
            ?: SubscriptionRepository(AppDatabase.getDatabase(applicationContext).subscriptionDao())
        SubscriptionViewModel.provideFactory(repo, applicationContext)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 重建（如旋转屏幕）时导航栈会自行恢复，不能再按通知跳一次详情页
        val targetSubscriptionId = if (savedInstanceState == null) {
            intent?.getLongExtra(NotificationHelper.EXTRA_SUBSCRIPTION_ID, -1L) ?: -1L
        } else {
            -1L
        }

        setContent {
            MyApplicationTheme {
                SubGuardApp(
                    viewModel = subscriptionViewModel,
                    initialSubscriptionId = targetSubscriptionId
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
    }
}

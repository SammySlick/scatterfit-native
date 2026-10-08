package com.scatterbrain.scatterfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.lifecycle.lifecycleScope
import com.scatterbrain.scatterfit.data.HcPermissions
import com.scatterbrain.scatterfit.sync.SyncHub
import com.scatterbrain.scatterfit.sync.SyncService
import com.scatterbrain.scatterfit.ui.MainAppScreen
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(HcPermissions.read)) {
                SyncService.start(this)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncHub.init(this)

        when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                lifecycleScope.launch {
                    val client = HealthConnectClient.getOrCreate(this@MainActivity)
                    val granted = client.permissionController.getGrantedPermissions()
                    if (granted.containsAll(HcPermissions.read)) {
                        SyncService.start(this@MainActivity)
                    } else {
                        permissionLauncher.launch(HcPermissions.read)
                    }
                }
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                // TODO (Sam's screen): provider-update prompt. For now nothing.
                {}
            HealthConnectClient.SDK_UNAVAILABLE -> {} // no HC provider: demo mode
            else -> {}
        }

        setContent {
            ScatterFitTheme {
                MainAppScreen()
            }
        }
    }
}

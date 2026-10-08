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
import kotlin.coroutines.cancellation.CancellationException

class MainActivity : ComponentActivity() {

    private val permissionLauncher =
        registerForActivityResult(PermissionController.createRequestPermissionResultContract()) { granted ->
            if (granted.containsAll(HcPermissions.read)) {
                SyncService.start(this)
            }
        }

    /** Ask for permissions at most once per process; the check itself re-runs on
     *  every resume (onResume) so a recreated activity never loses the result. */
    private var requestedPermissions = false

    private fun ensureHcPermissions() {
        lifecycleScope.launch {
            try {
                if (SyncHub.records.value == null) {
                    SyncHub.setStatus("Health Connect: connecting…")
                }
                val client = HealthConnectClient.getOrCreate(this@MainActivity)
                val granted = client.permissionController.getGrantedPermissions()
                if (granted.containsAll(HcPermissions.read)) {
                    if (SyncHub.records.value == null) {
                        SyncHub.setStatus("Health Connect: syncing…")
                    }
                    SyncService.start(this@MainActivity)
                } else if (!requestedPermissions) {
                    requestedPermissions = true
                    SyncHub.setStatus("")
                    permissionLauncher.launch(HcPermissions.read)
                } else {
                    SyncHub.setStatus("Health Connect permissions missing. Settings > Apps > ScatterFit > Permissions, or clear and reopen.")
                }
            } catch (e: CancellationException) {
                throw e // normal cancellation (activity recreated) — not an error
            } catch (e: Exception) {
                SyncHub.setStatus("Health Connect error: ${e.message ?: e.javaClass.simpleName}")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        SyncHub.init(this)

        when (HealthConnectClient.getSdkStatus(this)) {
            HealthConnectClient.SDK_AVAILABLE -> {
                // onResume also runs right after onCreate, so the check starts there;
                // it re-runs every time the app comes back (incl. after the dialog closes)
            }
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                SyncHub.setStatus("Health Connect is out of date. Update it (Settings > search 'Health Connect', or the Play Store), then reopen ScatterFit.")
            HealthConnectClient.SDK_UNAVAILABLE ->
                SyncHub.setStatus("Health Connect isn't installed on this phone. Install it from the Play Store, then reopen ScatterFit.")
            else ->
                SyncHub.setStatus("Health Connect unavailable (unknown status ${HealthConnectClient.getSdkStatus(this)}).")
        }

        setContent {
            ScatterFitTheme {
                MainAppScreen()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (HealthConnectClient.getSdkStatus(this) == HealthConnectClient.SDK_AVAILABLE) {
            ensureHcPermissions()
        }
    }
}

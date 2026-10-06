package scatterfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Text
import scatterfit.core.DayKeys
import java.time.Instant
import java.time.ZoneId

/** v0.1 placeholder screen. Steps Compose screen lands once core/ is green. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val today = DayKeys.todayKey(Instant.now(), ZoneId.systemDefault())
        setContent { Text("ScatterFit v0.1 — today: $today") }
    }
}

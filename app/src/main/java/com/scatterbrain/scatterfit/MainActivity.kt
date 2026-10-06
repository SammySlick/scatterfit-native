package com.scatterbrain.scatterfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.scatterbrain.scatterfit.core.todayKey

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    Hello()
                }
            }
        }
    }
}

@Composable
fun Hello() {
    // Foundations: ui/ is nearly empty for weeks — that is correct.
    // Sam writes screens; core/ maths lands first with tests as acceptance.
    Text(text = "ScatterFit — day ${todayKey()}", style = MaterialTheme.typography.bodyLarge)
}

@Preview(showBackground = true)
@Composable
fun HelloPreview() {
    MaterialTheme { Hello() }
}

package com.scatterbrain.scatterfit

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.scatterbrain.scatterfit.ui.MainAppScreen
import com.scatterbrain.scatterfit.ui.theme.ScatterFitTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            ScatterFitTheme {
                MainAppScreen()
            }
        }
    }
}

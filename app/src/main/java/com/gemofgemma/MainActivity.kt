package com.gemofgemma

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import com.gemofgemma.ai.GemmaService
import com.gemofgemma.navigation.NavGraph
import com.gemofgemma.ui.theme.GemOfGemmaTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        setContent {
            GemOfGemmaTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavGraph()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        startAiService()
    }

    private fun startAiService() {
        val serviceIntent = Intent(this, GemmaService::class.java)
        startForegroundService(serviceIntent)
    }
}

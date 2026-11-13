package com.example.mycompose.shepherdai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.mycompose.shepherdai.ui.App
import com.example.mycompose.shepherdai.ui.ShepherdTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { ShepherdTheme { App() } }
    }
}

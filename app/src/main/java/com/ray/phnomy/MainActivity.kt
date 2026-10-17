package com.ray.phnomy

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.ray.phnomy.ui.FocusViewModel
import com.ray.phnomy.ui.PhnomyRoot

class MainActivity : ComponentActivity() {
    private val model: FocusViewModel by viewModels {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                FocusViewModel(PhnomyApp.graph(this@MainActivity)) as T
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (intent.getBooleanExtra("phnomy_open_exit", false)) model.requestOpenExit()
        setContent { PhnomyRoot(model) }
    }
    override fun onResume() { super.onResume(); model.refresh() }
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getBooleanExtra("phnomy_open_exit", false)) model.requestOpenExit()
    }
}

package com.junchen.posestudio

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.junchen.posestudio.ui.PoseSpeedToolbar
import com.junchen.posestudio.ui.PoseStudioApp
import com.junchen.posestudio.ui.PoseStudioTheme
import com.junchen.posestudio.ui.PoseStudioViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PoseStudioTheme {
                val poseStudioViewModel: PoseStudioViewModel = viewModel()
                Box(Modifier.fillMaxSize()) {
                    PoseStudioApp(poseStudioViewModel)
                    PoseSpeedToolbar(poseStudioViewModel)
                }
            }
        }
    }
}

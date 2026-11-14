package com.xitora.autoclicker

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.animation.doOnEnd
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.xitora.autoclicker.model.ThemeOption
import com.xitora.autoclicker.ui.screens.MainScaffoldScreen
import com.xitora.autoclicker.ui.theme.AutoClickerTheme
import com.xitora.autoclicker.viewmodel.AutoClickerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var keepSplashOnScreen = savedInstanceState == null
        splashScreen.setKeepOnScreenCondition { keepSplashOnScreen }
        if (keepSplashOnScreen) {
            Handler(Looper.getMainLooper()).postDelayed({
                keepSplashOnScreen = false
            }, 750L)
        }
        splashScreen.setOnExitAnimationListener { splashProvider ->
            val fadeOut = ObjectAnimator.ofFloat(splashProvider.view, View.ALPHA, 1f, 0f)
            val scaleX = ObjectAnimator.ofFloat(splashProvider.view, View.SCALE_X, 1f, 1.04f)
            val scaleY = ObjectAnimator.ofFloat(splashProvider.view, View.SCALE_Y, 1f, 1.04f)
            AnimatorSet().apply {
                playTogether(fadeOut, scaleX, scaleY)
                duration = 320L
                interpolator = AccelerateDecelerateInterpolator()
                doOnEnd { splashProvider.remove() }
                start()
            }
        }
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val viewModel: AutoClickerViewModel = viewModel()
            val state by viewModel.state.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()
            val effectiveDark = when (state.settings.themeOption) {
                ThemeOption.SYSTEM -> systemDark
                ThemeOption.LIGHT -> false
                ThemeOption.DARK -> true
            }

            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                viewModel.refreshPermissions()
            }

            LaunchedEffect(effectiveDark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { effectiveDark },
                    navigationBarStyle = SystemBarStyle.auto(
                        android.graphics.Color.TRANSPARENT,
                        android.graphics.Color.TRANSPARENT
                    ) { effectiveDark }
                )
            }

            AutoClickerTheme(
                themeOption = state.settings.themeOption,
                colorStyle = state.settings.resolvedColorStyle(),
                customKeyColorHex = state.settings.customKeyColorHex
            ) {
                MainScaffoldScreen(viewModel = viewModel)
            }
        }
    }
}

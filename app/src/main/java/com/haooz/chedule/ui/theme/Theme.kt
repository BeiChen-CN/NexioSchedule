/** 应用主题 - 定义 Material3 主题配色方案 */
package com.haooz.chedule.ui.theme

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import top.yukonga.miuix.kmp.theme.ColorSchemeMode
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.theme.ThemeController

/** 应用触感反馈开关对应的 SharedPreferences 文件与键 */
const val APP_PREFS_NAME = "app_preferences"
const val KEY_HAPTIC_FEEDBACK = "haptic_feedback_enabled"
const val KEY_MONET_COLOR = "monet_color_enabled"

private val LocalMonetColorEnabled = staticCompositionLocalOf { false }

/** 让页面与弹窗的深浅色覆盖保留应用的莫奈配色，并保持 controller 实例稳定。 */
@Composable
fun rememberAppThemeController(
    isDark: Boolean? = null,
    themeMode: String = "system",
    monetEnabled: Boolean = LocalMonetColorEnabled.current,
): ThemeController {
    val useMonet = monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val mode = when {
        isDark == true || (isDark == null && themeMode == "dark") ->
            if (useMonet) ColorSchemeMode.MonetDark else ColorSchemeMode.Dark
        isDark == false || (isDark == null && themeMode == "light") ->
            if (useMonet) ColorSchemeMode.MonetLight else ColorSchemeMode.Light
        else -> if (useMonet) ColorSchemeMode.MonetSystem else ColorSchemeMode.System
    }
    val controller = remember { ThemeController(mode) }
    controller.colorSchemeMode = mode
    return controller
}

@Composable
fun CourseScheduleTheme(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_theme_prefs", Context.MODE_PRIVATE) }
    val themeMode = remember { mutableStateOf(prefs.getString("theme_mode", "system") ?: "system") }
    var monetEnabled by remember { mutableStateOf(prefs.getBoolean(KEY_MONET_COLOR, false)) }

    // 全局触感反馈开关：读取应用偏好，关闭后在整个 App 范围内屏蔽所有触感/震动
    // （本项目及 Miuix 组件的触感均通过 LocalHapticFeedback 触发，此处统一拦截即可全局生效）
    val hapticPrefs = remember { context.getSharedPreferences(APP_PREFS_NAME, Context.MODE_PRIVATE) }
    var hapticFeedbackEnabled by remember {
        mutableStateOf(hapticPrefs.getBoolean(KEY_HAPTIC_FEEDBACK, true))
    }

    DisposableEffect(prefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "theme_mode") {
                themeMode.value = prefs.getString("theme_mode", "system") ?: "system"
            } else if (key == KEY_MONET_COLOR) {
                monetEnabled = prefs.getBoolean(KEY_MONET_COLOR, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            prefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    DisposableEffect(hapticPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == KEY_HAPTIC_FEEDBACK) {
                hapticFeedbackEnabled = hapticPrefs.getBoolean(KEY_HAPTIC_FEEDBACK, true)
            }
        }
        hapticPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose {
            hapticPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val controller = rememberAppThemeController(
        themeMode = themeMode.value,
        monetEnabled = monetEnabled,
    )
    val systemDark = isSystemInDarkTheme()
    val isDark = when (themeMode.value) {
        "light" -> false
        "dark" -> true
        else -> systemDark
    }
    // Material3 与 MiUiX 共用系统动态配色，避免混用组件仍显示默认主题色。
    val materialColors = if (monetEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        MaterialTheme.colorScheme
    }
    // 捕获当前（系统默认）触感实现，封装为受开关控制的门控实现
    val defaultHaptic = LocalHapticFeedback.current
    val gatedHaptic = remember(defaultHaptic) {
        object : HapticFeedback {
            override fun performHapticFeedback(hapticFeedbackType: HapticFeedbackType) {
                if (hapticFeedbackEnabled) {
                    defaultHaptic.performHapticFeedback(hapticFeedbackType)
                }
            }
        }
    }
    MiuixTheme(
        controller = controller,
        content = {
            CompositionLocalProvider(
                LocalHapticFeedback provides gatedHaptic,
                LocalMonetColorEnabled provides monetEnabled,
            ) {
                MaterialTheme(colorScheme = materialColors) {
                    content()
                }
            }
        }
    )
}

package moe.haruue.wadb.component

import android.Manifest
import android.app.ActivityManager
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import moe.haruue.wadb.BuildConfig
import moe.haruue.wadb.R
import moe.haruue.wadb.WadbApplication
import moe.haruue.wadb.WadbPreferences
import moe.haruue.wadb.events.Events
import moe.haruue.wadb.events.GlobalRequestHandler
import moe.haruue.wadb.events.WadbFailureEvent
import moe.haruue.wadb.events.WadbStateChangedEvent
import moe.haruue.wadb.util.NetworksUtils
import moe.haruue.wadb.util.NotificationHelper
import moe.haruue.wadb.util.ScreenKeeper
import moe.haruue.wadb.util.ThemeHelper
import moe.haruue.wadb.wadbApplication

class HomeActivity : ComponentActivity(), WadbStateChangedEvent, WadbFailureEvent,
    SharedPreferences.OnSharedPreferenceChangeListener {

    private val preferences: SharedPreferences
        get() = WadbApplication.defaultSharedPreferences

    private var wadbActive by mutableStateOf(false)
    private var operationEnabled by mutableStateOf(true)
    private var ipSummary by mutableStateOf("")
    private var portText by mutableStateOf("5555")
    private var currentTheme by mutableStateOf(ThemeHelper.THEME_DEFAULT)
    private var showNotification by mutableStateOf(true)
    private var lowPriorityNotification by mutableStateOf(true)
    private var wakeLock by mutableStateOf(false)
    private var screenLockSwitch by mutableStateOf(false)
    private var startOnBoot by mutableStateOf(false)
    private var hideLauncherIcon by mutableStateOf(false)
    private var showPortDialog by mutableStateOf(false)
    private var showRootDialog by mutableStateOf(false)
    private var showHideIconDialog by mutableStateOf(false)
    private var showAboutDialog by mutableStateOf(false)
    private var pendingStartWadbPort: String? = null

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                preferences.edit().putBoolean(WadbPreferences.KEY_NOTIFICATION, true).apply()
                pendingStartWadbPort?.let(::startWadbNow)
                if (wadbActive) {
                    GlobalRequestHandler.checkWadbState()
                }
            } else {
                preferences.edit().putBoolean(WadbPreferences.KEY_NOTIFICATION, false).apply()
                pendingStartWadbPort?.let(::startWadbNow)
                Toast.makeText(this, R.string.toast_notification_permission_denied, Toast.LENGTH_SHORT).show()
            }
            pendingStartWadbPort = null
            refreshPreferenceState()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Events.registerAll(this)
        refreshPreferenceState()
        refreshWadbState()
        updateTaskDescription()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationHelper.createNotificationChannel(this)
        }

        setContent {
            WadbTheme(currentTheme) {
                HomeScreen(
                    wadbActive = wadbActive,
                    operationEnabled = operationEnabled,
                    ipSummary = ipSummary,
                    portText = portText,
                    showNotification = showNotification,
                    lowPriorityNotification = lowPriorityNotification,
                    wakeLock = wakeLock,
                    screenLockSwitch = screenLockSwitch,
                    startOnBoot = startOnBoot,
                    hideLauncherIcon = hideLauncherIcon,
                    currentTheme = currentTheme,
                    onToggleWadb = ::setWadbEnabled,
                    onEditPort = { showPortDialog = true },
                    onShowNotificationChange = ::setNotificationEnabled,
                    onLowPriorityNotificationChange = {
                        setBooleanPreference(WadbPreferences.KEY_NOTIFICATION_LOW_PRIORITY, it)
                    },
                    onWakeLockChange = { setBooleanPreference(WadbPreferences.KEY_WAKE_LOCK, it) },
                    onScreenLockSwitchChange = {
                        setBooleanPreference(WadbPreferences.KEY_SCREEN_LOCK_SWITCH, it)
                    },
                    onStartOnBootChange = ::updateStartOnBoot,
                    onHideLauncherIconChange = ::updateHideLauncherIcon,
                    onNotificationSettings = ::openNotificationSettings,
                    onThemeChange = ::setTheme,
                    onTranslate = ::openTranslationPage,
                    onAbout = { showAboutDialog = true },
                )

                if (showPortDialog) {
                    PortDialog(
                        currentPort = portText,
                        onDismiss = { showPortDialog = false },
                        onConfirm = ::setPort,
                    )
                }

                if (showRootDialog) {
                    AlertDialog(
                        onDismissRequest = { showRootDialog = false },
                        text = { Text(stringResource(R.string.dialog_not_rooted_message)) },
                        confirmButton = {
                            TextButton(onClick = { showRootDialog = false }) {
                                Text(stringResource(android.R.string.ok))
                            }
                        },
                    )
                }

                if (showHideIconDialog) {
                    AlertDialog(
                        onDismissRequest = { showHideIconDialog = false },
                        text = { Text(stringResource(R.string.dialog_hide_icon_message_q)) },
                        dismissButton = {
                            TextButton(onClick = { showHideIconDialog = false }) {
                                Text(stringResource(android.R.string.cancel))
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    showHideIconDialog = false
                                    wadbApplication.disableLauncherActivity()
                                    refreshPreferenceState()
                                },
                            ) {
                                Text(stringResource(android.R.string.ok))
                            }
                        },
                    )
                }

                if (showAboutDialog) {
                    AboutDialog(onDismiss = { showAboutDialog = false })
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        GlobalRequestHandler.checkWadbState()
    }

    override fun onResume() {
        super.onResume()
        preferences.registerOnSharedPreferenceChangeListener(this)
        refreshPreferenceState()
    }

    override fun onPause() {
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        super.onPause()
    }

    override fun onDestroy() {
        Events.unregisterAll(this)
        super.onDestroy()
    }

    override fun onWadbStarted(port: Int) {
        wadbActive = true
        operationEnabled = true
        portText = port.toString()
        ipSummary = buildIpSummary(port)
    }

    override fun onWadbStopped() {
        wadbActive = false
        operationEnabled = true
        ipSummary = ""
    }

    override fun onRootPermissionFailure() {
        onWadbStopped()
        showRootDialog = true
    }

    override fun onOperateFailure() {
        operationEnabled = true
        Toast.makeText(this, R.string.dialog_not_rooted_message, Toast.LENGTH_SHORT).show()
        refreshWadbState()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        refreshPreferenceState()
        when (key) {
            WadbPreferences.KEY_NOTIFICATION, WadbPreferences.KEY_NOTIFICATION_LOW_PRIORITY -> {
                if (wadbActive && sharedPreferences.getBoolean(WadbPreferences.KEY_NOTIFICATION, true)) {
                    GlobalRequestHandler.checkWadbState()
                } else {
                    NotificationHelper.cancelNotification(this)
                }
            }
            WadbPreferences.KEY_WAKE_LOCK -> {
                if (sharedPreferences.getBoolean(WadbPreferences.KEY_WAKE_LOCK, false) && wadbActive) {
                    ScreenKeeper.acquireWakeLock(this)
                } else {
                    ScreenKeeper.releaseWakeLock()
                }
            }
        }
    }

    private fun refreshPreferenceState() {
        val prefs = preferences
        currentTheme = ThemeHelper.getTheme()
        portText = WadbApplication.wadbPort
        showNotification = prefs.getBoolean(WadbPreferences.KEY_NOTIFICATION, true)
        lowPriorityNotification = prefs.getBoolean(WadbPreferences.KEY_NOTIFICATION_LOW_PRIORITY, true)
        wakeLock = prefs.getBoolean(WadbPreferences.KEY_WAKE_LOCK, false)
        screenLockSwitch = prefs.getBoolean(WadbPreferences.KEY_SCREEN_LOCK_SWITCH, false)
        startOnBoot = wadbApplication.isBootCompletedReceiverEnabled()
        hideLauncherIcon = !wadbApplication.isLauncherActivityEnabled()
    }

    private fun refreshWadbState() {
        val port = GlobalRequestHandler.getWadbPort()
        if (port == -1) {
            onWadbStopped()
        } else {
            onWadbStarted(port)
        }
    }

    private fun setWadbEnabled(enabled: Boolean) {
        operationEnabled = false
        if (enabled) {
            val port = WadbApplication.wadbPort
            if (ensureNotificationPermissionBeforeStart(port)) {
                startWadbNow(port)
            }
        } else {
            GlobalRequestHandler.stopWadb()
        }
    }

    private fun setPort(port: String) {
        val parsed = port.toIntOrNull()
        if (parsed == null || parsed < 1025 || parsed > 65535) {
            Toast.makeText(this, R.string.toast_bad_port_number, Toast.LENGTH_SHORT).show()
            return
        }
        showPortDialog = false
        preferences.edit().putString(WadbPreferences.KEY_WAKE_PORT, parsed.toString()).apply()
        portText = parsed.toString()
        if (wadbActive) {
            operationEnabled = false
            if (ensureNotificationPermissionBeforeStart(parsed.toString())) {
                startWadbNow(parsed.toString())
            }
        }
    }

    private fun setBooleanPreference(key: String, value: Boolean) {
        preferences.edit().putBoolean(key, value).apply()
    }

    private fun setNotificationEnabled(enabled: Boolean) {
        if (enabled && !hasNotificationPermission()) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            return
        }
        setBooleanPreference(WadbPreferences.KEY_NOTIFICATION, enabled)
        if (!enabled) {
            NotificationHelper.cancelNotification(this)
        }
    }

    private fun ensureNotificationPermissionBeforeStart(port: String): Boolean {
        if (!preferences.getBoolean(WadbPreferences.KEY_NOTIFICATION, true) || hasNotificationPermission()) {
            return true
        }
        pendingStartWadbPort = port
        notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        return false
    }

    private fun hasNotificationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    }

    private fun startWadbNow(port: String) {
        GlobalRequestHandler.startWadb(port)
    }

    private fun updateStartOnBoot(enabled: Boolean) {
        if (enabled) {
            wadbApplication.enableBootCompletedReceiver()
        } else {
            wadbApplication.disableBootCompletedReceiver()
        }
        refreshPreferenceState()
    }

    private fun updateHideLauncherIcon(enabled: Boolean) {
        if (enabled) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                showHideIconDialog = true
            } else {
                wadbApplication.disableLauncherActivity()
            }
        } else {
            wadbApplication.enableLauncherActivity()
        }
        refreshPreferenceState()
    }

    private fun setTheme(theme: String) {
        if (theme != currentTheme) {
            ThemeHelper.setLightTheme(theme)
            currentTheme = theme
            updateTaskDescription()
        }
    }

    private fun openNotificationSettings() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        startActivity(
            Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                .putExtra(Settings.EXTRA_CHANNEL_ID, "state"),
        )
    }

    private fun openTranslationPage() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BuildConfig.TRANSLATION_URL)))
    }

    private fun buildIpSummary(port: Int): String {
        val ipInfoList = NetworksUtils.getLocalIPInfo(this)
        return when {
            ipInfoList.isEmpty() -> ""
            ipInfoList.size == 1 -> "${ipInfoList[0].ip}:$port"
            else -> ipInfoList.joinToString(separator = "\n") {
                val uiInterfaceName = when (it.interfaceName) {
                    "wlan0" -> "WLAN"
                    "wlan1" -> "AP"
                    else -> it.interfaceName
                }
                "[$uiInterfaceName] ${it.ip}:$port"
            }
        }
    }

    private fun updateTaskDescription() {
        val color = when (ThemeHelper.getTheme()) {
            ThemeHelper.THEME_PINK -> 0xFFF5A9B8.toInt()
            else -> 0xFF88B984.toInt()
        }
        val icon = if (ThemeHelper.getTheme() == ThemeHelper.THEME_DEFAULT) {
            R.drawable.ic_task_icon_white
        } else {
            R.drawable.ic_task_icon_black
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            setTaskDescription(
                ActivityManager.TaskDescription.Builder()
                    .setPrimaryColor(color)
                    .setIcon(icon)
                    .build(),
            )
        } else if (Build.VERSION.SDK_INT >= 28) {
            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription(null, icon, color))
        } else {
            val drawable = ContextCompat.getDrawable(this, icon) ?: return
            val bitmap = Bitmap.createBitmap(drawable.intrinsicWidth, drawable.intrinsicHeight, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)

            @Suppress("DEPRECATION")
            setTaskDescription(ActivityManager.TaskDescription(null, bitmap, color))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(
    wadbActive: Boolean,
    operationEnabled: Boolean,
    ipSummary: String,
    portText: String,
    showNotification: Boolean,
    lowPriorityNotification: Boolean,
    wakeLock: Boolean,
    screenLockSwitch: Boolean,
    startOnBoot: Boolean,
    hideLauncherIcon: Boolean,
    currentTheme: String,
    onToggleWadb: (Boolean) -> Unit,
    onEditPort: () -> Unit,
    onShowNotificationChange: (Boolean) -> Unit,
    onLowPriorityNotificationChange: (Boolean) -> Unit,
    onWakeLockChange: (Boolean) -> Unit,
    onScreenLockSwitchChange: (Boolean) -> Unit,
    onStartOnBootChange: (Boolean) -> Unit,
    onHideLauncherIconChange: (Boolean) -> Unit,
    onNotificationSettings: () -> Unit,
    onThemeChange: (String) -> Unit,
    onTranslate: () -> Unit,
    onAbout: () -> Unit,
) {
    var menuExpanded by androidx.compose.runtime.remember { mutableStateOf(false) }
    var themeExpanded by androidx.compose.runtime.remember { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.wireless_adb_short)) },
                actions = {
                    TextButton(onClick = { menuExpanded = true }) {
                        Text("More")
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.theme)) },
                            onClick = {
                                menuExpanded = false
                                themeExpanded = true
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.contribute_translation)) },
                            onClick = {
                                menuExpanded = false
                                onTranslate()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.about)) },
                            onClick = {
                                menuExpanded = false
                                onAbout()
                            },
                        )
                    }
                    DropdownMenu(expanded = themeExpanded, onDismissRequest = { themeExpanded = false }) {
                        ThemeMenuItem(R.string.theme_default, ThemeHelper.THEME_DEFAULT, currentTheme, onThemeChange) {
                            themeExpanded = false
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            ThemeMenuItem(R.string.theme_green, ThemeHelper.THEME_GREEN, currentTheme, onThemeChange) {
                                themeExpanded = false
                            }
                        }
                        ThemeMenuItem(R.string.theme_pink, ThemeHelper.THEME_PINK, currentTheme, onThemeChange) {
                            themeExpanded = false
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SettingRow(
                title = stringResource(R.string.enable_wadb),
                summary = if (wadbActive) stringResource(R.string.settings_toggle_summary_on)
                else stringResource(R.string.settings_toggle_summary_off),
                trailing = {
                    Switch(
                        checked = wadbActive,
                        enabled = operationEnabled,
                        onCheckedChange = onToggleWadb,
                    )
                },
            )
            if (wadbActive) {
                SettingRow(
                    title = stringResource(R.string.settings_ip),
                    summary = ipSummary,
                )
            }
            SettingRow(
                title = stringResource(R.string.settings_port),
                summary = portText,
                enabled = operationEnabled,
                onClick = onEditPort,
            )

            SectionDivider()
            SettingRow(
                title = stringResource(R.string.settings_show_notification),
                summary = stringResource(R.string.settings_show_notification_summary, stringResource(R.string.wireless_adb)),
                trailing = {
                    Switch(checked = showNotification, onCheckedChange = onShowNotificationChange)
                },
            )
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
                SettingRow(
                    title = stringResource(R.string.settings_use_low_priority_notification),
                    summary = stringResource(R.string.settings_use_low_priority_notification_summary),
                    enabled = showNotification,
                    trailing = {
                        Switch(
                            checked = lowPriorityNotification,
                            enabled = showNotification,
                            onCheckedChange = onLowPriorityNotificationChange,
                        )
                    },
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                SettingRow(
                    title = stringResource(R.string.notification_settings),
                    summary = stringResource(R.string.notification_settings_summary),
                    onClick = onNotificationSettings,
                )
            }

            SectionDivider()
            SettingRow(
                title = stringResource(R.string.settings_keep_screen_on),
                summary = stringResource(R.string.settings_keep_screen_on_summary, stringResource(R.string.wireless_adb)),
                trailing = {
                    Switch(checked = wakeLock, onCheckedChange = onWakeLockChange)
                },
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                SettingRow(
                    title = stringResource(R.string.settings_allow_toggle_lock_screen),
                    summary = stringResource(R.string.settings_allow_toggle_lock_screen_summary),
                    trailing = {
                        Switch(checked = screenLockSwitch, onCheckedChange = onScreenLockSwitchChange)
                    },
                )
            }
            SettingRow(
                title = stringResource(R.string.settings_start_on_boot),
                summary = stringResource(R.string.settings_start_on_boot_summary, stringResource(R.string.wireless_adb)),
                trailing = {
                    Switch(checked = startOnBoot, onCheckedChange = onStartOnBootChange)
                },
            )
            SettingRow(
                title = stringResource(R.string.settings_hide_icon),
                summary = stringResource(R.string.settings_hide_icon_summary),
                trailing = {
                    Switch(checked = hideLauncherIcon, onCheckedChange = onHideLauncherIconChange)
                },
            )
        }
    }
}

@Composable
private fun ThemeMenuItem(
    titleRes: Int,
    value: String,
    currentTheme: String,
    onThemeChange: (String) -> Unit,
    onClose: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = if (value == currentTheme) "${stringResource(titleRes)} ✓" else stringResource(titleRes),
            )
        },
        onClick = {
            onThemeChange(value)
            onClose()
        },
    )
}

@Composable
private fun SettingRow(
    title: String,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (!summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(16.dp))
            trailing()
        }
    }
}

@Composable
private fun SectionDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 4.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun PortDialog(
    currentPort: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by androidx.compose.runtime.remember(currentPort) { mutableStateOf(currentPort) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_port)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it.filter(Char::isDigit).take(5) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val versionName = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
    } catch (_: PackageManager.NameNotFoundException) {
        ""
    }
    val translators = stringResource(R.string.translators)
    val translationText = if (translators.isNotBlank()) {
        "\n\n${stringResource(R.string.translation_contributors, translators)}"
    } else {
        ""
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.wireless_adb_short)) },
        text = {
            Text(
                text = "$versionName\n\n${BuildConfig.GITHUB_URL}\n${BuildConfig.LICENSE}$translationText\n\n${BuildConfig.COPYRIGHT}",
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.ok))
            }
        },
    )
}

@Composable
private fun WadbTheme(theme: String, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val colorScheme = when (theme) {
        ThemeHelper.THEME_GREEN -> if (dark) {
            darkColorScheme(primary = Color(0xFFAED9A9), secondary = Color(0xFFBBCBB6), tertiary = Color(0xFFA0D0C3))
        } else {
            lightColorScheme(primary = Color(0xFF386A34), secondary = Color(0xFF52634F), tertiary = Color(0xFF386667))
        }
        ThemeHelper.THEME_PINK -> if (dark) {
            darkColorScheme(primary = Color(0xFFFFB1C2), secondary = Color(0xFFE3BDC5), tertiary = Color(0xFFF0B8A6))
        } else {
            lightColorScheme(primary = Color(0xFF904B5B), secondary = Color(0xFF74565E), tertiary = Color(0xFF7D563F))
        }
        else -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else if (dark) {
            darkColorScheme(primary = Color(0xFFAED9A9), secondary = Color(0xFFBBCBB6), tertiary = Color(0xFFA0D0C3))
        } else {
            lightColorScheme(primary = Color(0xFF386A34), secondary = Color(0xFF52634F), tertiary = Color(0xFF386667))
        }
    }

    MaterialTheme(colorScheme = colorScheme, content = content)
}

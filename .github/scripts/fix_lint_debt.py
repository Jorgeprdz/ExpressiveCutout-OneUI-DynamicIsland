from pathlib import Path


def replace_once(text: str, old: str, new: str, label: str) -> str:
    if text.count(old) != 1:
        raise SystemExit(f"expected exactly one anchor for {label}, found {text.count(old)}")
    return text.replace(old, new, 1)


def update(path: str, transform) -> None:
    file = Path(path)
    before = file.read_text()
    after = transform(before)
    if after == before:
        raise SystemExit(f"no change produced for {path}")
    file.write_text(after)


def patch_manifest(text: str) -> str:
    return replace_once(
        text,
        '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n',
        '    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />\n'
        '    <uses-permission android:name="android.permission.BLUETOOTH" android:maxSdkVersion="30" />\n'
        '    <uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />\n',
        "bluetooth permissions",
    )


def patch_system_event_monitor(text: str) -> str:
    text = replace_once(
        text,
        "import android.app.KeyguardManager\n",
        "import android.Manifest\nimport android.app.KeyguardManager\n",
        "Manifest import",
    )
    text = replace_once(
        text,
        "import android.content.IntentFilter\n",
        "import android.content.IntentFilter\nimport android.content.pm.PackageManager\n",
        "PackageManager import",
    )
    guard = '''                    if (\n                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&\n                        ContextCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) !=\n                        PackageManager.PERMISSION_GRANTED\n                    ) {\n                        return\n                    }\n'''
    text = replace_once(
        text,
        "                BluetoothDevice.ACTION_ACL_CONNECTED -> {\n                    val device = getBluetoothDevice(intent)\n",
        "                BluetoothDevice.ACTION_ACL_CONNECTED -> {\n" + guard + "                    val device = getBluetoothDevice(intent)\n",
        "bluetooth connected permission guard",
    )
    text = replace_once(
        text,
        "                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {\n                    val device = getBluetoothDevice(intent)\n",
        "                BluetoothDevice.ACTION_ACL_DISCONNECTED -> {\n" + guard + "                    val device = getBluetoothDevice(intent)\n",
        "bluetooth disconnected permission guard",
    )
    return text


def patch_apps_screen(text: str) -> str:
    text = replace_once(
        text,
        "import androidx.compose.runtime.Composable\n",
        "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n",
        "AppsScreen LaunchedEffect import",
    )
    text = replace_once(text, "import androidx.compose.runtime.produceState\n", "", "AppsScreen produceState import")
    text = replace_once(
        text,
        '''    val apps by produceState<List<InstalledApp>?>(initialValue = null, context) {\n        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }\n    }\n''',
        '''    var apps by remember(context) { mutableStateOf<List<InstalledApp>?>(null) }\n    LaunchedEffect(context) {\n        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }\n    }\n''',
        "AppsScreen app loading",
    )
    text = replace_once(
        text,
        '''    val icon by produceState(initialValue = iconCache.get(packageName), packageName) {\n        if (value != null) return@produceState\n        val loaded = withContext(Dispatchers.IO) {\n            iconLoadLimit.withPermit { loadAppIcon(context, packageName) }\n        }\n        if (loaded != null) iconCache.put(packageName, loaded)\n        value = loaded\n    }\n''',
        '''    var icon by remember(packageName) { mutableStateOf(iconCache.get(packageName)) }\n    LaunchedEffect(packageName) {\n        if (icon != null) return@LaunchedEffect\n        val loaded = withContext(Dispatchers.IO) {\n            iconLoadLimit.withPermit { loadAppIcon(context, packageName) }\n        }\n        if (loaded != null) iconCache.put(packageName, loaded)\n        icon = loaded\n    }\n''',
        "AppIcon async load",
    )
    if "produceState" in text:
        raise SystemExit("AppsScreen still contains produceState")
    return text


def patch_event_icons(text: str) -> str:
    text = replace_once(text, "import androidx.compose.runtime.produceState\n", "", "EventIcons produceState import")
    text = replace_once(
        text,
        '''    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = source) {\n        value = when (val current = source) {\n            is IconSource.Image -> withContext(Dispatchers.IO) {\n                Uri.parse(current.uri).loadImageBitmapOrNull(context)\n            }\n\n            is IconSource.Material, null -> null\n        }\n    }\n''',
        '''    var bitmap by remember(source) { mutableStateOf<ImageBitmap?>(null) }\n    LaunchedEffect(source) {\n        bitmap = when (val current = source) {\n            is IconSource.Image -> withContext(Dispatchers.IO) {\n                Uri.parse(current.uri).loadImageBitmapOrNull(context)\n            }\n\n            is IconSource.Material, null -> null\n        }\n    }\n''',
        "EventIcons bitmap load",
    )
    if "produceState" in text:
        raise SystemExit("EventIconsScreen still contains produceState")
    return text


def patch_shows_when_empty(text: str) -> str:
    text = replace_once(
        text,
        "import androidx.compose.runtime.Composable\n",
        "import androidx.compose.runtime.Composable\nimport androidx.compose.runtime.LaunchedEffect\n",
        "ShowsWhenEmpty LaunchedEffect import",
    )
    text = replace_once(text, "import androidx.compose.runtime.produceState\n", "", "ShowsWhenEmpty produceState import")
    text = replace_once(
        text,
        '''    val apps by produceState<List<InstalledApp>?>(initialValue = null, context) {\n        value = withContext(Dispatchers.IO) { loadLaunchableApps(context) }\n    }\n''',
        '''    var apps by remember(context) { mutableStateOf<List<InstalledApp>?>(null) }\n    LaunchedEffect(context) {\n        apps = withContext(Dispatchers.IO) { loadLaunchableApps(context) }\n    }\n''',
        "AppPicker app loading",
    )
    text = replace_once(
        text,
        '''    val label by produceState(initialValue = packageName, packageName) {\n        value = withContext(Dispatchers.IO) {\n            runCatching {\n                val pm = context.packageManager\n                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()\n            }.getOrDefault(packageName)\n        }\n    }\n''',
        '''    var label by remember(packageName) { mutableStateOf(packageName) }\n    LaunchedEffect(packageName) {\n        label = withContext(Dispatchers.IO) {\n            runCatching {\n                val pm = context.packageManager\n                pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()\n            }.getOrDefault(packageName)\n        }\n    }\n''',
        "rememberAppLabel async load",
    )
    text = replace_once(
        text,
        '''    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = source) {\n        value = when (val current = source) {\n            is IconSource.Image -> withContext(Dispatchers.IO) {\n                Uri.parse(current.uri).loadImageBitmapOrNull(context)\n            }\n            is IconSource.Material, null -> null\n        }\n    }\n''',
        '''    var bitmap by remember(source) { mutableStateOf<ImageBitmap?>(null) }\n    LaunchedEffect(source) {\n        bitmap = when (val current = source) {\n            is IconSource.Image -> withContext(Dispatchers.IO) {\n                Uri.parse(current.uri).loadImageBitmapOrNull(context)\n            }\n            is IconSource.Material, null -> null\n        }\n    }\n''',
        "EmptyIconThumbnail bitmap load",
    )
    if "produceState" in text:
        raise SystemExit("ShowsWhenEmptyScreen still contains produceState")
    return text


def patch_dynamic_island(text: str) -> str:
    text = replace_once(text, "import androidx.compose.runtime.produceState\n", "", "DynamicIsland produceState import")
    text = replace_once(
        text,
        '''    val bitmap by produceState<ImageBitmap?>(initialValue = null, key1 = icon) {\n        value = when (icon) {\n            is IconSource.Image -> withContext(Dispatchers.IO) {\n                Uri.parse(icon.uri).loadImageBitmapOrNull(context)\n            }\n            is IconSource.Material -> null\n        }\n    }\n''',
        '''    var bitmap by remember(icon) { mutableStateOf<ImageBitmap?>(null) }\n    LaunchedEffect(icon) {\n        bitmap = when (icon) {\n            is IconSource.Image -> withContext(Dispatchers.IO) {\n                Uri.parse(icon.uri).loadImageBitmapOrNull(context)\n            }\n            is IconSource.Material -> null\n        }\n    }\n''',
        "EmptyPillContent bitmap load",
    )
    text = replace_once(
        text,
        '''    val label by produceState(initialValue = pkg, pkg) {\n        value = withContext(Dispatchers.IO) {\n            runCatching {\n                val pm = context.packageManager\n                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()\n            }.getOrDefault(pkg)\n        }\n    }\n''',
        '''    var label by remember(pkg) { mutableStateOf(pkg) }\n    LaunchedEffect(pkg) {\n        label = withContext(Dispatchers.IO) {\n            runCatching {\n                val pm = context.packageManager\n                pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()\n            }.getOrDefault(pkg)\n        }\n    }\n''',
        "centerShortcutLabel async load",
    )
    text = replace_once(
        text,
        '''    val icon by produceState<LoadedAppIcon?>(initialValue = null, packageName, themed) {\n        value = withContext(Dispatchers.IO) {\n            runCatching {\n                val drawable = context.packageManager.getApplicationIcon(packageName)\n                val monochrome = if (themed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {\n                    (drawable as? AdaptiveIconDrawable)?.monochrome\n                } else {\n                    null\n                }\n                if (monochrome != null) {\n                    LoadedAppIcon(monochrome.toBitmap().asImageBitmap(), themed = true)\n                } else {\n                    LoadedAppIcon(drawable.toBitmap().asImageBitmap(), themed = false)\n                }\n            }.getOrNull()\n        }\n    }\n''',
        '''    var icon by remember(packageName, themed) { mutableStateOf<LoadedAppIcon?>(null) }\n    LaunchedEffect(packageName, themed) {\n        icon = withContext(Dispatchers.IO) {\n            runCatching {\n                val drawable = context.packageManager.getApplicationIcon(packageName)\n                val monochrome = if (themed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {\n                    (drawable as? AdaptiveIconDrawable)?.monochrome\n                } else {\n                    null\n                }\n                if (monochrome != null) {\n                    LoadedAppIcon(monochrome.toBitmap().asImageBitmap(), themed = true)\n                } else {\n                    LoadedAppIcon(drawable.toBitmap().asImageBitmap(), themed = false)\n                }\n            }.getOrNull()\n        }\n    }\n''',
        "rememberAppIcon async load",
    )
    text = replace_once(
        text,
        '''    val relativeTime by produceState(initialValue = formatRelativeTime(postTimeMs), key1 = postTimeMs) {\n        while (true) {\n            delay(1_000L)\n            value = formatRelativeTime(postTimeMs)\n        }\n    }\n''',
        '''    var relativeTime by remember(postTimeMs) { mutableStateOf(formatRelativeTime(postTimeMs)) }\n    LaunchedEffect(postTimeMs) {\n        while (true) {\n            delay(1_000L)\n            relativeTime = formatRelativeTime(postTimeMs)\n        }\n    }\n''',
        "relative time state",
    )
    if "produceState" in text:
        raise SystemExit("DynamicIsland still contains produceState")
    return text


def patch_main_screen(text: str) -> str:
    text = replace_once(
        text,
        "    Scaffold { _ ->\n",
        "    Scaffold(contentWindowInsets = WindowInsets(0, 0, 0, 0)) { scaffoldPadding ->\n",
        "Scaffold explicit insets",
    )
    text = replace_once(
        text,
        "        Box(modifier = Modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.surfaceContainer)) {\n",
        "        Box(\n            modifier = Modifier\n                .fillMaxSize()\n                .padding(scaffoldPadding)\n                .background(color = MaterialTheme.colorScheme.surfaceContainer),\n        ) {\n",
        "Scaffold content padding consumption",
    )
    return text


def patch_build_workflow(text: str) -> str:
    return replace_once(
        text,
        "      - name: Lint\n        continue-on-error: true\n        run: ./gradlew lintDebug --no-daemon\n",
        "      - name: Lint\n        run: ./gradlew lintDebug --no-daemon\n",
        "strict lint workflow",
    )


update("app/src/main/AndroidManifest.xml", patch_manifest)
update("app/src/main/java/com/ekoehler/expressivecutout/events/SystemEventMonitor.kt", patch_system_event_monitor)
update("app/src/main/java/com/ekoehler/expressivecutout/ui/screen/SettingScreens/AppsScreen.kt", patch_apps_screen)
update("app/src/main/java/com/ekoehler/expressivecutout/ui/screen/SettingScreens/EventIconsScreen.kt", patch_event_icons)
update("app/src/main/java/com/ekoehler/expressivecutout/ui/screen/SettingScreens/ShowsWhenEmptyScreen.kt", patch_shows_when_empty)
update("app/src/main/java/com/ekoehler/expressivecutout/overlay/DynamicIsland.kt", patch_dynamic_island)
update("app/src/main/java/com/ekoehler/expressivecutout/ui/MainScreen.kt", patch_main_screen)
update(".github/workflows/build.yml", patch_build_workflow)
print("LINT_DEBT_FIXES=APPLIED")

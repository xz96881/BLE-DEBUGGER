package com.example

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.HorizontalDivider as Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch

// Beautiful Custom Palette - Cyber Industrial Theme
private val CyberBg = Color(0xFF0F1115)         // Dark Charcoal Steel
private val CyberSurface = Color(0xFF161920)    // Secondary Steel Card
private val CyberPanel = Color(0xFF1E222B)      // Panel Gray
private val NeonCyan = Color(0xFF00F0FF)        // Cyan Laser Accent
private val NeonGreen = Color(0xFF39FF14)       // Lime Green (Good states / RX / TX Success)
private val NeonAmber = Color(0xFFFFB300)       // Calibration Amber
private val NeonCoral = Color(0xFFFF4D4D)       // Crimson Warning
private val SlateText = Color(0xFF8A93A6)       // Soft steel text
private val LightText = Color(0xFFE3E8F0)       // Off-white crisp text

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BleDebuggerScreen(
    viewModel: BleViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Collect variables from ViewModel
    val scannedDevices by viewModel.scannedDevices.collectAsState()
    val activeConnections by viewModel.activeConnections.collectAsState()
    val isScanning by viewModel.isScanning.collectAsState()
    val isUShieldFilterEnabled by viewModel.isUShieldFilterEnabled.collectAsState()
    val logs by viewModel.logs.collectAsState()
    val serviceUuid by viewModel.serviceUuid.collectAsState()
    val characteristicUuid by viewModel.characteristicUuid.collectAsState()
    val commandInput by viewModel.commandInput.collectAsState()
    val isHexMode by viewModel.isHexMode.collectAsState()
    val selectedTab by viewModel.selectedTab.collectAsState()
    val isPermissionGranted by viewModel.isPermissionsGranted.collectAsState()
    val commandPresets by viewModel.commandPresets.collectAsState()

    // Determine the list of required permissions dynamically based on Android SDK build version
    val permissionsToRequest = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }
    }

    // Permission launcher for multiple Bluetooth/Location requirements
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { resultMap ->
        val allGranted = resultMap.values.all { it }
        viewModel.setPermissionsGranted(allGranted)
        if (allGranted) {
            Toast.makeText(context, "所有蓝牙相关硬件权限已获准通畅", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(context, "蓝牙权限未完全获准，仅可运行虚拟模拟模式", Toast.LENGTH_LONG).show()
        }
    }

    // Initial check on launch
    LaunchedEffect(Unit) {
        val hasPermissions = permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
        viewModel.setPermissionsGranted(hasPermissions)
    }

    // Main layout
    Surface(
        modifier = modifier
            .fillMaxSize()
            .imePadding(),
        color = CyberBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding(),
                    bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
                )
        ) {
            // Header Top Bar - Single Row Bounded Layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(CyberSurface)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "BLE 批量调试端",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = LightText
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(NeonGreen.copy(0.12f))
                            .border(1.dp, NeonGreen.copy(0.3f), RoundedCornerShape(3.dp))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "物理直连",
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            color = NeonGreen
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (isPermissionGranted) NeonGreen.copy(0.12f) else NeonAmber.copy(0.12f))
                        .border(1.dp, if (isPermissionGranted) NeonGreen.copy(0.3f) else NeonAmber.copy(0.3f), RoundedCornerShape(3.dp))
                        .clickable {
                            if (!isPermissionGranted) {
                                permissionLauncher.launch(permissionsToRequest)
                            }
                        }
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (isPermissionGranted) "HARDWARE READY" else "GRANT BLE",
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isPermissionGranted) NeonGreen else NeonAmber
                    )
                }
            }

            Divider(color = CyberSurface, thickness = 1.dp)

            // Tab navigation selector
            val tabLabels = listOf("设备连接", "群发控制", "调试终端")
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = CyberSurface,
                contentColor = NeonCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color = NeonCyan,
                        height = 2.dp
                    )
                }
            ) {
                tabLabels.forEachIndexed { index, label ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { viewModel.setSelectedTab(index) },
                        text = {
                            Text(
                                text = label,
                                fontSize = 14.sp,
                                fontWeight = if (selectedTab == index) FontWeight.Bold else FontWeight.Normal,
                                color = if (selectedTab == index) NeonCyan else SlateText
                            )
                        },
                        modifier = Modifier.testTag("nav_tab_$index")
                    )
                }
            }

            // Tab View contents
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when (selectedTab) {
                    0 -> TabConnectionManager(
                        scannedDevices = scannedDevices,
                        activeConnections = activeConnections,
                        isScanning = isScanning,
                        isUShieldFilterEnabled = isUShieldFilterEnabled,
                        isPermissionGranted = isPermissionGranted,
                        onToggleUShieldFilter = { viewModel.setUShieldFilterEnabled(it) },
                        onTriggerScan = { viewModel.triggerScan() },
                        onConnect = { viewModel.connectDevice(it) },
                        onDisconnect = { viewModel.disconnectDevice(it) },
                        onRequestPermissions = { permissionLauncher.launch(permissionsToRequest) }
                    )
                    1 -> TabBatchController(
                        activeConnections = activeConnections,
                        commandPresets = commandPresets,
                        serviceUuid = serviceUuid,
                        characteristicUuid = characteristicUuid,
                        commandInput = commandInput,
                        isHexMode = isHexMode,
                        onServiceUuidChange = { viewModel.setServiceUuid(it) },
                        onCharacteristicUuidChange = { viewModel.setCharacteristicUuid(it) },
                        onCommandInputChange = { viewModel.setCommandInput(it) },
                        onToggleHexMode = { viewModel.setHexMode(it) },
                        onApplyPreset = { viewModel.applyPreset(it) },
                        onToggleDeviceSelection = { viewModel.toggleDeviceSelection(it) },
                        onSendCommandBatch = { viewModel.sendCommandBatch() },
                        onDisconnectAll = { viewModel.disconnectAllDevices() },
                        onAddCustomPreset = { label, cmd, isHex -> viewModel.addCustomPreset(label, cmd, isHex) },
                        onRemoveCustomPreset = { viewModel.removeCustomPreset(it) }
                    )
                    2 -> TabTerminalLogs(
                        logs = logs,
                        onClearLogs = { viewModel.clearLogs() }
                    )
                }
            }
        }
    }
}

// ==========================================
// TAB 0: CONNECTION MANAGER
// ==========================================
@Composable
fun TabConnectionManager(
    scannedDevices: List<BleDevice>,
    activeConnections: Map<String, BleDeviceConnection>,
    isScanning: Boolean,
    isUShieldFilterEnabled: Boolean,
    isPermissionGranted: Boolean,
    onToggleUShieldFilter: (Boolean) -> Unit,
    onTriggerScan: () -> Unit,
    onConnect: (BleDevice) -> Unit,
    onDisconnect: (String) -> Unit,
    onRequestPermissions: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp)
    ) {
        // UShield Filter configuration card (Replaces running mode config card)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStrokeDefaults.solid(CyberPanel)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "ushield_filter",
                            tint = NeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "U盾特配查找过滤 🛡️",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = LightText
                        )
                    }
                    
                    Switch(
                        checked = isUShieldFilterEnabled,
                        onCheckedChange = { onToggleUShieldFilter(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = NeonCyan,
                            checkedTrackColor = NeonCyan.copy(0.3f),
                            uncheckedThumbColor = SlateText,
                            uncheckedTrackColor = CyberPanel
                        )
                    )
                }
                
                Text(
                    text = if (isUShieldFilterEnabled) {
                        "已开启过滤模式：自动屏蔽物理周围非U盾设备。当前仅列出蓝牙名称包含 'WATCH' 或 'WCH' 或 'BLE_DEVICE'的信道和端。"
                    } else {
                        "展示环境中全部被扫描发现的公开蓝牙。打开本项专属过滤可以直接排除其它电子流噪音设备。"
                    },
                    fontSize = 11.sp,
                    color = if (isUShieldFilterEnabled) NeonCyan else SlateText,
                    modifier = Modifier.padding(top = 4.dp),
                    lineHeight = 15.sp
                )

                if (!isPermissionGranted) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(NeonCoral.copy(0.12f))
                            .border(1.dp, NeonCoral.copy(0.3f), RoundedCornerShape(6.dp))
                            .padding(10.dp)
                    ) {
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "alert",
                                    tint = NeonCoral,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "未获得完整定位与蓝牙权限",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = NeonCoral
                                )
                            }
                            Text(
                                text = "由于Android系统安全约束，底层真实的BLE扫描需要获得硬件授权，您可以尝试点此完成配置授信。",
                                fontSize = 10.sp,
                                color = LightText.copy(0.8f),
                                modifier = Modifier.padding(top = 3.dp),
                                lineHeight = 13.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onRequestPermissions,
                                colors = ButtonDefaults.buttonColors(containerColor = NeonCoral),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(30.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
                            ) {
                                Text("立即授权蓝牙权限", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }
        }

        // Search devices scanner action bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isUShieldFilterEnabled) "U盾专用信道 (已过滤)" else "周围可用信道列表",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    color = LightText
                )
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isScanning) NeonGreen else SlateText)
                )
            }

            Button(
                onClick = onTriggerScan,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isScanning) NeonCoral else NeonCyan
                ),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .height(34.dp)
                    .testTag("action_scan_toggle"),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
            ) {
                Icon(
                    imageVector = if (isScanning) Icons.Default.Clear else Icons.Default.PlayArrow,
                    contentDescription = "Scan status",
                    tint = CyberBg,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isScanning) "停止扫描" else "扫描设备",
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    color = CyberBg
                )
            }
        }

        // Devices Scanned List (Displays active connection status at side)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStrokeDefaults.solid(CyberPanel)
        ) {
            if (scannedDevices.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Scanning progress",
                            tint = SlateText,
                            modifier = Modifier.size(34.dp)
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = if (isScanning) "扫描中, 正在发现可用测试终端..." else "通道列表为空，请启动上方蓝牙扫描器",
                            fontSize = 13.sp,
                            color = SlateText,
                            fontWeight = FontWeight.Medium
                        )
                        if (!isScanning) {
                            Text(
                                text = "提示: 请确保您的物理U盾处于通电且可广播状态",
                                fontSize = 11.sp,
                                color = SlateText.copy(0.7f),
                                modifier = Modifier.padding(top = 6.dp)
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(scannedDevices) { device ->
                        val connectionState = activeConnections[device.address]
                        BleDeviceRowItem(
                            device = device,
                            connection = connectionState,
                            onConnect = { onConnect(device) },
                            onDisconnect = { onDisconnect(device.address) }
                        )
                        Divider(color = CyberBg, thickness = 1.dp)
                    }
                }
            }
        }
    }
}

@Composable
fun BleDeviceRowItem(
    device: BleDevice,
    connection: BleDeviceConnection?,
    onConnect: () -> Unit,
    onDisconnect: () -> Unit
) {
    val state = connection?.state ?: BluetoothConnectionState.DISCONNECTED
    
    // Status color animate
    val textBgColor by animateColorAsState(
        targetValue = when (state) {
            BluetoothConnectionState.DISCONNECTED -> Color.Transparent
            BluetoothConnectionState.CONNECTING -> NeonAmber.copy(0.12f)
            BluetoothConnectionState.CONNECTED -> NeonCyan.copy(0.12f)
            BluetoothConnectionState.DISCOVERING_SERVICES -> NeonCyan.copy(0.12f)
            BluetoothConnectionState.READY -> NeonGreen.copy(0.12f)
            BluetoothConnectionState.DISCONNECTING -> NeonCoral.copy(0.12f)
        },
        animationSpec = tween(300),
        label = "state_bg"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(textBgColor)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = device.name.ifEmpty { "Unnamed (未知名称)" },
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    color = LightText
                )
            }
            
            Spacer(modifier = Modifier.height(2.dp))

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "MAC: ",
                        fontSize = 11.sp,
                        color = SlateText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = device.address,
                        fontSize = 11.sp,
                        color = LightText.copy(0.8f),
                        fontFamily = FontFamily.Monospace
                    )
                }
                Spacer(modifier = Modifier.height(1.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "RSSI: ",
                        fontSize = 11.sp,
                        color = SlateText,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${device.rssi} dBm",
                        fontSize = 11.sp,
                        color = when {
                            device.rssi > -60 -> NeonGreen
                            device.rssi > -80 -> NeonAmber
                            else -> NeonCoral
                        }
                    )
                }
            }

            if (state != BluetoothConnectionState.DISCONNECTED) {
                Row(
                    modifier = Modifier.padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when (state) {
                                    BluetoothConnectionState.READY -> NeonGreen
                                    BluetoothConnectionState.DISCONNECTED -> Color.Transparent
                                    else -> NeonAmber
                                }
                            )
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Text(
                        text = "服务协议: ${state.label}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when (state) {
                            BluetoothConnectionState.READY -> NeonGreen
                            BluetoothConnectionState.DISCONNECTED -> SlateText
                            else -> NeonAmber
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Action connect/disconnect button
        Button(
            onClick = {
                if (state == BluetoothConnectionState.DISCONNECTED) {
                    onConnect()
                } else {
                    onDisconnect()
                }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = when (state) {
                    BluetoothConnectionState.DISCONNECTED -> NeonCyan.copy(0.15f)
                    else -> NeonCoral.copy(0.15f)
                }
            ),
            shape = RoundedCornerShape(4.dp),
            modifier = Modifier
                .height(34.dp)
                .border(
                    1.dp,
                    when (state) {
                        BluetoothConnectionState.DISCONNECTED -> NeonCyan.copy(0.5f)
                        else -> NeonCoral.copy(0.5f)
                    },
                    RoundedCornerShape(4.dp)
                ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)
        ) {
            Text(
                text = when (state) {
                    BluetoothConnectionState.DISCONNECTED -> "连 接"
                    BluetoothConnectionState.CONNECTING -> "连接中"
                    BluetoothConnectionState.DISCONNECTING -> "断开中"
                    else -> "断 开"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = when (state) {
                    BluetoothConnectionState.DISCONNECTED -> NeonCyan
                    else -> NeonCoral
                }
            )
        }
    }
}

// ==========================================
// TAB 1: BATCH CONTROLLER (ONE-CLICK MASS SEND)
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TabBatchController(
    activeConnections: Map<String, BleDeviceConnection>,
    commandPresets: List<CommandPreset>,
    serviceUuid: String,
    characteristicUuid: String,
    commandInput: String,
    isHexMode: Boolean,
    onServiceUuidChange: (String) -> Unit,
    onCharacteristicUuidChange: (String) -> Unit,
    onCommandInputChange: (String) -> Unit,
    onToggleHexMode: (Boolean) -> Unit,
    onApplyPreset: (CommandPreset) -> Unit,
    onToggleDeviceSelection: (String) -> Unit,
    onSendCommandBatch: () -> Unit,
    onDisconnectAll: () -> Unit,
    onAddCustomPreset: (String, String, Boolean) -> Unit,
    onRemoveCustomPreset: (CommandPreset) -> Unit
) {
    val scrollState = rememberScrollState()
    val readyDevices = activeConnections.values.filter { it.state == BluetoothConnectionState.READY }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(scrollState)
    ) {
        // Step 1: Destination Address configuration Card (Gatt Service and Write Char definition)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStrokeDefaults.solid(CyberPanel)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text(
                    text = "⚙️ BLE 特征目标配置  (Target Configuration)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = LightText
                )
                Spacer(modifier = Modifier.height(10.dp))
                
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedTextField(
                        value = serviceUuid,
                        onValueChange = onServiceUuidChange,
                        label = { Text("服务 UUID (Gatt Service)", fontSize = 10.sp, color = SlateText) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = LightText),
                        shape = RoundedCornerShape(4.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberPanel,
                            focusedContainerColor = CyberBg,
                            unfocusedContainerColor = CyberBg
                        )
                    )
                    OutlinedTextField(
                        value = characteristicUuid,
                        onValueChange = onCharacteristicUuidChange,
                        label = { Text("写入特征 UUID (Write Char)", fontSize = 10.sp, color = SlateText) },
                        modifier = Modifier.fillMaxWidth(),
                        textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 12.sp, color = LightText),
                        shape = RoundedCornerShape(4.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NeonCyan,
                            unfocusedBorderColor = CyberPanel,
                            focusedContainerColor = CyberBg,
                            unfocusedContainerColor = CyberBg
                        )
                    )
                }
            }
        }

        // Step 2: Target Connections & Checkboxes of active connections
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStrokeDefaults.solid(CyberPanel)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "👥 被控群发节点列表 (${readyDevices.size} 个就绪通道)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = LightText
                    )
                    
                    if (activeConnections.isNotEmpty()) {
                        TextButton(
                            onClick = onDisconnectAll,
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Text("全部断开", fontSize = 11.sp, color = NeonCoral, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (readyDevices.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberBg)
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "empty",
                                tint = SlateText,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "暂无已【连接成功且服务就绪】的蓝牙设备",
                                fontSize = 11.sp,
                                color = SlateText,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "点击上方“设备连接”选项卡连接几个物理或模拟设备进行群发调试",
                                fontSize = 10.sp,
                                color = SlateText.copy(0.7f),
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CyberBg)
                            .padding(4.dp)
                    ) {
                        readyDevices.forEach { connection ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onToggleDeviceSelection(connection.device.address) }
                                    .padding(vertical = 4.dp, horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = connection.isSelectedForBatch,
                                    onCheckedChange = { onToggleDeviceSelection(connection.device.address) },
                                    colors = CheckboxDefaults.colors(
                                        checkedColor = NeonGreen,
                                        uncheckedColor = SlateText,
                                        checkmarkColor = CyberBg
                                    )
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = connection.device.name,
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = LightText
                                    )
                                    Text(
                                        text = connection.device.address,
                                        fontSize = 10.sp,
                                        color = SlateText,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                                
                                // Feedback send status
                                connection.lastTransmissionStatus?.let { status ->
                                    val statusColor = when {
                                        status == "成功" -> NeonGreen
                                        status.startsWith("失败") -> NeonCoral
                                        else -> NeonAmber
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(statusColor.copy(0.12f))
                                            .border(1.dp, statusColor.copy(0.3f), RoundedCornerShape(3.dp))
                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Step 3: Commands Quick Presets
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStrokeDefaults.solid(CyberPanel)
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                var isAddingPreset by remember { mutableStateOf(false) }
                var newPresetLabel by remember { mutableStateOf("") }
                var newPresetPayload by remember { mutableStateOf("") }
                var newPresetIsHex by remember { mutableStateOf(true) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "📦 快捷控制预设指令 (Quick Presets)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = LightText
                    )
                    
                    TextButton(
                        onClick = { isAddingPreset = !isAddingPreset },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                        modifier = Modifier.height(28.dp)
                    ) {
                        Text(
                            text = if (isAddingPreset) "收起 ➖" else "新增 ➕",
                            fontSize = 11.sp,
                            color = NeonCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (isAddingPreset) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .background(CyberBg)
                            .border(1.dp, CyberPanel, RoundedCornerShape(4.dp))
                            .padding(10.dp)
                    ) {
                        Text("🆕 新增自定义调试指令预设", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = LightText)
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = newPresetLabel,
                            onValueChange = { if (it.length <= 50) newPresetLabel = it },
                            label = { Text("指令名称 (Label)", fontSize = 10.sp, color = SlateText) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontSize = 11.sp, color = LightText),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = CyberPanel,
                                focusedContainerColor = CyberSurface,
                                unfocusedContainerColor = CyberSurface
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "${newPresetLabel.length}/50",
                                fontSize = 9.sp,
                                color = SlateText.copy(0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        OutlinedTextField(
                            value = newPresetPayload,
                            onValueChange = { newValue ->
                                val limit = if (newPresetIsHex) {
                                    val filtered = newValue.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ' ' }
                                    val rawHex = filtered.filter { it != ' ' }
                                    if (rawHex.length <= 512) filtered else newPresetPayload
                                } else {
                                    if (newValue.length <= 256) newValue else newPresetPayload
                                }
                                newPresetPayload = limit
                            },
                            label = { Text("指令负载 (Payload / HEX或ASCII)", fontSize = 10.sp, color = SlateText) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = LightText),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = NeonCyan,
                                unfocusedBorderColor = CyberPanel,
                                focusedContainerColor = CyberSurface,
                                unfocusedContainerColor = CyberSurface
                            )
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (newPresetIsHex) "十六进制 (偶数个16进制符，最大256字节)" else "文本 (最大256字符)",
                                fontSize = 9.sp,
                                color = SlateText.copy(0.5f)
                            )
                            val rawLength = if (newPresetIsHex) newPresetPayload.filter { it != ' ' }.length else newPresetPayload.length
                            val maxLength = if (newPresetIsHex) 512 else 256
                            Text(
                                text = "$rawLength/$maxLength",
                                fontSize = 9.sp,
                                color = if (rawLength >= maxLength) NeonCoral else SlateText.copy(0.7f)
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("模式: ", fontSize = 11.sp, color = SlateText)
                                Spacer(modifier = Modifier.width(6.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (newPresetIsHex) NeonAmber.copy(0.12f) else CyberPanel)
                                        .border(1.dp, if (newPresetIsHex) NeonAmber.copy(0.3f) else CyberBg, RoundedCornerShape(3.dp))
                                        .clickable { 
                                            newPresetIsHex = true
                                            // Reset or clamp payload
                                            newPresetPayload = ""
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NeonAmber))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("HEX", fontSize = 10.sp, color = if (newPresetIsHex) LightText else SlateText)
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                Row(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(3.dp))
                                        .background(if (!newPresetIsHex) NeonCyan.copy(0.12f) else CyberPanel)
                                        .border(1.dp, if (!newPresetIsHex) NeonCyan.copy(0.3f) else CyberBg, RoundedCornerShape(3.dp))
                                        .clickable { 
                                            newPresetIsHex = false
                                            // Reset or clamp payload
                                            newPresetPayload = ""
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(NeonCyan))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("TXT", fontSize = 10.sp, color = if (!newPresetIsHex) LightText else SlateText)
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                TextButton(
                                    onClick = {
                                        isAddingPreset = false
                                        newPresetLabel = ""
                                        newPresetPayload = ""
                                    },
                                    colors = ButtonDefaults.textButtonColors(contentColor = NeonCoral),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Text("取消", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = {
                                        if (newPresetLabel.isNotBlank() && newPresetPayload.isNotBlank()) {
                                            onAddCustomPreset(newPresetLabel.trim(), newPresetPayload.trim(), newPresetIsHex)
                                            isAddingPreset = false
                                            newPresetLabel = ""
                                            newPresetPayload = ""
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = NeonCyan),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(4.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text("保存", fontSize = 11.sp, color = CyberBg, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (commandPresets.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "💡 暂无自定义指令预设",
                            color = SlateText,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "点击右上角「新增 ➕」开始创建您专属的U盾快捷指令",
                            color = SlateText.copy(0.7f),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        commandPresets.forEach { preset ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(CyberPanel)
                                    .border(1.dp, if (commandInput == preset.payload && isHexMode == preset.isHex) NeonCyan else CyberBg, RoundedCornerShape(4.dp))
                                    .clickable { onApplyPreset(preset) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (preset.isHex) NeonAmber else NeonCyan)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = preset.label,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = LightText
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Delete preset",
                                        tint = NeonCoral.copy(0.7f),
                                        modifier = Modifier
                                            .size(12.dp)
                                            .clickable { onRemoveCustomPreset(preset) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Step 4: Batch Entry terminal console write panel
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = CyberSurface),
            border = BorderStrokeDefaults.solid(CyberPanel)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "✍️ 载荷及一键群发终端 (Payload Outbox)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = LightText
                    )
                    
                    // SEGMENTED ENCODER CONTROLLER (STR / HEX)
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CyberPanel)
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (!isHexMode) NeonCyan.copy(0.15f) else Color.Transparent)
                                .clickable { onToggleHexMode(false) }
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "TEXT (ASCII)",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (!isHexMode) NeonCyan else SlateText
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(3.dp))
                                .background(if (isHexMode) NeonAmber.copy(0.15f) else Color.Transparent)
                                .clickable { onToggleHexMode(true) }
                                .padding(horizontal = 10.dp, vertical = 3.dp)
                        ) {
                            Text(
                                "HEX十六进制",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isHexMode) NeonAmber else SlateText
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = commandInput,
                    onValueChange = { newValue ->
                        val limit = if (isHexMode) {
                            val filtered = newValue.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' || it == ' ' }
                            val rawHex = filtered.filter { it != ' ' }
                            if (rawHex.length <= 512) filtered else commandInput
                        } else {
                            if (newValue.length <= 256) newValue else commandInput
                        }
                        onCommandInputChange(limit)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .testTag("command_input_field"),
                    textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, color = LightText),
                    placeholder = {
                        Text(
                            text = if (isHexMode) "请输入16进制命令，例: AA 55 01 FF" else "请输入文本命令，例: READ_ALL_SENSORS",
                            fontSize = 11.sp,
                            color = SlateText.copy(0.6f)
                        )
                    },
                    shape = RoundedCornerShape(6.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (isHexMode) NeonAmber else NeonCyan,
                        unfocusedBorderColor = CyberPanel,
                        focusedContainerColor = CyberBg,
                        unfocusedContainerColor = CyberBg
                    )
                )

                // Character/Byte limitations overlay info below the field
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 2.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isHexMode) "限制: 最大 256 字节 (512个十六进制字符)" else "限制: 最大 256 字符",
                        fontSize = 10.sp,
                        color = SlateText.copy(0.5f)
                    )
                    val rawLen = if (isHexMode) commandInput.filter { it != ' ' }.length else commandInput.length
                    val maxLen = if (isHexMode) 512 else 256
                    Text(
                        text = "$rawLen/$maxLen",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (rawLen >= maxLen) NeonCoral else (if (isHexMode) NeonAmber else NeonCyan)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // GIANT BATCH Action trigger button
                val activeSenderCount = activeConnections.values.count { it.isSelectedForBatch && it.state == BluetoothConnectionState.READY }
                Button(
                    onClick = onSendCommandBatch,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isHexMode) NeonAmber else NeonCyan
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("action_batch_send"),
                    enabled = activeSenderCount > 0
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = CyberBg,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (activeSenderCount == 0) "请先连接并勾选设备" else "一键批量向 $activeSenderCount 个设备群发",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = CyberBg
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

// ==========================================
// TAB 2: TERMINAL LOGS
// ==========================================
@Composable
fun TabTerminalLogs(
    logs: List<ConsoleLog>,
    onClearLogs: () -> Unit
) {
    val listState = rememberLazyListState()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    var activeFilter by remember { mutableStateOf<LogLevel?>(null) }

    // Auto screw logs scroll to bottom on new event emission
    LaunchedEffect(logs.size) {
        if (logs.isNotEmpty()) {
            listState.animateScrollToItem(logs.size - 1)
        }
    }

    // Filter logs list
    val filteredLogs = remember(logs, activeFilter) {
        if (activeFilter == null) logs else logs.filter { it.level == activeFilter }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Terminal Filter Actions & Clear
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Logs category tab row chips
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // All
                FilterChipCompat(
                    label = "全部",
                    isActive = activeFilter == null,
                    onClick = { activeFilter = null },
                    activeColor = NeonCyan
                )
                // Tx Logged
                FilterChipCompat(
                    label = "TX",
                    isActive = activeFilter == LogLevel.TX,
                    onClick = { activeFilter = LogLevel.TX },
                    activeColor = NeonAmber
                )
                // Rx Logged
                FilterChipCompat(
                    label = "RX",
                    isActive = activeFilter == LogLevel.RX,
                    onClick = { activeFilter = LogLevel.RX },
                    activeColor = NeonGreen
                )
                // Errors
                FilterChipCompat(
                    label = "错误",
                    isActive = activeFilter == LogLevel.ERROR,
                    onClick = { activeFilter = LogLevel.ERROR },
                    activeColor = NeonCoral
                )
            }

            // Copy and Trash can clear button row
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                TextButton(
                    onClick = {
                        if (logs.isNotEmpty()) {
                            val logDump = logs.joinToString("\n") { "[${it.formattedTime}] [${it.tag}] ${it.message}" }
                            clipboardManager.setText(AnnotatedString(logDump))
                            Toast.makeText(context, "所有终端日志已复制至剪贴板", Toast.LENGTH_SHORT).show()
                        } else {
                            Toast.makeText(context, "控制台尚无日志", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.height(30.dp),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text("复制全部", fontSize = 11.sp, color = NeonCyan, fontWeight = FontWeight.Bold)
                }

                IconButton(
                    onClick = onClearLogs,
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear terminal content",
                        tint = NeonCoral,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // Terminal Console Shell Black screen
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF07080A)), // Solid dark black console shade
            border = BorderStrokeDefaults.solid(CyberPanel),
            shape = RoundedCornerShape(4.dp)
        ) {
            if (filteredLogs.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("终端控器运行日志为空", color = SlateText, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filteredLogs) { log ->
                        val color = when (log.level) {
                            LogLevel.INFO -> SlateText
                            LogLevel.SUCCESS -> NeonGreen
                            LogLevel.WARN -> NeonAmber
                            LogLevel.ERROR -> NeonCoral
                            LogLevel.TX -> NeonAmber
                            LogLevel.RX -> NeonCyan
                        }

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "[${log.formattedTime}]",
                                    color = SlateText,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(end = 6.dp)
                                )
                                
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(color.copy(0.12f))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = log.tag,
                                        color = color,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(3.dp))
                            
                            Text(
                                text = log.message,
                                color = if (log.level == LogLevel.INFO) LightText.copy(0.85f) else color,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FilterChipCompat(
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    activeColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(if (isActive) activeColor.copy(0.15f) else CyberSurface)
            .border(1.dp, if (isActive) activeColor else CyberPanel, RoundedCornerShape(4.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = label,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (isActive) activeColor else SlateText
        )
    }
}

// Simple local border token definitions
private object BorderStrokeDefaults {
    fun solid(color: Color) = androidx.compose.foundation.BorderStroke(1.dp, color)
}

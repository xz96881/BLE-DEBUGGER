package com.example

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BleViewModel(application: Application) : AndroidViewModel(application) {

    private val bleController = BleController(application.applicationContext)

    // Room Database and DAO
    private val database: AppDatabase by lazy {
        androidx.room.Room.databaseBuilder(
            getApplication(),
            AppDatabase::class.java,
            "ble_debugger_db"
        ).build()
    }
    private val presetDao by lazy { database.commandPresetDao() }

    // Exposed Flows from Controller
    val activeConnections = bleController.activeConnections
    val isScanning = bleController.isScanning

    // U-Shield filter tracking
    private val _isUShieldFilterEnabled = MutableStateFlow(false)
    val isUShieldFilterEnabled: StateFlow<Boolean> = _isUShieldFilterEnabled.asStateFlow()

    fun setUShieldFilterEnabled(enabled: Boolean) {
        _isUShieldFilterEnabled.value = enabled
        postLog("FILTER", LogLevel.INFO, if (enabled) "【过滤开启】仅查找特殊U盾调试通道 (WATCH / WCH/ BLE_DEVICE)" else "【过滤关闭】展示全部发现的设备")
    }

    // Combine scannedDevices with isUShieldFilterEnabled to produce filtered lists
    val scannedDevices: StateFlow<List<BleDevice>> = combine(
        bleController.scannedDevices,
        _isUShieldFilterEnabled
    ) { devices, isFilterOn ->
        if (!isFilterOn) {
            devices
        } else {
            devices.filter { dev ->
                val name = dev.name.trim()
                name.contains("WATCH", ignoreCase = true) ||
                name.contains("WCH", ignoreCase = true) ||
                name.contains("BLE_DEVICE", ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Live terminal log list
    private val _logs = MutableStateFlow<List<ConsoleLog>>(emptyList())
    val logs: StateFlow<List<ConsoleLog>> = _logs.asStateFlow()

    // Command configuration parameters
    private val _serviceUuid = MutableStateFlow("8653000a-43e6-47b7-9cb0-5fc21d4ae340")
    val serviceUuid: StateFlow<String> = _serviceUuid.asStateFlow()

    private val _characteristicUuid = MutableStateFlow("8653000c-43e6-47b7-9cb0-5fc21d4ae340")
    val characteristicUuid: StateFlow<String> = _characteristicUuid.asStateFlow()

    private val _commandInput = MutableStateFlow("000A0101")
    val commandInput: StateFlow<String> = _commandInput.asStateFlow()

    private val _isHexMode = MutableStateFlow(true)
    val isHexMode: StateFlow<Boolean> = _isHexMode.asStateFlow()

    // Permission tracking
    private val _isPermissionsGranted = MutableStateFlow(false)
    val isPermissionsGranted: StateFlow<Boolean> = _isPermissionsGranted.asStateFlow()

    // Selected Tab control in UI (0 = SCAN & MANAGE, 1 = BATCH SENDER, 2 = TERMINAL LOGS)
    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    // Room Database custom preset list flow
    val commandPresets: StateFlow<List<CommandPreset>> = presetDao.getAllPresets()
        .map { list -> list.map { it.toCommandPreset() } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = emptyList()
        )

    fun addCustomPreset(label: String, payload: String, isHex: Boolean) {
        // Enforce physical limits: Name up to 50 chars, Payload up to 256 bytes (512 hex digits or 256 ASCII characters)
        val cleanLabel = label.trim().take(50)
        
        val cleanPayload = if (isHex) {
            val rawHex = payload.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            if (rawHex.length > 512) {
                postLog("PRESET", LogLevel.WARN, "保存失败: 十六进制载荷长度超出 256 字节 (512位16进制字符)！")
                return
            }
            payload
        } else {
            if (payload.length > 256) {
                postLog("PRESET", LogLevel.WARN, "保存失败: 文本载荷长度超出 256 字符！")
                return
            }
            payload
        }

        viewModelScope.launch {
            try {
                presetDao.insertPreset(
                    CommandPresetEntity(
                        label = cleanLabel,
                        payload = cleanPayload,
                        isHex = isHex
                    )
                )
                postLog("PRESET", LogLevel.SUCCESS, "已保存自定义指令: \"$cleanLabel\"")
            } catch (e: Exception) {
                postLog("PRESET", LogLevel.ERROR, "保存自定义指令失败: ${e.localizedMessage}")
            }
        }
    }

    fun removeCustomPreset(preset: CommandPreset) {
        viewModelScope.launch {
            try {
                presetDao.deletePresetByFields(preset.label, preset.payload, preset.isHex)
                postLog("PRESET", LogLevel.WARN, "已删除指令: \"${preset.label}\"")
            } catch (e: Exception) {
                postLog("PRESET", LogLevel.ERROR, "删除指令失败: ${e.localizedMessage}")
            }
        }
    }

    init {
        // Collect logs emitted from the controller and insert them into viewModel state
        viewModelScope.launch {
            bleController.logFlow.collect { logItem ->
                _logs.update { list ->
                    // Cap console to 150 entries to keep memory and rendering extremely fast
                    val newList = list + logItem
                    if (newList.size > 150) newList.drop(newList.size - 150) else newList
                }
            }
        }

        // Emit initial configuration info
        postLog("SYSTEM", LogLevel.INFO, "BLE 调试控制台服务已启动")
        postLog("SYSTEM", LogLevel.INFO, "当前默认通道: U盾自定义信道 (Service: 8653000A, TX: 8653000B, RX: 8653000C)")
    }

    fun setSelectedTab(tabIndex: Int) {
        _selectedTab.value = tabIndex
    }

    fun setServiceUuid(uuid: String) {
        _serviceUuid.value = uuid
    }

    fun setCharacteristicUuid(uuid: String) {
        _characteristicUuid.value = uuid
    }

    fun setCommandInput(cmd: String) {
        val limited = if (_isHexMode.value) {
            val rawHex = cmd.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            if (rawHex.length > 512) {
                // Keep spacing but truncate to 512 raw digits
                // Simple representation check
                if (cmd.length > 1024) cmd.take(1024) else cmd
            } else {
                cmd
            }
        } else {
            if (cmd.length > 256) {
                cmd.take(256)
            } else {
                cmd
            }
        }
        _commandInput.value = limited
    }

    fun setHexMode(enabled: Boolean) {
        _isHexMode.value = enabled
        val current = _commandInput.value
        if (enabled) {
            val rawHex = current.filter { it.isDigit() || it.lowercaseChar() in 'a'..'f' }
            if (rawHex.length > 512) {
                _commandInput.value = current.take(512)
            }
        } else {
            if (current.length > 256) {
                _commandInput.value = current.take(256)
            }
        }
    }

    fun setPermissionsGranted(granted: Boolean) {
        _isPermissionsGranted.value = granted
        if (granted) {
            postLog("SYSTEM", LogLevel.SUCCESS, "运行时蓝牙和粗略定位定位协议已成功授权！")
        } else {
            postLog("SYSTEM", LogLevel.WARN, "未授权蓝牙扫描与连接。请进入系统设置开启蓝牙与定位权限。")
        }
    }

    fun postLog(tag: String, level: LogLevel, message: String) {
        viewModelScope.launch {
            _logs.update { list ->
                val newItem = ConsoleLog(tag = tag, level = level, message = message)
                val newList = list + newItem
                if (newList.size > 150) newList.drop(newList.size - 150) else newList
            }
        }
    }

    fun triggerScan() {
        if (isScanning.value) {
            bleController.stopScan()
        } else {
            bleController.startScan()
        }
    }

    fun stopScan() {
        bleController.stopScan()
    }

    fun toggleDeviceSelection(address: String) {
        bleController.toggleDeviceSelection(address)
    }

    fun connectDevice(device: BleDevice) {
        bleController.connect(device)
    }

    fun disconnectDevice(address: String) {
        bleController.disconnect(address)
    }

    fun disconnectAllDevices() {
        bleController.disconnectAll()
    }

    fun applyPreset(preset: CommandPreset) {
        _commandInput.value = preset.payload
        _isHexMode.value = preset.isHex
        postLog("PRESET", LogLevel.INFO, "加载预设指令: \"${preset.label}\"")
    }

    fun clearLogs() {
        _logs.value = emptyList()
        postLog("SYSTEM", LogLevel.INFO, "日志控制台已清空")
    }

    // Execute "一键群发" command to all chosen connected peripherals
    fun sendCommandBatch() {
        val payloadStr = _commandInput.value
        if (payloadStr.isEmpty()) {
            postLog("BATCH", LogLevel.ERROR, "发送失败: 指令不能为空字符串")
            return
        }

        val payloadBytes: ByteArray
        if (_isHexMode.value) {
            try {
                payloadBytes = parseHexStringToByteArray(payloadStr)
            } catch (e: Exception) {
                postLog("BATCH", LogLevel.ERROR, "HEX十六进制指令非法! 请输入正确的偶数位16进制字符(如 AA550300FF) | 错误: ${e.message}")
                return
            }
        } else {
            payloadBytes = payloadStr.toByteArray(Charsets.UTF_8)
        }

        bleController.sendBatchCommand(
            payload = payloadBytes,
            payloadString = payloadStr,
            isHex = _isHexMode.value,
            serviceUuidString = _serviceUuid.value,
            characteristicUuidString = _characteristicUuid.value
        )
    }

    // Utility helper to safely convert hex formatting (allows spaces, capitalization)
    private fun parseHexStringToByteArray(hexString: String): ByteArray {
        val clean = hexString.replace("\\s".toRegex(), "").uppercase()
        if (clean.length % 2 != 0) {
            throw IllegalArgumentException("十六进制字符总数大小必须是偶数")
        }
        val result = ByteArray(clean.length / 2)
        for (i in clean.indices step 2) {
            val h = digitForChar(clean[i])
            val l = digitForChar(clean[i + 1])
            if (h == -1 || l == -1) {
                throw IllegalArgumentException("包含非法非十六进制字符: '${clean[i]}${clean[i+1]}'")
            }
            result[i / 2] = ((h shl 4) + l).toByte()
        }
        return result
    }

    private fun digitForChar(c: Char): Int {
        return when (c) {
            in '0'..'9' -> c - '0'
            in 'A'..'F' -> c - 'A' + 10
            else -> -1
        }
    }
}

// Extension list update helpers
fun <T> MutableStateFlow<List<T>>.update(function: (List<T>) -> List<T>) {
    while (true) {
        val prev = value
        val next = function(prev)
        if (compareAndSet(prev, next)) {
            return
        }
    }
}

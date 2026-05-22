package com.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

@SuppressLint("MissingPermission")
class BleController(private val context: Context) {

    private val bluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter
    private val bluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner

    // Scope for background simulation jobs
    private val scope = CoroutineScope(Dispatchers.Default)

    // Flows for UI update
    private val _scannedDevices = MutableStateFlow<List<BleDevice>>(emptyList())
    val scannedDevices: StateFlow<List<BleDevice>> = _scannedDevices.asStateFlow()

    private val _activeConnections = MutableStateFlow<Map<String, BleDeviceConnection>>(emptyMap())
    val activeConnections: StateFlow<Map<String, BleDeviceConnection>> = _activeConnections.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    // Log emission channel
    private val _logFlow = MutableSharedFlow<ConsoleLog>(extraBufferCapacity = 50)
    val logFlow: SharedFlow<ConsoleLog> = _logFlow.asSharedFlow()

    // Active real BluetoothGatts
    private val gattServers = mutableMapOf<String, BluetoothGatt>()

    val defaultServiceUuidString = "8653000a-43e6-47b7-9cb0-5fc21d4ae340"
    val defaultNotifyCharacteristicUuidString = "8653000b-43e6-47b7-9cb0-5fc21d4ae340"
    val defaultWriteCharacteristicUuidString = "8653000c-43e6-47b7-9cb0-5fc21d4ae340"
    private val CCCD_UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

    private fun emitLog(tag: String, level: LogLevel, message: String) {
        scope.launch {
            _logFlow.emit(ConsoleLog(tag = tag, level = level, message = message))
        }
    }

    private fun getDeviceNameSafe(device: BluetoothDevice?): String {
        if (device == null) return "未知设备 (Unnamed)"
        return try {
            device.name ?: "未知设备 (Unnamed)"
        } catch (e: SecurityException) {
            "未知设备 (未授权)"
        } catch (e: Exception) {
            "未知设备 (Unnamed)"
        }
    }

    private fun enableNotificationForDevice(gatt: BluetoothGatt, serviceUuidStr: String, notifyUuidStr: String) {
        val address = gatt.device?.address ?: ""
        val name = getDeviceNameSafe(gatt.device)
        try {
            val sUid = UUID.fromString(serviceUuidStr)
            val cUid = UUID.fromString(notifyUuidStr)
            
            val service = gatt.getService(sUid)
            if (service == null) {
                emitLog("GATT", LogLevel.WARN, "[$address] 未找到U盾默认服务 ${serviceUuidStr.substring(0, 8)}...，跳过自动订阅通知")
                return
            }
            
            val characteristic = service.getCharacteristic(cUid)
            if (characteristic == null) {
                emitLog("GATT", LogLevel.WARN, "[$address] 服务下未找到U盾TX特征 ${notifyUuidStr.substring(0, 8)}...，跳过自动订阅通知")
                return
            }
            
            // Enable local notification
            val success = gatt.setCharacteristicNotification(characteristic, true)
            if (!success) {
                emitLog("GATT", LogLevel.ERROR, "[$address] 开启TX特征通知失败(setNotification returns false)")
                return
            }
            
            // Enable remote notification by writing 2902 descriptor
            val descriptor = characteristic.getDescriptor(CCCD_UUID)
            if (descriptor == null) {
                emitLog("GATT", LogLevel.WARN, "[$address] TX特征未找到 2902 描述符，跳过写入描述符")
                return
            }
            
            val descriptorWritten = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == 0 // 0 is Success
            } else {
                @Suppress("DEPRECATION")
                descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                gatt.writeDescriptor(descriptor)
            }
            
            if (descriptorWritten) {
                emitLog("GATT", LogLevel.SUCCESS, "[$address] 成功自动订阅U盾专属TX信道! [${notifyUuidStr.take(8)}...]")
            } else {
                emitLog("GATT", LogLevel.WARN, "[$address] 写入 2902 描述符返回否定(可能有写入限制)")
            }
        } catch (e: SecurityException) {
            emitLog("GATT", LogLevel.ERROR, "[$address] 自动订阅失败: 缺少读取/通知物理特征码权限")
        } catch (e: Exception) {
            emitLog("GATT", LogLevel.ERROR, "[$address] 自动订阅异常: ${e.localizedMessage}")
        }
    }

    fun startScan() {
        if (_isScanning.value) return

        _scannedDevices.value = emptyList()
        _isScanning.value = true
        emitLog("SCAN", LogLevel.INFO, "正在开启BLE设备扫描...")

        // Physical Scan
        if (bluetoothAdapter == null || bluetoothLeScanner == null) {
            emitLog("SCAN", LogLevel.ERROR, "扫描错误: 设备不支持BLE或蓝牙广播未开启")
            _isScanning.value = false
            return
        }

        try {
            val settings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .build()
            bluetoothLeScanner.startScan(null, settings, physicalScanCallback)
            emitLog("SCAN", LogLevel.SUCCESS, "物理LE扫描正在后台运行中...")
        } catch (e: SecurityException) {
            emitLog("SCAN", LogLevel.ERROR, "安全异常: 缺少扫描权限! 请在设置中授权。")
            _isScanning.value = false
        } catch (e: Exception) {
            emitLog("SCAN", LogLevel.ERROR, "启动物理扫描失败: ${e.localizedMessage}")
            _isScanning.value = false
        }
    }

    fun stopScan() {
        if (!_isScanning.value) return
        _isScanning.value = false
        emitLog("SCAN", LogLevel.INFO, "停止扫描。")

        try {
            bluetoothLeScanner?.stopScan(physicalScanCallback)
        } catch (e: SecurityException) {
            Log.e("BleController", "No permission to stop scanning", e)
        }
    }

    private val physicalScanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            super.onScanResult(callbackType, result)
            result?.device?.let { device ->
                val name = getDeviceNameSafe(device)
                val address = device.address ?: ""
                if (address.isNotEmpty()) {
                    val bleDevice = BleDevice(
                        name = name,
                        address = address,
                        rssi = result.rssi,
                        isSimulated = false
                    )
                    _scannedDevices.update { list ->
                        if (list.none { it.address == bleDevice.address }) {
                            emitLog("SCAN", LogLevel.INFO, "在物理通道上发现: $name [$address] | ${result.rssi}dBm")
                            list + bleDevice
                        } else {
                            // Update RSSI
                            list.map { if (it.address == bleDevice.address) it.copy(rssi = result.rssi) else it }
                        }
                    }
                }
            }
        }

        override fun onScanFailed(errorCode: Int) {
            super.onScanFailed(errorCode)
            val reason = when (errorCode) {
                SCAN_FAILED_ALREADY_STARTED -> "扫描已在运行"
                SCAN_FAILED_APPLICATION_REGISTRATION_FAILED -> "应用注册失败"
                SCAN_FAILED_INTERNAL_ERROR -> "硬件内部错误"
                SCAN_FAILED_FEATURE_UNSUPPORTED -> "BLE不支持此设备"
                else -> "未知错误代码: $errorCode"
            }
            emitLog("SCAN", LogLevel.ERROR, "物理扫描启动失败: $reason")
            _isScanning.value = false
        }
    }

    fun connect(device: BleDevice) {
        val address = device.address
        if (_activeConnections.value.containsKey(address) && 
            _activeConnections.value[address]?.state != BluetoothConnectionState.DISCONNECTED) {
            return
        }

        // Initialize connection state
        _activeConnections.update { map ->
            map + (address to BleDeviceConnection(device = device, state = BluetoothConnectionState.CONNECTING))
        }
        emitLog("GATT", LogLevel.INFO, "正在连接到设备: ${device.name} [$address]...")

        connectReal(device)
    }

    private fun connectReal(device: BleDevice) {
        val address = device.address
        if (bluetoothAdapter == null) {
            emitLog("GATT", LogLevel.ERROR, "物理设备连接错误: 本机蓝牙配置为空")
            updateConnectionState(address, BluetoothConnectionState.DISCONNECTED, "缺少蓝牙适配器")
            return
        }

        val remoteDevice = bluetoothAdapter.getRemoteDevice(address)
        if (remoteDevice == null) {
            emitLog("GATT", LogLevel.ERROR, "无法获取远程设备句柄: $address")
            updateConnectionState(address, BluetoothConnectionState.DISCONNECTED, "无效的句柄")
            return
        }

        try {
            // Connect GATT
            val gatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                remoteDevice.connectGatt(context, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                remoteDevice.connectGatt(context, false, gattCallback)
            }
            gattServers[address] = gatt
        } catch (e: SecurityException) {
            emitLog("GATT", LogLevel.ERROR, "安全错误: 连接 [$address] 缺少 BLUETOOTH_CONNECT 权限")
            updateConnectionState(address, BluetoothConnectionState.DISCONNECTED, "缺少权限")
        } catch (e: Exception) {
            emitLog("GATT", LogLevel.ERROR, "连接失败 [$address]: ${e.localizedMessage}")
            updateConnectionState(address, BluetoothConnectionState.DISCONNECTED, e.localizedMessage)
        }
    }

    fun disconnect(deviceAddress: String) {
        val connection = _activeConnections.value[deviceAddress] ?: return
        emitLog("GATT", LogLevel.INFO, "正在与 [${connection.device.name}] 断开连接...")
        _activeConnections.update { map ->
            map.mapValues { if (it.key == deviceAddress) it.value.copy(state = BluetoothConnectionState.DISCONNECTING) else it.value }
        }

        val gatt = gattServers[deviceAddress]
        if (gatt != null) {
            try {
                gatt.disconnect()
            } catch (e: SecurityException) {
                emitLog("GATT", LogLevel.ERROR, "缺少断开连接权限")
            }
        } else {
            _activeConnections.update { it - deviceAddress }
        }
    }

    fun disconnectAll() {
        val targets = _activeConnections.value.keys.toList()
        targets.forEach { disconnect(it) }
    }

    private fun updateConnectionState(address: String, state: BluetoothConnectionState, error: String? = null) {
        _activeConnections.update { map ->
            if (map.containsKey(address)) {
                if (state == BluetoothConnectionState.DISCONNECTED && error == null) {
                    // Remove from active list on planned disconnect to save view clutter,
                    // or keep with status. Let's keep it in list as DISCONNECTED so user can click connect again,
                    // or remove, let's keep and update state
                    map.mapValues { if (it.key == address) it.value.copy(state = state, errorMsg = error) else it.value }
                } else {
                    map.mapValues { if (it.key == address) it.value.copy(state = state, errorMsg = error) else it.value }
                }
            } else {
                map
            }
        }
    }

    // Toggle selection of device for bulk messaging
    fun toggleDeviceSelection(address: String) {
        _activeConnections.update { map ->
            if (map.containsKey(address)) {
                map.mapValues { if (it.key == address) it.value.copy(isSelectedForBatch = !it.value.isSelectedForBatch) else it.value }
            } else {
                map
            }
        }
    }

    // Batch Command Sender
    fun sendBatchCommand(
        payload: ByteArray,
        payloadString: String,
        isHex: Boolean,
        serviceUuidString: String,
        characteristicUuidString: String
    ) {
        val targets = _activeConnections.value.values.filter { 
            it.isSelectedForBatch && it.state == BluetoothConnectionState.READY 
        }

        if (targets.isEmpty()) {
            emitLog("BATCH", LogLevel.WARN, "一键发送取消: 没有已连接且被选中的 [Ready] 设备，请先将设备连接并勾选！")
            return
        }

        emitLog("BATCH", LogLevel.INFO, "【一键指令群发】开始! 准备发送指令至 ${targets.size} 个设备...")

        targets.forEach { connection ->
            val address = connection.device.address
            val name = connection.device.name

            // Clear previous TX status
            _activeConnections.update { map ->
                map.mapValues { if (it.key == address) it.value.copy(lastTransmissionStatus = "SENDING...") else it.value }
            }

            // Real BLE physical send
            val gatt = gattServers[address]
            if (gatt == null) {
                _activeConnections.update { map ->
                    map.mapValues { if (it.key == address) it.value.copy(lastTransmissionStatus = "失败: 未找到GATT接口") else it.value }
                }
                emitLog("BATCH", LogLevel.ERROR, "->$name [$address] 发送失败: 未找到GATT句柄")
                return@forEach
            }

            scope.launch(Dispatchers.Default) {
                try {
                    val sUid = UUID.fromString(serviceUuidString)
                    val cUid = UUID.fromString(characteristicUuidString)

                    val service = gatt.getService(sUid)
                    if (service == null) {
                        updateTxResult(address, "失败: 未找到特定UUID服务")
                        emitLog("BATCH", LogLevel.ERROR, "->$name [$address] 未找到UUID服务: $serviceUuidString")
                        return@launch
                    }

                    val characteristic = service.getCharacteristic(cUid)
                    if (characteristic == null) {
                        updateTxResult(address, "失败: 未找到特定写特征符")
                        emitLog("BATCH", LogLevel.ERROR, "->$name [$address] 未找到写入Characteristic: $characteristicUuidString")
                        return@launch
                    }

                    // Check property writable
                    val writeType = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0) {
                        BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
                    } else {
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                    }
                    characteristic.writeType = writeType

                    // Perform Write
                    val writeSuccess = writeCharacteristicCompat(gatt, characteristic, payload)
                    if (writeSuccess) {
                        updateTxResult(address, "成功")
                        emitLog("BATCH", LogLevel.TX, "->$name [$address] 物理信道执行写入(Write-Tx) [${payload.size} 字节]: $payloadString")
                    } else {
                        updateTxResult(address, "失败")
                        emitLog("BATCH", LogLevel.ERROR, "->$name [$address] 底层写写入任务被否定(Gatt write false)")
                    }
                } catch (e: IllegalArgumentException) {
                    updateTxResult(address, "失败: UUID无效")
                    emitLog("BATCH", LogLevel.ERROR, "->$name [$address] 遇到格式错误的UUID: ${e.message}")
                } catch (e: SecurityException) {
                    updateTxResult(address, "失败: 缺少底层发送权限")
                    emitLog("BATCH", LogLevel.ERROR, "->$name [$address] 缺少物理GATT指令发送连接权限")
                } catch (e: Exception) {
                    updateTxResult(address, "失败: ${e.localizedMessage}")
                    emitLog("BATCH", LogLevel.ERROR, "->$name [$address] 发送发生异常: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun updateTxResult(address: String, description: String) {
        _activeConnections.update { map ->
            map.mapValues { if (it.key == address) it.value.copy(lastTransmissionStatus = description) else it.value }
        }
    }

    private fun writeCharacteristicCompat(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic,
        value: ByteArray
    ): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val writeRes = gatt.writeCharacteristic(
                    characteristic,
                    value,
                    characteristic.writeType
                )
                writeRes == BluetoothStatusCodes.SUCCESS
            } else {
                @Suppress("DEPRECATION")
                characteristic.value = value
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(characteristic)
            }
        } catch (e: SecurityException) {
            false
        } catch (e: Exception) {
            false
        }
    }

    // Real Bluetooth GATT Callback handler
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            super.onConnectionStateChange(gatt, status, newState)
            val address = gatt?.device?.address ?: return
            val name = getDeviceNameSafe(gatt.device)

            if (status != BluetoothGatt.GATT_SUCCESS) {
                emitLog("GATT", LogLevel.ERROR, "[$address] 连接出错! 错误代码: $status")
                try {
                    gatt.close()
                } catch (e: Exception) {}
                gattServers.remove(address)
                updateConnectionState(address, BluetoothConnectionState.DISCONNECTED, "GATT错误 $status")
                return
            }

            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    emitLog("GATT", LogLevel.SUCCESS, "[$address] 已物理连接到 GATT 轮廓")
                    updateConnectionState(address, BluetoothConnectionState.CONNECTED)

                    // Automatically start service discovery
                    scope.launch {
                        delay(500)
                        emitLog("GATT", LogLevel.INFO, "[$address] 启动物理服务探索...")
                        updateConnectionState(address, BluetoothConnectionState.DISCOVERING_SERVICES)
                        try {
                            gatt.discoverServices()
                        } catch (e: SecurityException) {
                            emitLog("GATT", LogLevel.ERROR, "[$address] 缺失读取服务(BLUETOOTH_CONNECT)的权限")
                            updateConnectionState(address, BluetoothConnectionState.DISCONNECTED, "缺少权限")
                        }
                    }
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    emitLog("GATT", LogLevel.WARN, "[$address] 连接在远程设备被断开")
                    try {
                        gatt.close()
                    } catch (e: Exception) {}
                    gattServers.remove(address)
                    updateConnectionState(address, BluetoothConnectionState.DISCONNECTED)
                }
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(gatt, status)
            val address = gatt?.device?.address ?: return
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                emitLog("GATT", LogLevel.SUCCESS, "[$address] 服务探索完成已就绪！")
                updateConnectionState(address, BluetoothConnectionState.READY)
                
                // Read and list services found in developer log
                val services = gatt.services ?: emptyList()
                services.forEach { svc ->
                    val chars = svc.characteristics.joinToString { it.uuid.toString().take(8) }
                    Log.d("BleController", "Service found: ${svc.uuid} with chars: $chars")
                }

                // Automatically enable notification for U-shield's TX characteristic
                scope.launch {
                    delay(300)
                    enableNotificationForDevice(
                        gatt = gatt,
                        serviceUuidStr = defaultServiceUuidString,
                        notifyUuidStr = defaultNotifyCharacteristicUuidString
                    )
                }
            } else {
                emitLog("GATT", LogLevel.ERROR, "[$address] 发现服务遭遇故障。代码: $status")
                updateConnectionState(address, BluetoothConnectionState.CONNECTED, "发现服务异常 $status")
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            val address = gatt?.device?.address ?: return
            val name = getDeviceNameSafe(gatt.device)
            val charUuid = characteristic?.uuid?.toString() ?: ""
            
            if (status == BluetoothGatt.GATT_SUCCESS) {
                emitLog("BATCH", LogLevel.SUCCESS, "<-$name [$address] 成功物理写入特征：${charUuid.take(8)}")
                updateTxResult(address, "成功")
            } else {
                emitLog("BATCH", LogLevel.ERROR, "<-$name [$address] 物理写入特征失败。GATT代码: $status")
                updateTxResult(address, "失败代码: $status")
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            super.onCharacteristicChanged(gatt, characteristic, value)
            val address = gatt.device?.address ?: ""
            val name = getDeviceNameSafe(gatt.device)
            val hexStr = value.joinToString("") { String.format("%02X ", it) }.trim()
            val asciiStr = try {
                val s = String(value, Charsets.UTF_8).replace("[\\x00-\\x1F\\x7F]".toRegex(), ".")
                if (s.any { it.isLetterOrDigit() || it in " _-+=()[]{}<>@&*" }) s else "???"
            } catch (e: Exception) {
                "???"
            }
            emitLog("GATT", LogLevel.RX, "<-$name [$address] 数据透传(Notify) | HEX: [$hexStr] | ASCII: [$asciiStr]")
        }

        // Backward compatibility for older Android versions
        @Deprecated("Deprecated in Java")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            @Suppress("DEPRECATION")
            val value = characteristic?.value ?: return
            val address = gatt?.device?.address ?: ""
            val name = getDeviceNameSafe(gatt?.device)
            val hexStr = value.joinToString("") { String.format("%02X ", it) }.trim()
            val asciiStr = try {
                val s = String(value, Charsets.UTF_8).replace("[\\x00-\\x1F\\x7F]".toRegex(), ".")
                if (s.any { it.isLetterOrDigit() || it in " _-+=()[]{}<>@&*" }) s else "???"
            } catch (e: Exception) {
                "???"
            }
            emitLog("GATT", LogLevel.RX, "<-$name [$address] 数据透传(Legacy Notify) | HEX: [$hexStr] | ASCII: [$asciiStr]")
        }
    }
}

// Dummy constant mapping to support compile without needing massive framework imports
private const val SCAN_FAILED_ALREADY_STARTED = 1
private const val SCAN_FAILED_APPLICATION_REGISTRATION_FAILED = 2
private const val SCAN_FAILED_INTERNAL_ERROR = 3
private const val SCAN_FAILED_FEATURE_UNSUPPORTED = 4

// Standard Bluetooth Status for writing
private object BluetoothStatusCodes {
    const val SUCCESS = 0
}

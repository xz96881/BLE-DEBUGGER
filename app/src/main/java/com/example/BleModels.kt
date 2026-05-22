package com.example

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Represents a discovered or registered BLE device.
data class BleDevice(
    val name: String,
    val address: String,
    val rssi: Int = -50,
    val isSimulated: Boolean = false
)

enum class BluetoothConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    DISCOVERING_SERVICES,
    READY,           // Characteristic found and ready for command writing
    DISCONNECTING;

    val label: String
        get() = when (this) {
            DISCONNECTED -> "未连接 (Disconnected)"
            CONNECTING -> "正在连接 (Connecting...)"
            CONNECTED -> "已连接 (Connected)"
            DISCOVERING_SERVICES -> "发现服务中 (Discovering...)"
            READY -> "就绪 / 可写入 (Ready)"
            DISCONNECTING -> "正在断开 (Disconnecting...)"
        }
}

// Represents the state of an active connection and its transmission history.
data class BleDeviceConnection(
    val device: BleDevice,
    val state: BluetoothConnectionState = BluetoothConnectionState.DISCONNECTED,
    val errorMsg: String? = null,
    val isSelectedForBatch: Boolean = true, // By default, check newly scanned devices or checked in list
    val lastTransmissionStatus: String? = null // "SUCCESS", "FAILED: <reason>", etc.
)

enum class LogLevel {
    INFO, SUCCESS, WARN, ERROR, TX, RX
}

data class ConsoleLog(
    val timestamp: Long = System.currentTimeMillis(),
    val tag: String,
    val level: LogLevel,
    val message: String
) {
    val formattedTime: String
        get() = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

data class CommandPreset(
    val label: String,
    val payload: String,
    val isHex: Boolean = false
)

package com.marpd.monitor

import android.content.Context
import android.app.ActivityManager
import android.os.BatteryManager
import android.content.Intent
import android.content.IntentFilter
import android.os.StatFs
import android.os.Environment
import android.net.ConnectivityManager
import android.net.TrafficStats
import android.hardware.SensorManager
import android.hardware.Sensor
import android.util.Log
import java.io.RandomAccessFile
import java.text.SimpleDateFormat
import java.util.*

class SystemMonitor(private val context: Context) {
    
    companion object {
        private const val TAG = "SystemMonitor"
    }
    
    fun getDetailedSystemInfo(): Map<String, Any> {
        return mapOf(
            "cpu" to getDetailedCPUInfo(),
            "memory" to getDetailedMemoryInfo(),
            "battery" to getDetailedBatteryInfo(),
            "storage" to getDetailedStorageInfo(),
            "network" to getDetailedNetworkInfo(),
            "device" to getDeviceInfo(),
            "sensors" to getAvailableSensors(),
            "timestamp" to SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
        )
    }
    
    private fun getDetailedCPUInfo(): Map<String, Any> {
        return try {
            val reader = RandomAccessFile("/proc/cpuinfo", "r")
            val cpuInfo = StringBuilder()
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                cpuInfo.append(line).append("\n")
            }
            reader.close()
            
            // Parse CPU info
            val cores = Runtime.getRuntime().availableProcessors()
            val freqFile = RandomAccessFile("/sys/devices/system/cpu/cpu0/cpufreq/scaling_cur_freq", "r")
            val freq = freqFile.readLine().toInt() / 1000
            freqFile.close()
            
            mapOf(
                "cores" to cores,
                "frequency" to "$freq MHz",
                "architecture" to System.getProperty("os.arch"),
                "model" to Build.HARDWARE,
                "usage" to getCurrentCPUUsage()
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error getting CPU info", e)
            mapOf("error" to e.message ?: "Unknown error")
        }
    }
    
    private fun getCurrentCPUUsage(): String {
        return try {
            val reader = RandomAccessFile("/proc/stat", "r")
            val firstLine = reader.readLine()
            reader.close()
            
            val parts = firstLine.split("\\s+".toRegex())
            if (parts.size > 4) {
                val idle = parts[4].toLong()
                val total = parts.subList(1, 8).sumOf { it.toLong() }
                val usage = ((total - idle) * 100 / total).toInt()
                "$usage%"
            } else "N/A"
        } catch (e: Exception) {
            "ERR"
        }
    }
    
    private fun getDetailedMemoryInfo(): Map<String, Any> {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val total = memoryInfo.totalMem
        val available = memoryInfo.availMem
        val used = total - available
        val percent = (used * 100 / total).toInt()
        
        // Get detailed memory stats from /proc/meminfo
        val memInfo = mutableMapOf<String, Long>()
        try {
            val reader = RandomAccessFile("/proc/meminfo", "r")
            var line: String?
            
            while (reader.readLine().also { line = it } != null) {
                val parts = line!!.split(":\\s+".toRegex())
                if (parts.size == 2) {
                    val value = parts[1].replace("\\D".toRegex(), "").toLongOrNull() ?: 0
                    memInfo[parts[0]] = value * 1024 // Convert KB to bytes
                }
            }
            reader.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error reading /proc/meminfo", e)
        }
        
        return mapOf(
            "total" to total,
            "available" to available,
            "used" to used,
            "percent" to percent,
            "threshold" to memoryInfo.threshold,
            "lowMemory" to memoryInfo.lowMemory,
            "detailed" to memInfo
        )
    }
    
    private fun getDetailedBatteryInfo(): Map<String, Any> {
        val batteryStatus = context.registerReceiver(null, 
            IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        
        val temperature = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val voltage = batteryStatus?.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1)
        val health = batteryStatus?.getIntExtra(BatteryManager.EXTRA_HEALTH, BatteryManager.BATTERY_HEALTH_UNKNOWN)
        val plugged = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
        val technology = batteryStatus?.getStringExtra(BatteryManager.EXTRA_TECHNOLOGY) ?: "Unknown"
        
        val healthText = when(health) {
            BatteryManager.BATTERY_HEALTH_GOOD -> "Good"
            BatteryManager.BATTERY_HEALTH_OVERHEAT -> "Overheat"
            BatteryManager.BATTERY_HEALTH_DEAD -> "Dead"
            BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE -> "Over Voltage"
            BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE -> "Failure"
            else -> "Unknown"
        }
        
        val pluggedText = when(plugged) {
            BatteryManager.BATTERY_PLUGGED_AC -> "AC"
            BatteryManager.BATTERY_PLUGGED_USB -> "USB"
            BatteryManager.BATTERY_PLUGGED_WIRELESS -> "Wireless"
            else -> "Not Charging"
        }
        
        return mapOf(
            "percent" to percent,
            "temperature" to (temperature?.div(10.0) ?: -1.0),
            "voltage" to (voltage?.div(1000.0) ?: -1.0), // Convert mV to V
            "health" to healthText,
            "plugged" to pluggedText,
            "technology" to technology,
            "present" to (batteryStatus?.getBooleanExtra(BatteryManager.EXTRA_PRESENT, false) ?: false)
        )
    }
    
    private fun getDetailedStorageInfo(): Map<String, Any> {
        val internalStat = StatFs(Environment.getDataDirectory().path)
        val externalStat = StatFs(Environment.getExternalStorageDirectory().path)
        
        fun getStorageInfo(stat: StatFs, name: String): Map<String, Any> {
            val blockSize = stat.blockSizeLong
            val totalBlocks = stat.blockCountLong
            val availableBlocks = stat.availableBlocksLong
            
            val total = totalBlocks * blockSize
            val free = availableBlocks * blockSize
            val used = total - free
            val percent = if (total > 0) (used * 100 / total).toInt() else 0
            
            return mapOf(
                "name" to name,
                "total" to total,
                "free" to free,
                "used" to used,
                "percent" to percent,
                "blockSize" to blockSize
            )
        }
        
        return mapOf(
            "internal" to getStorageInfo(internalStat, "Internal"),
            "external" to getStorageInfo(externalStat, "External"),
            "isExternalStorageRemovable" to Environment.isExternalStorageRemovable(),
            "isExternalStorageEmulated" to Environment.isExternalStorageEmulated()
        )
    }
    
    private fun getDetailedNetworkInfo(): Map<String, Any> {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        
        val connected = networkInfo?.isConnected ?: false
        val type = networkInfo?.typeName ?: "Disconnected"
        val subtype = networkInfo?.subtypeName ?: "N/A"
        
        val rxBytes = TrafficStats.getTotalRxBytes()
        val txBytes = TrafficStats.getTotalTxBytes()
        val rxPackets = TrafficStats.getTotalRxPackets()
        val txPackets = TrafficStats.getTotalTxPackets()
        
        // Get WiFi info if available
        val wifiInfo = try {
            val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
            val info = wifiManager.connectionInfo
            mapOf(
                "ssid" to info.ssid.replace("\"", ""),
                "bssid" to info.bssid,
                "ip" to formatIP(info.ipAddress),
                "linkSpeed" to "${info.linkSpeed} Mbps",
                "signalStrength" to info.rssi
            )
        } catch (e: Exception) {
            mapOf("error" to "WiFi not available")
        }
        
        return mapOf(
            "connected" to connected,
            "type" to type,
            "subtype" to subtype,
            "rxBytes" to rxBytes,
            "txBytes" to txBytes,
            "rxPackets" to rxPackets,
            "txPackets" to txPackets,
            "wifi" to wifiInfo
        )
    }
    
    private fun formatIP(ip: Int): String {
        return (ip and 0xFF).toString() + "." +
               (ip shr 8 and 0xFF) + "." +
               (ip shr 16 and 0xFF) + "." +
               (ip shr 24 and 0xFF)
    }
    
    private fun getDeviceInfo(): Map<String, Any> {
        return mapOf(
            "manufacturer" to Build.MANUFACTURER,
            "model" to Build.MODEL,
            "device" to Build.DEVICE,
            "product" to Build.PRODUCT,
            "brand" to Build.BRAND,
            "board" to Build.BOARD,
            "hardware" to Build.HARDWARE,
            "androidVersion" to Build.VERSION.RELEASE,
            "sdkInt" to Build.VERSION.SDK_INT,
            "fingerprint" to Build.FINGERPRINT,
            "serial" to Build.getSerial(),
            "supportedABIs" to Build.SUPPORTED_ABIS.toList(),
            "bootloader" to Build.BOOTLOADER,
            "radioVersion" to Build.getRadioVersion(),
            "isEmulator" to (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
        )
    }
    
    private fun getAvailableSensors(): List<Map<String, Any>> {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        
        return sensors.map { sensor ->
            mapOf(
                "name" to sensor.name,
                "vendor" to sensor.vendor,
                "type" to sensor.type,
                "version" to sensor.version,
                "power" to "${sensor.power} mA",
                "resolution" to sensor.resolution,
                "maxRange" to sensor.maximumRange
            )
        }
    }
    
    fun isDeviceRooted(): Boolean {
        val paths = arrayOf(
            "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su", "/su/bin/su"
        )
        
        // Check for su binary
        if (paths.any { java.io.File(it).exists() }) {
            return true
        }
        
        // Check for test-keys in build tags
        val buildTags = Build.TAGS
        if (buildTags != null && buildTags.contains("test-keys")) {
            return true
        }
        
        return false
    }
    
    fun getRunningProcessCount(): Int {
        val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        return activityManager.runningAppProcesses.size
    }
    
    fun getUptime(): String {
        return try {
            val reader = RandomAccessFile("/proc/uptime", "r")
            val uptimeSeconds = reader.readLine().split(" ")[0].toFloat()
            reader.close()
            
            val hours = (uptimeSeconds / 3600).toInt()
            val minutes = ((uptimeSeconds % 3600) / 60).toInt()
            val seconds = (uptimeSeconds % 60).toInt()
            
            String.format("%02d:%02d:%02d", hours, minutes, seconds)
        } catch (e: Exception) {
            "N/A"
        }
    }
}

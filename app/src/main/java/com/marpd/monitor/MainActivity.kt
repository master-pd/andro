package com.marpd.monitor

import android.os.*
import androidx.appcompat.app.AppCompatActivity
import android.widget.*
import java.util.*
import android.content.*
import android.app.*
import android.net.*
import android.hardware.*
import android.os.BatteryManager
import android.util.Log

class MainActivity : AppCompatActivity() {
    
    private lateinit var terminalOutput: TextView
    private lateinit var realTimeStats: TextView
    private lateinit var commandInput: EditText
    private lateinit var refreshBtn: Button
    
    private val tag = "MAR-PD"
    private var updateTimer: Timer? = null
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        
        // Initialize UI
        terminalOutput = findViewById(R.id.terminalOutput)
        realTimeStats = findViewById(R.id.realTimeStats)
        commandInput = findViewById(R.id.commandInput)
        refreshBtn = findViewById(R.id.refreshBtn)
        
        // Start system monitoring
        startSystemMonitoring()
        
        // Setup terminal commands
        setupTerminalCommands()
        
        // Initial system scan
        performSystemScan()
    }
    
    private fun startSystemMonitoring() {
        updateTimer = Timer()
        updateTimer?.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    updateRealTimeStats()
                    logSystemActivity()
                }
            }
        }, 0, 2000) // Update every 2 seconds
    }
    
    private fun updateRealTimeStats() {
        val stats = """
            ┌─────────────────────────────────┐
            │    MAR-PD ☠️ SYSTEM MONITOR     │
            ├─────────────────────────────────┤
            │ CPU:  ${getCPUUsage()}           │
            │ RAM:  ${getRAMUsage()}           │
            │ BAT:  ${getBatteryInfo()}        │
            │ TEMP: ${getTemperature()}°C      │
            │ STOR: ${getStorageInfo()}        │
            │ NET:  ${getNetworkStatus()}      │
            └─────────────────────────────────┘
        """.trimIndent()
        
        realTimeStats.text = stats
    }
    
    private fun getCPUUsage(): String {
        return try {
            val reader = java.io.RandomAccessFile("/proc/stat", "r")
            val line = reader.readLine()
            reader.close()
            
            val parts = line.split("\\s+".toRegex())
            if (parts.size > 4) {
                val idle = parts[4].toLong()
                val total = parts.subList(1, 5).sumOf { it.toLong() }
                val usage = ((total - idle) * 100 / total).toInt()
                "${usage}%"
            } else "N/A"
        } catch (e: Exception) {
            "ERR"
        }
    }
    
    private fun getRAMUsage(): String {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val memoryInfo = ActivityManager.MemoryInfo()
        activityManager.getMemoryInfo(memoryInfo)
        
        val usedMB = (memoryInfo.totalMem - memoryInfo.availMem) / (1024 * 1024)
        val totalMB = memoryInfo.totalMem / (1024 * 1024)
        val percent = (usedMB * 100 / totalMB)
        
        return "${percent}% (${usedMB}MB/${totalMB}MB)"
    }
    
    private fun getBatteryInfo(): String {
        val batteryStatus = registerReceiver(null, 
            IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        
        val level = batteryStatus?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryStatus?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val percent = if (level >= 0 && scale > 0) (level * 100 / scale) else -1
        
        val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
        val tempC = if (temp != -1) temp / 10.0 else "N/A"
        
        return "${percent}% | ${tempC}°C"
    }
    
    private fun getTemperature(): String {
        return try {
            val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            val tempSensor = sensorManager.getDefaultSensor(Sensor.TYPE_AMBIENT_TEMPERATURE)
            if (tempSensor != null) {
                "SENSOR_OK"
            } else {
                val batteryStatus = registerReceiver(null, 
                    IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val temp = batteryStatus?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1)
                if (temp != -1) String.format("%.1f", temp / 10.0) else "N/A"
            }
        } catch (e: Exception) {
            "ERR"
        }
    }
    
    private fun getStorageInfo(): String {
        val stat = StatFs(Environment.getDataDirectory().path)
        val blockSize = stat.blockSizeLong
        val totalBlocks = stat.blockCountLong
        val availableBlocks = stat.availableBlocksLong
        
        val totalGB = totalBlocks * blockSize / (1024 * 1024 * 1024)
        val freeGB = availableBlocks * blockSize / (1024 * 1024 * 1024)
        val usedGB = totalGB - freeGB
        val percent = (usedGB * 100 / totalGB)
        
        return "${percent}% (${usedGB}GB/${totalGB}GB)"
    }
    
    private fun getNetworkStatus(): String {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        
        return if (networkInfo != null && networkInfo.isConnected) {
            val type = networkInfo.typeName
            val rxBytes = android.net.TrafficStats.getTotalRxBytes() / (1024 * 1024)
            val txBytes = android.net.TrafficStats.getTotalTxBytes() / (1024 * 1024)
            "${type} | RX:${rxBytes}MB TX:${txBytes}MB"
        } else {
            "DISCONNECTED"
        }
    }
    
    private fun performSystemScan() {
        val scanLog = """
            [*] Initializing MAR-PD System Scan...
            [✓] CPU Architecture: ${Build.SUPPORTED_ABIS.joinToString()}
            [✓] Android Version: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})
            [✓] Device Model: ${Build.MANUFACTURER} ${Build.MODEL}
            [✓] Kernel Version: ${System.getProperty("os.version")}
            [✓] Root Check: ${if (isRooted()) "DETECTED" else "NOT ROOTED"}
            [*] Starting real-time monitoring...
        """.trimIndent()
        
        appendToTerminal(scanLog)
    }
    
    private fun isRooted(): Boolean {
        val paths = arrayOf(
            "/sbin/su", "/system/bin/su", "/system/xbin/su",
            "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su",
            "/data/local/su"
        )
        return paths.any { java.io.File(it).exists() }
    }
    
    private fun setupTerminalCommands() {
        commandInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                val command = commandInput.text.toString().trim()
                processCommand(command)
                commandInput.text.clear()
                true
            } else {
                false
            }
        }
        
        refreshBtn.setOnClickListener {
            appendToTerminal("[!] Manual refresh triggered...")
            updateRealTimeStats()
        }
    }
    
    private fun processCommand(command: String) {
        appendToTerminal("\n$ $command")
        
        when (command.toLowerCase()) {
            "help" -> showHelp()
            "scan" -> performSystemScan()
            "process" -> showRunningProcesses()
            "sensors" -> showSensorInfo()
            "network" -> showNetworkDetails()
            "clear" -> terminalOutput.text = ""
            "reboot" -> appendToTerminal("[!] Reboot command disabled in safe mode")
            else -> appendToTerminal("[!] Unknown command: $command\nType 'help' for commands")
        }
    }
    
    private fun showHelp() {
        val helpText = """
            Available Commands:
            ------------------
            help     - Show this help
            scan     - Perform system scan
            process  - Show running processes
            sensors  - Show sensor information
            network  - Detailed network info
            clear    - Clear terminal
            ------------------
        """.trimIndent()
        
        appendToTerminal(helpText)
    }
    
    private fun showRunningProcesses() {
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val processes = activityManager.runningAppProcesses
        
        val processInfo = StringBuilder("[*] Running Processes:\n")
        processes.take(10).forEach { process ->
            processInfo.append("  ${process.processName} (PID: ${process.pid})\n")
        }
        processInfo.append("[*] Total: ${processes.size} processes")
        
        appendToTerminal(processInfo.toString())
    }
    
    private fun showSensorInfo() {
        val sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensors = sensorManager.getSensorList(Sensor.TYPE_ALL)
        
        val sensorInfo = StringBuilder("[*] Available Sensors:\n")
        sensors.take(15).forEach { sensor ->
            sensorInfo.append("  ${sensor.name}\n")
        }
        sensorInfo.append("[*] Total: ${sensors.size} sensors")
        
        appendToTerminal(sensorInfo.toString())
    }
    
    private fun showNetworkDetails() {
        val connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkInfo = connectivityManager.activeNetworkInfo
        
        val netInfo = StringBuilder("[*] Network Details:\n")
        if (networkInfo != null) {
            netInfo.append("  Type: ${networkInfo.typeName}\n")
            netInfo.append("  State: ${networkInfo.state.name}\n")
            netInfo.append("  Roaming: ${networkInfo.isRoaming}\n")
            netInfo.append("  Failover: ${networkInfo.isFailover}\n")
        }
        
        val wifiManager = applicationContext.getSystemService(Context.WIFI_SERVICE) as android.net.wifi.WifiManager
        val wifiInfo = wifiManager.connectionInfo
        netInfo.append("  WiFi SSID: ${wifiInfo.ssid}\n")
        netInfo.append("  BSSID: ${wifiInfo.bssid}\n")
        netInfo.append("  IP: ${formatIP(wifiInfo.ipAddress)}\n")
        netInfo.append("  Speed: ${wifiInfo.linkSpeed} Mbps")
        
        appendToTerminal(netInfo.toString())
    }
    
    private fun formatIP(ip: Int): String {
        return (ip and 0xFF).toString() + "." +
               (ip shr 8 and 0xFF) + "." +
               (ip shr 16 and 0xFF) + "." +
               (ip shr 24 and 0xFF)
    }
    
    private fun logSystemActivity() {
        val log = "[+] ${getCurrentTime()} | CPU:${getCPUUsage()} | RAM:${getRAMUsage().substringBefore(" ")}"
        if (terminalOutput.lineCount > 50) {
            terminalOutput.text = terminalOutput.text.toString().split("\n").drop(10).joinToString("\n")
        }
        appendToTerminal(log, false)
    }
    
    private fun getCurrentTime(): String {
        return SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date())
    }
    
    private fun appendToTerminal(text: String, newLine: Boolean = true) {
        val current = terminalOutput.text.toString()
        terminalOutput.text = if (newLine) "$current\n$text" else "$current$text"
        
        // Auto scroll to bottom
        terminalOutput.post {
            val scrollView = findViewById<ScrollView>(R.id.terminalScroll)
            scrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        updateTimer?.cancel()
        appendToTerminal("[!] MAR-PD monitoring stopped")
    }
}

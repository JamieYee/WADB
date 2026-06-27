package moe.haruue.wadb.util

import android.content.Context
import android.net.wifi.WifiManager
import android.system.OsConstants
import android.util.Log

object NetworksUtils {
    private val tag = NetworksUtils::class.java.simpleName

    @JvmStatic
    fun getLocalIPAddress(context: Context): String {
        return getLocalIPInfo(context).firstOrNull()?.ip.orEmpty()
    }

    @JvmStatic
    fun getLocalIPInfo(context: Context): List<LibWADB.InterfaceIPPair> {
        try {
            return LibWADB.getInterfaceIps(false)
        } catch (e: Exception) {
            Log.e(tag, "getLocalIPInfo: LibWADB.getInterfaceIps() failed", e)
        }

        val wifiManager = context.applicationContext.getSystemService(WifiManager::class.java) ?: return emptyList()
        @Suppress("DEPRECATION")
        val wifiInfo = wifiManager.connectionInfo
        @Suppress("DEPRECATION")
        val ipAddress = wifiInfo.ipAddress
        if (ipAddress == 0) {
            return emptyList()
        }

        return listOf(
            LibWADB.InterfaceIPPair(
                0,
                OsConstants.AF_INET,
                "wlan0",
                intToIp(ipAddress),
            ),
        )
    }

    private fun intToIp(value: Int): String {
        return "${value and 0xFF}.${value shr 8 and 0xFF}.${value shr 16 and 0xFF}.${value shr 24 and 0xFF}"
    }
}

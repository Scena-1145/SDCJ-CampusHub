package com.scena.campushub.data

import com.scena.campushub.Config
import java.net.InetSocketAddress
import java.net.Socket

/**
 * 判断当前是否连着校园网。
 *
 * 依据：学校的部分服务只有内网地址（192.168.x.x），公网无法连通，
 * 所以只要探测点里有一个能建 TCP 连接，就说明在学校网络里。
 */
object CampusNet {

    enum class State { UNKNOWN, CAMPUS, OUTSIDE }

    fun probe(timeoutMs: Int = 1200): State {
        for ((host, port) in Config.CAMPUS_PROBES) {
            if (canConnect(host, port, timeoutMs)) return State.CAMPUS
        }
        return State.OUTSIDE
    }

    private fun canConnect(host: String, port: Int, timeoutMs: Int): Boolean {
        return try {
            Socket().use {
                it.connect(InetSocketAddress(host, port), timeoutMs)
                true
            }
        } catch (e: Exception) {
            false
        }
    }
}

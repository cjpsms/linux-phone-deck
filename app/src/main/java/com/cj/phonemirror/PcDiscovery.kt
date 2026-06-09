package com.cj.phonemirror

import android.util.Log
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

private const val TAG = "PcDiscovery"

/**
 * Finds the phone-deck server on the LAN by UDP broadcast: send a magic
 * string to the broadcast address on [DISCOVERY_PORT], the server replies
 * from its real IP with "PHONE_DECK_V1:<http-port>".
 *
 * Requires the server's discovery responder (see phone-deck/server.py) to be
 * running and reachable on the same broadcast domain. Must be called off the
 * main thread.
 */
object PcDiscovery {

    private const val DISCOVERY_PORT = 8787
    private const val MAGIC = "PHONE_DECK_DISCOVER_V1"
    private const val REPLY_PREFIX = "PHONE_DECK_V1:"
    private const val TIMEOUT_MS = 2500

    /** Returns "http://<ip>:<port>" of the first server that answers, or null. */
    fun find(): String? {
        var socket: DatagramSocket? = null
        return try {
            socket = DatagramSocket().apply {
                broadcast = true
                soTimeout = TIMEOUT_MS
            }

            val request = MAGIC.toByteArray(Charsets.UTF_8)
            val broadcastAddr = InetAddress.getByName("255.255.255.255")
            socket.send(DatagramPacket(request, request.size, broadcastAddr, DISCOVERY_PORT))

            val buf = ByteArray(256)
            val reply = DatagramPacket(buf, buf.size)
            socket.receive(reply) // throws SocketTimeoutException if nobody answers in time

            val text = String(reply.data, 0, reply.length, Charsets.UTF_8)
            if (!text.startsWith(REPLY_PREFIX)) return null
            val port = text.removePrefix(REPLY_PREFIX).trim().toIntOrNull() ?: return null
            val ip = reply.address?.hostAddress ?: return null
            "http://$ip:$port"
        } catch (e: Exception) {
            Log.w(TAG, "PC discovery failed: ${e.message}")
            null
        } finally {
            socket?.close()
        }
    }
}

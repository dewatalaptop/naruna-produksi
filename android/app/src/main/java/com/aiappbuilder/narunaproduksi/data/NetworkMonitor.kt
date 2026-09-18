package com.aiappbuilder.narunaproduksi.data

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

fun isOnline(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

/** Emits the current connectivity state, then again on every change. */
fun observeConnectivity(context: Context): Flow<Boolean> = callbackFlow {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) { trySend(isOnline(context)) }
        override fun onLost(network: Network) { trySend(isOnline(context)) }
        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            trySend(isOnline(context))
        }
    }
    val request = NetworkRequest.Builder().addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET).build()
    cm.registerNetworkCallback(request, callback)
    trySend(isOnline(context))
    awaitClose { cm.unregisterNetworkCallback(callback) }
}

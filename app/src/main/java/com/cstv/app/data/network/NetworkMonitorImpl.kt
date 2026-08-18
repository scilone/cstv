package com.cstv.app.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import com.cstv.app.domain.network.NetworkMonitor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implémentation `ConnectivityManager`.
 *
 * `registerNetworkCallback(NetworkRequest, …)` et non
 * `registerDefaultNetworkCallback` : cette dernière exige l'API 24 alors que le
 * projet est en minSdk 21.
 *
 * Un réseau compte comme en ligne s'il porte `NET_CAPABILITY_INTERNET` **et**
 * `NET_CAPABILITY_VALIDATED` (API 23+). `VALIDATED` écarte le portail captif,
 * où le transport est up mais le panel injoignable.
 *
 * Aucune permission nouvelle : `ACCESS_NETWORK_STATE` est déjà déclarée.
 */
@Singleton
class NetworkMonitorImpl @Inject constructor(
    context: Context
) : NetworkMonitor {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(readCurrentState())
    override val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            _isOnline.value = readCurrentState()
        }

        override fun onLost(network: Network) {
            _isOnline.value = readCurrentState()
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            _isOnline.value = readCurrentState()
        }
    }

    init {
        try {
            connectivityManager?.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .build(),
                callback
            )
        } catch (e: Exception) {
            // Un enregistrement refusé ne doit pas empêcher l'application de
            // démarrer : on retombe sur l'interrogation ponctuelle.
        }
    }

    /**
     * Les appelants sont souvent des fonctions suspendues mais peuvent
     * reprendre sur le thread UI. Interroger ConnectivityManager ici passe
     * par Binder (`getActiveNetwork`) et certains firmwares Philips peuvent
     * bloquer plus d'une seconde. Le callback maintient déjà l'état courant :
     * cette lecture locale ne peut pas bloquer la navigation.
     */
    override fun isCurrentlyOnline(): Boolean = _isOnline.value

    @Suppress("DEPRECATION")
    private fun readCurrentState(): Boolean {
        val manager = connectivityManager ?: return false
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val network = manager.activeNetwork ?: return false
                val capabilities = manager.getNetworkCapabilities(network) ?: return false
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            } else {
                // API 21-22 : NET_CAPABILITY_VALIDATED n'existe pas, INTERNET seul.
                manager.activeNetworkInfo?.isConnected == true
            }
        } catch (e: Exception) {
            false
        }
    }
}

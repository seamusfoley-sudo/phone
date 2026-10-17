package ie.spnvoip.phone

import android.content.Context

enum class Transport { TCP, UDP, TLS }

/** The one and only SIP account this app ever registers. */
data class AccountConfig(
    val username: String,
    val password: String,
    val domain: String,          // host or host:port
    val transport: Transport = Transport.TCP,
    val keepAliveSeconds: Int = DEFAULT_KEEPALIVE_TCP,
) {
    companion object {
        // TCP/TLS NAT bindings live for minutes, so keep-alives can be sparse.
        const val DEFAULT_KEEPALIVE_TCP = 120
        // UDP NAT bindings often expire in 30-60 s, so UDP needs more frequent pings.
        const val DEFAULT_KEEPALIVE_UDP = 30
    }
}

/** Stores exactly one account. Saving always replaces the previous one. */
class AccountStore(context: Context) {
    private val prefs = context.getSharedPreferences("account", Context.MODE_PRIVATE)

    fun load(): AccountConfig? {
        val user = prefs.getString("username", null) ?: return null
        val domain = prefs.getString("domain", null) ?: return null
        return AccountConfig(
            username = user,
            password = prefs.getString("password", "") ?: "",
            domain = domain,
            transport = runCatching { Transport.valueOf(prefs.getString("transport", "TCP")!!) }
                .getOrDefault(Transport.TCP),
            keepAliveSeconds = prefs.getInt("keepalive", AccountConfig.DEFAULT_KEEPALIVE_TCP),
        )
    }

    fun save(cfg: AccountConfig) {
        prefs.edit()
            .clear()
            .putString("username", cfg.username)
            .putString("password", cfg.password)
            .putString("domain", cfg.domain)
            .putString("transport", cfg.transport.name)
            .putInt("keepalive", cfg.keepAliveSeconds)
            .apply()
    }

    fun clear() = prefs.edit().clear().apply()
}

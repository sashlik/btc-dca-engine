package com.hillariousstartups.btcdca

import java.math.BigDecimal

inline fun <reified T : Any> getEnv(name: String, default: T): T {
    val raw = System.getenv(name) ?: return default

    return try {
        when (T::class) {
            String::class -> raw
            Int::class -> raw.toInt()
            Long::class -> raw.toLong()
            Boolean::class -> raw.toBooleanStrict()
            Double::class -> raw.toDouble()
            BigDecimal::class -> raw.toBigDecimal()
            else -> error("Unsupported env var type: ${T::class}")
        } as T
    } catch (e: Exception) {
        throw IllegalArgumentException(
            "Env var $name='$raw' cannot be parsed as ${T::class.simpleName}",
            e
        )
    }
}
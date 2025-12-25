package com.hillariousstartups.btcdca

import com.fasterxml.jackson.databind.ObjectMapper
import java.math.BigDecimal
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.time.Instant

interface PriceSource {
    fun fetchNowUsd(now: Instant): BigDecimal
}

class FixedPriceSource(private val price: BigDecimal) : PriceSource {
    override fun fetchNowUsd(now: Instant): BigDecimal = price
}

class CoinGeckoPriceSource(
    private val http: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build(),
    private val json: ObjectMapper = ObjectMapper(),
) : PriceSource {

    override fun fetchNowUsd(now: Instant): BigDecimal {
        val request = HttpRequest.newBuilder()
            .uri(
                URI.create(
                    "https://api.coingecko.com/api/v3/simple/price" +
                            "?ids=bitcoin&vs_currencies=usd"
                )
            )
            .timeout(Duration.ofSeconds(5))
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofString())

        require(response.statusCode() == 200) {
            "CoinGecko HTTP ${response.statusCode()}: ${response.body()}"
        }

        val root = json.readTree(response.body())
        val priceNode = root.path("bitcoin").path("usd")

        require(!priceNode.isMissingNode && priceNode.isNumber) {
            "Unexpected CoinGecko response: ${response.body()}"
        }

        return priceNode.decimalValue()
    }
}
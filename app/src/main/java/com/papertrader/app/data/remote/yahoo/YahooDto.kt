package com.papertrader.app.data.remote.yahoo

data class YahooChartResponse(val chart: YahooChart?)

data class YahooChart(val result: List<YahooChartResult>?, val error: YahooError?)

data class YahooError(val code: String?, val description: String?)

data class YahooChartResult(
    val meta: YahooMeta?,
    val timestamp: List<Long>?,
    val indicators: YahooIndicators?
)

data class YahooMeta(
    val symbol: String?,
    val currency: String?,
    val exchangeName: String?,
    val longName: String?,
    val shortName: String?,
    val regularMarketPrice: Double?,
    val chartPreviousClose: Double?,
    val previousClose: Double?,
    val regularMarketDayHigh: Double?,
    val regularMarketDayLow: Double?,
    val regularMarketVolume: Double?,
    val fiftyTwoWeekHigh: Double?,
    val fiftyTwoWeekLow: Double?
)

data class YahooIndicators(val quote: List<YahooQuoteSeries>?)

data class YahooQuoteSeries(
    val open: List<Double?>?,
    val high: List<Double?>?,
    val low: List<Double?>?,
    val close: List<Double?>?,
    val volume: List<Double?>?
)

data class YahooSearchResponse(val quotes: List<YahooSearchQuote>?)

data class YahooSearchQuote(
    val symbol: String?,
    val shortname: String?,
    val longname: String?,
    val quoteType: String?,
    val exchDisp: String?
)

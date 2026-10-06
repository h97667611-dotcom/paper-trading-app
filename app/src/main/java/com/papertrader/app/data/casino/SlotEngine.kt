package com.papertrader.app.data.casino

import kotlin.random.Random

data class SlotWin(val type: String, val symbol: String, val count: Int, val multiplier: Double)
data class SlotBonus(val spins: Int, val scatterCount: Int)
data class SlotSpin(
    val reels: List<List<String>>,
    val wins: List<SlotWin>,
    val bonus: SlotBonus?,
    val totalMultiplier: Double
)

/**
 * Spin simulation (same rules as the demo-slot-api Node project). Wins are multipliers of a
 * fictional stake: nothing here touches real money.
 * reels[reelIndex][rowIndex]; symbols are ordered from lowest to highest value.
 */
object SlotEngine {
    private val COUNT_FACTOR = mapOf(3 to 1, 4 to 3, 5 to 8, 6 to 20)
    private val SCATTER_PAY = mapOf(3 to 2, 4 to 10, 5 to 50)

    private fun round2(v: Double): Double = Math.round(v * 100.0) / 100.0

    private fun weights(slot: DemoSlot): List<Pair<String, Int>> {
        val n = slot.symbols.size
        val list = slot.symbols.mapIndexed { i, s -> s to (n - i) }.toMutableList()
        slot.wild?.let { list.add(it to 1) }
        slot.scatter?.let { list.add(it to 1) }
        return list
    }

    private fun pick(weights: List<Pair<String, Int>>, random: Random): String {
        val total = weights.sumOf { it.second }
        var r = random.nextInt(total)
        for ((symbol, weight) in weights) {
            if (r < weight) return symbol
            r -= weight
        }
        return weights.last().first
    }

    fun randomGrid(slot: DemoSlot, random: Random = Random.Default): List<List<String>> {
        val w = weights(slot)
        return List(slot.reels) { List(slot.rows) { pick(w, random) } }
    }

    private fun payFor(slot: DemoSlot, symbol: String, count: Int): Double {
        val index = slot.symbols.indexOf(symbol)
        if (index < 0) return 0.0
        return (index + 1) * (COUNT_FACTOR[minOf(count, 6)] ?: 0).toDouble()
    }

    private fun paylines(rows: Int, reelCount: Int): List<List<Int>> {
        val lines = ArrayList<List<Int>>()
        for (r in 0 until rows) lines.add(List(reelCount) { r })
        if (rows >= 3) {
            val mid = rows / 2
            lines.add(List(reelCount) { i -> if (i % 2 == 0) 0 else mid })
            lines.add(List(reelCount) { i -> if (i % 2 == 0) rows - 1 else mid })
        }
        return lines
    }

    private fun evaluateLines(slot: DemoSlot, reels: List<List<String>>): List<SlotWin> {
        val wins = ArrayList<SlotWin>()
        for (line in paylines(slot.rows, slot.reels)) {
            val cells = line.mapIndexed { reel, row -> reels[reel][row] }
            val base = cells.firstOrNull { it != slot.wild } ?: slot.symbols.last()
            if (base == slot.scatter) continue
            var count = 0
            while (count < cells.size && (cells[count] == base || cells[count] == slot.wild)) count++
            if (count >= 3) wins.add(SlotWin("line", base, count, payFor(slot, base, count)))
        }
        return wins
    }

    private fun evaluateWays(slot: DemoSlot, reels: List<List<String>>): List<SlotWin> {
        val wins = ArrayList<SlotWin>()
        val starters = reels[0].filter { it != slot.wild && it != slot.scatter }.distinct()
        for (symbol in starters) {
            var length = 0
            var ways = 1
            for (reel in reels) {
                val matches = reel.count { it == symbol || it == slot.wild }
                if (matches == 0) break
                length++
                ways *= matches
            }
            if (length >= 3) {
                wins.add(SlotWin("ways", symbol, length, round2(payFor(slot, symbol, length) * ways * 0.5)))
            }
        }
        return wins
    }

    private fun evaluateCluster(slot: DemoSlot, reels: List<List<String>>): List<SlotWin> {
        val min = slot.minCluster ?: 8
        val counts = LinkedHashMap<String, Int>()
        var wilds = 0
        for (reel in reels) {
            for (s in reel) {
                if (s == slot.wild) wilds++
                else if (s != slot.scatter) counts[s] = (counts[s] ?: 0) + 1
            }
        }
        if (counts.isEmpty()) return emptyList()
        val best = counts.maxByOrNull { it.value }!!.key
        counts[best] = counts.getValue(best) + wilds
        return counts.filter { it.value >= min }.map { (symbol, count) ->
            val index = slot.symbols.indexOf(symbol)
            SlotWin("cluster", symbol, count, round2((index + 1) * 0.5 * (count - min + 1)))
        }
    }

    private fun evaluateScatter(slot: DemoSlot, reels: List<List<String>>): Pair<SlotBonus?, Double> {
        val scatter = slot.scatter ?: return null to 0.0
        val count = reels.sumOf { reel -> reel.count { it == scatter } }
        if (count < 3) return null to 0.0
        return SlotBonus(5 + (count - 3) * 5, count) to (SCATTER_PAY[minOf(count, 5)] ?: 0).toDouble()
    }

    fun spin(slot: DemoSlot, random: Random = Random.Default): SlotSpin {
        val reels = randomGrid(slot, random)
        val wins = when (slot.mechanic) {
            "ways" -> evaluateWays(slot, reels)
            "cluster" -> evaluateCluster(slot, reels)
            else -> evaluateLines(slot, reels)
        }
        val (bonus, scatterPay) = evaluateScatter(slot, reels)
        val total = round2(wins.sumOf { it.multiplier } + scatterPay)
        return SlotSpin(reels, wins, bonus, total)
    }
}

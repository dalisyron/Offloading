package tester

import core.*
import core.policy.*
import core.ue.OffloadingSystemConfig
import core.ue.OffloadingSystemConfig.Companion.withAlpha
import simulation.simulation.Simulator
import stochastic.lp.NoEffectivePolicyFoundException
import stochastic.lp.RangedOptimalPolicyFinder
import java.lang.Integer.min
import kotlin.concurrent.thread

class PolicyEffectivenessTester(
    private val baseSystemConfig: OffloadingSystemConfig,
    private val alphaRanges: List<AlphaRange>,
    private val precision: Int,
    private val simulationTicks: Int,
) {

    init {
        require(alphaRanges.isNotEmpty())
    }

    fun run(): Result {
        val alphaCombinations: List<List<Double>> = cartesianProduct(alphaRanges.map { it.toList() })

        var stochasticEffectiveCount: Int = 0
        var localOnlyEffectiveCount: Int = 0
        var offloadOnlyEffectiveCount: Int = 0
        var greedyOffloadFirstEffectiveCount: Int = 0
        var greedyLocalFirstEffectiveCount: Int = 0

        for (alpha in alphaCombinations) {
            val alphaConfig = baseSystemConfig.withAlpha(alpha)
            val simulator = Simulator(alphaConfig.withAlpha(alpha))

            try {
                val stochasticPolicy = RangedOptimalPolicyFinder.findOptimalPolicy(alphaConfig, precision)
                val simulationReportStochastic = simulator.simulatePolicy(stochasticPolicy, simulationTicks)
                if (simulationReportStochastic.isEffective) {
                    stochasticEffectiveCount++
                }

            } catch (_: NoEffectivePolicyFoundException) {
            }

            val simulationReportLocalOnly = simulator.simulatePolicy(LocalOnlyPolicy, simulationTicks)
            val simulationReportOffloadOnly = simulator.simulatePolicy(OffloadOnlyPolicy, simulationTicks)
            val simulationReportGreedyOffloadFirst = simulator.simulatePolicy(GreedyOffloadFirstPolicy, simulationTicks)
            val simulationReportGreedyLocalFirst = simulator.simulatePolicy(GreedyLocalFirstPolicy, simulationTicks)

            if (simulationReportLocalOnly.isEffective) {
                localOnlyEffectiveCount++
            }
            if (simulationReportOffloadOnly.isEffective) {
                offloadOnlyEffectiveCount++
            }
            if (simulationReportGreedyOffloadFirst.isEffective) {
                greedyOffloadFirstEffectiveCount++
            }
            if (simulationReportGreedyLocalFirst.isEffective) {
                greedyLocalFirstEffectiveCount++
            }
        }

        return Result(
            stochasticEffectivePercent = (stochasticEffectiveCount.toDouble() / alphaCombinations.size) * 100.0,
            localOnlyEffectivePercent = (localOnlyEffectiveCount.toDouble() / alphaCombinations.size) * 100.0,
            offloadOnlyEffectivePercent = (offloadOnlyEffectiveCount.toDouble() / alphaCombinations.size) * 100.0,
            greedyOffloadFirstEffectivePercent = (greedyOffloadFirstEffectiveCount.toDouble() / alphaCombinations.size) * 100.0,
            greedyLocalFirstEffectivePercent = (greedyLocalFirstEffectiveCount.toDouble() / alphaCombinations.size) * 100.0
        )
    }

    fun runConcurrent(numberOfThreads: Int): Result {
        val alphaCombinations: List<List<Double>> = cartesianProduct(alphaRanges.map { it.toList() })

        val stochasticIsEffective: MutableList<Int> = mutableListOfInt(alphaCombinations.size, -1)
        val localOnlyIsEffective: MutableList<Int> = mutableListOfInt(alphaCombinations.size, -1)
        val offloadOnlyIsEffective: MutableList<Int> = mutableListOfInt(alphaCombinations.size, -1)
        val greedyOffloadFirstIsEffective: MutableList<Int> = mutableListOfInt(alphaCombinations.size, -1)
        val greedyLocalFirstIsEffective: MutableList<Int> = mutableListOfInt(alphaCombinations.size, -1)

        val threadCount = min(min(numberOfThreads, 8), alphaCombinations.size)
        val alphaBatches = alphaCombinations.splitEqual(threadCount)
        val batchSizeAccumulative = alphaBatches.map { it.size }.toCumulative()

        val threads = (0 until threadCount).map { i ->
            thread(start = false) {
                for ((index: Int, alpha: List<Double>) in alphaBatches[i].withIndex()) {
                    val alphaConfig = baseSystemConfig.withAlpha(alpha)
                    val simulator = Simulator(alphaConfig)
                    val alphaIndex = (if (i > 0) batchSizeAccumulative[i - 1] else 0) + index

                    try {
                        val stochasticPolicy = RangedOptimalPolicyFinder.findOptimalPolicy(alphaConfig, precision)
                        val simulationReportStochastic = simulator.simulatePolicy(stochasticPolicy, simulationTicks)
                        if (simulationReportStochastic.isEffective) {
                            stochasticIsEffective[alphaIndex] = 1
                        } else {
                            stochasticIsEffective[alphaIndex] = 0
                        }

                    } catch (_: NoEffectivePolicyFoundException) {
                        stochasticIsEffective[alphaIndex] = 0
                    }

                    val simulationReportLocalOnly = simulator.simulatePolicy(LocalOnlyPolicy, simulationTicks)
                    val simulationReportOffloadOnly = simulator.simulatePolicy(OffloadOnlyPolicy, simulationTicks)
                    val simulationReportGreedyOffloadFirst =
                        simulator.simulatePolicy(GreedyOffloadFirstPolicy, simulationTicks)
                    val simulationReportGreedyLocalFirst =
                        simulator.simulatePolicy(GreedyLocalFirstPolicy, simulationTicks)

                    localOnlyIsEffective[alphaIndex] = simulationReportLocalOnly.isEffective.toInt()
                    offloadOnlyIsEffective[alphaIndex] = simulationReportOffloadOnly.isEffective.toInt()
                    greedyOffloadFirstIsEffective[alphaIndex] = simulationReportGreedyOffloadFirst.isEffective.toInt()
                    greedyLocalFirstIsEffective[alphaIndex] = simulationReportGreedyLocalFirst.isEffective.toInt()
                }

            }
        }

        threads.forEach {
            it.start()
        }

        threads.forEach {
            it.join()
        }

        check(stochasticIsEffective.all { it != -1 })
        check(localOnlyIsEffective.all { it != -1 })
        check(offloadOnlyIsEffective.all { it != -1 })
        check(greedyOffloadFirstIsEffective.all { it != -1 })
        check(greedyLocalFirstIsEffective.all { it != -1 })

        val stochasticEffectiveCount: Int = stochasticIsEffective.count { it == 1 }
        val localOnlyEffectiveCount: Int = localOnlyIsEffective.count { it == 1 }
        val offloadOnlyEffectiveCount: Int = offloadOnlyIsEffective.count { it == 1 }
        val greedyOffloadFirstEffectiveCount: Int = greedyOffloadFirstIsEffective.count { it == 1 }
        val greedyLocalFirstEffectiveCount: Int = greedyLocalFirstIsEffective.count { it == 1 }

        return Result(
            stochasticEffectivePercent = (stochasticEffectiveCount.toDouble() / alphaCombinations.size.toDouble()) * 100.0,
            localOnlyEffectivePercent = (localOnlyEffectiveCount.toDouble() / alphaCombinations.size.toDouble()) * 100.0,
            offloadOnlyEffectivePercent = (offloadOnlyEffectiveCount.toDouble() / alphaCombinations.size.toDouble()) * 100.0,
            greedyOffloadFirstEffectivePercent = (greedyOffloadFirstEffectiveCount.toDouble() / alphaCombinations.size.toDouble()) * 100.0,
            greedyLocalFirstEffectivePercent = (greedyLocalFirstEffectiveCount.toDouble() / alphaCombinations.size.toDouble()) * 100.0,
        )
    }

    data class Result(
        val stochasticEffectivePercent: Double,
        val localOnlyEffectivePercent: Double,
        val offloadOnlyEffectivePercent: Double,
        val greedyOffloadFirstEffectivePercent: Double,
        val greedyLocalFirstEffectivePercent: Double
    )
}
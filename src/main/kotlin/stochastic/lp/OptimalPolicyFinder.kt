package stochastic.lp

import ue.OffloadingSystemConfig
import ue.UserEquipmentState

data class StochasticPolicyConfig(
    val eta: Double,
    val averageDelay: Double,
    val decisionProbabilities: Map<Index, Double>
)

class OptimalPolicyFinder(
    val config: OffloadingSystemConfig
) {

    fun findOptimalPolicy(precision: Int): StochasticPolicyConfig {
        val optimalSolution = findOptimalSolution(precision)
        val variableCount =
            (config.taskQueueCapacity + 1) * (config.tuNumberOfPackets + 1) * (config.cpuNumberOfSections) * config.actionCount
        check(variableCount == optimalSolution.decisionProbabilities.size)

        val stateProbabilities = mutableMapOf<UserEquipmentState, Double>()

        val stateActionProbabilities =
            optimalSolution.decisionProbabilities.mapIndexed { index, d -> stateActionIndex(index) to d }.toMap()

        stateActionProbabilities.forEach { (key: Index, value: Double) ->
            if (stateProbabilities.containsKey(key.state)) {
                stateProbabilities[key.state] = stateProbabilities[key.state]!! + value
            } else {
                stateProbabilities[key.state] = value
            }
        }

        val decisions: Map<Index, Double> = stateActionProbabilities.mapValues { (key: Index, value: Double) ->
            value / stateProbabilities[key.state]!!
        }

        return StochasticPolicyConfig(
            decisionProbabilities = decisions,
            eta = optimalSolution.eta,
            averageDelay = optimalSolution.averageDelay
        )
    }

    private fun stateActionIndex(index: Int): Index {
        val r1 = (config.tuNumberOfPackets + 1) * (config.cpuNumberOfSections) * config.actionCount
        val taskQueueLength = index / r1
        val r2 = (config.cpuNumberOfSections) * config.actionCount
        val tuState = (index % r1) / r2
        val r3 = config.actionCount
        val cpuState = ((index % r1) % r2) / config.actionCount
        val action = config.allActions.find { it.order == ((index % r1) % r2) % r3 }!!

        return Index(
            state = UserEquipmentState(
                taskQueueLength = taskQueueLength,
                tuState = tuState,
                cpuState = cpuState
            ),
            action = action
        )
    }

    data class LPOffloadingSolution(
        val eta: Double,
        val averageDelay: Double,
        val decisionProbabilities: List<Double>
    )

    private fun findOptimalSolution(precision: Int): LPOffloadingSolution {
        var optimalSolution: LPSolution? = null
        var minEta: Double = 0.0

        for (i in 0..precision) {
            println("cycle $i of $precision")
            val eta = i.toDouble() / precision

            val tempConfig = config.copy(
                userEquipmentConfig = config.userEquipmentConfig.copy(
                    componentsConfig = config.userEquipmentConfig.componentsConfig.copy(
                        eta = eta
                    )
                )
            )

            val offloadingLPCreator: OffloadingLPCreator = OffloadingLPCreator(tempConfig)
            val linearProgram = offloadingLPCreator.createLP()

            val solution = LPSolver.solve(linearProgram)

            if (optimalSolution == null || solution.objectiveValue < optimalSolution.objectiveValue) {
                optimalSolution = solution
                minEta = eta
            }
        }

        return LPOffloadingSolution(
            eta = minEta,
            averageDelay = optimalSolution!!.objectiveValue,
            decisionProbabilities =  optimalSolution.variableValues
        )
    }
}
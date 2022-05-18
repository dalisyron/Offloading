package core.policy

import policy.Action
import ue.OffloadingSystemConfig

class LocalOnlyPolicy(
    val systemConfig: OffloadingSystemConfig
) : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        if (state.cpuState == 0 && state.taskQueueLength > 0) {
            return Action.AddToCPU
        } else {
            return Action.NoOperation
        }
    }
}

class TransmitOnlyPolicy(
    val systemConfig: OffloadingSystemConfig
) : Policy {
    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        if (state.tuState == 0 && state.taskQueueLength > 0) {
            return Action.AddToTransmissionUnit
        } else {
            return Action.NoOperation
        }
    }
}

class GreedyLocalFirstPolicy(
    val systemConfig: OffloadingSystemConfig
) : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        val canRunLocally = state.cpuState == 0
        val canTransmit = state.tuState == 0

        if (canRunLocally && canTransmit && state.taskQueueLength >= 2) {
            return Action.AddToBothUnits
        } else if (canRunLocally && state.taskQueueLength >= 1) {
            return Action.AddToCPU
        } else if (canTransmit && state.taskQueueLength >= 1) {
            return Action.AddToTransmissionUnit
        } else {
            return Action.NoOperation
        }
    }
}

class GreedyOffloadFirstPolicy(
    val systemConfig: OffloadingSystemConfig
) : Policy {

    override fun getActionForState(state: UserEquipmentExecutionState): Action {
        val canRunLocally = state.cpuState == 0
        val canTransmit = state.tuState == 0

        if (canRunLocally && canTransmit && state.taskQueueLength >= 2) {
            return Action.AddToBothUnits
        } else if (canTransmit && state.taskQueueLength >= 1) {
            return Action.AddToTransmissionUnit
        } else if (canRunLocally && state.taskQueueLength >= 1) {
            return Action.AddToCPU
        }

        return Action.NoOperation
    }
}
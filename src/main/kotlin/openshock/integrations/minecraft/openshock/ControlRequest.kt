package openshock.integrations.minecraft.openshock

data class ControlRequest(val shocks: List<ControlItem>, val customName: String)

data class ControlItem(val id: String, val type: ControlType, val intensity: Byte, val duration: UShort)

package at.hannibal2.skyhanni.events.mining

import at.hannibal2.skyhanni.api.HotmApi
import at.hannibal2.skyhanni.api.event.SkyHanniEvent

open class PowderEvent(val powder: HotmApi.PowderType) : SkyHanniEvent() {
    class Gain(powder: HotmApi.PowderType, val amount: Long) : PowderEvent(powder)
    class Spent(powder: HotmApi.PowderType, val amount: Long) : PowderEvent(powder)
}

package at.hannibal2.skyhanni.events.minecraft

import at.hannibal2.skyhanni.api.event.SkyHanniEvent

class SkyHanniTickEvent(private val tick: Int) : SkyHanniEvent() {

    fun isMod(i: Int, offset: Int = 0) = (tick + offset) % i == 0

    /**
     * Use of this method is discouraged, use [SecondPassedEvent] instead.
     * Only use if very needed.
     */
    fun repeatSeconds(i: Int, offset: Int = 0) = isMod(i * 20, offset)
}

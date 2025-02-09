package at.hannibal2.skyhanni.features.rift.area.livingcave

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.ClickType
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.events.BlockClickEvent
import at.hannibal2.skyhanni.events.ReceiveParticleEvent
import at.hannibal2.skyhanni.events.ServerBlockChangeEvent
import at.hannibal2.skyhanni.events.TitleReceivedEvent
import at.hannibal2.skyhanni.events.minecraft.SkyHanniRenderWorldEvent
import at.hannibal2.skyhanni.features.rift.RiftApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.LocationUtils
import at.hannibal2.skyhanni.utils.LocationUtils.distanceToPlayer
import at.hannibal2.skyhanni.utils.LorenzVec
import at.hannibal2.skyhanni.utils.RenderUtils.drawWaypointFilled
import at.hannibal2.skyhanni.utils.SimpleTimeMark
import at.hannibal2.skyhanni.utils.SpecialColor.toSpecialColor
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@SkyHanniModule
object LivingCaveLivingMetalHelper {

    private val config get() = RiftApi.config.area.livingCave.livingCaveLivingMetalConfig
    private var lastClicked: LorenzVec? = null
    private var pair: Pair<LorenzVec, LorenzVec>? = null
    private var animationStartTime = SimpleTimeMark.farPast()

    @HandleEvent(onlyOnIsland = IslandType.THE_RIFT)
    fun onBlockClick(event: BlockClickEvent) {
        if (!isEnabled()) return
        if (event.clickType == ClickType.LEFT_CLICK) {
            val name = event.getBlockState.block.toString()
            if (name.contains("lapis_ore")) {
                lastClicked = event.position
            }
        }
    }

    @HandleEvent
    fun onBlockChange(event: ServerBlockChangeEvent) {
        if (!isEnabled()) return
        val location = event.location
        if (location.distanceToPlayer() >= 7) return

        if (event.old == "lapis_ore") {
            pair?.let {
                if (it.second == location) {
                    pair = null
                }
            }
        }

        if (event.new != "lapis_ore") return

        lastClicked?.let {
            val distance = location.distance(it)
            if (distance < 2) {
                pair = Pair(it, location)
                animationStartTime = SimpleTimeMark.now()
            }
        }
    }

    @HandleEvent
    fun onRenderWorld(event: SkyHanniRenderWorldEvent) {
        if (!isEnabled()) return
        val (a, b) = pair ?: return
        if (animationStartTime.passedSince() > 4.seconds) return

        val maxTime = 500.milliseconds
        val location = LocationUtils.interpolateOverTime(animationStartTime, maxTime, a, b)
        event.drawWaypointFilled(
            location,
            color,
            seeThroughBlocks = location.distanceToPlayer() < 10,
        )
    }

    @HandleEvent
    fun onReceiveParticle(event: ReceiveParticleEvent) {
        if (!isEnabled()) return
        if (!config.hideParticles) return

        pair?.let {
            if (it.second.distance(event.location) < 3) {
                event.cancel()
            }
        }
    }

    @HandleEvent
    fun onTitleReceived(event: TitleReceivedEvent) {
        if (!isEnabled()) return
        if (event.title.contains("Living Metal")) {
            pair = null
        }
    }

    val color get() = config.color.get().toSpecialColor()

    fun isEnabled() = RiftApi.inRift() && (RiftApi.inLivingCave() || RiftApi.inLivingStillness()) && config.enabled
}

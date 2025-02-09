package at.hannibal2.skyhanni.features.garden.pests

import at.hannibal2.skyhanni.api.event.HandleEvent
import at.hannibal2.skyhanni.data.IslandType
import at.hannibal2.skyhanni.data.ProfileStorageData
import at.hannibal2.skyhanni.events.ConfigLoadEvent
import at.hannibal2.skyhanni.events.GuiRenderEvent
import at.hannibal2.skyhanni.events.chat.SkyHanniChatEvent
import at.hannibal2.skyhanni.events.minecraft.WorldChangeEvent
import at.hannibal2.skyhanni.features.garden.GardenApi
import at.hannibal2.skyhanni.skyhannimodule.SkyHanniModule
import at.hannibal2.skyhanni.utils.ConditionalUtils
import at.hannibal2.skyhanni.utils.ItemUtils
import at.hannibal2.skyhanni.utils.NeuItems.getItemStack
import at.hannibal2.skyhanni.utils.RegexUtils.matchMatcher
import at.hannibal2.skyhanni.utils.RegexUtils.matches
import at.hannibal2.skyhanni.utils.RenderUtils
import at.hannibal2.skyhanni.utils.RenderUtils.renderRenderables
import at.hannibal2.skyhanni.utils.SkullTextureHolder
import at.hannibal2.skyhanni.utils.renderables.Renderable
import at.hannibal2.skyhanni.utils.repopatterns.RepoPattern

@SkyHanniModule
object StereoHarmonyDisplay {

    private val config get() = PestApi.config.stereoHarmony

    private var activeVinyl: VinylType?
        get() = ProfileStorageData.profileSpecific?.garden?.activeVinyl
        private set(type) {
            ProfileStorageData.profileSpecific?.garden?.activeVinyl = type
        }

    private fun VinylType.getPest() = PestType.filterableEntries.find { it.vinyl == this }

    private val vinylTypeGroup = RepoPattern.group("garden.vinyl")

    /**
     * REGEX-TEST: §aYou are now playing §r§eNot Just a Pest§r§a!
     */
    private val selectVinylPattern by vinylTypeGroup.pattern(
        "select",
        "§aYou are now playing §r§e(?<type>.*)§r§a!",
    )

    /**
     * REGEX-TEST: §aYou are no longer playing §r§eNot Just a Pest§r§a!
     */
    private val unselectVinylPattern by vinylTypeGroup.pattern(
        "unselect",
        "§aYou are no longer playing §r§e.*§r§a!",
    )

    private var display = emptyList<Renderable>()

    private val questionMarkSkull by lazy {
        ItemUtils.createSkull(
            displayName = "§c?",
            uuid = "28aa984a-2077-40cc-8de7-e641adf2c497",
            value = SkullTextureHolder.getTexture("QUESTION_MARK"),
        )
    }

    private fun update() {
        display = drawDisplay()
    }

    private fun drawDisplay() = buildList {
        val vinyl = activeVinyl ?: return@buildList
        val pest = vinyl.getPest()

        val itemStack = pest?.internalName?.getItemStack() ?: questionMarkSkull
        if (config.showHead.get()) add(Renderable.itemStack(itemStack, 1.67))
        val list = mutableListOf<Renderable>()
        val vinylName = vinyl.displayName
        val pestName = pest?.displayName ?: "None"
        list.add(Renderable.string("§ePlaying: §a$vinylName"))
        val pestLine = mutableListOf<Renderable>()
        pestLine.add(Renderable.string("§ePest: §c$pestName "))
        if (pest?.crop != null && config.showCrop.get()) pestLine.add(Renderable.itemStack(pest.crop.icon))
        list.add(Renderable.horizontalContainer(pestLine))
        add(Renderable.verticalContainer(list, verticalAlign = RenderUtils.VerticalAlignment.CENTER))
    }

    @HandleEvent(onlyOnIsland = IslandType.GARDEN)
    fun onChat(event: SkyHanniChatEvent) {
        selectVinylPattern.matchMatcher(event.message) {
            activeVinyl = VinylType.getByName(group("type"))
            update()
        }
        if (unselectVinylPattern.matches(event.message)) {
            activeVinyl = VinylType.NONE
            update()
        }
    }

    @HandleEvent
    fun onRenderOverlay(event: GuiRenderEvent.GuiOverlayRenderEvent) {
        if (!isEnabled()) return
        if (!GardenApi.isCurrentlyFarming() && !config.alwaysShow) return

        if (activeVinyl == VinylType.NONE && config.hideWhenNone) return
        else if (display.isEmpty()) update()
        if (display.isEmpty()) return
        val content = Renderable.horizontalContainer(display, 1, verticalAlign = RenderUtils.VerticalAlignment.CENTER)
        val renderables = listOf(content)
        config.position.renderRenderables(renderables, posLabel = "Stereo Harmony Display")
    }

    @HandleEvent
    fun onWorldChange(event: WorldChangeEvent) {
        display = emptyList()
    }

    @HandleEvent
    fun onConfigLoad(event: ConfigLoadEvent) {
        ConditionalUtils.onToggle(config.showHead, config.showCrop) { update() }
    }

    fun isEnabled() = GardenApi.inGarden() && config.displayEnabled
}

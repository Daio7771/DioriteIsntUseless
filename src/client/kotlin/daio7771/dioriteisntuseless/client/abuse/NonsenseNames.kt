package daio7771.dioriteisntuseless.client.abuse

import com.mojang.blaze3d.systems.RenderSystem
import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.abuse.signal.NonsenseNameSignal
import daio7771.dioriteisntuseless.mixin.client.GuiAccessor
import daio7771.dioriteisntuseless.network.NonsenseNamesPacket
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.network.chat.Component
import net.minecraft.world.item.Item
import net.minecraft.world.item.ItemStack
import java.util.Random

/**
 * Abuse Mode nonsense names, only on this client (see NonsenseNameSignal).
 *
 * - Visual only: ItemStackMixin changes what getHoverName returns on the render thread, so the
 *   server (also the integrated one in single player) always sees the real name. The item, its
 *   NBT, its stacks and its recipes do not change.
 * - It is never seen changing (golden rule 2): names only appear or disappear when no screen is
 *   open and no item name is floating above the hotbar. Meanwhile, each name always stays the
 *   same (no changing every frame).
 * - Each name wears off while the player has an inventory open; when they close it after seeing
 *   it, the name goes back to normal. If they never see it, it expires silently.
 *
 * Everything is used from the client thread.
 */
object NonsenseNames {

    private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
    private val LENGTH = 10..14

    private class Scramble(val name: Component, val exposureNeeded: Int, val lifetime: Int) {
        var exposure = 0
        var age = 0
        val expired: Boolean get() = exposure >= exposureNeeded || age >= lifetime
    }

    private val active = HashMap<Item, Scramble>()

    /**
     * Phase B of the ending: every item except diorite and the mod's own, until "Start over". The
     * names are made up the first time they are shown and do not change after that.
     */
    private var everything = false
    private val everythingNames = HashMap<Item, Component>()
    private val keepsItsName = HashMap<Item, Boolean>()

    /** Received packets waiting for a calm moment to be applied, in order. */
    private val pending = ArrayDeque<NonsenseNamesPacket>()

    private val random = Random()

    /** An error has turned it off until the next world (golden rule 1). */
    private var broken = false

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(NonsenseNamesPacket.TYPE) { packet, _, _ ->
            if (!broken) pending += packet
        }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    /** Called by ItemStackMixin with the real name. */
    @JvmStatic
    fun displayName(stack: ItemStack, original: Component): Component {
        // Only the render thread: the integrated server and any other thread see the real name.
        if (!RenderSystem.isOnRenderThread() || (!everything && active.isEmpty()) || stack.hasCustomHoverName()) {
            return original
        }
        val item = stack.item
        active[item]?.let { return it.name }
        if (!everything || keepsItsName.getOrPut(item) { NonsenseNameSignal.keepsItsName(item) }) return original
        return everythingNames.getOrPut(item) { Component.literal(randomName()) }
    }

    private fun tick(client: Minecraft) {
        if (broken || (active.isEmpty() && pending.isEmpty())) return
        try {
            if (client.player == null) return
            val viewing = client.screen is AbstractContainerScreen<*>
            for (scramble in active.values) {
                scramble.age++
                if (viewing) scramble.exposure++
            }
            val quiet = client.screen == null && (client.gui as GuiAccessor).`dioriteisntuseless$getToolHighlightTimer`() <= 0
            if (!quiet) return
            active.values.removeIf { it.expired }
            while (pending.isNotEmpty()) apply(pending.removeFirst())
        } catch (e: Exception) {
            LOGGER.error("Abuse mode: internal error in the client; disabled until the next world.", e)
            reset()
            broken = true
        }
    }

    private fun apply(packet: NonsenseNamesPacket) {
        if (packet.clear) {
            clearNames()
            return
        }
        if (packet.everything) everything = true
        for (id in packet.items) {
            val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null) ?: continue
            if (NonsenseNameSignal.keepsItsName(item)) continue
            // If it already had one, it keeps the same name: only the time is renewed.
            val name = active[item]?.name ?: Component.literal(randomName())
            active[item] = Scramble(name, packet.exposureTicks, packet.lifetimeTicks)
        }
    }

    private fun randomName(): String {
        val length = LENGTH.first + random.nextInt(LENGTH.last - LENGTH.first + 1)
        return buildString(length) { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
    }

    private fun clearNames() {
        active.clear()
        everything = false
        everythingNames.clear()
        keepsItsName.clear()
    }

    private fun reset() {
        clearNames()
        pending.clear()
        broken = false
    }
}

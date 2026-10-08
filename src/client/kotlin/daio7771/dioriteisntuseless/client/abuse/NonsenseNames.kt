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
 * Nombres sin sentido del Abuse Mode, solo en este cliente (ver NonsenseNameSignal).
 *
 * - Solo visual: ItemStackMixin cambia lo que devuelve getHoverName en el hilo de dibujo, así
 *   que el servidor (también el integrado de un solo jugador) siempre ve el nombre de verdad.
 *   El ítem, su NBT, sus pilas y sus recetas no cambian.
 * - Nunca se le ve cambiar (regla de oro 2): los nombres solo aparecen o desaparecen cuando no hay
 *   ninguna pantalla abierta ni el nombre del ítem flotando sobre la barra rápida. Mientras tanto,
 *   cada nombre es siempre el mismo (nada de cambiar cada frame).
 * - Cada nombre se gasta mientras el jugador tiene un inventario abierto; cuando lo cierra
 *   después de verlo, el nombre vuelve a la normalidad. Si nunca lo ve, caduca en silencio.
 *
 * Todo se usa desde el hilo del cliente.
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

    /** Paquetes recibidos que esperan a un momento tranquilo para aplicarse, en orden. */
    private val pending = ArrayDeque<NonsenseNamesPacket>()

    private val random = Random()

    /** Un error lo ha apagado hasta el próximo mundo (regla de oro 1). */
    private var broken = false

    fun init() {
        ClientPlayNetworking.registerGlobalReceiver(NonsenseNamesPacket.TYPE) { packet, _, _ ->
            if (!broken) pending += packet
        }
        ClientTickEvents.END_CLIENT_TICK.register(::tick)
        ClientPlayConnectionEvents.DISCONNECT.register { _, _ -> reset() }
    }

    /** Lo llama ItemStackMixin con el nombre de verdad. */
    @JvmStatic
    fun displayName(stack: ItemStack, original: Component): Component {
        // Solo el hilo de dibujo: el servidor integrado y cualquier otro hilo ven el nombre real.
        if (!RenderSystem.isOnRenderThread() || active.isEmpty() || stack.hasCustomHoverName()) return original
        return active[stack.item]?.name ?: original
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
            active.clear()
            return
        }
        for (id in packet.items) {
            val item = BuiltInRegistries.ITEM.getOptional(id).orElse(null) ?: continue
            if (NonsenseNameSignal.keepsItsName(item)) continue
            // Si ya lo tenía, conserva el mismo nombre: solo se renueva el tiempo.
            val name = active[item]?.name ?: Component.literal(randomName())
            active[item] = Scramble(name, packet.exposureTicks, packet.lifetimeTicks)
        }
    }

    private fun randomName(): String {
        val length = LENGTH.first + random.nextInt(LENGTH.last - LENGTH.first + 1)
        return buildString(length) { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }
    }

    private fun reset() {
        active.clear()
        pending.clear()
        broken = false
    }
}

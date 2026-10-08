package daio7771.dioriteisntuseless.abuse

import daio7771.dioriteisntuseless.Dioriteisntuseless
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.MinecraftServer
import net.minecraft.world.item.ItemStack
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

/**
 * Estado del Abuse Mode de cada jugador, guardado con el mundo (data/dioriteisntuseless_abuse.dat
 * del Overworld). Está en el mundo y no en el archivo del jugador para poder consultarlo y
 * cambiarlo aunque el jugador no esté conectado.
 *
 * Solo se usa desde el hilo del servidor.
 */
class AbuseData private constructor() : SavedData() {

    private val players = HashMap<UUID, PlayerAbuse>()

    fun get(uuid: UUID): PlayerAbuse = players.getOrPut(uuid) { PlayerAbuse() }

    fun getIfPresent(uuid: UUID): PlayerAbuse? = players[uuid]

    override fun save(tag: CompoundTag): CompoundTag {
        val list = ListTag()
        for ((uuid, state) in players) {
            list.add(CompoundTag().apply {
                putUUID("Player", uuid)
                state.save(this)
            })
        }
        tag.putInt("DataVersion", DATA_VERSION)
        tag.put("Players", list)
        return tag
    }

    companion object {
        private val NAME = "${Dioriteisntuseless.MOD_ID}_abuse"

        /** Sube si cambia el formato y hay que convertir los datos guardados. */
        private const val DATA_VERSION = 1

        fun get(server: MinecraftServer): AbuseData =
            server.overworld().dataStorage.computeIfAbsent(::load, ::AbuseData, NAME)

        private fun load(tag: CompoundTag): AbuseData {
            val data = AbuseData()
            for (entry in tag.getList("Players", Tag.TAG_COMPOUND.toInt())) {
                val compound = entry as CompoundTag
                if (!compound.hasUUID("Player")) continue
                data.players[compound.getUUID("Player")] = PlayerAbuse.load(compound)
            }
            return data
        }
    }
}

/** Progreso de un jugador. Todo se pone a 0 con "Start over" ([resetProgress]). */
class PlayerAbuse {
    /** 0 = nada; 1 a 4 = niveles. Nunca baja (salvo "Start over" o el comando de pruebas). */
    var level = 0
    var dioriteMined = 0L
    var stoneMined = 0L
    var logsFelled = 0L
    var axesCrafted = 0L

    /** Ticks jugados por este jugador (solo cuentan conectado y con el Abuse Mode activo). */
    var playTicks = 0L

    /** [playTicks] cuando llegó al nivel actual. */
    var levelReachedAt = 0L

    /** [playTicks] en que toca la próxima señal, o -1 si no hay ninguna programada. */
    var nextSignalAt = -1L

    /** Id de la última señal, para no repetir la misma dos veces seguidas. */
    var lastSignal = ""

    /** Última frase en Morse, por lo mismo. */
    var lastMorsePhrase = ""

    /** Última palabra de cartel, por lo mismo. */
    var lastSignWord = ""

    /** [playTicks] cuando apareció EL cartel del nivel 4, o -1 si aún no ha aparecido. */
    var finalSignPlacedAt = -1L

    /** Fase A del final: el hacha no tala y fundir diorita no le da cristales a este jugador. */
    var dioriteUseless = false

    /**
     * Lo que le quitó la fase A del final, para devolvérselo en "Start over". No se borra con
     * [resetProgress]: se vacía al devolverlo.
     */
    val takenItems = ArrayList<ItemStack>()

    /** "Start over" se hizo sin el jugador conectado: [takenItems] se le devuelve al entrar. */
    var returnItemsOnJoin = false

    /** abuso = diorita minada + troncos talados con el hacha / 4 + hachas fabricadas * 16 */
    val score: Long get() = dioriteMined + logsFelled / 4 + axesCrafted * 16

    /** Días de juego desde que llegó al nivel actual. */
    val daysAtLevel: Double get() = (playTicks - levelReachedAt).toDouble() / AbuseTracker.TICKS_PER_DAY

    /** "Start over": nivel, contadores, tiempo y señales a 0, y la diorita vuelve a ser útil. */
    fun resetProgress() {
        level = 0
        dioriteMined = 0
        stoneMined = 0
        logsFelled = 0
        axesCrafted = 0
        playTicks = 0
        levelReachedAt = 0
        nextSignalAt = -1
        lastSignal = ""
        lastMorsePhrase = ""
        lastSignWord = ""
        finalSignPlacedAt = -1
        dioriteUseless = false
    }

    fun save(tag: CompoundTag) {
        tag.putInt("Level", level)
        tag.putLong("DioriteMined", dioriteMined)
        tag.putLong("StoneMined", stoneMined)
        tag.putLong("LogsFelled", logsFelled)
        tag.putLong("AxesCrafted", axesCrafted)
        tag.putLong("PlayTicks", playTicks)
        tag.putLong("LevelReachedAt", levelReachedAt)
        tag.putLong("NextSignalAt", nextSignalAt)
        tag.putString("LastSignal", lastSignal)
        tag.putString("LastMorsePhrase", lastMorsePhrase)
        tag.putString("LastSignWord", lastSignWord)
        tag.putLong("FinalSignPlacedAt", finalSignPlacedAt)
        tag.putBoolean("DioriteUseless", dioriteUseless)
        tag.put("TakenItems", ListTag().apply { takenItems.forEach { add(it.save(CompoundTag())) } })
        tag.putBoolean("ReturnItemsOnJoin", returnItemsOnJoin)
    }

    companion object {
        fun load(tag: CompoundTag) = PlayerAbuse().apply {
            level = tag.getInt("Level").coerceIn(0, AbuseTracker.MAX_LEVEL)
            dioriteMined = tag.getLong("DioriteMined").coerceAtLeast(0)
            stoneMined = tag.getLong("StoneMined").coerceAtLeast(0)
            logsFelled = tag.getLong("LogsFelled").coerceAtLeast(0)
            axesCrafted = tag.getLong("AxesCrafted").coerceAtLeast(0)
            playTicks = tag.getLong("PlayTicks").coerceAtLeast(0)
            levelReachedAt = tag.getLong("LevelReachedAt").coerceIn(0, playTicks)
            // Si falta (datos de antes de las señales), -1: se programa en el siguiente tick.
            nextSignalAt = if (tag.contains("NextSignalAt")) tag.getLong("NextSignalAt").coerceAtLeast(-1) else -1
            lastSignal = tag.getString("LastSignal")
            lastMorsePhrase = tag.getString("LastMorsePhrase")
            lastSignWord = tag.getString("LastSignWord")
            finalSignPlacedAt = if (tag.contains("FinalSignPlacedAt")) tag.getLong("FinalSignPlacedAt").coerceIn(-1, playTicks) else -1
            dioriteUseless = tag.getBoolean("DioriteUseless")
            for (item in tag.getList("TakenItems", Tag.TAG_COMPOUND.toInt())) {
                // Un ítem de un mod que ya no está se lee como vacío: se pierde (no hay nada que devolver).
                ItemStack.of(item as CompoundTag).takeUnless { it.isEmpty }?.let(takenItems::add)
            }
            returnItemsOnJoin = tag.getBoolean("ReturnItemsOnJoin")
        }
    }
}

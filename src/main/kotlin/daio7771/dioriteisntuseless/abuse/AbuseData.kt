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

        /**
         * Sube si cambia el formato y hay que convertir los datos guardados.
         * 2: el nivel avanza por árboles talados (antes, por puntuación y días).
         * 3: también por picadas del pico; las señales van por acciones (árboles + picadas).
         */
        private const val DATA_VERSION = 3

        fun get(server: MinecraftServer): AbuseData =
            server.overworld().dataStorage.computeIfAbsent(::load, ::AbuseData, NAME)

        private fun load(tag: CompoundTag): AbuseData {
            val data = AbuseData()
            val version = tag.getInt("DataVersion")
            for (entry in tag.getList("Players", Tag.TAG_COMPOUND.toInt())) {
                val compound = entry as CompoundTag
                if (!compound.hasUUID("Player")) continue
                data.players[compound.getUUID("Player")] = PlayerAbuse.load(compound, version)
            }
            return data
        }
    }
}

/** Progreso de un jugador. Todo se pone a 0 con "Start over" ([resetProgress]). */
class PlayerAbuse {
    /**
     * 0 = nada; 1 a 4 = niveles; 5 = final (AbuseTracker.LEVEL_FINAL). Nunca baja (salvo
     * "Start over" o el comando de pruebas).
     */
    var level = 0

    /** Árboles enteros talados con el hacha de dioritina (en supervivencia y con el Abuse Mode activo). */
    var treesFelled = 0L

    /** Árboles talados desde que llegó al nivel actual: deciden cuándo sube y cuándo llega el final. */
    var treesAtLevel = 0L

    /** Picadas 3x3 con el pico de dioritina (en supervivencia y con el Abuse Mode activo). */
    var strikes = 0L

    /** Picadas desde que llegó al nivel actual: suman con [treesAtLevel] (AbuseTracker.stepReached). */
    var strikesAtLevel = 0L

    /** Acciones de abuso: cada árbol y cada picada es una. Marcan el ritmo de las señales. */
    val actions: Long get() = treesFelled + strikes

    /** Ticks jugados por este jugador (solo cuentan conectado y con el Abuse Mode activo). */
    var playTicks = 0L

    /**
     * [playTicks] en que ocurre la próxima señal, o -1 si no hay ninguna en camino. En los niveles
     * 1 a 4 se fija poco después de llegar a [signalDueAtActions]; en el final, solo por tiempo.
     */
    var nextSignalAt = -1L

    /** Niveles 1 a 4: con estas [actions] toca la próxima señal, o -1. */
    var signalDueAtActions = -1L

    /** Id de la última señal, para no repetir la misma dos veces seguidas. */
    var lastSignal = ""

    /** Última frase en Morse, por lo mismo. */
    var lastMorsePhrase = ""

    /** Última palabra de cartel, por lo mismo. */
    var lastSignWord = ""

    /** [playTicks] cuando apareció EL cartel del nivel 4, o -1 si aún no ha aparecido. */
    var finalSignPlacedAt = -1L

    /** [playTicks] en que empezó a tocar el final (nivel 4, cartel puesto y árboles o picadas), o -1. */
    var endingDueAt = -1L

    /** [playTicks] de la fase A del final (empieza la fase B), o -1. */
    var endingStartedAt = -1L

    /** Mensajes de la fase B ya enviados (EndingMessages). */
    var endingMessagesSent = 0

    /** Ya se le ha dicho a su cliente que tocan los créditos. No se guarda: al entrar se le repite. */
    var creditsAnnounced = false

    /** Fase A del final: el hacha no tala, el pico no pica 3x3 y fundir diorita no le da cristales a este jugador. */
    var dioriteUseless = false

    /**
     * Lo que le quitó la fase A del final, para devolvérselo en "Start over". No se borra con
     * [resetProgress]: se vacía al devolverlo.
     */
    val takenItems = ArrayList<ItemStack>()

    /** "Start over" se hizo sin el jugador conectado: [takenItems] se le devuelve al entrar. */
    var returnItemsOnJoin = false

    /** "Start over": nivel, contadores, tiempo y señales a 0, y la diorita vuelve a ser útil. */
    fun resetProgress() {
        level = 0
        treesFelled = 0
        treesAtLevel = 0
        strikes = 0
        strikesAtLevel = 0
        playTicks = 0
        nextSignalAt = -1
        signalDueAtActions = -1
        lastSignal = ""
        lastMorsePhrase = ""
        lastSignWord = ""
        finalSignPlacedAt = -1
        endingDueAt = -1
        endingStartedAt = -1
        endingMessagesSent = 0
        creditsAnnounced = false
        dioriteUseless = false
    }

    fun save(tag: CompoundTag) {
        tag.putInt("Level", level)
        tag.putLong("TreesFelled", treesFelled)
        tag.putLong("TreesAtLevel", treesAtLevel)
        tag.putLong("Strikes", strikes)
        tag.putLong("StrikesAtLevel", strikesAtLevel)
        tag.putLong("PlayTicks", playTicks)
        tag.putLong("NextSignalAt", nextSignalAt)
        tag.putLong("SignalDueAtActions", signalDueAtActions)
        tag.putString("LastSignal", lastSignal)
        tag.putString("LastMorsePhrase", lastMorsePhrase)
        tag.putString("LastSignWord", lastSignWord)
        tag.putLong("FinalSignPlacedAt", finalSignPlacedAt)
        tag.putLong("EndingDueAt", endingDueAt)
        tag.putLong("EndingStartedAt", endingStartedAt)
        tag.putInt("EndingMessagesSent", endingMessagesSent)
        tag.putBoolean("DioriteUseless", dioriteUseless)
        tag.put("TakenItems", ListTag().apply { takenItems.forEach { add(it.save(CompoundTag())) } })
        tag.putBoolean("ReturnItemsOnJoin", returnItemsOnJoin)
    }

    companion object {
        /**
         * [version] es el DataVersion del archivo. Los datos de la versión 1 (nivel por puntuación
         * y días) conservan el nivel y empiezan a contar árboles desde 0 en él. Los de la 2 no
         * tienen picadas: empiezan en 0 y sus acciones son sus árboles.
         */
        fun load(tag: CompoundTag, version: Int) = PlayerAbuse().apply {
            level = tag.getInt("Level").coerceIn(0, AbuseTracker.LEVEL_FINAL)
            treesFelled = tag.getLong("TreesFelled").coerceAtLeast(0)
            treesAtLevel = tag.getLong("TreesAtLevel").coerceIn(0, treesFelled)
            strikes = tag.getLong("Strikes").coerceAtLeast(0)
            strikesAtLevel = tag.getLong("StrikesAtLevel").coerceIn(0, strikes)
            playTicks = tag.getLong("PlayTicks").coerceAtLeast(0)
            // Si falta (datos de antes de las señales), -1: se programa en el siguiente tick.
            nextSignalAt = if (tag.contains("NextSignalAt")) tag.getLong("NextSignalAt").coerceAtLeast(-1) else -1
            // En la versión 1 las señales de los niveles 1 a 4 iban por tiempo: se reprograman por árboles.
            if (version < 2 && level < AbuseTracker.LEVEL_FINAL) nextSignalAt = -1
            // Hasta la versión 2 se guardaba por árboles; sin picadas, árboles y acciones son lo mismo.
            val dueKey = if (tag.contains("SignalDueAtActions")) "SignalDueAtActions" else "SignalDueAtTrees"
            signalDueAtActions = if (tag.contains(dueKey)) tag.getLong(dueKey).coerceAtLeast(-1) else -1
            lastSignal = tag.getString("LastSignal")
            lastMorsePhrase = tag.getString("LastMorsePhrase")
            lastSignWord = tag.getString("LastSignWord")
            finalSignPlacedAt = if (tag.contains("FinalSignPlacedAt")) tag.getLong("FinalSignPlacedAt").coerceIn(-1, playTicks) else -1
            endingDueAt = if (tag.contains("EndingDueAt")) tag.getLong("EndingDueAt").coerceIn(-1, playTicks) else -1
            endingStartedAt = if (tag.contains("EndingStartedAt")) tag.getLong("EndingStartedAt").coerceIn(-1, playTicks) else -1
            // Si falta (datos de antes de los mensajes), los que ya tocaban se dan por enviados:
            // nada de soltarlos todos de golpe.
            endingMessagesSent = if (tag.contains("EndingMessagesSent")) {
                tag.getInt("EndingMessagesSent").coerceIn(0, EndingMessages.COUNT)
            } else {
                EndingMessages.dueFor(this)
            }
            dioriteUseless = tag.getBoolean("DioriteUseless")
            for (item in tag.getList("TakenItems", Tag.TAG_COMPOUND.toInt())) {
                // Un ítem de un mod que ya no está se lee como vacío: se pierde (no hay nada que devolver).
                ItemStack.of(item as CompoundTag).takeUnless { it.isEmpty }?.let(takenItems::add)
            }
            returnItemsOnJoin = tag.getBoolean("ReturnItemsOnJoin")
        }
    }
}

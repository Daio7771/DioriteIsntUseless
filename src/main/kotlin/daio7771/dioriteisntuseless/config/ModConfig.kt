package daio7771.dioriteisntuseless.config

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParseException
import com.google.gson.JsonParser
import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import daio7771.dioriteisntuseless.block.DioriteStats
import net.fabricmc.loader.api.FabricLoader
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.CharacterCodingException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

/**
 * Lee, valida y guarda config/dioriteisntuseless.json, y guarda la configuración en vigor.
 *
 * Nada de lo que haya en el archivo puede tumbar el juego: cualquier problema se registra en el
 * log y se sigue con valores válidos. Las operaciones con el archivo van sincronizadas porque en
 * un solo jugador pueden coincidir la pantalla de configuración (hilo del cliente) y /diu reload
 * (hilo del servidor integrado).
 */
object ModConfig {

    private val GSON = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()
    private val BROKEN_SUFFIX_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss")

    private val path: Path = FabricLoader.getInstance().configDir.resolve("${Dioriteisntuseless.MOD_ID}.json")

    /**
     * Configuración en vigor. Se lee sin bloqueos desde cualquier hilo; como es inmutable y se
     * sustituye entera, cada lectura ve un estado coherente. Si se van a usar varios valores
     * juntos, leer el campo una vez y usar esa instancia.
     */
    @Volatile
    var current: DiuConfig = DiuConfig()
        private set

    /**
     * Durabilidad con la que se registró el hacha. No cambia hasta reiniciar, aunque cambie
     * axe.durability en el archivo.
     */
    var axeDurabilityAtStartup: Int = DiuConfig().axe.durability
        private set

    sealed interface ReloadResult {
        /**
         * @param corrections valores ajustados o sustituidos (los detalles están en el log).
         * @param restartPendingDurability la nueva axe.durability si es distinta de la que está
         *   en vigor, o null.
         */
        data class Success(val corrections: Int, val restartPendingDurability: Int?) : ReloadResult

        /** El JSON no se puede leer. No se ha tocado nada: siguen los valores anteriores. */
        data object InvalidJson : ReloadResult

        /** Error al leer el archivo (permisos, etc.). Siguen los valores anteriores. */
        data object ReadError : ReloadResult
    }

    /** Llamar al principio de onInitialize, antes de registrar el hacha. */
    @Synchronized
    fun loadAtStartup() {
        val config = try {
            loadOrRecover()
        } catch (e: Exception) {
            LOGGER.error("Unexpected error while loading {}; using the default values.", path, e)
            DiuConfig()
        }
        axeDurabilityAtStartup = config.axe.durability
        apply(config)
    }

    /** Para /diu reload. Si el archivo no se puede leer, no cambia nada. */
    @Synchronized
    fun reload(): ReloadResult = try {
        when (val file = readFile()) {
            FileState.Missing -> {
                LOGGER.info("Config file {} not found; creating it with the default values.", path)
                val config = DiuConfig()
                write(ConfigCodec.encode(config, JsonObject()))
                apply(config)
                ReloadResult.Success(0, pendingDurability(config))
            }
            is FileState.Unreadable -> {
                LOGGER.error("Could not read {}; nothing was changed and the previous values are still in use.", path, file.error)
                ReloadResult.ReadError
            }
            is FileState.Corrupt -> {
                LOGGER.error("Config file {} is not valid JSON ({}). Nothing was changed and the previous values are " +
                    "still in use; fix the file and run /diu reload again.", path, file.detail)
                ReloadResult.InvalidJson
            }
            is FileState.Parsed -> {
                val decoded = decode(file.root)
                apply(decoded.config)
                ReloadResult.Success(decoded.corrections, pendingDurability(decoded.config))
            }
        }
    } catch (e: Exception) {
        LOGGER.error("Unexpected error while reloading {}; the previous values are still in use.", path, e)
        ReloadResult.ReadError
    }

    /**
     * Guarda [config] (desde la pantalla de configuración) y lo aplica. Conserva las claves que no
     * son del mod. Si el archivo estaba roto, se aparta como .broken para no perder lo que hubiera.
     */
    @Synchronized
    fun save(config: DiuConfig) {
        try {
            val root = when (val file = readFile()) {
                is FileState.Parsed -> file.root
                is FileState.Corrupt -> {
                    LOGGER.error("Config file {} is not valid JSON ({}); replacing it with the values from the config screen.",
                        path, file.detail)
                    // Si no se puede apartar, no se escribe encima: los cambios valen solo para esta sesión.
                    if (moveAsideBroken()) JsonObject() else null
                }
                else -> JsonObject()
            }
            if (root != null) write(ConfigCodec.encode(config, root))
        } catch (e: Exception) {
            LOGGER.error("Unexpected error while saving {}.", path, e)
        }
        apply(config)
    }

    private fun apply(config: DiuConfig) {
        current = config
        DioriteStats.applyLocal(config.diorite)
    }

    private fun pendingDurability(config: DiuConfig): Int? =
        config.axe.durability.takeIf { it != axeDurabilityAtStartup }

    private fun loadOrRecover(): DiuConfig = when (val file = readFile()) {
        FileState.Missing -> {
            LOGGER.info("Config file {} not found; creating it with the default values.", path)
            DiuConfig().also { write(ConfigCodec.encode(it, JsonObject())) }
        }
        is FileState.Unreadable -> {
            // No se sobrescribe: el archivo puede estar bien y ser un problema de permisos.
            LOGGER.error("Could not read {}; using the default values. The file was left untouched.", path, file.error)
            DiuConfig()
        }
        is FileState.Corrupt -> {
            LOGGER.error("Config file {} is not valid JSON ({}).", path, file.detail)
            if (moveAsideBroken()) {
                write(ConfigCodec.encode(DiuConfig(), JsonObject()))
                LOGGER.error("A new {} with the default values has been created.", path.fileName)
            } else {
                LOGGER.error("Using the default values; the broken file was left in place.")
            }
            DiuConfig()
        }
        is FileState.Parsed -> decode(file.root).config
    }

    /** Valida el árbol y, si le faltaban claves, reescribe el archivo con ellas. */
    private fun decode(root: JsonObject): ConfigCodec.Decoded {
        val decoded = ConfigCodec.decode(root)
        if (decoded.corrections > 0) {
            LOGGER.warn("Config: {} invalid value(s) corrected; see the warnings above.", decoded.corrections)
        }
        if (decoded.treeChanged) write(root)
        return decoded
    }

    private sealed interface FileState {
        data object Missing : FileState
        class Parsed(val root: JsonObject) : FileState
        class Corrupt(val detail: String) : FileState
        class Unreadable(val error: IOException) : FileState
    }

    private fun readFile(): FileState {
        if (Files.notExists(path)) return FileState.Missing
        val text = try {
            Files.readString(path)
        } catch (e: CharacterCodingException) {
            return FileState.Corrupt("not valid UTF-8 text")
        } catch (e: IOException) {
            return FileState.Unreadable(e)
        }
        val root = try {
            // Modo permisivo de Gson: admite comentarios y claves sin comillas.
            JsonParser.parseString(text)
        } catch (e: JsonParseException) {
            return FileState.Corrupt(rootMessage(e))
        }
        if (!root.isJsonObject) return FileState.Corrupt("the file must contain a JSON object { ... }")
        return FileState.Parsed(root.asJsonObject)
    }

    /** El mensaje útil de Gson ("Unterminated object at line 5 column 3 path $.diorite"). */
    private fun rootMessage(e: Throwable): String {
        val root = generateSequence(e) { it.cause }.last()
        // Este consejo es para programadores; al jugador le basta con saber dónde está el error.
        return (root.message ?: root.toString())
            .replace("Use JsonReader.setLenient(true) to accept malformed JSON", "Malformed JSON")
    }

    /**
     * Renombra el archivo roto a dioriteisntuseless.json.broken-<fecha> para no perderlo.
     * Devuelve false si no se ha podido (y entonces no hay que escribir encima).
     */
    private fun moveAsideBroken(): Boolean {
        val base = "${path.fileName}.broken-${LocalDateTime.now().format(BROKEN_SUFFIX_FORMAT)}"
        var target = path.resolveSibling(base)
        var n = 2
        while (Files.exists(target)) target = path.resolveSibling("$base-${n++}")
        return try {
            Files.move(path, target)
            LOGGER.error("The broken config file has been renamed to {}.", target.fileName)
            true
        } catch (e: IOException) {
            LOGGER.error("Could not rename the broken config file {}.", path, e)
            false
        }
    }

    /**
     * Escribe primero un archivo temporal y luego lo pone en el sitio del bueno, para que un
     * cierre a mitad de escritura nunca deje un JSON a medias. Si falla, solo lo registra: la
     * configuración en memoria sigue siendo válida.
     */
    private fun write(root: JsonObject) {
        var temp: Path? = null
        try {
            Files.createDirectories(path.parent)
            temp = Files.createTempFile(path.parent, "${path.fileName}.", ".tmp")
            val bytes = ByteBuffer.wrap((GSON.toJson(root) + "\n").toByteArray(Charsets.UTF_8))
            FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                while (bytes.hasRemaining()) channel.write(bytes)
                channel.force(true)
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING)
            }
            temp = null
        } catch (e: IOException) {
            LOGGER.error("Could not write {}; the values in memory are still in use.", path, e)
        } finally {
            if (temp != null) runCatching { Files.deleteIfExists(temp) }
        }
    }
}

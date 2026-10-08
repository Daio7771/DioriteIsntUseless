package daio7771.dioriteisntuseless.abuse.morse

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.google.gson.JsonPrimitive
import daio7771.dioriteisntuseless.Dioriteisntuseless
import daio7771.dioriteisntuseless.Dioriteisntuseless.Companion.LOGGER
import net.fabricmc.fabric.api.resource.ResourceManagerHelper
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener
import net.minecraft.server.packs.PackType
import net.minecraft.server.packs.resources.ResourceManager

/**
 * Frases de los mensajes en Morse, en data/dioriteisntuseless/abuse/morse_phrases.json:
 *
 * ```
 * { "replace": false, "phrases": ["DELETE THIS MOD", "..."] }
 * ```
 *
 * Como los tags: un datapack puede añadir frases, o sustituirlas todas con "replace": true.
 * Se recargan con /reload. Un archivo roto se ignora (con aviso en el log), nunca tumba el juego.
 */
object MorsePhrases {

    class Phrase(val text: String, val code: String)

    private val FILE = Dioriteisntuseless.id("abuse/morse_phrases.json")

    /** Frases ya traducidas a Morse. Se sustituye entera al recargar. */
    @Volatile
    var phrases: List<Phrase> = emptyList()
        private set

    fun init() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(object : SimpleSynchronousResourceReloadListener {
            override fun getFabricId() = Dioriteisntuseless.id("morse_phrases")
            override fun onResourceManagerReload(manager: ResourceManager) {
                // Un fallo aquí haría fallar /reload o la carga del mundo: sin frases antes que eso.
                try {
                    reload(manager)
                } catch (e: Exception) {
                    LOGGER.error("Could not load the Morse phrases; there will be no Morse messages.", e)
                    phrases = emptyList()
                }
            }
        })
    }

    private fun reload(manager: ResourceManager) {
        val texts = LinkedHashSet<String>()
        for (resource in manager.getResourceStack(FILE)) {
            try {
                val root = resource.openAsReader().use { JsonParser.parseReader(it) } as? JsonObject
                    ?: throw IllegalArgumentException("the file must contain a JSON object { ... }")
                if ((root.get("replace") as? JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean == true) texts.clear()
                val list = root.getAsJsonArray("phrases") ?: throw IllegalArgumentException("missing \"phrases\" list")
                for (element in list) {
                    val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.asString?.trim()
                    if (text.isNullOrEmpty()) {
                        LOGGER.warn("{} in pack '{}': ignoring {}, it is not a text.", FILE, resource.sourcePackId(), element)
                    } else {
                        texts += text
                    }
                }
            } catch (e: Exception) {
                LOGGER.error("Could not read {} from pack '{}'; ignoring it.", FILE, resource.sourcePackId(), e)
            }
        }
        phrases = texts.mapNotNull(::encode)
    }

    private fun encode(text: String): Phrase? {
        val encoded = MorseCode.encode(text)
        if (encoded.unsupported.isNotEmpty()) {
            LOGGER.warn("{}: \"{}\" has characters that do not exist in Morse code ({}); they are left out.",
                FILE, text, encoded.unsupported.joinToString(" "))
        }
        if (encoded.code.isEmpty()) return null
        return Phrase(text, encoded.code)
    }
}

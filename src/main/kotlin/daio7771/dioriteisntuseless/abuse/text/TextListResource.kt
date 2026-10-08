package daio7771.dioriteisntuseless.abuse.text

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
 * Lista de textos de un archivo de datos del mod, por ejemplo:
 *
 * ```
 * { "replace": false, "<key>": ["texto", "..."] }
 * ```
 *
 * Como los tags: un datapack puede añadir textos, o sustituirlos todos con "replace": true.
 * Se recarga con /reload. Un archivo roto se ignora (con aviso en el log): nunca tumba el juego.
 *
 * @param path ruta dentro de data/dioriteisntuseless/, por ejemplo "abuse/sign_words.json".
 * @param onLoad recibe los textos (sin repetir, en orden) cada vez que se cargan.
 */
class TextListResource(path: String, private val key: String, private val onLoad: (List<String>) -> Unit) {

    private val file = Dioriteisntuseless.id(path)

    fun register() {
        ResourceManagerHelper.get(PackType.SERVER_DATA).registerReloadListener(object : SimpleSynchronousResourceReloadListener {
            override fun getFabricId() = Dioriteisntuseless.id("text_list/${file.path}")
            override fun onResourceManagerReload(manager: ResourceManager) {
                // Un fallo aquí haría fallar /reload o la carga del mundo: sin textos antes que eso.
                try {
                    onLoad(read(manager))
                } catch (e: Exception) {
                    LOGGER.error("Could not load {}; it will be empty.", file, e)
                    onLoad(emptyList())
                }
            }
        })
    }

    private fun read(manager: ResourceManager): List<String> {
        val texts = LinkedHashSet<String>()
        for (resource in manager.getResourceStack(file)) {
            try {
                val root = resource.openAsReader().use { JsonParser.parseReader(it) } as? JsonObject
                    ?: throw IllegalArgumentException("the file must contain a JSON object { ... }")
                if ((root.get("replace") as? JsonPrimitive)?.takeIf { it.isBoolean }?.asBoolean == true) texts.clear()
                val list = root.getAsJsonArray(key) ?: throw IllegalArgumentException("missing \"$key\" list")
                for (element in list) {
                    val text = (element as? JsonPrimitive)?.takeIf { it.isString }?.asString?.trim()
                    if (text.isNullOrEmpty()) {
                        LOGGER.warn("{} in pack '{}': ignoring {}, it is not a text.", file, resource.sourcePackId(), element)
                    } else {
                        texts += text
                    }
                }
            } catch (e: Exception) {
                LOGGER.error("Could not read {} from pack '{}'; ignoring it.", file, resource.sourcePackId(), e)
            }
        }
        return texts.toList()
    }

    override fun toString(): String = file.toString()
}

package daio7771.dioriteisntuseless.client.config

import com.terraformersmc.modmenu.api.ConfigScreenFactory
import com.terraformersmc.modmenu.api.ModMenuApi
import net.fabricmc.loader.api.FabricLoader

/**
 * Entrypoint "modmenu": Mod Menu solo carga esta clase si está instalado. La pantalla necesita
 * además Cloth Config; sin él, Mod Menu no muestra botón de configuración y el JSON se edita a mano.
 */
class ModMenuIntegration : ModMenuApi {

    override fun getModConfigScreenFactory(): ConfigScreenFactory<*> {
        if (!FabricLoader.getInstance().isModLoaded("cloth-config")) return super.getModConfigScreenFactory()
        // DiuConfigScreen (y con ella Cloth Config) no se carga hasta abrir la pantalla.
        return ConfigScreenFactory { parent -> DiuConfigScreen.create(parent) }
    }
}

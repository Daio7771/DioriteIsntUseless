package daio7771.dioriteisntuseless.client

import daio7771.dioriteisntuseless.client.abuse.ClientAbuseState
import daio7771.dioriteisntuseless.client.abuse.NonsenseNames
import net.fabricmc.api.ClientModInitializer

class DioriteisntuselessClient : ClientModInitializer {

    override fun onInitializeClient() {
        ClientConfigSync.init()
        NonsenseNames.init()
        ClientAbuseState.init()
    }
}

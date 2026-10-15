package daio7771.dioriteisntuseless.client

import daio7771.dioriteisntuseless.client.abuse.AbuseAmbience
import daio7771.dioriteisntuseless.client.abuse.ClientAbuseState
import daio7771.dioriteisntuseless.client.abuse.ClientFreeze
import daio7771.dioriteisntuseless.client.abuse.CreditsGate
import daio7771.dioriteisntuseless.client.abuse.NonsenseNames
import daio7771.dioriteisntuseless.client.entity.MinerBubbles
import daio7771.dioriteisntuseless.client.entity.VisitorRenderer
import net.fabricmc.api.ClientModInitializer

class DioriteisntuselessClient : ClientModInitializer {

    override fun onInitializeClient() {
        ClientConfigSync.init()
        NonsenseNames.init()
        ClientAbuseState.init()
        ClientFreeze.init()
        CreditsGate.init()
        AbuseAmbience.init()
        VisitorRenderer.init()
        MinerBubbles.init()
    }
}

package com.tupinicraft.client;

import com.tupinicraft.client.skilltree.ClientSkillCache;
import com.tupinicraft.client.skilltree.SkillTreeScreen;
import com.tupinicraft.network.SkillDefinitionsPayload;
import com.tupinicraft.network.SkillNoticePayload;
import com.tupinicraft.network.SkillStatePayload;
import net.minecraft.client.Minecraft;

/** Somente os pacotes da UI de skill trees. */
public final class ClientPayloadHandler {
    private ClientPayloadHandler() {}
    public static void handleSkillDefinitions(SkillDefinitionsPayload payload) {
        ClientSkillCache.setDefinitions(payload);
        refresh();
    }
    public static void handleSkillState(SkillStatePayload payload) {
        ClientSkillCache.setState(payload);
        refresh();
    }
    public static void handleSkillNotice(SkillNoticePayload payload) {
        SkillNoticeToast.show(payload);
    }
    private static void refresh() {
        if (Minecraft.getInstance().screen instanceof SkillTreeScreen screen) screen.refresh();
    }
}

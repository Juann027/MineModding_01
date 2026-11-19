package com.respectcraft.client.playergeo;

import com.mojang.blaze3d.platform.InputConstants;
import com.respectcraft.RespectCraft;
import com.respectcraft.animation.PlayerAnimations;
import com.respectcraft.client.animation.ClientPlayerAnimations;
import com.respectcraft.network.PlayerAnimationRequestPayload;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.util.ObfuscationReflectionHelper;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

/**
 * Decide, por jogador e por quadro, se o desenho e o vanilla ou o rig GeckoLib, e cuida das teclas de teste.
 *
 * <p>Jogo normal: jogador vanilla + camadas. Enquanto o servidor manda uma animacao (ou com o modo de teste
 * F8 ligado): o corpo vanilla deixa de ser desenhado e o {@link PlayerRigLayer} desenha o rig no lugar.
 * Tudo aqui e cache visual do cliente, limpo ao sair do servidor.
 */
public final class PlayerRigController {
    private static final KeyMapping TOGGLE = new KeyMapping("key.respectcraft.geo_toggle",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, "key.categories.respectcraft");
    private static final KeyMapping WAVE = new KeyMapping("key.respectcraft.geo_wave",
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F9, "key.categories.respectcraft");

    private static final Map<UUID, PlayerRigAnimatable> RIGS = new HashMap<>();
    private static boolean debugRig;

    // Quadro em andamento: jogador cujo corpo vanilla foi escondido e o que precisa ser restaurado depois.
    private static AbstractClientPlayer framePlayer;
    private static final List<ModelPart> hiddenParts = new ArrayList<>();
    private static final List<PartPose> savedPoses = new ArrayList<>();

    private PlayerRigController() {}

    public static void registerKeys(RegisterKeyMappingsEvent event) {
        event.register(TOGGLE);
        event.register(WAVE);
    }

    /** Coloca o rig como PRIMEIRA camada, para que as demais (armadura, itens, capa...) recebam a pose dele. */
    public static void addLayer(PlayerRenderer renderer) {
        PlayerRigLayer layer = new PlayerRigLayer(renderer);
        try {
            List<RenderLayer<?, ?>> layers = ObfuscationReflectionHelper.getPrivateValue(
                    LivingEntityRenderer.class, renderer, "layers");
            layers.add(0, layer);
        } catch (RuntimeException e) {
            RespectCraft.LOGGER.error("Não foi possível colocar o rig como primeira camada do jogador; "
                    + "armadura e itens não acompanharão as animações", e);
            renderer.addLayer(layer);
        }
    }

    /** Modo de teste (F8): todos os jogadores deste cliente sao desenhados com o rig o tempo todo. */
    public static boolean debugRig() { return debugRig; }

    static ClientPlayerAnimations.Clip idleClipOrNull() {
        return ClientPlayerAnimations.clip(PlayerAnimations.IDLE.id());
    }

    public static PlayerRigAnimatable rig(AbstractClientPlayer player) {
        PlayerRigAnimatable rig = RIGS.computeIfAbsent(player.getUUID(), PlayerRigAnimatable::new);
        rig.setPlayer(player);
        return rig;
    }

    static boolean isRigFrame(AbstractClientPlayer player) {
        return framePlayer == player;
    }

    private static boolean shouldUseRig(AbstractClientPlayer player) {
        // Invisivel/espectador/morrendo: o vanilla tem regras proprias (translucidez, so a cabeca, tombar).
        if (player.isInvisible() || player.isSpectator() || !player.isAlive()) return false;
        if (!ClientPlayerAnimations.rigAvailable()) return false;
        if (debugRig) return true;
        PlayerRigAnimatable rig = RIGS.get(player.getUUID());
        return ClientPlayerAnimations.active(player.getUUID()) != null
                ? ClientPlayerAnimations.clip(ClientPlayerAnimations.active(player.getUUID()).animation()) != null
                : rig != null && rig.wantsRig();
    }

    public static void onRenderPre(RenderPlayerEvent.Pre event) {
        endFrame(); // seguranca: se outro mod cancelou o quadro anterior, o Post nao veio
        if (!(event.getEntity() instanceof AbstractClientPlayer player) || !shouldUseRig(player)) return;
        PlayerModel<AbstractClientPlayer> model = event.getRenderer().getModel();
        framePlayer = player;
        for (ModelPart part : List.of(model.head, model.hat, model.body, model.jacket, model.rightArm,
                model.rightSleeve, model.leftArm, model.leftSleeve, model.rightLeg, model.rightPants,
                model.leftLeg, model.leftPants)) {
            hiddenParts.add(part);
            savedPoses.add(part.storePose());
            part.skipDraw = true;
        }
    }

    public static void onRenderPost(RenderPlayerEvent.Post event) {
        endFrame();
    }

    /** Devolve o modelo vanilla como estava: nada fica escondido nem preso na pose da animacao. */
    private static void endFrame() {
        for (int i = 0; i < hiddenParts.size(); i++) {
            ModelPart part = hiddenParts.get(i);
            part.skipDraw = false;
            part.loadPose(savedPoses.get(i));
        }
        hiddenParts.clear();
        savedPoses.clear();
        framePlayer = null;
    }

    public static void onTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        endFrame();
        while (TOGGLE.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            debugRig = !debugRig;
            mc.player.displayClientMessage(Component.translatable(
                    debugRig ? "message.respectcraft.geo_debug_on" : "message.respectcraft.geo_debug_off"), true);
        }
        while (WAVE.consumeClick()) {
            if (mc.player == null || mc.screen != null) continue;
            // So a intencao: o servidor valida e avisa todos que estao vendo este jogador.
            PacketDistributor.sendToServer(new PlayerAnimationRequestPayload(PlayerAnimations.WAVE.id(), false));
        }
        if (mc.level != null && mc.level.getGameTime() % 100 == 0) {
            RIGS.keySet().removeIf(uuid -> {
                Player player = mc.level.getPlayerByUUID(uuid);
                return player == null;
            });
        }
    }

    public static void clear() {
        endFrame();
        debugRig = false;
        RIGS.clear();
    }
}

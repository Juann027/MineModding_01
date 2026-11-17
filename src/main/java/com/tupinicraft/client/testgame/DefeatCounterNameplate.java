package com.tupinicraft.client.testgame;

import com.mojang.blaze3d.vertex.PoseStack;
import com.tupinicraft.TupiniCraft;
import com.tupinicraft.network.DefeatCountClientCache;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Team;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.RenderNameTagEvent;
import org.joml.Matrix4f;

/**
 * Linha "berinjela + gotas + número" uma linha ACIMA do nick vanilla. Não altera o nome, equipes nem o scoreboard.
 *
 * <p>Só aparece quando o próprio nick apareceria: mesmas regras de {@code LivingEntityRenderer.shouldShowName}
 * (distância, agachado, invisibilidade, visibilidade de equipe, F1, não na própria câmera) e do
 * {@code RenderNameTagEvent} (outro mod que esconda o nick esconde o contador). Posição, escala, billboard e as duas
 * passadas (através de paredes translúcida + normal) copiam {@code EntityRenderer.renderNameTag} do 1.21.1, por isso
 * acompanha o jogador com ou sem o rig GeckoLib (o rig é só uma camada do {@code PlayerRenderer}).
 *
 * <p>Os ícones são glifos de uma fonte bitmap própria ({@code tupinicraft:defeat_icons}, U+E000 berinjela e
 * U+E001 gotas), não emoji do sistema; a largura do conjunto vem de {@code Font.width}, então fica centralizado de 0 a
 * qualquer número de dígitos.
 */
public final class DefeatCounterNameplate {
    private static final float SCALE = 0.025F;
    /** Uma linha de texto (9) + 1 pixel de folga acima do nick. */
    private static final float LINE_ABOVE = -10.0F;
    private static final Style ICON_STYLE = Style.EMPTY
            .withFont(TupiniCraft.id("defeat_icons"))
            .withColor(TextColor.fromRgb(0xFFFFFF));
    private static final Component ICONS = Component.literal("").withStyle(ICON_STYLE);
    /** Lilás claro: tema da berinjela, legível sobre o fundo escuro do nameplate. */
    private static final int NUMBER_RGB = 0xE3B5FF;
    private static final int FLASH_RGB = 0xFFFFFF;

    private DefeatCounterNameplate() {}

    public static void render(RenderNameTagEvent event) {
        if (!(event.getEntity() instanceof AbstractClientPlayer player)) return;
        if (!event.canRender().isTrue() && !(event.canRender().isDefault() && vanillaShowsName(player))) return;

        Minecraft mc = Minecraft.getInstance();
        double distSq = mc.getEntityRenderDispatcher().distanceToSqr(player);
        if (!ClientHooks.isNameplateInRenderDistance(player, distSq)) return;
        Vec3 attach = player.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0,
                player.getViewYRot(event.getPartialTick()));
        if (attach == null) return;

        long now = System.currentTimeMillis();
        float flash = DefeatCountClientCache.flashProgress(player.getUUID(), now); // 1 = acabou de subir, 0 = nada
        int rgb = lerpRgb(NUMBER_RGB, FLASH_RGB, flash);
        MutableComponent line = Component.empty().append(ICONS).append(Component.literal(" "
                + DefeatCountClientCache.get(player.getUUID())).withStyle(Style.EMPTY.withColor(TextColor.fromRgb(rgb))));

        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(attach.x, attach.y + 0.5, attach.z);
        // PlayerRenderer sobe o nick uma linha quando há objetivo BELOW_NAME (até 10 blocos).
        if (distSq < 100.0 && player.getScoreboard().getDisplayObjective(DisplaySlot.BELOW_NAME) != null) {
            pose.translate(0.0F, 9.0F * 1.15F * SCALE, 0.0F);
        }
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        pose.scale(SCALE, -SCALE, SCALE);
        if (flash > 0) {
            // Pequeno "pulo" ao aumentar: escala em torno do centro da linha.
            float s = 1.0F + 0.3F * flash;
            pose.translate(0.0F, LINE_ABOVE + 4.0F, 0.0F);
            pose.scale(s, s, 1.0F);
            pose.translate(0.0F, -(LINE_ABOVE + 4.0F), 0.0F);
        }
        Matrix4f matrix = pose.last().pose();
        Font font = mc.font;
        float x = -font.width(line) / 2.0F;
        boolean seeThrough = !player.isDiscrete();
        int background = (int) (mc.options.getBackgroundOpacity(0.25F) * 255.0F) << 24;
        font.drawInBatch(line, x, LINE_ABOVE, 0x20FFFFFF, false, matrix, event.getMultiBufferSource(),
                seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, background, event.getPackedLight());
        if (seeThrough) {
            font.drawInBatch(line, x, LINE_ABOVE, 0xFFFFFFFF, false, matrix, event.getMultiBufferSource(),
                    Font.DisplayMode.NORMAL, 0, event.getPackedLight());
        }
        pose.popPose();
    }

    /** Cópia de {@code LivingEntityRenderer.shouldShowName} (protegido) para jogadores. */
    private static boolean vanillaShowsName(AbstractClientPlayer player) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer local = mc.player;
        if (local == null) return false;
        double distSq = mc.getEntityRenderDispatcher().distanceToSqr(player);
        float max = player.isDiscrete() ? 32.0F : 64.0F;
        if (distSq >= (double) (max * max)) return false;
        boolean visible = !player.isInvisibleTo(local);
        if (player != local) {
            Team team = player.getTeam();
            Team own = local.getTeam();
            if (team != null) {
                return switch (team.getNameTagVisibility()) {
                    case ALWAYS -> visible;
                    case NEVER -> false;
                    case HIDE_FOR_OTHER_TEAMS -> own == null ? visible
                            : team.isAlliedTo(own) && (team.canSeeFriendlyInvisibles() || visible);
                    case HIDE_FOR_OWN_TEAM -> own == null ? visible : !team.isAlliedTo(own) && visible;
                };
            }
        }
        return Minecraft.renderNames() && player != mc.getCameraEntity() && visible && !player.isVehicle();
    }

    private static int lerpRgb(int from, int to, float t) {
        if (t <= 0) return from;
        int r = (int) (((from >> 16) & 255) + (((to >> 16) & 255) - ((from >> 16) & 255)) * t);
        int g = (int) (((from >> 8) & 255) + (((to >> 8) & 255) - ((from >> 8) & 255)) * t);
        int b = (int) ((from & 255) + ((to & 255) - (from & 255)) * t);
        return (r << 16) | (g << 8) | b;
    }
}

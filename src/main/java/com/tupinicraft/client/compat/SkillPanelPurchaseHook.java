package com.tupinicraft.client.compat;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.network.SkillPurchaseRequestPayload;
import cn.skilllib.client.ClientSkillCache;
import cn.skilllib.client.SkillScreen;
import cn.skilllib.data.PlayerSkillData;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * Compra pelo próprio painel do SkillLib (tecla K): clicar numa skill BLOQUEADA do TupiniCraft pede a compra
 * ao servidor. Skills já desbloqueadas seguem o comportamento normal do SkillLib (liga/desliga).
 * <p>
 * O SkillLib 1.4.0 não tem API para isso: na tela, um clique em skill bloqueada é simplesmente ignorado.
 * Lemos a skill sob o cursor no campo {@code hoveredSkill} da {@link SkillScreen} (preenchido a cada frame)
 * por reflexão, sem copiar nem alterar classes do SkillLib. Se uma versão futura mudar esse detalhe, o
 * gancho se desliga sozinho (com aviso no log) e a compra continua possível por {@code /brasil skills comprar}.
 * O cliente só envia a intenção; quem decide, cobra e desbloqueia é o servidor.
 */
public final class SkillPanelPurchaseHook {
    private static Field hoveredSkillField;
    private static Method skillIdAccessor;
    private static boolean broken;

    private SkillPanelPurchaseHook() {
    }

    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (broken || event.getButton() != GLFW.GLFW_MOUSE_BUTTON_LEFT || !(event.getScreen() instanceof SkillScreen screen)) {
            return;
        }
        ResourceLocation skillId = hoveredSkill(screen);
        if (skillId == null || !TupiniCraft.MOD_ID.equals(skillId.getNamespace())) {
            return;
        }
        PlayerSkillData data = ClientSkillCache.getSkillData();
        if (data != null && data.isUnlocked(skillId)) {
            return; // já comprada: o SkillLib liga/desliga normalmente
        }
        PacketDistributor.sendToServer(new SkillPurchaseRequestPayload(skillId));
        event.setCanceled(true);
    }

    private static ResourceLocation hoveredSkill(SkillScreen screen) {
        try {
            if (hoveredSkillField == null) {
                Field field = SkillScreen.class.getDeclaredField("hoveredSkill");
                field.setAccessible(true);
                Method accessor = field.getType().getDeclaredMethod("skillId");
                accessor.setAccessible(true);
                hoveredSkillField = field;
                skillIdAccessor = accessor;
            }
            Object hovered = hoveredSkillField.get(screen);
            return hovered == null ? null : (ResourceLocation) skillIdAccessor.invoke(hovered);
        } catch (ReflectiveOperationException | RuntimeException e) {
            broken = true;
            TupiniCraft.LOGGER.warn("Compra pelo painel do SkillLib desativada (versão do SkillLib incompatível); "
                    + "use /brasil skills comprar.", e);
            return null;
        }
    }
}

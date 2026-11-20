package com.respectcraft.client.holo;

import com.mojang.blaze3d.platform.InputConstants;
import com.respectcraft.client.character.CharacterCreatorKeybinds;
import com.respectcraft.ui.HoloInputArbiter;
import com.respectcraft.ui.HoloInputArbiter.Owner;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Roteador de entrada do menu holografico no mundo (Etapa 4.8.4-B). Os paineis continuam sem {@code Screen}: WASD,
 * pulo e mouse-look nunca sao capturados; so o clique, a roda e as acoes do vanilla <b>com a mira numa placa</b>.
 *
 * <p><b>Ganchos conferidos no NeoForge 21.1.265</b> (fontes do {@code MouseHandler}/{@code Minecraft} do jar):
 * <ul>
 *   <li>{@code InputEvent.MouseButton.Pre}: postado em {@code MouseHandler.onPress} antes do vanilla (grab do mouse,
 *   tela, {@code KeyMapping.set/click}). Cancelado, o {@code KeyMapping} de ataque/uso nem desce: sem ataque, sem
 *   mineracao continua ({@code continueAttack} so roda com {@code keyAttack.isDown()}), sem balanco da mao, sem uso.
 *   O dono e decidido no aperto e o soltar vai para o mesmo dono ({@link HoloInputArbiter}).</li>
 *   <li>{@code InputEvent.MouseScrollingEvent}: postado em {@code MouseHandler.onScroll} sem tela, antes de
 *   {@code Inventory.swapPaint}; cancelado so com a mira numa placa (a hotbar nao troca).</li>
 *   <li>{@code InputEvent.InteractionKeyMappingTriggered}: postado por {@code startAttack}, {@code continueAttack}
 *   (mineracao segurada), {@code startUseItem} e {@code pickBlock}. Rede de seguranca: cancela com
 *   {@code setSwingHand(false)} enquanto o holograma e dono, e decide o dono quando ataque/uso/pegar bloco estao
 *   presos a uma tecla do teclado (o evento de botao do mouse nao existe nesse caso).</li>
 * </ul>
 *
 * <p><b>Modo cursor</b> (opcional, tecla configuravel, Alt esquerdo por padrao, so enquanto segurada): solta o mouse
 * para apontar com o cursor; a camera para de girar e WASD continua. Soltar a tecla prende o mouse de novo (centro da
 * janela, como ao fechar uma tela). Enquanto ativo, todo clique e roda sao do holograma (nada vai para o mundo).
 *
 * <p>Estado so deste cliente; limpo ao fechar os paineis, abrir uma tela, perder o foco e no logout.
 */
public final class HoloWorldInput {
    private static final HoloInputArbiter ARBITER = new HoloInputArbiter();
    private static boolean cursorMode;

    private HoloWorldInput() {}

    /** Modo cursor ativo (mouse solto, raio pelo cursor). */
    static boolean cursorMode() { return cursorMode; }

    // ================================================================ eventos

    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        int button = event.getButton();
        if (button < 0 || button >= HoloInputArbiter.MOUSE_BUTTONS) return;
        if (event.getAction() == InputConstants.PRESS) {
            HoloWorld w = HoloWorld.current();
            int action = actionOf(button);
            boolean holo = w != null && w.acceptsInput() && (cursorMode || action >= 0 && w.ownsAim());
            if (ARBITER.press(button, holo) == Owner.HOLO) {
                event.setCanceled(true);
                if (w != null) w.press(button, action == HoloInputArbiter.ATTACK || action == HoloInputArbiter.USE);
            }
        } else if (event.getAction() == InputConstants.RELEASE) {
            if (ARBITER.release(button) == Owner.HOLO) {
                event.setCanceled(true);
                HoloWorld w = HoloWorld.current();
                if (w != null) w.release(button);
            }
        }
    }

    static void onScroll(InputEvent.MouseScrollingEvent event) {
        HoloWorld w = HoloWorld.current();
        if (w == null || !w.acceptsInput() || !(cursorMode || w.ownsAim())) return;
        event.setCanceled(true);
        w.scroll(event.getScrollDeltaY());
    }

    static void onInteraction(InputEvent.InteractionKeyMappingTriggered event) {
        int action = event.isAttack() ? HoloInputArbiter.ATTACK : event.isUseItem() ? HoloInputArbiter.USE
                : event.isPickBlock() ? HoloInputArbiter.PICK : -1;
        if (action < 0) return;
        InputConstants.Key key = event.getKeyMapping().getKey();
        Owner owner;
        if (key.getType() == InputConstants.Type.MOUSE) {
            // o botao do mouse ja teve dono no aperto; o mundo segue normal quando o dono e ele
            owner = ARBITER.owner(key.getValue());
        } else {
            int source = HoloInputArbiter.keySource(action);
            owner = ARBITER.owner(source);
            if (owner == Owner.NONE) {
                HoloWorld w = HoloWorld.current();
                boolean holo = w != null && w.acceptsInput() && (cursorMode || w.ownsAim());
                owner = ARBITER.press(source, holo);
                if (owner == Owner.HOLO) w.press(source, action != HoloInputArbiter.PICK);
            }
        }
        if (owner == Owner.HOLO) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    static void onClientTick(ClientTickEvent.Post event) { poll(); }

    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ARBITER.clear();
        cursorMode = false;
    }

    // ================================================================ estado

    /**
     * A cada quadro (paineis abertos) e a cada tick: acoes do teclado soltas, botoes do mouse que subiram fora da
     * janela (soltar perdido com o foco) e o modo cursor.
     */
    static void poll() {
        Minecraft mc = Minecraft.getInstance();
        HoloWorld w = HoloWorld.current();
        for (int action = HoloInputArbiter.ATTACK; action <= HoloInputArbiter.PICK; action++) {
            int source = HoloInputArbiter.keySource(action);
            if (ARBITER.owner(source) != Owner.NONE && !mapping(mc, action).isDown()
                    && ARBITER.release(source) == Owner.HOLO && w != null) w.release(source);
        }
        long window = mc.getWindow().getWindow();
        for (int b = 0; b < HoloInputArbiter.MOUSE_BUTTONS; b++) {
            if (ARBITER.owner(b) == Owner.HOLO && GLFW.glfwGetMouseButton(window, b) != GLFW.GLFW_PRESS) {
                ARBITER.release(b);
                if (w != null) w.release(b);
            }
        }
        boolean want = w != null && w.acceptsInput() && !w.menuClosing() && mc.isWindowActive()
                && physicallyDown(mc, CharacterCreatorKeybinds.cursorKey());
        if (want && !cursorMode) {
            if (w != null) w.cancelDrag();
            cursorMode = true;
            mc.mouseHandler.releaseMouse();
        } else if (!want && cursorMode) {
            exitCursor(true);
        }
    }

    /** Paineis fecharam: sai do modo cursor e devolve o mouse-look. */
    static void closed() { exitCursor(true); }

    /** Uma tela vai abrir (ela mesma solta o mouse e o prende de novo ao fechar): sai do modo cursor sem prender. */
    static void screenOpened() { exitCursor(false); }

    private static void exitCursor(boolean regrab) {
        if (!cursorMode) return;
        cursorMode = false;
        HoloWorld w = HoloWorld.current();
        if (w != null) w.cancelDrag();
        Minecraft mc = Minecraft.getInstance();
        if (regrab && mc.screen == null && mc.level != null && mc.isWindowActive()) mc.mouseHandler.grabMouse();
    }

    /** Acao do vanilla presa a este botao do mouse ({@link HoloInputArbiter#ATTACK}/USE/PICK) ou -1. */
    private static int actionOf(int button) {
        var o = Minecraft.getInstance().options;
        if (o.keyAttack.matchesMouse(button)) return HoloInputArbiter.ATTACK;
        if (o.keyUse.matchesMouse(button)) return HoloInputArbiter.USE;
        if (o.keyPickItem.matchesMouse(button)) return HoloInputArbiter.PICK;
        return -1;
    }

    private static KeyMapping mapping(Minecraft mc, int action) {
        return action == HoloInputArbiter.ATTACK ? mc.options.keyAttack : action == HoloInputArbiter.USE ? mc.options.keyUse
                : mc.options.keyPickItem;
    }

    /**
     * Tecla segurada de verdade: o {@code KeyMapping} e o estado fisico (a tecla solta com a janela sem foco, ex.:
     * Alt+Tab, nunca deixa o modo cursor preso).
     */
    private static boolean physicallyDown(Minecraft mc, KeyMapping km) {
        if (!km.isDown()) return false;
        InputConstants.Key key = km.getKey();
        long window = mc.getWindow().getWindow();
        return switch (key.getType()) {
            case KEYSYM -> InputConstants.isKeyDown(window, key.getValue());
            case MOUSE -> GLFW.glfwGetMouseButton(window, key.getValue()) == GLFW.GLFW_PRESS;
            default -> true;
        };
    }
}

package com.tupinicraft.client.skilltree;

import com.daqem.uilib.api.client.gui.component.IComponent;
import com.daqem.uilib.api.client.gui.component.advancement.IAdvancement;
import com.daqem.uilib.api.client.gui.component.advancement.IAdvancementTree;
import com.daqem.uilib.client.gui.AbstractScreen;
import com.daqem.uilib.client.gui.background.Backgrounds;
import com.daqem.uilib.client.gui.component.TextComponent;
import com.daqem.uilib.client.gui.component.advancement.AdvancementComponent;
import com.daqem.uilib.client.gui.component.advancement.AdvancementTreeContainerComponent;
import com.daqem.uilib.client.gui.component.advancement.AdvancementsComponent;
import com.daqem.uilib.client.gui.component.tab.TabComponent;
import com.tupinicraft.network.SkillDefinitionsPayload;
import com.tupinicraft.network.SkillPurchaseRequestPayload;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Tela das árvores de habilidades (tecla K), feita com o componente de árvore em estilo "Progressos" do UI Lib:
 * uma aba por árvore visível, nós com moldura (comprada = dourada), linhas ligando cada skill à anterior.
 * Clicar num nó ainda não comprado pede a compra ao servidor; a resposta chega como notificação e a tela se
 * redesenha quando o estado muda. Só exibe dados do servidor ({@link ClientSkillCache}); nenhuma regra roda aqui.
 */
public final class SkillTreeScreen extends AbstractScreen {
    private AdvancementsComponent treeView;
    private AdvancementTreeContainerComponent hookedContainer;
    private final List<HookedNode> hookedNodes = new ArrayList<>();
    private String selectedTreeId;

    public SkillTreeScreen() {
        super(Component.literal("Árvores de habilidades"));
    }

    @Override
    public void startScreen() {
        setPauseScreen(false);
        setBackground(Backgrounds.getDefaultBackground(getWidth(), getHeight()));
        build();
    }

    /** Estado novo do servidor (compra, suspensão...): redesenha mantendo a aba selecionada. */
    public void refresh() {
        selectedTreeId = currentTree().map(Tree::id).orElse(selectedTreeId);
        if (treeView != null) {
            removeComponent(treeView);
        }
        treeView = null;
        hookedContainer = null;
        hookedNodes.clear();
        build();
    }

    private void build() {
        List<IAdvancementTree> trees = new ArrayList<>(buildTrees());
        if (trees.isEmpty()) {
            return;
        }
        treeView = new AdvancementsComponent(trees);
        treeView.center();
        addComponent(treeView);
        if (selectedTreeId != null) {
            findTabs(treeView).stream().filter(tab -> tab.getTabInformation() instanceof Tree tree && tree.id().equals(selectedTreeId))
                    .findFirst().ifPresent(tab -> tab.getOnClickEvent().onClick(tab, this, 0, 0, 0));
        }
    }

    @Override
    public void onTickScreen(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        hookClicks();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        hookClicks();
        super.render(graphics, mouseX, mouseY, delta);
        if (treeView == null) {
            graphics.drawCenteredString(font, Component.literal("Nenhuma árvore de habilidades disponível."),
                    width / 2, height / 2, 0xFFFFFF);
        }
    }

    /** A aba trocada recria os nós; liga o clique de cada nó à skill correspondente (mesma ordem da árvore). */
    private void hookClicks() {
        if (treeView == null) {
            return;
        }
        AdvancementTreeContainerComponent container = find(treeView, AdvancementTreeContainerComponent.class);
        Optional<Tree> tree = currentTree();
        if (container == null || container == hookedContainer || tree.isEmpty()) {
            return;
        }
        hookedContainer = container;
        hookedNodes.clear();
        // UI Lib 9.0.0: ao trocar de aba, o título novo é adicionado sem remover o anterior (os dois se sobrepõem).
        List<IComponent<?>> titles = treeView.getChildren().stream().filter(TextComponent.class::isInstance).toList();
        for (int index = 0; index < titles.size() - 1; index++) {
            treeView.removeChild(titles.get(index));
        }
        List<AdvancementComponent> components = container.getTreeComponent().getAllAdvancementComponents();
        List<Node> nodes = tree.get().preOrder();
        for (int index = 0; index < Math.min(components.size(), nodes.size()); index++) {
            Node node = nodes.get(index);
            hookedNodes.add(new HookedNode(components.get(index), node));
            components.get(index).getIconComponent().setOnClickEvent((clicked, screen, mouseX, mouseY, button) -> {
                if (!node.isObtained()) {
                    PacketDistributor.sendToServer(new SkillPurchaseRequestPayload(ResourceLocation.parse(node.id)));
                }
                return true;
            });
        }
    }

    private record HookedNode(AdvancementComponent component, Node node) {
    }

    /**
     * Dicas (tooltips) desenhadas aqui, com a dica padrão do Minecraft: no UI Lib 9.0.0 a dica dos nós sai no
     * canto da tela (não acompanha a posição do nó) e perde as cores do texto.
     */
    @Override
    public void renderTooltips(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (treeView == null) {
            return;
        }
        if (hookedContainer != null && hookedContainer.isTotalHovered(mouseX, mouseY)) {
            for (HookedNode hooked : hookedNodes) {
                if (hooked.component().getIconComponent().isTotalHovered(mouseX, mouseY)) {
                    List<Component> lines = new ArrayList<>();
                    lines.add(hooked.node().getName().copy().withStyle(hooked.node().isObtained() ? ChatFormatting.GOLD : ChatFormatting.WHITE));
                    lines.addAll(hooked.node().getDescription());
                    graphics.renderComponentTooltip(font, lines, mouseX, mouseY);
                    return;
                }
            }
        }
        for (TabComponent tab : findTabs(treeView)) {
            if (tab.isTotalHovered(mouseX, mouseY)) {
                graphics.renderTooltip(font, tab.getTabInformation().getName(), mouseX, mouseY);
                return;
            }
        }
    }

    private Optional<Tree> currentTree() {
        return treeView == null ? Optional.empty() : findTabs(treeView).stream().filter(TabComponent::isSelected)
                .map(TabComponent::getTabInformation).filter(Tree.class::isInstance).map(Tree.class::cast).findFirst();
    }

    private static List<TabComponent> findTabs(IComponent<?> root) {
        List<TabComponent> tabs = new ArrayList<>();
        collect(root, TabComponent.class, tabs);
        return tabs;
    }

    private static <C> C find(IComponent<?> root, Class<C> type) {
        List<C> found = new ArrayList<>();
        collect(root, type, found);
        return found.isEmpty() ? null : found.get(0);
    }

    private static <C> void collect(IComponent<?> component, Class<C> type, List<C> out) {
        if (type.isInstance(component)) {
            out.add(type.cast(component));
        }
        for (IComponent<?> child : component.getChildren()) {
            collect(child, type, out);
        }
    }

    // ------------------------------------------------------------------ modelo para o UI Lib

    private static List<Tree> buildTrees() {
        List<Tree> trees = new ArrayList<>();
        for (SkillDefinitionsPayload.TreeView view : ClientSkillCache.visibleTrees()) {
            Map<String, Node> nodes = new LinkedHashMap<>();
            for (SkillDefinitionsPayload.SkillView skill : ClientSkillCache.skills()) {
                if (skill.tree().equals(view.id())) {
                    Node parent = skill.parent().isEmpty() ? null : nodes.get(skill.parent());
                    Node node = new Node(skill, parent);
                    nodes.put(skill.id(), node);
                    if (parent != null) {
                        parent.addChild(node);
                    }
                }
            }
            nodes.values().stream().filter(node -> node.parent == null).findFirst()
                    .ifPresent(root -> trees.add(new Tree(view, root)));
        }
        return trees;
    }

    private static ItemStack item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null || !BuiltInRegistries.ITEM.containsKey(location)
                ? new ItemStack(Items.BARRIER) : BuiltInRegistries.ITEM.get(location).getDefaultInstance();
    }

    /** Uma aba. */
    private record Tree(SkillDefinitionsPayload.TreeView view, Node root) implements IAdvancementTree {
        String id() {
            return view.id();
        }

        List<Node> preOrder() {
            List<Node> out = new ArrayList<>();
            root.collect(out);
            return out;
        }

        @Override
        public Optional<IAdvancement> getRoot() {
            return Optional.of(root);
        }

        @Override
        public ResourceLocation getBackgroundTexture() {
            return ResourceLocation.parse(view.background());
        }

        @Override
        public Component getName() {
            return Component.literal(view.name());
        }

        @Override
        public ItemStack getIcon() {
            return item(view.icon());
        }
    }

    /** Um nó (skill). */
    private static final class Node implements IAdvancement {
        private final String id;
        private final SkillDefinitionsPayload.Display display;
        private final Node parent;
        private final List<IAdvancement> children = new ArrayList<>();
        private final ItemStack icon;

        Node(SkillDefinitionsPayload.SkillView view, Node parent) {
            this.id = view.id();
            this.display = view.display();
            this.parent = parent;
            this.icon = item(display.icon());
        }

        void collect(List<Node> out) {
            out.add(this);
            children.forEach(child -> ((Node) child).collect(out));
        }

        @Override
        public Optional<IAdvancement> getParent() {
            return Optional.ofNullable(parent);
        }

        @Override
        public List<IAdvancement> getChildren() {
            return children;
        }

        @Override
        public void addChild(IAdvancement advancement) {
            children.add(advancement);
        }

        @Override
        public ItemStack getIcon() {
            return icon;
        }

        @Override
        public Component getName() {
            return Component.literal(display.name());
        }

        @Override
        public List<Component> getDescription() {
            List<Component> lines = new ArrayList<>();
            for (String line : display.description().split("\n")) {
                if (!line.isBlank()) {
                    lines.add(Component.literal(line).withStyle(ChatFormatting.GRAY));
                }
            }
            lines.add(Component.literal(display.costLine()).withStyle(ChatFormatting.YELLOW));
            lines.add(Component.literal(capitalize(display.requirementLine())).withStyle(ChatFormatting.DARK_GRAY));
            lines.add(status());
            return lines;
        }

        private Component status() {
            if (ClientSkillCache.suspended(id)) {
                return Component.literal("⚠ Suspensa: requisito não atendido agora").withStyle(ChatFormatting.GOLD);
            }
            if (ClientSkillCache.unlocked(id)) {
                return Component.literal("✔ Comprada e ativa").withStyle(ChatFormatting.GREEN);
            }
            if (parent != null && !parent.isObtained()) {
                return Component.literal("Compre antes: " + parent.display.name()).withStyle(ChatFormatting.RED);
            }
            return Component.literal("Clique para comprar").withStyle(ChatFormatting.AQUA);
        }

        private static String capitalize(String text) {
            return text.isEmpty() ? text : text.substring(0, 1).toUpperCase(Locale.ROOT) + text.substring(1);
        }

        @Override
        public boolean isObtained() {
            return ClientSkillCache.unlocked(id);
        }

        @Override
        public AdvancementType getFrameType() {
            return switch (display.frame()) {
                case "goal" -> AdvancementType.GOAL;
                case "challenge" -> AdvancementType.CHALLENGE;
                default -> AdvancementType.TASK;
            };
        }
    }

    /** Abre a tela se as árvores já chegaram do servidor. */
    public static void open() {
        if (ClientSkillCache.ready()) {
            Minecraft.getInstance().setScreen(new SkillTreeScreen());
        }
    }
}

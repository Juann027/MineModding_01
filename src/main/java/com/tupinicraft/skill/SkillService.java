package com.tupinicraft.skill;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.profile.BirthTrait;
import com.tupinicraft.profile.CharacterAttribute;
import com.tupinicraft.profile.CharacterProfile;
import com.tupinicraft.profile.ProfileService;
import com.tupinicraft.registry.ModSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.joml.Vector3f;

/**
 * Compra de habilidades e modificadores de atributo (atributos de nascimento + habilidades + efeitos definidos
 * nos tracos de nascimento). Servidor apenas.
 * Os modificadores sao transitorios ({@code tupinicraft:stat/...}) e recolocados a partir da ficha no login,
 * respawn e a cada mudanca: nunca ficam presos no save.
 */
public final class SkillService {
    private static final String PREFIX = "stat/";
    private record Applied(Holder<Attribute> attribute, ResourceLocation id) {}
    /** Quem traz efeitos: uma habilidade comprada ou um traco de nascimento ({@code path} forma o id do modificador). */
    private record Source(String path, List<SkillEffect> effects) {}

    private SkillService() {}

    public static boolean purchase(ServerPlayer player, ResourceLocation id) {
        SkillDef def = Skills.get(id);
        if (def == null) return false;
        CharacterProfile profile = ProfileService.get(player);
        String blocker = Skills.purchaseBlocker(def, profile.skillSet(), profile.skillPoints(), profile.born());
        if (blocker != null) {
            player.displayClientMessage(Component.translatable("msg.tupinicraft.skill_blocked." + blocker), true);
            ProfileService.sync(player);
            return false;
        }
        List<String> skills = new ArrayList<>(profile.skills());
        skills.add(def.id().toString());
        ProfileService.set(player, profile.withSkills(skills, profile.skillPoints() - def.cost()));
        // VFX no mundo, visto por todos por perto.
        int rgb = def.branch().accent() & 0xFFFFFF;
        var dust = new DustParticleOptions(new Vector3f(((rgb >> 16) & 255) / 255F, ((rgb >> 8) & 255) / 255F,
                (rgb & 255) / 255F), 1.4F);
        var level = player.serverLevel();
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI * 2 / 24;
            level.sendParticles(dust, player.getX() + Math.cos(a) * 0.9, player.getY() + 0.1 + (i % 3) * 0.5,
                    player.getZ() + Math.sin(a) * 0.9, 1, 0, 0.05, 0, 0);
        }
        level.sendParticles(ParticleTypes.ENCHANT, player.getX(), player.getY() + 1.2, player.getZ(), 30, 0.4, 0.6, 0.4, 0.6);
        level.playSound(null, player.blockPosition(), ModSounds.SKILL_UNLOCK.get(), SoundSource.PLAYERS, 0.9F, 1F);
        player.displayClientMessage(Component.translatable("msg.tupinicraft.skill_unlocked",
                Component.translatable(def.translationKey())), true);
        return true;
    }

    /** Remove os modificadores antigos e aplica os atuais (atributos + habilidades). */
    public static void refreshModifiers(ServerPlayer player) {
        // Sem estado estatico: remove todos os ids que o catalogo pode ter gerado e recoloca os atuais.
        for (Applied applied : possible()) {
            AttributeInstance instance = player.getAttribute(applied.attribute());
            if (instance != null) instance.removeModifier(applied.id());
        }
        CharacterProfile profile = ProfileService.get(player);
        List<Applied> now = new ArrayList<>();
        if (profile.born()) {
            int f = profile.attribute(CharacterAttribute.FORCA) - CharacterAttribute.BASE;
            int a = profile.attribute(CharacterAttribute.AGILIDADE) - CharacterAttribute.BASE;
            int v = profile.attribute(CharacterAttribute.VIGOR) - CharacterAttribute.BASE;
            int s = profile.attribute(CharacterAttribute.SORTE) - CharacterAttribute.BASE;
            add(player, now, Attributes.ATTACK_DAMAGE, "attr/forca", 0.25 * f, AttributeModifier.Operation.ADD_VALUE);
            add(player, now, Attributes.MOVEMENT_SPEED, "attr/agilidade", 0.015 * a, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            add(player, now, Attributes.MAX_HEALTH, "attr/vigor", 1.0 * v, AttributeModifier.Operation.ADD_VALUE);
            add(player, now, Attributes.LUCK, "attr/sorte", 0.5 * s, AttributeModifier.Operation.ADD_VALUE);
        }
        for (Source source : sources(profile)) {
            for (int i = 0; i < source.effects().size(); i++) {
                SkillEffect effect = source.effects().get(i);
                if (effect.kind() != SkillEffect.Kind.ATTRIBUTE_ADD && effect.kind() != SkillEffect.Kind.ATTRIBUTE_MULTIPLY) continue;
                ResourceLocation attributeId = ResourceLocation.tryParse(effect.target());
                Holder<Attribute> attribute = attributeId == null ? null
                        : BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).orElse(null);
                if (attribute == null) {
                    TupiniCraft.LOGGER.warn("Efeito {}: atributo desconhecido {}", source.path(), effect.target());
                    continue;
                }
                add(player, now, attribute, source.path() + "/" + i, effect.amount(),
                        effect.kind() == SkillEffect.Kind.ATTRIBUTE_ADD ? AttributeModifier.Operation.ADD_VALUE
                                : AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            }
        }
        if (player.getHealth() > player.getMaxHealth()) player.setHealth(player.getMaxHealth());
    }

    private static void add(ServerPlayer player, List<Applied> list, Holder<Attribute> attribute, String path,
                            double amount, AttributeModifier.Operation operation) {
        if (amount == 0) return;
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) return;
        ResourceLocation id = TupiniCraft.id(PREFIX + path);
        // Uma mesma habilidade pode somar mais de uma vez no mesmo atributo (id diferente por efeito).
        instance.addOrUpdateTransientModifier(new AttributeModifier(id, amount, operation));
        list.add(new Applied(attribute, id));
    }

    /** Habilidades compradas e tracos de nascimento da ficha (ids desconhecidos sao ignorados sem apagar o dado). */
    private static List<Source> sources(CharacterProfile profile) {
        List<Source> list = new ArrayList<>();
        for (String skillId : profile.skills()) {
            SkillDef def = Skills.get(skillId);
            if (def != null) list.add(new Source("skill/" + def.id().getPath(), def.effects()));
        }
        if (profile.born()) {
            for (BirthTrait trait : profile.birthTraits()) list.add(new Source("trait/" + trait.id(), trait.effects()));
        }
        return list;
    }

    /** Todas as fontes de efeito que esta versao conhece (para remover modificadores antigos). */
    private static List<Source> allSources() {
        List<Source> list = new ArrayList<>();
        for (SkillDef def : Skills.all()) list.add(new Source("skill/" + def.id().getPath(), def.effects()));
        for (BirthTrait trait : BirthTrait.values()) list.add(new Source("trait/" + trait.id(), trait.effects()));
        return list;
    }

    /** Produto dos multiplicadores de uma necessidade nas habilidades compradas e nos tracos de nascimento. */
    public static float needRate(CharacterProfile profile, String need) {
        float rate = 1F;
        for (Source source : sources(profile)) {
            for (SkillEffect effect : source.effects()) {
                if (effect.kind() == SkillEffect.Kind.NEED_RATE && effect.target().equals(need)) rate *= (float) effect.amount();
            }
        }
        return rate;
    }

    public static int moodBonus(CharacterProfile profile) {
        int bonus = 0;
        for (Source source : sources(profile)) {
            for (SkillEffect effect : source.effects()) {
                if (effect.kind() == SkillEffect.Kind.MOOD_BONUS) bonus += (int) effect.amount();
            }
        }
        return bonus;
    }

    /** Todos os modificadores que esta versao pode aplicar (atributos de nascimento + efeitos do catalogo). */
    private static List<Applied> possible() {
        List<Applied> all = new ArrayList<>();
        all.add(new Applied(Attributes.ATTACK_DAMAGE, TupiniCraft.id(PREFIX + "attr/forca")));
        all.add(new Applied(Attributes.MOVEMENT_SPEED, TupiniCraft.id(PREFIX + "attr/agilidade")));
        all.add(new Applied(Attributes.MAX_HEALTH, TupiniCraft.id(PREFIX + "attr/vigor")));
        all.add(new Applied(Attributes.LUCK, TupiniCraft.id(PREFIX + "attr/sorte")));
        for (Source source : allSources()) {
            for (int i = 0; i < source.effects().size(); i++) {
                ResourceLocation attributeId = ResourceLocation.tryParse(source.effects().get(i).target());
                if (attributeId == null) continue;
                int index = i;
                BuiltInRegistries.ATTRIBUTE.getHolder(attributeId).ifPresent(holder -> all.add(
                        new Applied(holder, TupiniCraft.id(PREFIX + source.path() + "/" + index))));
            }
        }
        return all;
    }
}

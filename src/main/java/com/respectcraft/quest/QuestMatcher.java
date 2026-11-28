package com.respectcraft.quest;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import java.util.function.Predicate;
import net.minecraft.resources.ResourceLocation;

/**
 * Alvo de entidade numa condicao/objetivo (Etapa 4.14.2): {@code "minecraft:zombie"} (um tipo),
 * {@code "#minecraft:undead"} (uma tag de tipo de entidade) ou {@code "*"} (qualquer).
 */
public record QuestMatcher(ResourceLocation id, boolean tag) {
    public static final QuestMatcher ANY = new QuestMatcher(null, false);

    public boolean any() { return id == null; }

    public boolean matches(ResourceLocation subject, Predicate<ResourceLocation> tags) {
        if (id == null) return true;
        if (subject == null) return false;
        return tag ? tags.test(id) : id.equals(subject);
    }

    public String raw() { return id == null ? "*" : (tag ? "#" : "") + id; }

    public static DataResult<QuestMatcher> parse(String s) {
        if (s == null || s.isEmpty() || s.equals("*")) return DataResult.success(ANY);
        boolean tag = s.startsWith("#");
        ResourceLocation id = ResourceLocation.tryParse(tag ? s.substring(1) : s);
        return id == null ? DataResult.error(() -> "Invalid entity id or tag: " + s) : DataResult.success(new QuestMatcher(id, tag));
    }

    public static final Codec<QuestMatcher> CODEC = Codec.STRING.comapFlatMap(QuestMatcher::parse, QuestMatcher::raw);
}

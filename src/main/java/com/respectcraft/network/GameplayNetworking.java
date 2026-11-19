package com.respectcraft.network;

import com.respectcraft.RespectCraft;
import com.respectcraft.needs.ActiveNeedEffects;
import com.respectcraft.needs.Needs;
import com.respectcraft.profile.BirthRoulette;
import com.respectcraft.profile.CharacterProfile;
import com.respectcraft.profile.ProfileService;
import com.respectcraft.profile.SpecialityOdds;
import com.respectcraft.profile.SpecialityRarity;
import com.respectcraft.skill.SkillService;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * Pacotes da ficha do personagem, das habilidades e das necessidades. O cliente so recebe copias para exibir
 * e envia intencoes (girar a roleta, comprar uma habilidade); o servidor revalida tudo.
 */
public final class GameplayNetworking {
    private GameplayNetworking() {}

    /** Servidor -> dono: ficha completa. */
    public record ProfileSync(CharacterProfile profile) implements CustomPacketPayload {
        public static final Type<ProfileSync> TYPE = new Type<>(RespectCraft.id("profile_sync"));
        public static final StreamCodec<ByteBuf, ProfileSync> STREAM_CODEC =
                CharacterProfile.STREAM_CODEC.map(ProfileSync::new, ProfileSync::profile);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Servidor -> dono: necessidades (so quando alguma porcentagem muda). */
    public record NeedsSync(Needs needs) implements CustomPacketPayload {
        public static final Type<NeedsSync> TYPE = new Type<>(RespectCraft.id("needs_sync"));
        public static final StreamCodec<ByteBuf, NeedsSync> STREAM_CODEC =
                Needs.STREAM_CODEC.map(NeedsSync::new, NeedsSync::needs);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Servidor -> dono: efeitos de consumiveis em andamento (ao comecar/terminar um, login e respawn). */
    public record NeedEffectsSync(ActiveNeedEffects effects) implements CustomPacketPayload {
        public static final Type<NeedEffectsSync> TYPE = new Type<>(RespectCraft.id("need_effects_sync"));
        public static final StreamCodec<ByteBuf, NeedEffectsSync> STREAM_CODEC =
                ActiveNeedEffects.STREAM_CODEC.map(NeedEffectsSync::new, NeedEffectsSync::effects);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Cliente -> servidor: girar a roleta de nascimento (primeiro giro ou o novo giro). {@code expectedRollCount}
     * e o {@code rollCount} da ficha que o cliente via: pedidos repetidos ou atrasados sao recusados.
     */
    public record BirthRollRequest(boolean reroll, int expectedRollCount) implements CustomPacketPayload {
        public static final Type<BirthRollRequest> TYPE = new Type<>(RespectCraft.id("birth_roll_request"));
        public static final StreamCodec<ByteBuf, BirthRollRequest> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.BOOL, BirthRollRequest::reroll,
                ByteBufCodecs.VAR_INT, BirthRollRequest::expectedRollCount,
                BirthRollRequest::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Servidor -> dono: resposta a todo {@link BirthRollRequest} (sempre depois do {@link ProfileSync}).
     * {@code rollCount} e o da ficha ja enviada; a tela usa a resposta para sair do estado "girando".
     */
    public record BirthRollReply(BirthRoulette.Outcome outcome, int rollCount) implements CustomPacketPayload {
        public static final Type<BirthRollReply> TYPE = new Type<>(RespectCraft.id("birth_roll_reply"));
        public static final StreamCodec<ByteBuf, BirthRollReply> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.map(BirthRoulette.Outcome::byOrdinal, BirthRoulette.Outcome::ordinal), BirthRollReply::outcome,
                ByteBufCodecs.VAR_INT, BirthRollReply::rollCount,
                BirthRollReply::new);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Cliente -> servidor: Continuar na roleta (confirma o resultado da ficha com esse {@code rollCount} e descarta
     * o novo giro restante). Respondido com {@link ProfileSync}.
     */
    public record BirthConfirmRequest(int expectedRollCount) implements CustomPacketPayload {
        public static final Type<BirthConfirmRequest> TYPE = new Type<>(RespectCraft.id("birth_confirm_request"));
        public static final StreamCodec<ByteBuf, BirthConfirmRequest> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.map(BirthConfirmRequest::new, BirthConfirmRequest::expectedRollCount);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /**
     * Servidor -> dono: chances de raridade das Specialities ativas no servidor (config), em milesimos na ordem de
     * {@link SpecialityRarity}. So exibicao: a roleta mostra as chances reais; o sorteio continua so no servidor e
     * o cliente nunca envia chances. Mandado no login, antes de cada resposta de giro e quando a config recarrega.
     */
    public record SpecialityOddsSync(java.util.List<Integer> permille) implements CustomPacketPayload {
        public static final Type<SpecialityOddsSync> TYPE = new Type<>(RespectCraft.id("speciality_odds_sync"));
        public static final StreamCodec<ByteBuf, SpecialityOddsSync> STREAM_CODEC =
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(SpecialityRarity.values().length))
                        .map(SpecialityOddsSync::new, SpecialityOddsSync::permille);

        public static SpecialityOddsSync of(SpecialityOdds odds) {
            return new SpecialityOddsSync(java.util.Arrays.stream(odds.toPermilleArray()).boxed().toList());
        }

        public SpecialityOdds odds() {
            return SpecialityOdds.ofOrDefault(permille.stream().mapToInt(Integer::intValue).toArray());
        }

        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    /** Cliente -> servidor: comprar a habilidade. */
    public record SkillPurchaseRequest(ResourceLocation skill) implements CustomPacketPayload {
        public static final Type<SkillPurchaseRequest> TYPE = new Type<>(RespectCraft.id("skill_purchase_request"));
        public static final StreamCodec<ByteBuf, SkillPurchaseRequest> STREAM_CODEC =
                ResourceLocation.STREAM_CODEC.map(SkillPurchaseRequest::new, SkillPurchaseRequest::skill);
        @Override public Type<? extends CustomPacketPayload> type() { return TYPE; }
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        // "2": a roleta passou a ter pedido com rollCount esperado e resposta explicita (Etapa 4.5).
        // "3": ficha com tracos de nascimento e confirmacao do resultado (Etapa 4.5 revisada).
        // "4": mod id respectcraft e chances de raridade sincronizadas para a roleta (Etapa 4.7.1).
        var registrar = event.registrar("4");
        registrar.playToClient(ProfileSync.TYPE, ProfileSync.STREAM_CODEC,
                (payload, context) -> ProfileClientCache.receive(payload.profile()));
        registrar.playToClient(NeedsSync.TYPE, NeedsSync.STREAM_CODEC,
                (payload, context) -> ProfileClientCache.receive(payload.needs()));
        registrar.playToClient(NeedEffectsSync.TYPE, NeedEffectsSync.STREAM_CODEC,
                (payload, context) -> ProfileClientCache.receive(payload.effects()));
        registrar.playToClient(BirthRollReply.TYPE, BirthRollReply.STREAM_CODEC,
                (payload, context) -> ProfileClientCache.receive(payload));
        registrar.playToClient(SpecialityOddsSync.TYPE, SpecialityOddsSync.STREAM_CODEC,
                (payload, context) -> ProfileClientCache.receive(payload.odds()));
        registrar.playToServer(BirthRollRequest.TYPE, BirthRollRequest.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) ProfileService.requestRoll(player, payload.reroll(), payload.expectedRollCount());
        });
        registrar.playToServer(BirthConfirmRequest.TYPE, BirthConfirmRequest.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) ProfileService.confirmBirth(player, payload.expectedRollCount());
        });
        registrar.playToServer(SkillPurchaseRequest.TYPE, SkillPurchaseRequest.STREAM_CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) SkillService.purchase(player, payload.skill());
        });
    }
}

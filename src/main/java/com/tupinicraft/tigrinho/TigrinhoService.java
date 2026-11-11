package com.tupinicraft.tigrinho;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.registry.ModBlocks;
import com.tupinicraft.network.*;
import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import com.mojang.brigadier.arguments.LongArgumentType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/** Main-thread only service. Settlement precedes animation and never depends on a client ACK. */
public final class TigrinhoService {
    private static final Map<MinecraftServer,Map<UUID,Session>> SESSIONS=new WeakHashMap<>();
    private static final class Session {
        final UUID token=UUID.randomUUID();
        final ResourceKey<Level> dimension;
        final BlockPos machine;
        final String symbols=TigrinhoConfig.SYMBOLS.get(), bets=TigrinhoConfig.BETS.get();
        final List<SlotRules.Symbol> rules=SlotRules.parse(symbols);
        final List<Integer> allowed=SlotRules.bets(bets);
        final int animation=TigrinhoConfig.ANIMATION.get();
        final boolean sounds=TigrinhoConfig.SOUNDS.get();
        long expires;
        long lastRequest=Long.MIN_VALUE;
        final SpinGate gate=new SpinGate();
        Session(ServerPlayer p,BlockPos pos,long now) {
            dimension=p.level().dimension(); machine=pos==null ? null : pos.immutable();
            expires=now+TigrinhoConfig.SESSION_SECONDS.get()*20L;
        }
    }
    private static Map<UUID,Session> sessions(MinecraftServer server) { return SESSIONS.computeIfAbsent(server,k -> new HashMap<>()); }
    private static long now(ServerPlayer p) { return p.server.overworld().getGameTime(); }
    public static void registerEvents() {
        NeoForge.EVENT_BUS.addListener(TigrinhoService::logout);
        NeoForge.EVENT_BUS.addListener(TigrinhoService::stopped);
        NeoForge.EVENT_BUS.addListener(TigrinhoService::tick);
        NeoForge.EVENT_BUS.addListener(TigrinhoService::commands);
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent e) {
        if(e.getEntity() instanceof ServerPlayer p) sessions(p.server).remove(p.getUUID());
    }
    private static void stopped(ServerStoppedEvent e) { SESSIONS.remove(e.getServer()); }
    private static void tick(ServerTickEvent.Post e) {
        if(e.getServer().getTickCount()%20==0) {
            long now=e.getServer().overworld().getGameTime();
            sessions(e.getServer()).values().removeIf(s -> s.expires<now);
        }
    }
    private static boolean accessible(ServerPlayer p,Session s) {
        if(!p.isAlive() || p.isSpectator() || !p.level().dimension().equals(s.dimension)) return false;
        if(s.machine!=null) return p.distanceToSqr(Vec3.atCenterOf(s.machine))<=64
                && p.level().hasChunkAt(s.machine) && p.level().getBlockState(s.machine).is(ModBlocks.TIGRINHO_MACHINE.get());
        return p.getMainHandItem().getItem() instanceof TigrinhoItem || p.getOffhandItem().getItem() instanceof TigrinhoItem;
    }
    public static void open(ServerPlayer p,BlockPos pos) {
        if(!TigrinhoConfig.ENABLED.get() || !p.isAlive() || p.isSpectator()) return;
        long now=now(p); TigrinhoLedger ledger=TigrinhoLedger.get(p.server);
        if(now<ledger.next(p.getUUID())) { p.displayClientMessage(Component.literal("Aguarde o giro anterior terminar."),true); return; }
        Session previous=sessions(p.server).get(p.getUUID());
        if(previous!=null && previous.lastRequest==now) return;
        Session s=new Session(p,pos,now);
        if(!accessible(p,s)) return;
        s.lastRequest=now; sessions(p.server).put(p.getUUID(),s);
        send(p,s,true,null,"Escolha sua aposta. Fichas fictícias, sem valor monetário.");
    }
    public static void spin(ServerPlayer p,TigrinhoSpinPayload request) {
        Session s=sessions(p.server).get(p.getUUID());
        if(s==null || !s.token.equals(request.session())) return;
        if(request.bet()==-1) { sessions(p.server).remove(p.getUUID()); return; }
        long now=now(p);
        if(!TigrinhoConfig.ENABLED.get() || now>s.expires || !accessible(p,s)) {
            sessions(p.server).remove(p.getUUID());
            send(p,s,false,null,"Sessão encerrada. Reabra pelo item ou pela máquina."); return;
        }
        // One packet per tick at most, even for rejected requests. Exact sequence rejects replays.
        if(!s.gate.accept(request.sequence(),now)) return;
        s.lastRequest=now;
        TigrinhoLedger ledger=TigrinhoLedger.get(p.server); long balance=ledger.balance(p.getUUID());
        if(now<ledger.next(p.getUUID())) { send(p,s,false,null,"Aguarde o intervalo entre giros."); return; }
        if(!SlotRules.canBet(balance,request.bet(),s.allowed)) { send(p,s,false,null,"Aposta inválida ou saldo insuficiente."); return; }
        // Validate every configured item before drawing/debiting, never bias the result on errors.
        for(SlotRules.Symbol symbol:s.rules) {
            ResourceLocation id=ResourceLocation.parse(symbol.reward());
            if(symbol.count()>0 && (!BuiltInRegistries.ITEM.containsKey(id) || BuiltInRegistries.ITEM.get(id)==Items.AIR)) {
                send(p,s,false,null,"Recompensa inválida na configuração. Avise o administrador."); return;
            }
        }
        long maxPrize=(long)request.bet()*s.rules.stream().mapToInt(SlotRules.Symbol::multiplier).max().orElse(0);
        if(balance-request.bet()>SlotRules.MAX_BALANCE-maxPrize) { send(p,s,false,null,"Saldo próximo do limite de fichas."); return; }
        SlotRules.Result result=SlotRules.settle(s.rules,request.bet(),
            SlotRules.draw(s.rules,p.getRandom()::nextInt),SlotRules.draw(s.rules,p.getRandom()::nextInt),SlotRules.draw(s.rules,p.getRandom()::nextInt));
        ledger.set(p.getUUID(),balance-request.bet()+result.prize(),now+Math.max(s.animation,TigrinhoConfig.COOLDOWN.get()));
        s.expires=now+TigrinhoConfig.SESSION_SECONDS.get()*20L;
        String message=result.prize()>0 ? "Você ganhou "+result.prize()+" fichas!" : "Sem prêmio neste giro.";
        if(result.winner()>=0 && s.rules.get(result.winner()).id().equals("t")) message="TIGRINHO DOURADO! "+result.prize()+" fichas!";
        if(result.winner()>=0) {
            SlotRules.Symbol symbol=s.rules.get(result.winner());
            if(symbol.count()>0) {
                Item item=BuiltInRegistries.ITEM.get(ResourceLocation.parse(symbol.reward()));
                int remaining=symbol.count();
                while(remaining>0) {
                    ItemStack reward=new ItemStack(item,Math.min(remaining,item.getDefaultInstance().getMaxStackSize()));
                    remaining-=reward.getCount();
                    if(!p.getInventory().add(reward)) p.drop(reward,false);
                }
                message+=" + "+symbol.count()+" item(ns)";
            }
        }
        send(p,s,false,result,message);
    }
    private static void send(ServerPlayer p,Session s,boolean open,SlotRules.Result r,String message) {
        TigrinhoLedger ledger=TigrinhoLedger.get(p.server);
        PacketDistributor.sendToPlayer(p,new TigrinhoStatePayload(s.token,s.gate.sequence(),open,ledger.balance(p.getUUID()),
            r==null?-1:r.a(),r==null?-1:r.b(),r==null?-1:r.c(),r==null?0:r.prize(),s.animation,
            (int)Math.clamp(ledger.next(p.getUUID())-now(p),0L,1200L),s.sounds,message,s.symbols,s.bets));
    }
    private static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("tigrinho")
            .then(Commands.literal("saldo").executes(c -> {
                ServerPlayer p=c.getSource().getPlayerOrException();
                c.getSource().sendSuccess(() -> Component.literal("Fichas: "+TigrinhoLedger.get(p.server).balance(p.getUUID())),false); return 1;
            }))
            .then(Commands.literal("definir").requires(s -> s.hasPermission(2))
                .then(Commands.argument("jogador",EntityArgument.player())
                    .then(Commands.argument("fichas",LongArgumentType.longArg(0,SlotRules.MAX_BALANCE)).executes(c -> {
                        ServerPlayer p=EntityArgument.getPlayer(c,"jogador");
                        TigrinhoLedger ledger=TigrinhoLedger.get(p.server);
                        ledger.set(p.getUUID(),LongArgumentType.getLong(c,"fichas"),ledger.next(p.getUUID()));
                        Session s=sessions(p.server).get(p.getUUID());
                        if(s!=null) send(p,s,false,null,"Saldo atualizado pelo administrador.");
                        c.getSource().sendSuccess(() -> Component.literal("Fichas atualizadas para "+p.getScoreboardName()),true); return 1;
                    })))));
    }
    private TigrinhoService() {}
}

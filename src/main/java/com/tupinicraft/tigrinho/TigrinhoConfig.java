package com.tupinicraft.tigrinho;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class TigrinhoConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLED, SOUNDS;
    public static final ModConfigSpec.IntValue INITIAL, ANIMATION, COOLDOWN, SESSION_SECONDS;
    public static final ModConfigSpec.ConfigValue<String> BETS, SYMBOLS;
    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        ENABLED=b.define("enabled",true);
        SOUNDS=b.define("sounds",true);
        INITIAL=b.comment("Crédito único por UUID; não é renovado ao morrer ou reconectar.").defineInRange("initialChips",1000,0,1000000);
        ANIMATION=b.comment("Duração em ticks (20 ticks = 1 segundo).").defineInRange("animationTicks",60,20,200);
        COOLDOWN=b.defineInRange("cooldownTicks",80,20,1200);
        SESSION_SECONDS=b.defineInRange("sessionSeconds",300,30,3600);
        BETS=b.comment("Valores permitidos; o custo de um giro é exatamente a aposta.").define("bets","10,25,50,100",v -> validBets(v));
        SYMBOLS=b.comment("id|texture|width|height|weight|tripleMultiplier|rewardItem|rewardCount; ...",
                "Rolos independentes: P(símbolo)=peso/soma. Somente três iguais pagam. Crédito bruto=aposta*multiplicador.",
                "Item de recompensa é adicional por combinação vencedora, independente da aposta; count=0 desativa.")
                .define("symbols", defaults(),v -> validSymbols(v));
        SPEC=b.build();
    }
    private static boolean validBets(Object v) { try { SlotRules.bets((String)v); return true; } catch(RuntimeException e) { return false; } }
    private static boolean validSymbols(Object v) { try { SlotRules.parse((String)v); return true; } catch(RuntimeException e) { return false; } }
    private static String defaults() {
        return "tiger|tupinicraft:textures/gui/tigrinho/tiger.png|64|64|12|60|minecraft:diamond|0;"
            +"cards|tupinicraft:textures/gui/tigrinho/cards.png|64|64|20|24|minecraft:gold_ingot|0;"
            +"pouch|tupinicraft:textures/gui/tigrinho/pouch.png|64|64|25|15|minecraft:emerald|0;"
            +"fortune|tupinicraft:textures/gui/tigrinho/fortune.png|64|64|25|15|minecraft:gold_ingot|0;"
            +"t|tupinicraft:textures/gui/tigrinho/t.png|64|64|5|300|tupinicraft:icone_tigrinho|0;"
            +"diamond|tupinicraft:textures/gui/tigrinho/diamond.png|605|497|5|300|minecraft:diamond|0;"
            +"apple|tupinicraft:textures/gui/tigrinho/apple.png|48|48|8|120|minecraft:golden_apple|0";
    }
    private TigrinhoConfig() {}
}

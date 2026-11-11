package com.tupinicraft.tigrinho;

import java.util.*;
import java.util.function.IntUnaryOperator;

/** Pure, deterministic rules. No player history or client supplied outcomes. */
public final class SlotRules {
    public static final long MAX_BALANCE = 1_000_000_000_000L;
    public record Symbol(String id, String texture, int width, int height, int weight,
                         int multiplier, String reward, int count) {}
    public record Result(int a, int b, int c, long prize, int winner) {}
    public static List<Symbol> parse(String value) {
        List<Symbol> result = new ArrayList<>();
        Set<String> ids = new HashSet<>();
        for (String row : value.split(";")) {
            String[] v = row.trim().split("\\|", -1);
            if (v.length != 8) throw new IllegalArgumentException("Símbolo deve ter 8 campos");
            Symbol s = new Symbol(v[0], v[1], Integer.parseInt(v[2]), Integer.parseInt(v[3]),
                    Integer.parseInt(v[4]), Integer.parseInt(v[5]), v[6], Integer.parseInt(v[7]));
            if (!ids.add(s.id()) || !s.id().matches("[a-z0-9_]{1,32}")
                    || !s.texture().matches("[a-z0-9_.-]+:textures/[a-z0-9_./-]+\\.png")
                    || s.texture().contains("..") || s.width()<1 || s.width()>2048 || s.height()<1 || s.height()>2048
                    || s.weight()<1 || s.weight()>10000 || s.multiplier()<0 || s.multiplier()>10000
                    || !s.reward().matches("[a-z0-9_.-]+:[a-z0-9_./-]+") || s.count()<0 || s.count()>64)
                throw new IllegalArgumentException("Símbolo inválido: " + s.id());
            result.add(s);
        }
        if (result.size()<2 || result.size()>32 || value.length()>8192) throw new IllegalArgumentException("Use 2–32 símbolos");
        return List.copyOf(result);
    }
    public static List<Integer> bets(String value) {
        if(value.length()>256) throw new IllegalArgumentException("Lista de apostas muito longa");
        List<Integer> bets = Arrays.stream(value.split(",")).map(String::trim).map(Integer::parseInt).distinct().sorted().toList();
        if (bets.isEmpty() || bets.size()>12 || bets.getFirst()<1 || bets.getLast()>100000)
            throw new IllegalArgumentException("Apostas inválidas");
        return bets;
    }
    public static boolean canBet(long balance, int bet, List<Integer> allowed) {
        return balance >= bet && allowed.contains(bet) && bet > 0;
    }
    public static int draw(List<Symbol> symbols, IntUnaryOperator random) {
        int total = symbols.stream().mapToInt(Symbol::weight).sum();
        int roll = random.applyAsInt(total);
        if (roll<0 || roll>=total) throw new IllegalArgumentException("Random fora dos limites");
        for (int i=0; i<symbols.size(); i++) { roll -= symbols.get(i).weight(); if (roll<0) return i; }
        throw new AssertionError();
    }
    public static Result settle(List<Symbol> symbols, int bet, int a, int b, int c) {
        if (bet<=0 || bet>100000) throw new IllegalArgumentException("Aposta inválida");
        for (int i : new int[]{a,b,c}) if (i<0 || i>=symbols.size()) throw new IllegalArgumentException("Símbolo inválido");
        int winner = a==b && b==c ? a : -1;
        return new Result(a,b,c,winner<0 ? 0 : Math.multiplyExact((long)bet,symbols.get(winner).multiplier()),winner);
    }
    private SlotRules() {}
}

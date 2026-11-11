package com.tupinicraft;
import com.tupinicraft.tigrinho.*;
import com.tupinicraft.client.tigrinho.ReelAnimation;
import java.util.*;
public final class TigrinhoSelfTest {
    private static int checks;
    private static void check(boolean condition) { checks++; if(!condition) throw new AssertionError("Check "+checks); }
    private static void rejects(Runnable r) { boolean rejected=false; try {r.run();} catch(IllegalArgumentException e) {rejected=true;} check(rejected); }
    public static void main(String[] args) {
        String base="a|tupinicraft:textures/a.png|64|64|1|10|minecraft:diamond|1;b|tupinicraft:textures/b.png|64|64|3|2|minecraft:air|0";
        var symbols=SlotRules.parse(base); var bets=SlotRules.bets("100,10,25,10");
        check(bets.equals(List.of(10,25,100)));
        check(SlotRules.draw(symbols,b -> 0)==0);
        for(int i=1;i<4;i++) { final int v=i; check(SlotRules.draw(symbols,b -> v)==1); }
        // Exhaustive weighted tickets: exact independence and triple payouts, no statistical flakiness.
        int winsA=0,winsB=0; long totalPrize=0;
        for(int a=0;a<4;a++) for(int b=0;b<4;b++) for(int c=0;c<4;c++) {
            final int ta=a,tb=b,tc=c;
            var result=SlotRules.settle(symbols,10,SlotRules.draw(symbols,n->ta),SlotRules.draw(symbols,n->tb),SlotRules.draw(symbols,n->tc));
            totalPrize+=result.prize(); if(result.winner()==0) winsA++; if(result.winner()==1) winsB++;
        }
        check(winsA==1 && winsB==27 && totalPrize==640);
        check(SlotRules.canBet(10,10,bets)); check(!SlotRules.canBet(9,10,bets));
        check(!SlotRules.canBet(100,0,bets)); check(!SlotRules.canBet(100,-1,bets)); check(!SlotRules.canBet(100,11,bets));
        check(SlotRules.settle(symbols,100000,0,0,0).prize()==1000000L);
        rejects(()->SlotRules.parse(base.replace("|1|10|","|0|10|")));
        rejects(()->SlotRules.parse(base.replace("b|","a|")));
        rejects(()->SlotRules.parse(base.replace("textures/a.png","textures/../a.png")));
        rejects(()->SlotRules.bets("0,1")); rejects(()->SlotRules.bets("100001"));
        rejects(()->SlotRules.settle(symbols,-1,0,0,0)); rejects(()->SlotRules.settle(symbols,10,-1,0,0));
        rejects(()->SlotRules.draw(symbols,b->b));
        SpinGate gate=new SpinGate(); check(gate.accept(0,1)); check(!gate.accept(0,2));
        check(!gate.accept(1,1)); check(!gate.accept(2,2)); check(!gate.accept(-1,2)); check(gate.accept(1,2)); check(gate.sequence()==2);
        for(int reel=0;reel<3;reel++) for(int target=0;target<7;target++) {
            double last=-1;
            for(int step=0;step<=100;step++) { double value=ReelAnimation.position(3,target,7,step/100.0,reel);check(value>=last);last=value; }
            check(Math.floorMod((int)Math.round(last),7)==target);
        }
        System.out.println("TigrinhoSelfTest: "+checks+" verificações passaram.");
    }
}

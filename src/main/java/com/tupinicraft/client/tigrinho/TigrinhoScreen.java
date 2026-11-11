package com.tupinicraft.client.tigrinho;

import com.tupinicraft.TupiniCraft;
import com.tupinicraft.network.*;
import com.tupinicraft.tigrinho.SlotRules;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.neoforged.neoforge.network.PacketDistributor;

/** Purely presentational client. No RNG for results, balance mutations or local payouts. */
public final class TigrinhoScreen extends Screen {
    private static final int W=360,H=280;
    private static final ResourceLocation MASCOT=TupiniCraft.id("textures/item/skilltree/tigrinho.png");
    private TigrinhoStatePayload state;
    private final List<SlotRules.Symbol> symbols;
    private final List<ResourceLocation> textures;
    private final List<Integer> bets;
    private int betIndex,page;
    private boolean table,pending,animating,finishedSound,stale;
    private long animationStart,readyAt,requestAt;
    private final int[] previous={0,1,2};
    private float scale,left,top;
    private Button spin,minus,plus;
    public TigrinhoScreen(TigrinhoStatePayload state) {
        super(Component.literal("Tigrinho • TupiniCraft")); this.state=state;
        symbols=SlotRules.parse(state.symbols()); bets=SlotRules.bets(state.bets());
        textures=symbols.stream().map(s -> ResourceLocation.parse(s.texture())).toList();
        readyAt=System.nanoTime()+state.waitTicks()*50_000_000L;
        previous[2]%=symbols.size();
    }
    public static void receive(TigrinhoStatePayload p) {
        Minecraft mc=Minecraft.getInstance();
        if(p.open()) mc.setScreen(new TigrinhoScreen(p));
        else if(mc.screen instanceof TigrinhoScreen screen && screen.state.session().equals(p.session())) screen.accept(p);
    }
    private void accept(TigrinhoStatePayload p) {
        if(p.sequence()<state.sequence()) return;
        boolean newResult=p.a()>=0 && p.sequence()>state.sequence();
        // Administrative sync must not cut off a currently playing result.
        if(animating && !newResult) {
            state=new TigrinhoStatePayload(state.session(),p.sequence(),false,p.balance(),state.a(),state.b(),state.c(),
                state.prize(),state.animation(),p.waitTicks(),state.sounds(),state.message(),state.symbols(),state.bets());
            return;
        }
        state=p; pending=false;
        if(p.message().startsWith("Sessão encerrada")) stale=true;
        readyAt=System.nanoTime()+p.waitTicks()*50_000_000L;
        if(newResult) {
            animating=true; finishedSound=false; animationStart=System.nanoTime();
            if(state.sounds()) minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK.value(),0.8f));
        }
    }
    @Override protected void init() {
        scale=Math.min(1.5f,Math.min((width-12f)/W,(height-12f)/H));
        scale=Math.max(0.1f,scale); left=(width-W*scale)/2; top=(height-H*scale)/2;
        minus=addRenderableWidget(new GoldButton(24,211,32,24,"−",b -> betIndex=Math.max(0,betIndex-1)));
        plus=addRenderableWidget(new GoldButton(130,211,32,24,"+",b -> betIndex=Math.min(bets.size()-1,betIndex+1)));
        spin=addRenderableWidget(new GoldButton(174,211,162,24,"GIRAR",b -> requestSpin()));
        addRenderableWidget(new GoldButton(24,247,146,20,"Tabela de prêmios",b -> {table=!table; rebuildWidgets();}));
        if(table) addRenderableWidget(new GoldButton(180,247,74,20,"Página +",b -> page=(page+1)%((symbols.size()+4)/5)));
        addRenderableWidget(new GoldButton(264,247,72,20,"Fechar",b -> onClose()));
    }
    private void requestSpin() {
        if(pending || animating || stale || System.nanoTime()<readyAt) return;
        pending=true; requestAt=System.nanoTime();
        PacketDistributor.sendToServer(new TigrinhoSpinPayload(state.session(),state.sequence(),bets.get(betIndex)));
    }
    @Override public void tick() {
        super.tick();
        if(pending && System.nanoTime()-requestAt>15_000_000_000L) { pending=false; stale=true; }
        if(animating && progress()>=1) {
            animating=false; previous[0]=state.a(); previous[1]=state.b(); previous[2]=state.c();
            if(!finishedSound && state.sounds() && state.a()==state.b() && state.b()==state.c()) {
                minecraft.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.PLAYER_LEVELUP,state.prize()>=bets.get(betIndex)*20L ? 0.8f : 1.2f));
            }
            finishedSound=true;
        }
        spin.active=!table && !pending && !animating && !stale && System.nanoTime()>=readyAt && state.balance()>=bets.get(betIndex);
        minus.active=!table && !pending && !animating && betIndex>0;
        plus.active=!table && !pending && !animating && betIndex<bets.size()-1;
    }
    private double progress() { return (System.nanoTime()-animationStart)/(state.animation()*50_000_000.0); }
    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partial) {
        g.fill(0,0,width,height,0xC0100712);
        g.pose().pushPose(); g.pose().translate(left,top,0); g.pose().scale(scale,scale,1);
        g.fill(0,0,W,H,0xFFDAAF44); g.fill(2,2,W-2,H-2,0xFF45101C);
        g.fillGradient(6,6,W-6,H-6,0xFF9B1F29,0xFF330E1C);
        g.fill(12,60,348,196,0xFFDCB653); g.fill(15,63,345,193,0xFF0B4034);
        image(g,MASCOT,18,7,48,128,128);
        g.drawString(font,"TUPINICRAFT",76,14,0xFFFFD775,false);
        g.drawString(font,"T I G R I N H O",76,32,0xFFFFFFFF,false);
        g.drawString(font,"FICHAS",257,14,0xFFEDD5A8,false);
        g.drawString(font,Long.toString(state.balance()),257,29,0xFFFFFFFF,false);
        if(table) renderTable(g); else renderReels(g);
        g.drawCenteredString(font,"Aposta: "+bets.get(betIndex),94,219,0xFFFFE3A0);
        String message=stale ? "Sem resposta. Feche e reabra a sessão." : pending ? "Aguardando servidor…" : animating ? "Girando…" : state.message();
        g.drawCenteredString(font,font.plainSubstrByWidth(message,332),180,199,0xFFFFDF83);
        g.drawCenteredString(font,"Fichas fictícias • sem valor monetário",180,238,0xFFE9B69E);
        super.render(g,(int)((mouseX-left)/scale),(int)((mouseY-top)/scale),partial);
        g.pose().popPose();
    }
    private void renderReels(GuiGraphics g) {
        int[] targets={state.a(),state.b(),state.c()};
        for(int reel=0;reel<3;reel++) {
            int x=39+reel*96;
            g.fill(x-3,74,x+77,184,0xFFF2C966); g.fill(x,77,x+74,181,0xFFFFF4D4);
            double position=animating ? ReelAnimation.position(previous[reel],targets[reel],symbols.size(),progress(),reel) : previous[reel];
            int base=(int)Math.floor(position); double fraction=position-base;
            for(int row=-2;row<=2;row++) {
                int index=Math.floorMod(base+row,symbols.size());
                int y=105+(int)Math.round((row-fraction)*64);
                clippedSymbol(g,index,x+9,y,56,77,181);
            }
            g.fill(x,77,x+74,81,0x660E2F24); g.fill(x,177,x+74,181,0x660E2F24);
        }
        // Payline plus bounded deterministic sparkles; no particle entities or texture allocations.
        g.fill(24,132,35,134,0xFFFFD24A); g.fill(325,132,336,134,0xFFFFD24A);
        if(!animating && state.a()>=0 && state.a()==state.b() && state.b()==state.c()) {
            long phase=System.nanoTime()/60_000_000;
            for(int i=0;i<20;i++) { int x=20+Math.floorMod(i*37+(int)(phase%320),320); int y=67+Math.floorMod(i*19-(int)(phase%120),120);
                g.fill(x,y,x+2,y+2,(i+phase)%3==0?0xFFFFFFFF:0xFFFFD660); }
        }
    }
    private void renderTable(GuiGraphics g) {
        g.drawCenteredString(font,"TRÊS IGUAIS • crédito bruto",180,69,0xFFFFDE82);
        int total=symbols.stream().mapToInt(SlotRules.Symbol::weight).sum();
        for(int row=0;row<5;row++) {
            int index=page*5+row; if(index>=symbols.size()) break;
            SlotRules.Symbol s=symbols.get(index); int y=84+row*19;
            image(g,textures.get(index),24,y,17,s.width(),s.height());
            double odds=Math.pow((double)s.weight()/total,3)*100;
            String line=s.id()+"  ×"+s.multiplier()+"  "+String.format(Locale.ROOT,"%.3f%%",odds)+(s.count()>0?" +"+s.count()+" item":"");
            g.drawString(font,font.plainSubstrByWidth(line,290),46,y+4,0xFFFFF0D1,false);
        }
        g.drawCenteredString(font,"Página "+(page+1)+"/"+((symbols.size()+4)/5)+" • demais combinações: 0",180,181,0xFFB8D6C3);
    }
    private void clippedSymbol(GuiGraphics g,int index,int x,int y,int size,int minY,int maxY) {
        SlotRules.Symbol s=symbols.get(index);
        int visibleTop=Math.max(y,minY),visibleBottom=Math.min(y+size,maxY);
        if(visibleBottom<=visibleTop) return;
        int uvTop=(int)Math.round((visibleTop-y)*s.height()/(double)size);
        int uvHeight=(int)Math.round((visibleBottom-visibleTop)*s.height()/(double)size);
        if(uvHeight<1) return;
        g.pose().pushPose(); g.pose().translate(x,visibleTop,0); g.pose().scale(size/(float)s.width(),size/(float)s.height(),1);
        g.blit(textures.get(index),0,0,0f,(float)uvTop,s.width(),uvHeight,s.width(),s.height()); g.pose().popPose();
    }
    private static void image(GuiGraphics g,ResourceLocation texture,int x,int y,int size,int tw,int th) {
        g.pose().pushPose(); g.pose().translate(x,y,0); g.pose().scale(size/(float)tw,size/(float)th,1);
        g.blit(texture,0,0,0f,0f,tw,th,tw,th); g.pose().popPose();
    }
    @Override public boolean mouseClicked(double x,double y,int button) { return super.mouseClicked((x-left)/scale,(y-top)/scale,button); }
    @Override public boolean mouseReleased(double x,double y,int button) { return super.mouseReleased((x-left)/scale,(y-top)/scale,button); }
    @Override public void mouseMoved(double x,double y) { super.mouseMoved((x-left)/scale,(y-top)/scale); }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void removed() {
        if(minecraft.getConnection()!=null) PacketDistributor.sendToServer(new TigrinhoSpinPayload(state.session(),state.sequence(),-1));
    }
    private final class GoldButton extends Button {
        GoldButton(int x,int y,int w,int h,String label,OnPress action) { super(x,y,w,h,Component.literal(label),action,DEFAULT_NARRATION); }
        @Override public void playDownSound(net.minecraft.client.sounds.SoundManager manager) { if(state.sounds()) super.playDownSound(manager); }
        @Override protected void renderWidget(GuiGraphics g,int mx,int my,float partial) {
            int border=active ? (isHoveredOrFocused()?0xFFFFF2BD:0xFFE2B751) : 0xFF846C4A;
            g.fill(getX(),getY(),getX()+getWidth(),getY()+getHeight(),border);
            g.fill(getX()+2,getY()+2,getX()+getWidth()-2,getY()+getHeight()-2,active?0xFF176B4A:0xFF433B32);
            g.drawCenteredString(font,getMessage(),getX()+getWidth()/2,getY()+(getHeight()-8)/2,active?0xFFFFE7A5:0xFFA49379);
        }
    }
}

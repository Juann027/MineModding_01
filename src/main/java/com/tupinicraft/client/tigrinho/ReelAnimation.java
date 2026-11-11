package com.tupinicraft.client.tigrinho;
/** Cubic ease-out, different stop times and complete cycles for each reel. */
public final class ReelAnimation {
    public static double position(int from,int target,int symbols,double progress,int reel) {
        double t=Math.clamp(progress/(0.65+0.175*reel),0,1);
        int distance=symbols*(5+reel)+Math.floorMod(target-from,symbols);
        return from+distance*(1-Math.pow(1-t,3));
    }
    private ReelAnimation() {}
}

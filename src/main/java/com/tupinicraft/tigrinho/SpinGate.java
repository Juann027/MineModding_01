package com.tupinicraft.tigrinho;
/** Main-thread sequence and packet-rate gate, scoped to one server-issued nonce. */
public final class SpinGate {
    private int sequence;
    private long lastTick=Long.MIN_VALUE;
    public int sequence() { return sequence; }
    public boolean accept(int received,long tick) {
        if(received!=sequence || tick==lastTick || sequence==Integer.MAX_VALUE) return false;
        lastTick=tick; sequence++; return true;
    }
}

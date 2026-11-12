package com.tupinicraft.client;
/** Pure pagination arithmetic shared by the ballot and regression checks. */
public final class BallotLayout {
    private BallotLayout() {}
    public static int rows(int height) { return Math.max(1, (height - 178) / 27); }
    public static int pages(int count, int rows) { return Math.max(1, (count + rows - 1) / rows); }
    public static int clampPage(int page, int count, int rows) { return Math.max(0, Math.min(page, pages(count, rows) - 1)); }
}

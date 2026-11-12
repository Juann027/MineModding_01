package com.tupinicraft;
import com.tupinicraft.client.BallotLayout;
import com.tupinicraft.client.AnnouncementLayout;
import java.util.List;
public final class CivicUiSelfTest {
    public static void main(String[] args) {
        int checks = 0;
        for (int h = 224; h <= 336; h++) {
            int rows = BallotLayout.rows(h);
            if (76 + (rows - 1) * 27 + 23 > h - 99) throw new AssertionError("Footer overlap");
            for (int count : new int[]{0, 1, 2, 5, 6, 30, 501}) {
                int pages = BallotLayout.pages(count, rows);
                int seen = 0;
                for (int p = 0; p < pages; p++) seen += Math.max(0, Math.min(count, (p + 1) * rows) - p * rows);
                if (seen != count) throw new AssertionError("Missing candidates");
                if (BallotLayout.clampPage(999, count, rows) != pages - 1) throw new AssertionError("Stale page");
                if (BallotLayout.clampPage(-1, count, rows) != 0) throw new AssertionError("Negative page");
                checks += 3;
            }
            checks++;
        }
        for (int width : new int[]{320, 427, 640, 854, 1280}) {
            var obstacle = new AnnouncementLayout.Rect(width - 154, 2, width - 2, 100);
            var placement = AnnouncementLayout.place(width, 240, 32, List.of(obstacle), 286, 12,
                    textWidth -> new int[]{textWidth + 24, 88});
            var box = placement.box();
            if (box.left() < 0 || box.right() > width || box.top() < 36 || box.intersects(obstacle, 4))
                throw new AssertionError("Event collision at " + width);
            checks++;
        }
        System.out.println("Civic UI: " + checks + " checks passed");
    }
}

import com.tupinicraft.testgame.FinisherRules;

/** java -cp /tmp/tupini-test FinisherRulesTest */
public final class FinisherRulesTest {
    private static void check(boolean value) { if (!value) throw new AssertionError("Rule check failed"); }
    public static void main(String[] args) {
        check(FinisherRules.isVulnerable(4.99F, 20F));
        check(!FinisherRules.isVulnerable(5F, 20F));
        check(!FinisherRules.isVulnerable(0F, 20F));
        check(!FinisherRules.isVulnerable(5F, 0F));
        check(FinisherRules.isValidMobSize(0.6F, 1.9F));
        check(!FinisherRules.isValidMobSize(2F, 3F));
        check(!FinisherRules.isCooldownOver(99, 100));
        check(FinisherRules.isCooldownOver(100, 100));
        check(FinisherRules.ANIMATION_TICKS == 60);
        check(FinisherRules.INCAPACITATED_TICKS == 200);
        System.out.println("FinisherRules: 10 checks passed");
    }
}

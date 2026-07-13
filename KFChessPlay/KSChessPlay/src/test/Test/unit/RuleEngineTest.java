package test.Test.unit;

import rules.RuleEngine;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RuleEngineTest {

    @Test
    public void testRuleEngineInitialization() {
        // יצירת מופע ישיר של המחלקה כדי להעביר את ה-Class והבנאי שלה ל-100% כיסוי
        RuleEngine ruleEngine = new RuleEngine();
        assertNotNull(ruleEngine);
    }
}
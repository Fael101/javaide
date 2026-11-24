package com.duy.ide.ai.context;

import com.duy.ide.ai.prompt.TokenBudget;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TokenBudgetTest {

    @Test
    public void estimatesRoughlyFourCharactersPerToken() {
        assertEquals(0, TokenBudget.estimate(null));
        assertEquals(0, TokenBudget.estimate(""));
        assertEquals(1, TokenBudget.estimate("a"));
        assertEquals(1, TokenBudget.estimate("abcd"));
        assertEquals(2, TokenBudget.estimate("abcde"));
    }

    @Test
    public void leavesShortTextAlone() {
        assertEquals("hello", TokenBudget.truncate("hello", 100));
    }

    @Test
    public void truncatesLongTextAndMarksTheGap() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 1000; i++) {
            text.append("0123456789");
        }
        String truncated = TokenBudget.truncate(text.toString(), 100);
        assertTrue(truncated.length() < text.length());
        assertTrue(truncated.contains("characters omitted"));
        assertTrue(truncated.startsWith("0123456789"));
        assertTrue(truncated.endsWith("0123456789"));
    }
}

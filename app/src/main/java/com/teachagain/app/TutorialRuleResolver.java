package com.teachagain.app;

import java.util.List;
import java.util.Objects;

/** Only exact copies of a reviewed tutorial may reuse its page-completion rules. */
public final class TutorialRuleResolver {
    private TutorialRuleResolver() {}
    public static String resolve(Tutorial tutorial) {
        if (tutorial == null || tutorial.steps == null) return null;
        for (Tutorial known : TutorialRepository.builtinTutorials()) {
            if (sameSteps(tutorial.steps, known.steps)) return known.id;
        }
        return null;
    }
    private static boolean sameSteps(List<Tutorial.Step> left, List<Tutorial.Step> right) {
        if (left.size() != right.size()) return false;
        for (int i = 0; i < left.size(); i++) {
            Tutorial.Step a = left.get(i), b = right.get(i);
            if (a == null || b == null || !Objects.equals(a.title, b.title)
                    || !Objects.equals(a.desc, b.desc)) return false;
        }
        return true;
    }
}

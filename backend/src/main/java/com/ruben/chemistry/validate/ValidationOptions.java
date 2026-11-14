package com.ruben.chemistry.validate;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

/**
 * Which {@link Rule}s a {@link MoleculeValidator} reports.
 */
public final class ValidationOptions
{
    private final Set<Rule> enabled;

    private ValidationOptions(Set<Rule> enabled)
    {
        this.enabled = enabled;
    }

    /**
     * @return the rules that are on unless asked otherwise
     */
    public static ValidationOptions defaults()
    {
        EnumSet<Rule> rules = EnumSet.noneOf(Rule.class);
        Arrays.stream(Rule.values()).filter(Rule::isEnabledByDefault).forEach(rules::add);
        return new ValidationOptions(rules);
    }

    /**
     * @return every rule, including those a caller normally has to opt into
     */
    public static ValidationOptions all()
    {
        return new ValidationOptions(EnumSet.allOf(Rule.class));
    }

    /**
     * @param rules the rules to report
     * @return just those rules
     */
    public static ValidationOptions only(Rule... rules)
    {
        EnumSet<Rule> chosen = EnumSet.noneOf(Rule.class);
        chosen.addAll(Arrays.asList(rules));
        return new ValidationOptions(chosen);
    }

    /**
     * @param rules the rules to add
     * @return these options with those rules reported as well
     */
    public ValidationOptions with(Rule... rules)
    {
        EnumSet<Rule> chosen = EnumSet.copyOf(enabled);
        chosen.addAll(Arrays.asList(rules));
        return new ValidationOptions(chosen);
    }

    /**
     * @param rules the rules to drop
     * @return these options with those rules no longer reported
     */
    public ValidationOptions without(Rule... rules)
    {
        EnumSet<Rule> chosen = EnumSet.copyOf(enabled);
        Arrays.asList(rules).forEach(chosen::remove);
        return new ValidationOptions(chosen);
    }

    /**
     * @param rule a rule
     * @return whether it is reported
     */
    public boolean reports(Rule rule)
    {
        return enabled.contains(rule);
    }

    /**
     * @param rules the rules a check is responsible for
     * @return whether any of them is reported, i.e. whether the check is worth running
     */
    public boolean reportsAnyOf(Set<Rule> rules)
    {
        return rules.stream().anyMatch(this::reports);
    }
}

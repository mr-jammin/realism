package com.realism;

import java.awt.Color;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/**
 * Configuration for the Realism plugin.  This config exposes settings for
 * hunger, thirst and durability meters.  Users can tune drain rates, colours,
 * threshold messages and notification behaviour.  It also includes an
 * option to restrict equipping items with zero durability.
 */
@ConfigGroup("realism")
public interface RealismConfig extends Config
{
    // General
    /**
     * Show the hunger meter.
     */
    @ConfigItem(
        keyName = "showHunger",
        name = "Show Hunger Meter",
        description = "Display the hunger meter orb"
    )
    default boolean showHunger()
    {
        return true;
    }

    /**
     * Show the thirst meter.
     */
    @ConfigItem(
        keyName = "showThirst",
        name = "Show Thirst Meter",
        description = "Display the thirst meter orb"
    )
    default boolean showThirst()
    {
        return true;
    }

    /**
     * Show the durability meter.
     */
    @ConfigItem(
        keyName = "showDurability",
        name = "Show Durability Meter",
        description = "Display an aggregate durability meter orb"
    )
    default boolean showDurability()
    {
        return true;
    }

    // Drain rates
    /**
     * How many minutes it takes for the hunger meter to drain from 100% to 0%.
     */
    @ConfigItem(
        keyName = "hungerDrainRate",
        name = "Hunger Drain Rate (minutes)",
        description = "Number of minutes for hunger to drain from full to empty"
    )
    @Range(min = 1, max = 120)
    default int hungerDrainRate()
    {
        return 30;
    }

    /**
     * How many minutes it takes for the thirst meter to drain from 100% to 0%.
     */
    @ConfigItem(
        keyName = "thirstDrainRate",
        name = "Thirst Drain Rate (minutes)",
        description = "Number of minutes for thirst to drain from full to empty"
    )
    @Range(min = 1, max = 120)
    default int thirstDrainRate()
    {
        return 20;
    }

    /**
     * How many minutes it takes for an item's durability to drain from full to broken.
     */
    @ConfigItem(
        keyName = "durabilityDrainRate",
        name = "Durability Drain Rate (minutes)",
        description = "Approximate minutes for an equipped item's durability to drain from full to broken"
    )
    @Range(min = 1, max = 240)
    default int durabilityDrainRate()
    {
        return 60;
    }

    // Colours
    @ConfigItem(
        keyName = "hungerColour",
        name = "Hunger Colour",
        description = "Colour of the hunger orb when above low threshold"
    )
    default Color hungerColour()
    {
        return Color.ORANGE;
    }

    @ConfigItem(
        keyName = "thirstColour",
        name = "Thirst Colour",
        description = "Colour of the thirst orb when above low threshold"
    )
    default Color thirstColour()
    {
        return Color.CYAN;
    }

    @ConfigItem(
        keyName = "durabilityColour",
        name = "Durability Colour",
        description = "Colour of the durability orb when above low threshold"
    )
    default Color durabilityColour()
    {
        return Color.YELLOW;
    }

    // Low thresholds
    @ConfigItem(
        keyName = "hungerLowThreshold",
        name = "Hunger Low Threshold (%)",
        description = "Percentage at which to warn about low hunger"
    )
    @Range(min = 1, max = 99)
    default int hungerLowThreshold()
    {
        return 20;
    }

    @ConfigItem(
        keyName = "thirstLowThreshold",
        name = "Thirst Low Threshold (%)",
        description = "Percentage at which to warn about low thirst"
    )
    @Range(min = 1, max = 99)
    default int thirstLowThreshold()
    {
        return 20;
    }

    @ConfigItem(
        keyName = "durabilityLowThreshold",
        name = "Durability Low Threshold (%)",
        description = "Percentage at which to warn about low durability on any equipped item"
    )
    @Range(min = 1, max = 99)
    default int durabilityLowThreshold()
    {
        return 20;
    }

    // Messages and notifications
    @ConfigItem(
        keyName = "hungerLowMessage",
        name = "Hunger Low Message",
        description = "Chat message when hunger falls below the low threshold"
    )
    default String hungerLowMessage()
    {
        return "You feel hungry.";
    }

    @ConfigItem(
        keyName = "hungerCriticalMessage",
        name = "Hunger Critical Message",
        description = "Chat message when hunger is depleted"
    )
    default String hungerCriticalMessage()
    {
        return "You are starving!";
    }

    @ConfigItem(
        keyName = "thirstLowMessage",
        name = "Thirst Low Message",
        description = "Chat message when thirst falls below the low threshold"
    )
    default String thirstLowMessage()
    {
        return "You feel thirsty.";
    }

    @ConfigItem(
        keyName = "thirstCriticalMessage",
        name = "Thirst Critical Message",
        description = "Chat message when thirst is depleted"
    )
    default String thirstCriticalMessage()
    {
        return "You are parched!";
    }

    @ConfigItem(
        keyName = "durabilityLowMessage",
        name = "Durability Low Message",
        description = "Chat message when any equipped item falls below the low durability threshold"
    )
    default String durabilityLowMessage()
    {
        return "Your gear is getting worn down.";
    }

    @ConfigItem(
        keyName = "durabilityBrokenMessage",
        name = "Durability Broken Message",
        description = "Chat message when any equipped item reaches zero durability"
    )
    default String durabilityBrokenMessage()
    {
        return "Your gear has broken!";
    }

    @ConfigItem(
        keyName = "notifyHunger",
        name = "Notify Hunger",
        description = "Trigger RuneLite desktop notifications for hunger alerts"
    )
    default boolean notifyHunger()
    {
        return true;
    }

    @ConfigItem(
        keyName = "notifyThirst",
        name = "Notify Thirst",
        description = "Trigger RuneLite desktop notifications for thirst alerts"
    )
    default boolean notifyThirst()
    {
        return true;
    }

    @ConfigItem(
        keyName = "notifyDurability",
        name = "Notify Durability",
        description = "Trigger RuneLite desktop notifications for durability alerts"
    )
    default boolean notifyDurability()
    {
        return true;
    }

    // Consumption behaviour
    /**
     * Percentage of HP restored by food that should be applied to the hunger meter.
     * For example, a value of 1.0 means 1 HP healed restores 1 hunger point.
     */
    @ConfigItem(
        keyName = "foodHealWeight",
        name = "Food Heal Weight",
        description = "Multiplier for converting HP restored by food into hunger restoration"
    )
    default double foodHealWeight()
    {
        return 1.0;
    }

    /**
     * Penalty applied to hunger when taking poison or disease damage.  This is
     * applied as half the HP lost multiplied by this value.
     */
    @ConfigItem(
        keyName = "poisonPenalty",
        name = "Poison Penalty",
        description = "Multiplier for converting HP lost from poison/disease into hunger loss"
    )
    default double poisonPenalty()
    {
        return 0.5;
    }

    /**
     * Fixed amount of thirst to restore when drinking a potion or beverage.
     */
    @ConfigItem(
        keyName = "potionRestore",
        name = "Potion Thirst Restore",
        description = "Amount of thirst restored when drinking a potion or beverage"
    )
    @Range(min = 0, max = 100)
    default int potionRestore()
    {
        return 10;
    }

    // Icons
    enum HungerIcon
    {
        MEAT,
        FISH,
        CABBAGE
    }

    @ConfigItem(
        keyName = "hungerIcon",
        name = "Hunger Icon",
        description = "Icon used for the hunger orb"
    )
    default HungerIcon hungerIcon()
    {
        return HungerIcon.MEAT;
    }

    /**
     * Restrict equipping items with zero durability.  When enabled, the plugin
     * will cancel equip actions on broken gear and display a warning.
     */
    @ConfigItem(
        keyName = "restrictBrokenEquip",
        name = "Restrict Broken Equip",
        description = "Prevent equipping items with zero durability"
    )
    default boolean restrictBrokenEquip()
    {
        return true;
    }
}
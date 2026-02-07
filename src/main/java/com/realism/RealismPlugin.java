/*
 * Copyright (c) 2026, mr-jammin
 * All rights reserved.
 * Licensed under BSD 2-Clause; see the LICENSE file.
 */
package com.realism;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

@PluginDescriptor(
	name = "Realism",
	description = "Adds hunger, thirst and equipment durability meters with immersive orbs and restricts equipping broken items.",
	tags = {"realism", "hunger", "thirst", "durability", "status", "orb"}
)
public class RealismPlugin extends Plugin
{
	private static final int TICKS_PER_MINUTE = 100;
	private static final Set<String> EQUIP_OPTIONS = ImmutableSet.of("Wield", "Wear", "Equip", "Hold");
	private static final Set<MenuAction> EQUIP_ACTIONS = ImmutableSet.of(MenuAction.CC_OP, MenuAction.CC_OP_LOW_PRIORITY);

	@Inject
	private Client client;
	@Inject
	private RealismConfig config;
	@Inject
	private ItemManager itemManager;
	@Inject
	private SpriteManager spriteManager;
	@Inject
	private OverlayManager overlayManager;
	@Inject
	private Notifier notifier;
	@Inject
	private RealismOrbOverlay orbOverlay;

	private final MeterState hunger = new MeterState(100.0);
	private final MeterState thirst = new MeterState(100.0);
	private final DurabilityTracker durabilityTracker = new DurabilityTracker();
	private final ConsumptionDetector consumptionDetector = new ConsumptionDetector();

	private BufferedImage hungerIcon;
	private BufferedImage thirstIcon;
	private BufferedImage durabilityIcon;
	private RealismConfig.HungerIcon cachedHungerIcon;
	private boolean iconsDirty = true;

	@Provides
	RealismConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(RealismConfig.class);
	}

	@Override
	protected void startUp()
	{
		resetState();
		updateInventorySnapshot();
		updateEquipmentSnapshot();
		overlayManager.add(orbOverlay);
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(orbOverlay);
		resetState();
		consumptionDetector.reset();
		durabilityTracker.reset();
		invalidateIcons();
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if ("realism".equals(event.getGroup()))
		{
			invalidateIcons();
		}
	}

	@Subscribe
	public void onGameTick(GameTick tick)
	{
		if (client.getGameState() != GameState.LOGGED_IN)
		{
			return;
		}

		hunger.drain(perTickLoss(config.hungerDrainRate()));
		thirst.drain(perTickLoss(config.thirstDrainRate()));
		durabilityTracker.drain(perTickLoss(config.durabilityDrainRate()));

		checkHungerThresholds();
		checkThirstThresholds();
		checkDurabilityThresholds();
	}

	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		int containerId = event.getContainerId();
		if (containerId == InventoryID.INVENTORY.getId())
		{
			consumptionDetector.handleInventoryChange(event.getItemContainer(), this::handleItemConsumed);
			return;
		}

		if (containerId == InventoryID.EQUIPMENT.getId())
		{
			durabilityTracker.handleEquipmentChange(event.getItemContainer());
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (!config.restrictBrokenEquip())
		{
			return;
		}

		String option = Text.removeTags(event.getMenuOption());
		if (!EQUIP_OPTIONS.contains(option))
		{
			return;
		}

		if (!EQUIP_ACTIONS.contains(event.getMenuAction()))
		{
			return;
		}

		int itemId = event.getItemId();
		if (itemId <= 0)
		{
			return;
		}

		Double durability = durabilityTracker.getDurability(itemId);
		if (durability != null && durability <= 0.0)
		{
			event.consume();
			sendMessage(config.durabilityBrokenMessage(), Color.RED);
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			resetState();
			updateInventorySnapshot();
			updateEquipmentSnapshot();
		}
	}

	double getHunger()
	{
		return hunger.getValue();
	}

	double getThirst()
	{
		return thirst.getValue();
	}

	double getAverageDurability()
	{
		return durabilityTracker.getAverageDurability();
	}

	BufferedImage getIconForMeter(RealismOrbOverlay.MeterType type)
	{
		ensureIcons();
		switch (type)
		{
			case HUNGER:
				return hungerIcon;
			case THIRST:
				return thirstIcon;
			case DURABILITY:
				return durabilityIcon;
			default:
				return null;
		}
	}

	Color getColourForMeter(RealismOrbOverlay.MeterType type)
	{
		switch (type)
		{
			case HUNGER:
				return config.hungerColour();
			case THIRST:
				return config.thirstColour();
			case DURABILITY:
				return config.durabilityColour();
			default:
				return Color.WHITE;
		}
	}

	private void resetState()
	{
		hunger.reset();
		thirst.reset();
		durabilityTracker.reset();
	}

	private void updateInventorySnapshot()
	{
		consumptionDetector.initializeSnapshot(client.getItemContainer(InventoryID.INVENTORY));
	}

	private void updateEquipmentSnapshot()
	{
		durabilityTracker.handleEquipmentChange(client.getItemContainer(InventoryID.EQUIPMENT));
	}

	private void handleItemConsumed(int itemId, int count)
	{
		ItemStats stats = itemManager.getItemStats(itemId, false);
		int healAmount = stats != null ? Math.max(0, stats.getHeal()) : 0;
		if (healAmount > 0)
		{
			double restore = healAmount * config.foodHealWeight() * count;
			hunger.restore(restore);
		}

		ItemComposition comp = itemManager.getItemComposition(itemId);
		if (comp != null)
		{
			String name = Text.removeTags(comp.getName()).toLowerCase();
			if (isBeverage(name))
			{
				int restore = config.potionRestore() * count;
				thirst.restore(restore);
			}
		}
	}

	private boolean isBeverage(String name)
	{
		return name.contains("potion")
			|| name.contains("brew")
			|| name.contains("rum")
			|| name.contains("ale")
			|| name.contains("beer")
			|| name.contains("wine")
			|| name.contains("cider")
			|| name.contains("milk")
			|| name.contains("tea")
			|| name.contains("water")
			|| name.contains("coffee")
			|| name.contains("gourd")
			|| name.contains("jug")
			|| name.contains("flask")
			|| name.contains("waterskin");
	}

	private void checkHungerThresholds()
	{
		checkMeterThresholds(
			hunger,
			config.hungerLowThreshold(),
			config.hungerLowMessage(),
			config.hungerCriticalMessage(),
			Color.ORANGE,
			Color.RED,
			config.notifyHunger()
		);
	}

	private void checkThirstThresholds()
	{
		checkMeterThresholds(
			thirst,
			config.thirstLowThreshold(),
			config.thirstLowMessage(),
			config.thirstCriticalMessage(),
			Color.CYAN,
			Color.RED,
			config.notifyThirst()
		);
	}

	private void checkMeterThresholds(
		MeterState meter,
		int lowThreshold,
		String lowMessage,
		String criticalMessage,
		Color lowColour,
		Color criticalColour,
		boolean notifyDesktop
	)
	{
		double value = meter.getValue();
		if (value <= 0.0)
		{
			if (!meter.isCritSent())
			{
				sendMessage(criticalMessage, criticalColour);
				if (notifyDesktop)
				{
					notifier.notify(criticalMessage);
				}
				meter.setCritSent(true);
				meter.setLowSent(true);
			}
			return;
		}

		if (value <= lowThreshold)
		{
			if (!meter.isLowSent())
			{
				sendMessage(lowMessage, lowColour);
				if (notifyDesktop)
				{
					notifier.notify(lowMessage);
				}
				meter.setLowSent(true);
			}
			meter.setCritSent(false);
			return;
		}

		meter.setLowSent(false);
		meter.setCritSent(false);
	}

	private void checkDurabilityThresholds()
	{
		double minDurability = durabilityTracker.getMinimumDurability();
		int lowThreshold = config.durabilityLowThreshold();
		if (minDurability <= 0.0)
		{
			if (!durabilityTracker.isCritSent())
			{
				sendMessage(config.durabilityBrokenMessage(), Color.RED);
				if (config.notifyDurability())
				{
					notifier.notify(config.durabilityBrokenMessage());
				}
				durabilityTracker.setCritSent(true);
				durabilityTracker.setLowSent(true);
			}
			return;
		}

		if (minDurability <= lowThreshold)
		{
			if (!durabilityTracker.isLowSent())
			{
				sendMessage(config.durabilityLowMessage(), Color.YELLOW);
				if (config.notifyDurability())
				{
					notifier.notify(config.durabilityLowMessage());
				}
				durabilityTracker.setLowSent(true);
			}
			durabilityTracker.setCritSent(false);
			return;
		}

		durabilityTracker.setLowSent(false);
		durabilityTracker.setCritSent(false);
	}

	private void sendMessage(String message, Color colour)
	{
		String colourTag = String.format("<col=%06x>", colour.getRGB() & 0xFFFFFF);
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", colourTag + message, null);
	}

	private double perTickLoss(int drainMinutes)
	{
		if (drainMinutes <= 0)
		{
			return 0.0;
		}
		return 100.0 / (drainMinutes * (double) TICKS_PER_MINUTE);
	}

	private void ensureIcons()
	{
		RealismConfig.HungerIcon iconSetting = config.hungerIcon();
		if (iconsDirty || hungerIcon == null || cachedHungerIcon != iconSetting)
		{
			cachedHungerIcon = iconSetting;
			int itemId;
			switch (iconSetting)
			{
				case FISH:
					itemId = ItemID.TROUT;
					break;
				case CABBAGE:
					itemId = ItemID.CABBAGE;
					break;
				case MEAT:
				default:
					itemId = ItemID.COOKED_MEAT;
					break;
			}
			hungerIcon = spriteManager.getSprite(itemId, 0);
		}

		if (iconsDirty || thirstIcon == null)
		{
			thirstIcon = spriteManager.getSprite(ItemID.VIAL_OF_WATER, 0);
		}

		if (iconsDirty || durabilityIcon == null)
		{
			durabilityIcon = spriteManager.getSprite(ItemID.IRON_FULL_HELM, 0);
		}

		iconsDirty = hungerIcon == null || thirstIcon == null || durabilityIcon == null;
	}

	private void invalidateIcons()
	{
		iconsDirty = true;
	}
}package com.realism;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Provides;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemComposition;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemID;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.client.Notifier;
import net.runelite.client.chat.ChatMessageManager;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.game.SpriteManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.util.Text;

/**
 * Core plugin class for the Realism plugin.  This class is responsible for
 * tracking hunger, thirst and durability values, updating them over time
 * and responding to game events such as eating food, drinking potions or
 * equipping items.  It also handles sending chat messages and notifications
 * when thresholds are crossed, and restricts equipping broken items if
 * configured to do so.
 */
@Slf4j
@PluginDescriptor(
    name = "Realism",
    description = "Adds hunger, thirst and equipment durability meters with immersive orbs and restricts equipping broken items.",
    tags = {"realism", "hunger", "thirst", "durability", "status", "orb"}
)
public class RealismPlugin extends Plugin
{
    // Injected RuneLite services
    @Inject
    private Client client;
    @Inject
    private RealismConfig config;
    @Inject
    private ItemManager itemManager;
    @Inject
    private SpriteManager spriteManager;
    @Inject
    private OverlayManager overlayManager;
    @Inject
    private Notifier notifier;
    @Inject
    private ChatMessageManager chatMessageManager;
    @Inject
    private RealismOrbOverlay orbOverlay;

    // Current hunger and thirst values (0–100)
    @Getter
    private double hunger = 100.0;
    @Getter
    private double thirst = 100.0;

    // Durability for each equipped item, keyed by item ID.  Values are 0–100.
    private final Map<Integer, Double> durabilityMap = new HashMap<>();

    // Snapshots of inventory and equipment state to detect consumption and equipment changes
    private final Map<Integer, Integer> inventorySnapshot = new HashMap<>();
    private final Map<Integer, Integer> equipmentSnapshot = new HashMap<>();

    // Notification flags to avoid spamming messages
    private boolean hungerLowSent;
    private boolean hungerCritSent;
    private boolean thirstLowSent;
    private boolean thirstCritSent;
    private boolean durabilityLowSent;
    private boolean durabilityCritSent;

    // Menu options that represent equipping an item
    private static final Set<String> EQUIP_OPTIONS = ImmutableSet.of("Wield", "Wear", "Equip", "Hold");

    /**
     * Provides the plugin configuration to RuneLite's config system.
     *
     * @param configManager the config manager
     * @return the realism config instance
     */
    @Provides
    RealismConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(RealismConfig.class);
    }

    @Override
    protected void startUp() throws Exception
    {
        resetMeters();
        updateInventorySnapshot();
        updateEquipmentSnapshot();
        overlayManager.add(orbOverlay);
    }

    @Override
    protected void shutDown() throws Exception
    {
        overlayManager.remove(orbOverlay);
        durabilityMap.clear();
        inventorySnapshot.clear();
        equipmentSnapshot.clear();
    }

    /**
     * Reset all meter values and notification flags.  Called on startup and
     * whenever the player logs in.
     */
    private void resetMeters()
    {
        hunger = 100.0;
        thirst = 100.0;
        hungerLowSent = false;
        hungerCritSent = false;
        thirstLowSent = false;
        thirstCritSent = false;
        durabilityLowSent = false;
        durabilityCritSent = false;
        durabilityMap.clear();
    }

    /**
     * Called every game tick (~0.6s).  Degrades meters over time, updates
     * durability values and sends alerts when thresholds are crossed.
     */
    @Subscribe
    public void onGameTick(GameTick tick)
    {
        // Degrade hunger and thirst
        degradeHunger();
        degradeThirst();

        // Degrade durability for equipped items
        degradeDurability();

        // Send alerts if needed
        checkHungerThresholds();
        checkThirstThresholds();
        checkDurabilityThresholds();
    }

    /**
     * Decrease the hunger meter based on the configured drain rate.
     */
    private void degradeHunger()
    {
        int drainMinutes = config.hungerDrainRate();
        // Avoid division by zero; treat very small rates as no drain
        if (drainMinutes <= 0)
        {
            return;
        }
        double perTickLoss = 100.0 / (drainMinutes * 100.0); // 100 ticks per minute
        hunger = Math.max(0.0, hunger - perTickLoss);
    }

    /**
     * Decrease the thirst meter based on the configured drain rate.
     */
    private void degradeThirst()
    {
        int drainMinutes = config.thirstDrainRate();
        if (drainMinutes <= 0)
        {
            return;
        }
        double perTickLoss = 100.0 / (drainMinutes * 100.0);
        thirst = Math.max(0.0, thirst - perTickLoss);
    }

    /**
     * Decrease the durability of all equipped items.  Durability drains
     * proportionally to the configured minutes.
     */
    private void degradeDurability()
    {
        int drainMinutes = config.durabilityDrainRate();
        if (drainMinutes <= 0)
        {
            return;
        }
        double perTickLoss = 100.0 / (drainMinutes * 100.0);
        for (Map.Entry<Integer, Double> entry : durabilityMap.entrySet())
        {
            double newVal = entry.getValue() - perTickLoss;
            if (newVal < 0.0)
            {
                newVal = 0.0;
            }
            entry.setValue(newVal);
        }
    }

    /**
     * Send hunger alerts and reset flags based on current hunger value and
     * thresholds configured by the user.
     */
    private void checkHungerThresholds()
    {
        int lowThreshold = config.hungerLowThreshold();
        if (hunger <= 0.0)
        {
            if (!hungerCritSent)
            {
                sendMessage(config.hungerCriticalMessage(), Color.RED);
                if (config.notifyHunger())
                {
                    notifier.notify(config.hungerCriticalMessage());
                }
                hungerCritSent = true;
                hungerLowSent = true; // also mark low as sent
            }
        }
        else if (hunger <= lowThreshold)
        {
            if (!hungerLowSent)
            {
                sendMessage(config.hungerLowMessage(), Color.ORANGE);
                if (config.notifyHunger())
                {
                    notifier.notify(config.hungerLowMessage());
                }
                hungerLowSent = true;
            }
            // Reset critical flag if above zero
            hungerCritSent = false;
        }
        else
        {
            // Reset flags if meter recovered above low threshold
            hungerLowSent = false;
            hungerCritSent = false;
        }
    }

    /**
     * Send thirst alerts and reset flags based on current thirst value and
     * thresholds configured by the user.
     */
    private void checkThirstThresholds()
    {
        int lowThreshold = config.thirstLowThreshold();
        if (thirst <= 0.0)
        {
            if (!thirstCritSent)
            {
                sendMessage(config.thirstCriticalMessage(), Color.RED);
                if (config.notifyThirst())
                {
                    notifier.notify(config.thirstCriticalMessage());
                }
                thirstCritSent = true;
                thirstLowSent = true;
            }
        }
        else if (thirst <= lowThreshold)
        {
            if (!thirstLowSent)
            {
                sendMessage(config.thirstLowMessage(), Color.CYAN);
                if (config.notifyThirst())
                {
                    notifier.notify(config.thirstLowMessage());
                }
                thirstLowSent = true;
            }
            thirstCritSent = false;
        }
        else
        {
            thirstLowSent = false;
            thirstCritSent = false;
        }
    }

    /**
     * Send durability alerts when any equipped item crosses thresholds and
     * reset flags accordingly.
     */
    private void checkDurabilityThresholds()
    {
        // Determine min durability across all items
        double minDurability = 100.0;
        for (double val : durabilityMap.values())
        {
            if (val < minDurability)
            {
                minDurability = val;
            }
        }
        int lowThreshold = config.durabilityLowThreshold();
        if (minDurability <= 0.0)
        {
            if (!durabilityCritSent)
            {
                sendMessage(config.durabilityBrokenMessage(), Color.RED);
                if (config.notifyDurability())
                {
                    notifier.notify(config.durabilityBrokenMessage());
                }
                durabilityCritSent = true;
                durabilityLowSent = true;
            }
        }
        else if (minDurability <= lowThreshold)
        {
            if (!durabilityLowSent)
            {
                sendMessage(config.durabilityLowMessage(), Color.YELLOW);
                if (config.notifyDurability())
                {
                    notifier.notify(config.durabilityLowMessage());
                }
                durabilityLowSent = true;
            }
            durabilityCritSent = false;
        }
        else
        {
            durabilityLowSent = false;
            durabilityCritSent = false;
        }
    }

    /**
     * Handle inventory changes to detect consumption of food and beverages.
     * Restores hunger and thirst based on food healing and potion detection.
     */
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        int containerId = event.getContainerId();
        if (containerId == InventoryID.INVENTORY.getId())
        {
            handleInventoryChange(event.getItemContainer());
        }
        else if (containerId == InventoryID.EQUIPMENT.getId())
        {
            handleEquipmentChange(event.getItemContainer());
        }
    }

    /**
     * Detect items consumed from the inventory and adjust meters.  This method
     * compares the current inventory state with the stored snapshot and
     * determines which items have decreased in quantity.  It treats items
     * with positive heal values as food and items with beverage keywords in
     * their names as drinks.
     *
     * @param container the current inventory container
     */
    private void handleInventoryChange(ItemContainer container)
    {
        if (container == null)
        {
            return;
        }
        Map<Integer, Integer> newSnapshot = new HashMap<>();
        // Build new snapshot counts
        for (int slot = 0; slot < container.size(); slot++)
        {
            int itemId = container.getItemId(slot);
            int qty = container.getQuantity(slot);
            if (itemId > 0 && qty > 0)
            {
                newSnapshot.merge(itemId, qty, Integer::sum);
            }
        }
        // Compare with previous snapshot
        for (Map.Entry<Integer, Integer> entry : inventorySnapshot.entrySet())
        {
            int itemId = entry.getKey();
            int oldQty = entry.getValue();
            int newQty = newSnapshot.getOrDefault(itemId, 0);
            int consumed = oldQty - newQty;
            if (consumed > 0)
            {
                // Handle consumption of 'consumed' quantity of this item
                handleItemConsumed(itemId, consumed);
            }
        }
        // Items in new snapshot but not in old snapshot are new, ignore
        inventorySnapshot.clear();
        inventorySnapshot.putAll(newSnapshot);
    }

    /**
     * Adjust hunger and thirst based on the consumed item.
     *
     * @param itemId  the ID of the consumed item
     * @param count   how many were consumed
     */
    private void handleItemConsumed(int itemId, int count)
    {
        // Determine if the item is edible and how much HP it heals
        ItemStats stats = itemManager.getItemStats(itemId, false);
        int healAmount = 0;
        if (stats != null && stats.getHeal() > 0)
        {
            healAmount = stats.getHeal();
        }

        // Restore hunger based on heal amount
        if (healAmount > 0)
        {
            double restore = healAmount * config.foodHealWeight() * count;
            hunger = Math.min(100.0, hunger + restore);
        }

        // Determine if the item counts as a drink; check name for beverage keywords
        ItemComposition comp = itemManager.getItemComposition(itemId);
        if (comp != null)
        {
            String name = Text.removeTags(comp.getName()).toLowerCase();
            if (isBeverage(name))
            {
                int restore = config.potionRestore() * count;
                thirst = Math.min(100.0, thirst + restore);
            }
        }
    }

    /**
     * Basic heuristic to determine if an item name represents a drink or
     * beverage.  Potions, brews, ales, wines, ciders, beers and waters are
     * considered drinks.
     *
     * @param name the lowercase item name
     * @return true if it is a drink
     */
    private boolean isBeverage(String name)
    {
        return name.contains("potion") || name.contains("brew") || name.contains("rum") ||
            name.contains("ale") || name.contains("beer") || name.contains("wine") ||
            name.contains("cider") || name.contains("milk") || name.contains("tea") ||
            name.contains("water") || name.contains("coffee") || name.contains("gourd") ||
            name.contains("jug") || name.contains("flask") || name.contains("waterskin");
    }

    /**
     * Detect equipment changes and update the durability map.  New items are
     * initialised at full durability and removed items are dropped from the
     * map.
     *
     * @param container the current equipment container
     */
    private void handleEquipmentChange(ItemContainer container)
    {
        if (container == null)
        {
            return;
        }
        Map<Integer, Integer> newSnapshot = new HashMap<>();
        for (int slot = 0; slot < container.size(); slot++)
        {
            int itemId = container.getItemId(slot);
            int qty = container.getQuantity(slot);
            if (itemId > 0 && qty > 0)
            {
                newSnapshot.merge(itemId, qty, Integer::sum);
            }
        }
        // Items removed: decrease durability map entries
        for (Integer itemId : new HashSet<>(equipmentSnapshot.keySet()))
        {
            if (!newSnapshot.containsKey(itemId))
            {
                durabilityMap.remove(itemId);
            }
        }
        // Items added: initialise durability to 100 if not present
        for (Integer itemId : newSnapshot.keySet())
        {
            if (!durabilityMap.containsKey(itemId))
            {
                durabilityMap.put(itemId, 100.0);
            }
        }
        equipmentSnapshot.clear();
        equipmentSnapshot.putAll(newSnapshot);
    }

    /**
     * Intercept menu clicks to prevent equipping broken items when configured.
     * If the player attempts to equip an item with zero durability, the click
     * event is consumed and a warning message is displayed.
     */
    @Subscribe
    public void onMenuOptionClicked(MenuOptionClicked event)
    {
        if (!config.restrictBrokenEquip())
        {
            return;
        }
        // We only care about equipping actions
        String option = event.getMenuOption();
        if (!EQUIP_OPTIONS.contains(option))
        {
            return;
        }
        int itemId = event.getItemId();
        if (itemId <= 0)
        {
            return;
        }
        Double dura = durabilityMap.get(itemId);
        if (dura != null && dura <= 0.0)
        {
            // Cancel the equip action
            event.consume();
            sendMessage(config.durabilityBrokenMessage(), Color.RED);
        }
    }

    /**
     * Reset meter states when game state changes (e.g. log in).  This clears
     * previous snapshots and reinitialises values.
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        if (event.getGameState() == GameState.LOGGED_IN)
        {
            resetMeters();
            updateInventorySnapshot();
            updateEquipmentSnapshot();
        }
    }

    /**
     * Create a snapshot of the current inventory for later comparison.
     */
    private void updateInventorySnapshot()
    {
        inventorySnapshot.clear();
        ItemContainer inv = client.getItemContainer(InventoryID.INVENTORY);
        if (inv == null)
        {
            return;
        }
        for (int slot = 0; slot < inv.size(); slot++)
        {
            int itemId = inv.getItemId(slot);
            int qty = inv.getQuantity(slot);
            if (itemId > 0 && qty > 0)
            {
                inventorySnapshot.merge(itemId, qty, Integer::sum);
            }
        }
    }

    /**
     * Create a snapshot of the current equipment for later comparison and
     * initialise durability values for any equipped items.
     */
    private void updateEquipmentSnapshot()
    {
        equipmentSnapshot.clear();
        ItemContainer eq = client.getItemContainer(InventoryID.EQUIPMENT);
        if (eq == null)
        {
            return;
        }
        for (int slot = 0; slot < eq.size(); slot++)
        {
            int itemId = eq.getItemId(slot);
            int qty = eq.getQuantity(slot);
            if (itemId > 0 && qty > 0)
            {
                equipmentSnapshot.merge(itemId, qty, Integer::sum);
                if (!durabilityMap.containsKey(itemId))
                {
                    durabilityMap.put(itemId, 100.0);
                }
            }
        }
    }

    /**
     * Sends a coloured message to the game chat.  Colour codes are applied
     * using RuneLite's message formatting syntax.
     *
     * @param message the message to send
     * @param colour  the colour to display the message in
     */
    private void sendMessage(String message, Color colour)
    {
        String colourTag = String.format("<col=%06x>", colour.getRGB() & 0xFFFFFF);
        client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", colourTag + message, null);
    }

    /**
     * Compute the aggregate durability percentage across all equipped items.  If
     * no items are equipped, returns 100.
     *
     * @return average durability percentage
     */
    double getAverageDurability()
    {
        if (durabilityMap.isEmpty())
        {
            return 100.0;
        }
        double total = 0.0;
        for (double val : durabilityMap.values())
        {
            total += val;
        }
        return total / durabilityMap.size();
    }

    /**
     * Retrieve the sprite for a given meter type.  The icons used are
     * configurable for hunger (meat/fish/cabbage) and fixed for thirst and
     * durability.
     *
     * @param type which meter icon to retrieve
     * @return a sprite image, or null if unavailable
     */
    BufferedImage getIconForMeter(RealismOrbOverlay.MeterType type)
    {
        int itemId;
        switch (type)
        {
            case HUNGER:
                RealismConfig.HungerIcon icon = config.hungerIcon();
                switch (icon)
                {
                    case FISH:
                        itemId = ItemID.TROUT;
                        break;
                    case CABBAGE:
                        itemId = ItemID.CABBAGE;
                        break;
                    case MEAT:
                    default:
                        itemId = ItemID.COOKED_MEAT;
                        break;
                }
                break;
            case THIRST:
                itemId = ItemID.VIAL_OF_WATER;
                break;
            case DURABILITY:
                // Use a generic helmet to represent durability
                itemId = ItemID.IRON_FULL_HELM;
                break;
            default:
                return null;
        }
        return spriteManager.getSprite(itemId, 0);
    }

    /**
     * Retrieve the configured colour for the given meter type.
     *
     * @param type the meter type
     * @return a colour
     */
    Color getColourForMeter(RealismOrbOverlay.MeterType type)
    {
        switch (type)
        {
            case HUNGER:
                return config.hungerColour();
            case THIRST:
                return config.thirstColour();
            case DURABILITY:
                return config.durabilityColour();
            default:
                return Color.WHITE;
        }
    }
}
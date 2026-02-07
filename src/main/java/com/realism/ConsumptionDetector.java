/*
 * Copyright (c) 2026, mr-jammin
 * All rights reserved.
 * Licensed under BSD 2-Clause; see the LICENSE file.
 */
package com.realism;

import java.util.HashMap;
import java.util.Map;
import net.runelite.api.ItemContainer;

class ConsumptionDetector
{
	interface ConsumptionHandler
	{
		void onItemConsumed(int itemId, int count);
	}

	private final Map<Integer, Integer> inventorySnapshot = new HashMap<>();

	void reset()
	{
		inventorySnapshot.clear();
	}

	void initializeSnapshot(ItemContainer container)
	{
		inventorySnapshot.clear();
		if (container == null)
		{
			return;
		}
		for (int slot = 0; slot < container.size(); slot++)
		{
			int itemId = container.getItemId(slot);
			int qty = container.getQuantity(slot);
			if (itemId > 0 && qty > 0)
			{
				inventorySnapshot.merge(itemId, qty, Integer::sum);
			}
		}
	}

	void handleInventoryChange(ItemContainer container, ConsumptionHandler handler)
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

		for (Map.Entry<Integer, Integer> entry : inventorySnapshot.entrySet())
		{
			int itemId = entry.getKey();
			int oldQty = entry.getValue();
			int newQty = newSnapshot.getOrDefault(itemId, 0);
			int consumed = oldQty - newQty;
			if (consumed > 0)
			{
				handler.onItemConsumed(itemId, consumed);
			}
		}

		inventorySnapshot.clear();
		inventorySnapshot.putAll(newSnapshot);
	}
}
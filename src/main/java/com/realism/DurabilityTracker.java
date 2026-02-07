/*
 * Copyright (c) 2026, mr-jammin
 * All rights reserved.
 * Licensed under BSD 2-Clause; see the LICENSE file.
 */
package com.realism;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.runelite.api.ItemContainer;

class DurabilityTracker
{
	private final Map<Integer, Double> durabilityByItemId = new HashMap<>();
	private boolean lowSent;
	private boolean critSent;

	void reset()
	{
		durabilityByItemId.clear();
		lowSent = false;
		critSent = false;
	}

	void drain(double perTickLoss)
	{
		if (perTickLoss <= 0.0)
		{
			return;
		}
		for (Map.Entry<Integer, Double> entry : durabilityByItemId.entrySet())
		{
			double newValue = entry.getValue() - perTickLoss;
			entry.setValue(Math.max(0.0, newValue));
		}
	}

	void handleEquipmentChange(ItemContainer container)
	{
		if (container == null)
		{
			return;
		}

		Map<Integer, Integer> equipped = new HashMap<>();
		for (int slot = 0; slot < container.size(); slot++)
		{
			int itemId = container.getItemId(slot);
			int qty = container.getQuantity(slot);
			if (itemId > 0 && qty > 0)
			{
				equipped.merge(itemId, qty, Integer::sum);
			}
		}

		for (Iterator<Integer> it = durabilityByItemId.keySet().iterator(); it.hasNext(); )
		{
			Integer itemId = it.next();
			if (!equipped.containsKey(itemId))
			{
				it.remove();
			}
		}

		for (Integer itemId : equipped.keySet())
		{
			durabilityByItemId.putIfAbsent(itemId, 100.0);
		}
	}

	Double getDurability(int itemId)
	{
		return durabilityByItemId.get(itemId);
	}

	double getAverageDurability()
	{
		if (durabilityByItemId.isEmpty())
		{
			return 100.0;
		}
		double total = 0.0;
		for (double value : durabilityByItemId.values())
		{
			total += value;
		}
		return total / durabilityByItemId.size();
	}

	double getMinimumDurability()
	{
		if (durabilityByItemId.isEmpty())
		{
			return 100.0;
		}
		double min = 100.0;
		for (double value : durabilityByItemId.values())
		{
			if (value < min)
			{
				min = value;
			}
		}
		return min;
	}

	boolean isLowSent()
	{
		return lowSent;
	}

	void setLowSent(boolean lowSent)
	{
		this.lowSent = lowSent;
	}

	boolean isCritSent()
	{
		return critSent;
	}

	void setCritSent(boolean critSent)
	{
		this.critSent = critSent;
	}
}
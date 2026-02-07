/*
 * Copyright (c) 2026, mr-jammin
 * All rights reserved.
 * Licensed under BSD 2-Clause; see the LICENSE file.
 */
package com.realism;

class MeterState
{
	private double value;
	private boolean lowSent;
	private boolean critSent;

	MeterState(double initialValue)
	{
		this.value = initialValue;
	}

	void reset()
	{
		value = 100.0;
		lowSent = false;
		critSent = false;
	}

	void drain(double perTickLoss)
	{
		if (perTickLoss <= 0.0)
		{
			return;
		}
		value = Math.max(0.0, value - perTickLoss);
	}

	void restore(double amount)
	{
		if (amount <= 0.0)
		{
			return;
		}
		value = Math.min(100.0, value + amount);
	}

	double getValue()
	{
		return value;
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
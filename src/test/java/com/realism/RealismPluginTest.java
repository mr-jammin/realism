/*
 * Copyright (c) 2026, mr-jammin
 * All rights reserved.
 * Licensed under BSD 2-Clause; see the LICENSE file.
 */
package com.realism;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class RealismPluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(RealismPlugin.class);
		RuneLite.main(args);
	}
}
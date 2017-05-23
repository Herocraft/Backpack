/*
 * This file is part of Backpack.
 *
 * Copyright (c) 2012, AlmuraDev <http://www.almuramc.com/>
 * Backpack is licensed under the Almura Development License version 1.
 *
 * Backpack is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * As an exception, all classes which do not reference GPL licensed code
 * are hereby licensed under the GNU Lesser Public License, as described
 * in Almura Development License version 1.
 *
 * Backpack is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License,
 * the GNU Lesser Public License (for classes that fulfill the exception)
 * and the Almura Development License version 1 along with this program. If not, see
 * <http://www.gnu.org/licenses/> for the GNU General Public License and
 * the GNU Lesser Public License.
 */
package com.almuramc.backpack.storage;

import java.util.HashMap;
import java.util.UUID;

import com.almuramc.backpack.inventory.BackpackInventory;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public abstract class Storage {
	protected static final HashMap<String, HashMap<UUID, BackpackInventory>> BACKPACKS = new HashMap<String, HashMap<UUID, BackpackInventory>>();

	public final void store(OfflinePlayer player, String worldName, BackpackInventory toStore) {
		if (player == null || worldName == null) {
			return;
		}
		HashMap<UUID, BackpackInventory> playerMap = BACKPACKS.get(worldName);
		if (playerMap == null) {
			playerMap = new HashMap<UUID, BackpackInventory>();
		}
		if (playerMap.containsKey(player.getUniqueId()) && toStore == null) {
			playerMap.remove(player.getUniqueId());
			BACKPACKS.put(worldName, playerMap);
			return;
		}
		playerMap.put(player.getUniqueId(), toStore);
		BACKPACKS.put(worldName, playerMap);
	}

	public final BackpackInventory fetch(OfflinePlayer player, String worldName) {
		if (player == null || worldName == null) {
			return null;
		}
		HashMap<UUID, BackpackInventory> playerMap = BACKPACKS.get(worldName);
		if (playerMap == null) {
			return null;
		}
		BackpackInventory backpack = playerMap.get(player.getUniqueId());
		if (backpack == null) {
			return null;
		}
		return backpack;
	}

	public final boolean has(String worldName) {
		return BACKPACKS.get(worldName) != null;
	}

	public final boolean has(OfflinePlayer player, String worldName) {
		HashMap<UUID, BackpackInventory> map = BACKPACKS.get(worldName);
		return map != null && map.get(player.getUniqueId()) != null;
	}

	public abstract void initWorld(String worldName);

	public abstract BackpackInventory load(Player player, String worldName);

	public abstract BackpackInventory edit(Player player, OfflinePlayer target, String worldName);

	public abstract void save(OfflinePlayer player, String worldName, BackpackInventory backpack);

	public abstract void purge(Player player, String worldName);

	public abstract void updateSize(Player player, String worldName, int size);
}

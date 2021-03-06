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
package com.almuramc.backpack.storage.type;

import java.io.File;
import java.io.FilenameFilter;
import java.util.ArrayList;
import java.util.Set;
import java.util.logging.Logger;

import com.almuramc.backpack.BackpackPlugin;
import com.almuramc.backpack.inventory.BackpackInventory;
import com.almuramc.backpack.storage.Storage;
import com.almuramc.backpack.util.PermissionHelper;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class YamlStorage extends Storage {
	private static File STORAGE_DIR;
	private static final YamlConfiguration READER = new YamlConfiguration();

	public YamlStorage(File parentDir) {
		STORAGE_DIR = new File(parentDir, "backpacks");

		//Initialize worlds loaded on startup.
		for (World world : Bukkit.getWorlds()) {
			File worldDir = new File(STORAGE_DIR, world.getName());
			if (!worldDir.exists()) {
				worldDir.mkdirs();
			}
		}
	}

	@Override
	public void initWorld(String worldName) {
		File worldDir = new File(STORAGE_DIR, worldName);
		if (!worldDir.exists()) {
			worldDir.mkdirs();
		}
	}

	@Override
	public BackpackInventory load(Player player, String worldName) {
		BackpackInventory backpack;
		if (has(player, worldName)) {
			backpack = fetch(player, worldName);
		} else {
			backpack = loadFromFile(player, player, worldName, "Backpack");
		}
		return backpack;
	}

	@Override
	public BackpackInventory edit(Player player, OfflinePlayer target, String worldName) {
        BackpackInventory backpack;
        if (has(player, worldName)) {
            backpack = fetch(player, worldName);
        } else {
            backpack = loadFromFile(player, player, worldName, String.format("Backpack (%s)", target.getName()));
        }
        return backpack;
	}

	@Override
	public void save(OfflinePlayer player, String worldName, BackpackInventory backpack) {
		//Store this backpack to memory
		store(player, worldName, backpack);
		//Save to file
		if (!has(player, worldName)) {
			return;
		}
		saveToFile(player, worldName, backpack);
	}

	@Override
	public void updateSize(Player player, String worldName, int size) {
		if (player == null || worldName == null || !BackpackInventory.isValidSize(size)) {
			return;
		}

		File file = new File(STORAGE_DIR + File.separator + worldName, player.getName() + ".yml");
		try {
			//Load in the file to write
			READER.load(file);
			READER.set("contents-amount", size);
			READER.save(file);
		} catch (Exception ignore) {
		}
	}

	private BackpackInventory loadFromFile(Player player, OfflinePlayer target, String worldName, String backpackName) {
		File worldDir = new File(STORAGE_DIR, worldName);
		File playerDat = null;
		String[] backpacks = worldDir.list(new BackpackFilter());
		if (backpacks != null) {
    		for (String fname : backpacks) {
    			String name = fname.split(".yml")[0];
    			if (target.getName().equals(name)) {
    				playerDat = new File(worldDir, fname);
    				break;
    			}
    		}
		} else {
		    Logger.getLogger("Minecraft").severe("Could not find backpack directory " + worldName);
		}

		try {
			READER.load(playerDat);
			ArrayList<ItemStack> items = new ArrayList<ItemStack>();
			ConfigurationSection parent = READER.getConfigurationSection("backpack");
			Set<String> temp = parent.getKeys(false);
			String[] keys = temp.toArray(new String[temp.size()]);
			int psize = PermissionHelper.getMaxSizeFor(target, worldName);
			int size = READER.getInt("contents-amount", BackpackPlugin.getInstance().getCached().getDefaultSize());
			if (size > psize) {
				size = psize;
			}
			for (int i = 0; i < size; i++) {
				if (i >= keys.length) {
					break;
				} else {
					final ConfigurationSection section = parent.getConfigurationSection(keys[i]);
					ItemStack stack = section.getItemStack("ItemStack", null);
					items.add(stack);
				}
			}
			BackpackInventory backpack = new BackpackInventory(Bukkit.createInventory(player, size, backpackName));
			backpack.setContents(items.toArray(new ItemStack[items.size()]));
			return backpack;
		} catch (Exception e) {
			return new BackpackInventory(Bukkit.createInventory(player, BackpackPlugin.getInstance().getCached().getDefaultSize(), "Backpack"));
		}
	}

	private void saveToFile(OfflinePlayer player, String worldName, BackpackInventory backpack) {
		File playerBackpack = new File(STORAGE_DIR + File.separator + worldName, player.getName() + ".yml");
		try {
			if (!playerBackpack.exists()) {
				playerBackpack.createNewFile();
			} else {
				playerBackpack.delete();
				playerBackpack.createNewFile();
				READER.load(playerBackpack);
			}
			ItemStack[] contents = backpack.getContents();
			READER.set("contents-amount", backpack.getSize());
			ConfigurationSection parent = READER.getConfigurationSection("backpack");
			if (parent == null) {
				parent = READER.createSection("backpack");
			}
			for (int i = 0; i < 54; i++) {
				ConfigurationSection slot = parent.createSection("Slot " + i);
				if (i >= contents.length) {
					continue;
				}
				final ItemStack stack = contents[i];
				slot.set("ItemStack", stack);
				if (stack == null) {
					continue;
				}
			}
			READER.save(playerBackpack);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

    @Override
    public void purge(Player player, String worldName)
    {
        //Preserve cache for this storage type
    }
}

class BackpackFilter implements FilenameFilter {
	@Override
	public boolean accept(File dir, String name) {
		if (name.endsWith(".yml") && !new File(dir, name).isDirectory()) {
			return true;
		}
		return false;
	}
}
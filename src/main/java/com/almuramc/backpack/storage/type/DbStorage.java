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
import java.util.List;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import com.almuramc.backpack.BackpackPlugin;
import com.almuramc.backpack.inventory.BackpackInventory;
import com.almuramc.backpack.storage.Storage;
import com.almuramc.backpack.storage.model.Backpack;
import com.almuramc.backpack.storage.model.BackpackSlot;
import com.almuramc.backpack.util.PermissionHelper;
import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.TxRunnable;
import com.comphenix.protocol.utility.StreamSerializer;

public class DbStorage extends Storage
{
    private EbeanServer database;

    public DbStorage(EbeanServer database)
    {
        this.database = database;
    }

    @Override
    public void initWorld(String worldName)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public BackpackInventory load(Player player, String worldName)
    {
        BackpackInventory inventory;

        if (has(player, worldName)) {
            inventory = fetch(player, worldName);
        }
        else {
            int psize = PermissionHelper.getMaxSizeFor(player, worldName);
            Backpack backpack = database.find(Backpack.class).where().eq("player_name", player.getName()).eq("world_name", worldName).findUnique();

            if (backpack != null) {
                int size = Math.min(psize, backpack.getContentAmount());
                ItemStack[] items = new ItemStack[size];

                for (BackpackSlot slot : backpack.getSlots()) {
                    if (slot.getSlotNumber() >= size) {
                        continue;
                    }
                    try {
                        ItemStack stack = StreamSerializer.getDefault().deserializeItemStack(slot.getItemStackString());
                        items[slot.getSlotNumber()] = stack;
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }

                inventory = new BackpackInventory(Bukkit.createInventory(player, size, "Backpack"));
                inventory.setContents(items);
            }
            else {
                inventory = new BackpackInventory(Bukkit.createInventory(player, psize, "Backpack"));
            }
        }

        return inventory;
    }

    @Override
    public void save(Player player, String worldName, BackpackInventory inventory)
    {
        // Store this backpack to memory
        store(player, worldName, inventory);
        // Save to db
        if (!has(player, worldName)) {
            return;
        }

        saveToDb(player.getName(), worldName, inventory);
    }

    public void saveToDb(final String playerName, final String worldName, final BackpackInventory inventory)
    {
        database.execute(new TxRunnable() {

            @Override
            public void run()
            {
                Backpack backpack = database.find(Backpack.class).where().eq("player_name", playerName).eq("world_name", worldName).findUnique();
                if (backpack == null) {
                    backpack = database.createEntityBean(Backpack.class);
                    backpack.setPlayerName(playerName);
                    backpack.setWorldName(worldName);
                }
                else {
                    database.delete(backpack.getSlots());
                }
                backpack.setContentAmount(inventory.getSize());

                List<BackpackSlot> slots = new ArrayList<BackpackSlot>();
                ItemStack[] contents = inventory.getContents();
                for (int i = 0; i < 54 && i < contents.length; i++) {
                    final ItemStack stack = contents[i];
                    if (stack == null) {
                        continue;
                    }
                    try {
                        BackpackSlot slot = database.createEntityBean(BackpackSlot.class);
                        slot.setSlotNumber(i);
                        slot.setItemStackString(StreamSerializer.getDefault().serializeItemStack(stack));
                        slots.add(slot);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                backpack.setSlots(slots);
                database.save(backpack);
            }
        });
    }

    @Override
    public void updateSize(Player player, String worldName, int size)
    {
        Backpack backpack = database.find(Backpack.class).where().eq("player_name", player.getName()).eq("world_name", worldName).findUnique();
        backpack.setContentAmount(size);
        database.save(backpack);
    }

    @Override
    public void purge(Player player, String worldName) {
        if (has(player, worldName)) {
            BACKPACKS.get(worldName).remove(player.getUniqueId());
        }
    }

    public void migrate()
    {
        YamlConfiguration playerYaml = new YamlConfiguration();
        File backpacks = new File(BackpackPlugin.getInstance().getDataFolder(), "backpacks");
        File[] worldDirs = backpacks.listFiles();

        for (File worldDir : worldDirs) {
            File[] playerFiles = worldDir.listFiles(new FilenameFilter() {
                
                @Override
                public boolean accept(File dir, String name)
                {
                    return name.endsWith(".yml") && !new File(dir, name).isDirectory();
                }
            });

            for (File playerFile : playerFiles) {
                try {
                    playerYaml.load(playerFile);
                    String playerName = playerFile.getName().split(".yml")[0];
                    String worldName = PermissionHelper.getWorldToOpen(playerName, worldDir.getName());
                    Backpack backpack = database.find(Backpack.class).where().eq("player_name", playerName).eq("world_name", worldName).findUnique();

                    if (backpack == null) {
                        backpack = database.createEntityBean(Backpack.class);
                        backpack.setPlayerName(playerName);
                        backpack.setWorldName(worldName);
                        backpack.setContentAmount(playerYaml.getInt("contents-amount"));

                        List<BackpackSlot> slots = new ArrayList<BackpackSlot>();
                        ConfigurationSection backpackSection = playerYaml.getConfigurationSection("backpack");
                        Set<String> slotKeys = backpackSection.getKeys(false);
                        String[] contents = slotKeys.toArray(new String[slotKeys.size()]);

                        for (int i = 0; i < 54 && i < contents.length; i++) {
                            final ConfigurationSection section = backpackSection.getConfigurationSection(contents[i]);
                            ItemStack stack = section.getItemStack("ItemStack", null);
                            if (stack == null) {
                                continue;
                            }

                            BackpackSlot slot = database.createEntityBean(BackpackSlot.class);
                            slot.setSlotNumber(i);
                            slot.setItemStackString(StreamSerializer.getDefault().serializeItemStack(stack));
                            slots.add(slot);
                        }

                        backpack.setSlots(slots);
                        database.save(backpack);
                        System.out.println("Migration of " + playerFile.getName() + " OK");
                    }
                }
                catch (Exception e) {
                    System.out.println("Migration of " + playerFile.getName() + " FAILED");
                }
            }
        }
    }
}

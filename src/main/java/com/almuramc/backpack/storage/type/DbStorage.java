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
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import com.almuramc.backpack.BackpackPlugin;
import com.almuramc.backpack.inventory.BackpackInventory;
import com.almuramc.backpack.storage.Storage;
import com.almuramc.backpack.storage.model.Backpack;
import com.almuramc.backpack.util.PermissionHelper;
import com.avaje.ebean.EbeanServer;
import com.avaje.ebean.TxRunnable;
import com.evilmidget38.UUIDFetcher;
import com.google.common.base.Function;
import com.google.common.collect.Lists;

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
        return load(player, player, worldName, "Backpack");
    }

    @Override
    public BackpackInventory edit(Player player, OfflinePlayer target, String worldName)
    {
        return load(player, target, worldName, String.format("Backpack (%s)", target.getName()));
    }

    private BackpackInventory load(Player player, OfflinePlayer target, String worldName, String backpackName)
    {
        BackpackInventory inventory;

        if (has(target, worldName)) {
            inventory = fetch(target, worldName);
        }
        else {
            int psize = PermissionHelper.getMaxSizeFor(target, worldName);
            Backpack backpack = database.find(Backpack.class).where().eq("uuid", target.getUniqueId()).eq("world_name", worldName).findUnique();
            inventory = new BackpackInventory(Bukkit.createInventory(player, psize, backpackName));

            if (backpack != null) {
                YamlConfiguration yaml = new YamlConfiguration();
                try {
                    yaml.loadFromString(backpack.getInventory());
                    List<ItemStack> items = (List<ItemStack>) yaml.getList("items");
                    inventory.setContents(items.toArray(new ItemStack[items.size()]));
                }
                catch (InvalidConfigurationException e) {
                    e.printStackTrace();
                }
            }
        }

        return inventory;
    }

    @Override
    public void save(OfflinePlayer player, String worldName, BackpackInventory inventory)
    {
        // Store this backpack to memory
        store(player, worldName, inventory);
        // Save to db
        if (!has(player, worldName)) {
            return;
        }

        saveToDb(player, worldName, inventory);
    }

    public void saveToDb(final OfflinePlayer player, final String worldName, final BackpackInventory inventory)
    {
        database.execute(new TxRunnable() {

            @Override
            public void run()
            {
                Backpack backpack = database.find(Backpack.class).where().eq("uuid", player.getUniqueId()).eq("world_name", worldName).findUnique();
                if (backpack == null) {
                    backpack = database.createEntityBean(Backpack.class);
                    backpack.setPlayerName(player.getName());
                    backpack.setUuid(player.getUniqueId());
                    backpack.setWorldName(worldName);
                }
                backpack.setContentAmount(inventory.getSize());
                YamlConfiguration yaml = new YamlConfiguration();
                yaml.set("items", Arrays.asList(inventory.getContents()));
                backpack.setInventory(yaml.saveToString());
                database.save(backpack);
            }
        });
    }

    @Override
    public void updateSize(Player player, String worldName, int size)
    {
        Backpack backpack = database.find(Backpack.class).where().eq("uuid", player.getUniqueId()).eq("world_name", worldName).findUnique();
        backpack.setContentAmount(size);
        database.save(backpack);
    }

    @Override
    public void purge(Player player, String worldName)
    {
        if (has(player, worldName)) {
            BACKPACKS.get(worldName).remove(player.getUniqueId());
        }
    }

    public void migrate()
    {
        final YamlConfiguration playerYaml = new YamlConfiguration();
        File backpacks = new File(BackpackPlugin.getInstance().getDataFolder(), "backpacks");
        File[] worldDirs = backpacks.listFiles();

        for (final File worldDir : worldDirs) {
            final File[] playerFiles = worldDir.listFiles(new FilenameFilter() {

                @Override
                public boolean accept(File dir, String name)
                {
                    return name.endsWith(".yml") && !new File(dir, name).isDirectory();
                }
            });

            List<String> playerNames = Lists.transform(Lists.newArrayList(playerFiles), new Function<File, String>() {

                @Override
                public String apply(File playerFile)
                {
                    return playerFile.getName().split(".yml")[0];
                }
            });

            final UUIDFetcher fetcher = new UUIDFetcher(playerNames);
            final String worldName = worldDir.getName();

            Bukkit.getScheduler().runTaskAsynchronously(BackpackPlugin.getInstance(), new Runnable() {

                @Override
                public void run()
                {
                    Map<String, UUID> uuidMap = null;

                    try {
                        uuidMap = fetcher.call();
                    }
                    catch (Exception e) {
                        Logger.getLogger("Minecraft").severe("Error fetching UUIDs: " + e.getMessage());
                    }

                    if (uuidMap == null) {
                        return;
                    }

                    for (File playerFile : playerFiles) {
                        try {
                            playerYaml.load(playerFile);
                        }
                        catch (Exception e) {
                            Logger.getLogger("Minecraft").severe("Error reading " + playerFile.getName() + ": " + e.getMessage());
                            continue;
                        }

                        String playerName = playerFile.getName().split(".yml")[0];
                        String parentWorld = PermissionHelper.getParentWorld(worldName);
                        Backpack backpack = database.find(Backpack.class).where().eq("uuid", uuidMap.get(playerName)).eq("world_name", worldName).findUnique();

                        if (backpack == null) {
                            backpack = database.createEntityBean(Backpack.class);
                            backpack.setPlayerName(playerName);
                            backpack.setUuid(uuidMap.get(playerName));
                            backpack.setWorldName(parentWorld);
                            backpack.setContentAmount(playerYaml.getInt("contents-amount"));

                            ConfigurationSection backpackSection = playerYaml.getConfigurationSection("backpack");
                            Set<String> slotKeys = backpackSection.getKeys(false);
                            String[] contents = slotKeys.toArray(new String[slotKeys.size()]);

                            Inventory inventory = Bukkit.createInventory(null, contents.length, "Backpack");
                            for (int i = 0; i < 54 && i < contents.length; i++) {
                                final ConfigurationSection section = backpackSection.getConfigurationSection(contents[i]);
                                ItemStack stack = section.getItemStack("ItemStack", null);
                                if (stack == null) {
                                    continue;
                                }
                                inventory.addItem(stack);
                            }

                            YamlConfiguration yaml = new YamlConfiguration();
                            yaml.set("items", Arrays.asList(inventory.getContents()));
                            backpack.setInventory(yaml.saveToString());
                            database.save(backpack);
                            Logger.getLogger("Minecraft").info("Migration of " + playerFile.getName() + " OK");
                        }
                    }
                }
            });
        }
    }
}

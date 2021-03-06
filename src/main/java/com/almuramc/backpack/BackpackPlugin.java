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
package com.almuramc.backpack;

import java.util.List;

import javax.persistence.PersistenceException;

import org.bukkit.Bukkit;

import com.almuramc.backpack.command.BackpackCommands;
import com.almuramc.backpack.listener.BackpackListener;
import com.almuramc.backpack.storage.Storage;
import com.almuramc.backpack.storage.model.Backpack;
import com.almuramc.backpack.storage.type.DbStorage;
import com.almuramc.backpack.storage.type.YamlStorage;
import com.almuramc.backpack.util.CachedConfiguration;
import com.almuramc.backpack.util.Dependency;
import com.almuramc.backpack.util.SafeSpout;

public class BackpackPlugin extends EbeanJavaPlugin implements EbeanPlugin {
	private static BackpackPlugin instance;
	private static Storage store;
	private static CachedConfiguration cached;
	private static Dependency hooks;
	private BackpackCommands executor;

	@Override
	public void onDisable() {
		cached = null;
		instance = null;
		store = null;
		hooks = null;
	}

	@Override
	public void onEnable() {
		//Assign configured storage
	    if ("db".equals(cached.getStorageType())) {
	        store = new DbStorage(getDatabase());
            initDb();
	    } else {
	        store = new YamlStorage(getDataFolder());
	    }
		hooks.setupVaultEconomy();
		hooks.setupVaultPermissions();
		if (cached.useSpout() && hooks.isSpoutPluginEnabled()) {
			SafeSpout.registerSpoutBindings();
		}
		//Register commands
		executor = new BackpackCommands();
		getCommand("backpack").setExecutor(executor);
		//Register events
		Bukkit.getServer().getPluginManager().registerEvents(new BackpackListener(), this);
	}

    private void initDb()
    {
        try {
            this.getDatabase().find(Backpack.class).findRowCount();
        }
        catch (PersistenceException e) {
            this.installDDL();
        }
    }

	@Override
	public void onLoad() {
		//Assign plugin instance
		instance = this;
		//Setup config
		cached = new CachedConfiguration();
		hooks = new Dependency(this);
	}

    @Override
    public List<Class<?>> getDatabaseClasses()
    {
        List<Class<?>> classes = super.getDatabaseClasses();
        classes.add(Backpack.class);
        return classes;
    }

	public static final BackpackPlugin getInstance() {
		return instance;
	}

	public final Storage getStore() {
		return store;
	}

	public final CachedConfiguration getCached() {
		return cached;
	}

	public final Dependency getHooks() {
		return hooks;
	}
}

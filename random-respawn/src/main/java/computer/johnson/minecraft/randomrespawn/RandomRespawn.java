/*
 * This file is part of mc-plugins-random-respawn.
 *
 * mc-plugins-random-respawn is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * mc-plugins-random-respawn is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with mc-plugins-random-respawn.  If not, see <http://www.gnu.org/licenses/>.
 */

package computer.johnson.minecraft.randomrespawn;

import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

public class RandomRespawn extends JavaPlugin implements Listener {

	private double minDistance = 0;
	private double maxDistance = 0;

	@Override
	public void onEnable() {
		boolean configValid = this.handleConfig();
		if (!configValid) {
			// Configuration wasn't valid
			this.getPluginLoader().disablePlugin(this);
			return;
		}

		this.getServer().getPluginManager().registerEvents(this, this);
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		if (event.isBedSpawn()) {
			return; // don't change bed spawns
		}

		Location spawnLocation = this.getRandomSpawnLocation(
			event.getRespawnLocation().getWorld().getSpawnLocation());
		event.setRespawnLocation(spawnLocation);
	}

	private Location getRandomSpawnLocation(Location defaultSpawn) {
		// we use trig to get uniformly random directions (thanks @decodaman!)
		ThreadLocalRandom rand = ThreadLocalRandom.current();
		double theta = rand.nextDouble(0, Math.PI * 2);
		Vector direction = new Vector(Math.sin(theta), 0, Math.cos(theta)).normalize();
		double distance = rand.nextDouble(this.minDistance, this.maxDistance);

		Location spawnAt = defaultSpawn.clone().add(direction.multiply(distance));
		spawnAt.setY(0);

		int highestBlock = this.getHighestBlockYAt(spawnAt);
		spawnAt.setY(highestBlock + 0.5);

		return spawnAt;
	}

	private int getHighestBlockYAt(Location loc) {
		this.ensureChunkLoaded(loc);
		return loc.getWorld().getHighestBlockYAt(loc);
	}

	//Make sure the target is loaded, if not, load it
	private void ensureChunkLoaded(Location loc) {
		Chunk chunk = loc.getChunk();
		//To check if loaded and if not load
		if (!chunk.isLoaded()) {
			chunk.load();
		}
	}

	private boolean handleConfig() {
		// we use a valid boolean so that we can log all configuration errors on
		// the first go.
		boolean valid = true;

		FileConfiguration config = this.getConfig();

		// setup and add the defaults
		config.addDefault("min-distance", 256d);
		config.addDefault("max-distance", 2048d);
		config.options().copyDefaults(true);

		// save with the new defaults, if necessary
		this.saveConfig();

		this.minDistance = config.getDouble("min-distance", 256);
		this.maxDistance = config.getDouble("max-distance", 2048);

		if (this.maxDistance < this.minDistance) {
			valid = false;
			this.getLogger().severe(
				"The configuration has a maximum distance that is shorter than the minimum distance. This is not allowed.");
		}

		if (!checkSpawnCircleInBorder()) {
			this.getLogger().warning(
				"The configured spawn area is not within the world border. Players may spawn outside of the world border.");
		}

		return valid;
	}

	private boolean checkSpawnCircleInBorder() {
		World defaultWorld = this.getServer().getWorlds().get(0);
		Location spawnLocation = defaultWorld.getSpawnLocation();

		return isCircleInBorder(spawnLocation, this.maxDistance);
	}

	private boolean isCircleInBorder(Location center, double radius) {
		WorldBorder border = center.getWorld().getWorldBorder();

		if (!border.isInside(center)) // shortcut: the center isn't even inside the border
		{
			return false;
		}

		Location borderCenter = border.getCenter();
		double borderSize = border.getSize();

		return (center.getX() - radius >= borderCenter.getX() - borderSize &&
			center.getX() + radius <= borderCenter.getX() + borderSize &&
			center.getZ() - radius >= borderCenter.getZ() - borderSize &&
			center.getZ() + radius <= borderCenter.getZ() + borderSize);
	}
}

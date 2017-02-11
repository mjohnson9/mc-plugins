/*
 * This file is part of mc-plugins.
 *
 * mc-plugins is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * mc-plugins is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with mc-plugins.  If not, see <http://www.gnu.org/licenses/>.
 */

package computer.johnson.minecraft.randomrespawn;

import java.util.concurrent.ThreadLocalRandom;

import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldBorder;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Created by michael on 2/7/2017.
 */
public class Main extends JavaPlugin implements Listener {
	@Override
	public void onEnable() {
		this.getServer().getPluginManager().registerEvents(this, this);
	}

	@EventHandler
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		if(event.isBedSpawn())
			return; // don't change bed spawns

		World spawnWorld = event.getRespawnLocation().getWorld();
		Location defaultSpawn = spawnWorld.getSpawnLocation();
		WorldBorder border = spawnWorld.getWorldBorder();

		double distanceToBorder = this.maxSafeDist(defaultSpawn, border);
		double distanceFromSpawn = Math.min(2048, distanceToBorder);

		Vector direction = new Vector(ThreadLocalRandom.current().nextDouble(-1, 1), 0, ThreadLocalRandom.current().nextDouble(-1, 1)).normalize();

		Location spawnLocation = defaultSpawn.add(direction.multiply(distanceFromSpawn));
		int highestBlock = this.getHighestBlockYAt(spawnLocation);
		spawnLocation.setY(highestBlock + 2);

		event.setRespawnLocation(spawnLocation);
	}

	private double maxSafeDist(Location loc, WorldBorder border) {
		Location max = border.getCenter().add(border.getSize(), border.getSize(), border.getSize());
		Location min = border.getCenter().subtract(border.getSize(), border.getSize(), border.getSize());

		double dX = Math.min(Math.abs(min.getX() - loc.getX()), Math.abs(loc.getX() - max.getX()));
		double dY = Math.min(Math.abs(min.getY() - loc.getY()), Math.abs(loc.getY() - max.getY()));

		return Math.sqrt(dX * dX + dY * dY);
	}

	private int getHighestBlockYAt(Location loc) {
		ensureChunkLoaded(loc);
		return loc.getWorld().getHighestBlockYAt(loc);
	}

	//Make sure the target is loaded, if not, load it
	private void ensureChunkLoaded(Location loc) {
		Chunk chunk = loc.getChunk();
		//To check if loaded and if not load
		if(!chunk.isLoaded())
			chunk.load();
	}
}

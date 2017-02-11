/*
 * This file is part of mc-plugins-analytics.
 *
 * mc-plugins-analytics is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * mc-plugins-analytics is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with mc-plugins-analytics.  If not, see <http://www.gnu.org/licenses/>.
 */

package computer.johnson.minecraft.analytics;

import com.brsanthu.googleanalytics.EventHit;
import com.brsanthu.googleanalytics.GoogleAnalytics;
import com.brsanthu.googleanalytics.GoogleAnalyticsConfig;
import com.brsanthu.googleanalytics.GoogleAnalyticsRequest;
import com.brsanthu.googleanalytics.PageViewHit;
import computer.johnson.minecraft.utilities.Names;
import java.util.Map.Entry;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerAchievementAwardedEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerKickEvent;
import org.bukkit.event.player.PlayerLevelChangeEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

	private GoogleAnalytics ga;

	private String minecraftVersion;

	@Override
	public void onDisable() {
	}

	@Override
	public void onEnable() {
		Pattern versionPattern = Pattern.compile(".*\\(.*MC.\\s*([a-zA-z0-9\\-.]+)\\s*\\)");
		Matcher version = versionPattern.matcher(Bukkit.getVersion());
		if (version.matches() && version.group(1) != null) {
			minecraftVersion = version.group(1);
		} else {
			minecraftVersion = "Unknown";
			getLogger().warning(
				"Unable to extract the current Minecraft version from \"" + Bukkit.getVersion()
					+ "\"");
		}

		boolean configValid = handleConfig();
		if (!configValid) {
			// Configuration wasn't valid
			getPluginLoader().disablePlugin(this);
			return;
		}

		getServer().getPluginManager().registerEvents(this, this);
	}

	private boolean handleConfig() {
		FileConfiguration config = getConfig();
		config.addDefault("property-id", "");
		config.options().copyDefaults(true);
		saveConfig();

		String propertyID = config.getString("property-id");
		if (propertyID.length() == 0) {
			getLogger()
				.warning("Not activating: you must set the property ID in the configuration");
			return false;
		}

		GoogleAnalyticsConfig gaConfig = new GoogleAnalyticsConfig();

		String userAgent = System.getProperty("http.agent", "");
		if (userAgent.length() > 0) {
			gaConfig.setUserAgent(userAgent);
		}

		ga = new GoogleAnalytics(gaConfig, propertyID);

		return true;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerJoin(PlayerJoinEvent event) {
		//EventHit joinEvent = this.constructEvent(event.getPlayer(), "Presence", "Join");
		PageViewHit joinEvent = new PageViewHit("/", "In-game");
		setBaseValues(event.getPlayer(), joinEvent);
		joinEvent.sessionControl("start");
		sendTrack(joinEvent);
	}

	private void setBaseValues(Player player, GoogleAnalyticsRequest request) {
		request.userAgent("Minecraft/" + minecraftVersion);
		/*request.applicationName("Minecraft");
		request.applicationVersion(this.minecraftVersion);*/
		request.userLanguage(player.spigot().getLocale().toLowerCase());
		request.userIp(player.getAddress().getAddress().getHostAddress());
		request.clientId(player.getUniqueId().toString());
		request.customDimension(1, player.getName());

		// Position
		Location location = player.getLocation();
		if (location != null) {
			setEventLocation(request, location);
		}
	}

	private void sendTrack(GoogleAnalyticsRequest request) {
		ga.postAsync(request);
		//this.getLogger().info("Sent event: " + request.toString());
	}

	private void setEventLocation(GoogleAnalyticsRequest request, Location location) {
		request.customDimension(3, String.valueOf(location.getX()));
		request.customDimension(4, String.valueOf(location.getY()));
		request.customDimension(5, String.valueOf(location.getZ()));
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerQuit(PlayerQuitEvent event) {
		EventHit quitEvent = constructEvent(event.getPlayer(), "Presence", "Leave");
		quitEvent.sessionControl("end");
		quitEvent.nonInteractionHit("1");
		quitEvent.eventLabel(
			makeMessageGeneric(event.getQuitMessage(), event.getPlayer().getDisplayName()));
		sendTrack(quitEvent);
	}

	private EventHit constructEvent(Player player, String category, String action) {
		EventHit event = new EventHit(category, action);
		setBaseValues(player, event);

		return event;
	}

	private String makeMessageGeneric(String message, String playerName) {
		message = ChatColor.stripColor(message);
		message = message.replace(playerName, "");
		message = message.trim();
		return message;
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerKicked(PlayerKickEvent event) {
		EventHit kickEvent = constructEvent(event.getPlayer(), "Presence", "Kick");
		kickEvent.eventLabel(event.getReason());
		kickEvent.sessionControl("end");
		kickEvent.nonInteractionHit("1");
		sendTrack(kickEvent);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerDeath(PlayerDeathEvent event) {
		trackDeath(event);
		trackKill(event);
	}

	private void trackDeath(PlayerDeathEvent event) {
		Player victim = event.getEntity();

		EventHit deathEvent = constructEvent(event.getEntity(), "Combat", "Death");
		deathEvent.nonInteractionHit("1");
		deathEvent.customMetric(2, String.valueOf(event.getDrops().size()));
		deathEvent.customMetric(3, String.valueOf(event.getDroppedExp()));

		EntityDamageEvent lastDamage = victim.getLastDamageCause();
		if (lastDamage != null) {
			if (lastDamage instanceof EntityDamageByEntityEvent) {
				EntityDamageByEntityEvent damageByEntity = (EntityDamageByEntityEvent) lastDamage;
				Entity damager = damageByEntity.getDamager();
				if (damager instanceof Player) {
					deathEvent.eventLabel("Player: " + damager.getName());
				} else if (damager instanceof Tameable) {
					Tameable tamedAnimal = (Tameable) damager;
					if (tamedAnimal.isTamed()) {
						deathEvent.eventLabel("Player: " + tamedAnimal.getOwner().getName());
					} else {
						deathEvent.eventLabel(damager.getName());
					}
				} else {
					deathEvent.eventLabel(damager.getName());
				}
			} else {
				deathEvent.eventLabel(lastDamage.getCause().toString());
			}
		}

		sendTrack(deathEvent);
	}

	private void trackKill(PlayerDeathEvent event) {
		Player killer = event.getEntity().getKiller();
		if (killer == null) {
			return;
		}

		EventHit killEvent = constructEvent(killer, "Combat", "Kill");
		killEvent.eventLabel(event.getEntity().getName());
		killEvent.customMetric(2, String.valueOf(event.getDrops().size()));
		killEvent.customMetric(3, String.valueOf(event.getDroppedExp()));
		sendTrack(killEvent);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerRespawn(PlayerRespawnEvent event) {
		EventHit respawnEvent = constructEvent(event.getPlayer(), "Combat", "Respawn");
		if (event.isBedSpawn()) {
			respawnEvent.eventLabel("Bed");
		} else {
			respawnEvent.eventLabel("Wild");
		}

		setEventLocation(respawnEvent, event.getRespawnLocation());

		sendTrack(respawnEvent);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onAchievement(PlayerAchievementAwardedEvent event) {
		EventHit achievementEvent = constructEvent(event.getPlayer(), "Achievement", "Awarded");
		achievementEvent.eventLabel(event.getAchievement().toString());

		sendTrack(achievementEvent);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onChat(AsyncPlayerChatEvent event) {
		EventHit chatEvent = constructEvent(event.getPlayer(), "Chat", "Send Message");

		sendTrack(chatEvent);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onWorldChange(PlayerChangedWorldEvent event) {
		EventHit worldChangeEvent = constructEvent(event.getPlayer(), "Presence", "Enter World");
		worldChangeEvent.eventLabel(event.getPlayer().getWorld().getName());

		sendTrack(worldChangeEvent);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onBedEnter(PlayerBedEnterEvent event) {
		EventHit bedEnterEvent = constructEvent(event.getPlayer(), "Bed", "Enter");
		setEventLocation(bedEnterEvent, event.getBed().getLocation());

		sendTrack(bedEnterEvent);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onBedLeave(PlayerBedLeaveEvent event) {
		EventHit bedLeaveEvent = constructEvent(event.getPlayer(), "Bed", "Leave");
		setEventLocation(bedLeaveEvent, event.getBed().getLocation());

		sendTrack(bedLeaveEvent);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerLevelChange(PlayerLevelChangeEvent event) {
		EventHit levelChangeEvent = constructEvent(event.getPlayer());
		levelChangeEvent.eventCategory("Level");

		int oldLevel = event.getOldLevel();
		int newLevel = event.getNewLevel();

		levelChangeEvent.eventValue(newLevel);

		if (newLevel > oldLevel) {
			levelChangeEvent.eventAction("Increase");
		} else if (newLevel < oldLevel) {
			levelChangeEvent.eventAction("Decrease");
		} else {
			// the level didn't change?
			return;
		}

		sendTrack(levelChangeEvent);
	}

	private EventHit constructEvent(Player player) {
		return constructEvent(player, null, null);
	}

	@EventHandler(priority = EventPriority.MONITOR)
	public void onEnchantItem(EnchantItemEvent event) {
		EventHit enchantItemEvent = constructEvent(event.getEnchanter(), "Item", "Enchant");
		enchantItemEvent.eventLabel(createEnchantmentLabel(event));

		sendTrack(enchantItemEvent);
	}

	private String createEnchantmentLabel(EnchantItemEvent event) {
		StringBuilder builder = new StringBuilder();

		ItemStack item = event.getItem();

		builder.append(Names.getFriendlyName(item));
		if (item.getAmount() != 1) {
			builder.append(" x");
			builder.append(item.getAmount());
		}
		builder.append(" <- ");

		boolean first = true;
		for (Entry<Enchantment, Integer> entry : event.getEnchantsToAdd().entrySet()) {
			if (first) {
				first = false;
			} else {
				builder.append(", ");
			}

			builder.append(entry.getKey().getName());
			builder.append(" (level ");
			builder.append(entry.getValue());
			builder.append(")");
		}

		return builder.toString();
	}

	private String getBlockString(Block block) {
		return Names.getFriendlyName(block.getType());
	}
}

/*
 * This file is part of mc-plugins-chat-changes.
 *
 * mc-plugins-chat-changes is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * mc-plugins-chat-changes is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with mc-plugins-chat-changes.  If not, see <http://www.gnu.org/licenses/>.
 */

package computer.johnson.minecraft.chatchanges;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerChatTabCompleteEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

public class Main extends JavaPlugin implements Listener {

	private static final String chatFormat = "<%1$s" + ChatColor.RESET + "> %2$s" + ChatColor.RESET;
	private Double radiusSquared;

	@Override
	public void onDisable() {
	}

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

	private boolean handleConfig() {
		FileConfiguration config = this.getConfig();
		config.addDefault("radius", 64);
		config.options().copyDefaults(true);
		this.saveConfig();

		double radius = config.getDouble("radius");
		if (radius <= 0) {
			this.getLogger().warning("Not activating: radius must be greater than 0");
			return false;
		}

		this.radiusSquared = radius * radius;
		return true;
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerSendCommand(PlayerCommandPreprocessEvent event) {
		Player sender = event.getPlayer();
		if (sender instanceof ConsoleCommandSender || sender.isOp()) {
			// we don't interfere with ops or the console
			return;
		}

		this.sendLocalChatMessage(sender, event.getMessage());
		event.setCancelled(true);
	}

	private void sendLocalChatMessage(Player sender, String message) {
		message = String.format(chatFormat, sender.getDisplayName(), message);

		sender.sendMessage(message);
		Location senderLocation = sender.getLocation();

		Location receiverLocation = sender.getLocation();

		int sentTo = 0;
		for (Player receiver : this.getServer().getOnlinePlayers()) {
			if (receiver.equals(sender)) {
				continue;
			}
			if (receiver.isDead()) {
				continue;
			}

			receiver.getLocation(receiverLocation);
			if (receiverLocation.distanceSquared(senderLocation) > this.radiusSquared) {
				continue;
			}

			sentTo += 1;
			receiver.sendMessage(message);
		}

		this.getLogger()
			.info(ChatColor.stripColor(message) + " (sent to " + String.valueOf(sentTo) + ")");
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerChat(AsyncPlayerChatEvent event) {
		Player sender = event.getPlayer();
		if (sender instanceof ConsoleCommandSender) {
			// we don't interfere with the console
			return;
		}

		if (sender.isDead()) {
			sender.sendMessage(ChatColor.RED + "You can not chat while dead!" + ChatColor.RESET);
			event.setCancelled(true);
			return;
		}

		if (this.isMessageEmpty(event.getMessage())) {
			event.setCancelled(true);
			return;
		}

		this.sendLocalChatMessage(sender, event.getMessage());
		event.setCancelled(true);
	}

	private boolean isMessageEmpty(String chatMessage) {
		chatMessage = ChatColor.stripColor(chatMessage);
		chatMessage = chatMessage.trim();

		return chatMessage.length() == 0;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerJoin(PlayerJoinEvent event) {
		event.setJoinMessage("");
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerQuit(PlayerQuitEvent event) {
		event.setQuitMessage("");
	}

	@EventHandler(priority = EventPriority.HIGHEST)
	public void onPlayerTabComplete(PlayerChatTabCompleteEvent event) {
		Player sender = event.getPlayer();
		if (sender instanceof ConsoleCommandSender) {
			// we don't interfere with the console
			return;
		}

		event.getTabCompletions().clear();
	}
}

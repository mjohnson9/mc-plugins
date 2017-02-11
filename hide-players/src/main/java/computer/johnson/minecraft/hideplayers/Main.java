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

package computer.johnson.minecraft.hideplayers;

import java.lang.reflect.InvocationTargetException;

import com.comphenix.protocol.PacketType;
import com.comphenix.protocol.ProtocolLibrary;
import com.comphenix.protocol.ProtocolManager;
import com.comphenix.protocol.events.*;
import com.comphenix.protocol.reflect.StructureModifier;
import com.comphenix.protocol.wrappers.WrappedChatComponent;
import com.comphenix.protocol.wrappers.WrappedServerPing;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Created by michael on 2/5/2017.
 */
public class Main extends JavaPlugin implements Listener {
	private ProtocolManager protocolManager;
	private PacketListener tabListListener;
	private PacketListener pingListener;

	private WrappedChatComponent header;
	private WrappedChatComponent footer;

	@Override
	public void onDisable() {
		if(this.tabListListener != null) {
			this.protocolManager.removePacketListener(this.tabListListener);
			this.tabListListener = null;
		}
		if(this.pingListener != null) {
			this.protocolManager.removePacketListener(this.pingListener);
			this.pingListener = null;
		}
	}

	@Override
	public void onEnable() {
		boolean configValid = this.handleConfig();
		if(!configValid) {
			// Configuration wasn't valid
			this.getPluginLoader().disablePlugin(this);
			return;
		}

		this.protocolManager = ProtocolLibrary.getProtocolManager();

		this.pingListener = this.createPingListener();
		this.protocolManager.addPacketListener(this.pingListener);

		this.getServer().getPluginManager().registerEvents(this, this);
	}

	private boolean handleConfig() {
		FileConfiguration config = this.getConfig();
		config.addDefault("header", "");
		config.addDefault("footer", "");
		config.options().copyDefaults(true);
		this.saveConfig();

		String headerText = ChatColor.translateAlternateColorCodes('&', config.getString("header", "").trim());
		if(ChatColor.stripColor(headerText).length() > 0) {
			this.header = WrappedChatComponent.fromText(headerText);
		} else {
			this.header = null;
		}
		String footerText = ChatColor.translateAlternateColorCodes('&', config.getString("footer", "").trim());
		if(ChatColor.stripColor(footerText).length() > 0) {
			this.footer = WrappedChatComponent.fromText(footerText);
		} else {
			this.footer = null;
		}
		return true;
	}

	private PacketAdapter createPingListener() {
		return new PacketAdapter(this, ListenerPriority.NORMAL, PacketType.Status.Server.SERVER_INFO) {
			@Override
			public void onPacketSending(PacketEvent event) {
				PacketContainer packet = event.getPacket();

				WrappedServerPing pingPacket = packet.getServerPings().read(0);
				pingPacket.setPlayersVisible(false);
			}
		};
	}

	@EventHandler
	public void onPlayerJoin(PlayerJoinEvent event) throws InvocationTargetException {
		Player player = event.getPlayer();
		this.sendListHeaderFooter(player);

		player.setPlayerListName("");
	}

	private void sendListHeaderFooter(Player player) throws InvocationTargetException {
		if(this.header == null && this.footer == null)
			return; // no work to do

		PacketContainer packet = this.protocolManager.createPacket(PacketType.Play.Server.PLAYER_LIST_HEADER_FOOTER);
		StructureModifier<WrappedChatComponent> components = packet.getChatComponents();
		if(this.header != null) {
			components.write(0, header);
		}
		if(this.footer != null) {
			components.write(1, footer);
		}
		this.protocolManager.sendServerPacket(player, packet);
	}
}

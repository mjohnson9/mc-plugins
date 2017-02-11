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

package computer.johnson.minecraft.utilities;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * Created by michael on 2/4/2017.
 */
public class Names {
	private static final String OBC_PREFIX = Bukkit.getServer().getClass().getPackage().getName();
	private static final String NMS_PREFIX = OBC_PREFIX.replace("org.bukkit.craftbukkit", "net.minecraft.server");
	private static Class localeClass;
	private static Class craftItemStackClass, nmsItemStackClass, nmsItemClass;

	public static String getFriendlyName(Material material) {
		return material == null ? "Air" : getFriendlyName(new ItemStack(material), false);
	}

	public static String getFriendlyName(ItemStack itemStack, boolean checkDisplayName) {
		if(itemStack == null || itemStack.getType() == Material.AIR)
			return "Air";
		try {
			if(craftItemStackClass == null)
				craftItemStackClass = Class.forName(OBC_PREFIX + ".inventory.CraftItemStack");
			Method nmsCopyMethod = craftItemStackClass.getMethod("asNMSCopy", ItemStack.class);

			if(nmsItemStackClass == null)
				nmsItemStackClass = Class.forName(NMS_PREFIX + ".ItemStack");
			Object nmsItemStack = nmsCopyMethod.invoke(null, itemStack);

			Object itemName = null;
			if(checkDisplayName) {
				Method getNameMethod = nmsItemStackClass.getMethod("getName");
				itemName = getNameMethod.invoke(nmsItemStack);
			} else {
				Method getItemMethod = nmsItemStackClass.getMethod("getItem");
				Object nmsItem = getItemMethod.invoke(nmsItemStack);

				if(nmsItemClass == null)
					nmsItemClass = Class.forName(NMS_PREFIX + ".Item");

				Method getNameMethod = nmsItemClass.getMethod("getName");
				Object localItemName = getNameMethod.invoke(nmsItem);

				if(localeClass == null)
					localeClass = Class.forName(NMS_PREFIX + ".LocaleI18n");
				Method getLocaleMethod = localeClass.getMethod("get", String.class);

				Object localeString = localItemName == null ? "" : getLocaleMethod.invoke(null, localItemName);
				itemName = ("" + getLocaleMethod.invoke(null, localeString + ".name")).trim();
			}
			return itemName != null ? itemName.toString() : capitalizeFully(itemStack.getType().name().replace("_", " ").toLowerCase());
		} catch(Exception ex) {
			ex.printStackTrace();
		}
		return capitalizeFully(itemStack.getType().name().replace("_", " ").toLowerCase());
	}

	private static String capitalizeFully(String name) {
		if(name != null) {
			if(name.length() > 1) {
				if(name.contains("_")) {
					StringBuilder sbName = new StringBuilder();
					for(String subName : name.split("_"))
						sbName.append(subName.substring(0, 1).toUpperCase() + subName.substring(1).toLowerCase()).append(" ");
					return sbName.toString().substring(0, sbName.length() - 1);
				} else {
					return name.substring(0, 1).toUpperCase() + name.substring(1).toLowerCase();
				}
			} else {
				return name.toUpperCase();
			}
		} else {
			return "";
		}
	}

	public static String getFriendlyName(ItemStack stack) {
		return getFriendlyName(stack, false);
	}
}

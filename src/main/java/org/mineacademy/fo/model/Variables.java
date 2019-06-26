package org.mineacademy.fo.model;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.mineacademy.fo.Common;
import org.mineacademy.fo.GeoAPI;
import org.mineacademy.fo.MinecraftVersion;
import org.mineacademy.fo.TimeUtil;
import org.mineacademy.fo.Valid;
import org.mineacademy.fo.GeoAPI.GeoResponse;
import org.mineacademy.fo.collection.StrictMap;
import org.mineacademy.fo.collection.expiringmap.ExpiringMap;
import org.mineacademy.fo.debug.Debugger;
import org.mineacademy.fo.plugin.SimplePlugin;
import org.mineacademy.fo.remain.Remain;
import org.mineacademy.fo.settings.SimpleSettings;

import lombok.AccessLevel;
import lombok.Getter;

/**
 * A simple engine that replaces lots of variables in a message.
 *
 * Utilizes {@link FileReader}
 */
public final class Variables {

	/**
	 * The pattern to find simple {} placeholders
	 */
	public static final Pattern BRACKET_PLACEHOLDER_PATTERN = Pattern.compile("[{]([^{}]+)[}]");

	/**
	 * The patter to find simple {} placeholders starting with {rel_ (used for PlaceholderAPI)
	 */
	public static final Pattern BRACKET_REL_PLACEHOLDER_PATTERN = Pattern.compile("[{](rel_)([^}]+)[}]");

	// ------------------------------------------------------------------------------------------------------------
	// Changing variables for loading
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * The loaded variables
	 */
	@Getter(value = AccessLevel.PROTECTED)
	private static final List<ScriptVariable> variables = new ArrayList<>();

	/**
	 * The file loader for variables
	 */
	private static FileReader<ScriptVariable> variableFileReader;

	/**
	 * Player, Their Cached Variables
	 */
	private static final StrictMap<String, Map<String, String>> cache = new StrictMap<>();

	/**
	 * Player, Original Message, Translated Message
	 */
	private static final Map<String, Map<String, String>> customCache = makeNewFastCache();

	// ------------------------------------------------------------------------------------------------------------
	// Loading
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Loads variables from the variables/javascript.txt file, if it exists
	 * within your plugin. Used in ChatControl Pro
	 */
	public static void loadVariables() {

		variableFileReader = new FileReader<ScriptVariable>("define") {

			@Override
			public boolean canFinish() {
				Valid.checkBoolean(getObject().hasScript(), "Script must be set!");

				return true;
			}

			@Override
			public ScriptVariable newInstance(String line) {
				return new ScriptVariable(line.replaceFirst("define ", ""));
			}

			private final List<String> scriptLines = new ArrayList<>();

			private boolean creatingScript = false;

			@Override
			public void onObjectPreSave() {
				if (creatingScript) {
					getObject().setScript(scriptLines);

					scriptLines.clear();
					creatingScript = false;
				}
			}

			@Override
			public void onLineParse(String line) {
				if (creatingScript) {
					scriptLines.add(line);

					return;
				}

				if (line.startsWith("return ") || line.startsWith("local script:")) {
					Common.logFramed(false, "Warning! Reflections and local scrips", "has been removed!", "Please only use 'script:' now.", " Please upgrade: ", line);
					return;
				}

				Valid.checkBoolean(line.startsWith("script:"), "Invalid operator: " + line);
				Valid.checkBoolean(!creatingScript, "Already creating script!");

				creatingScript = true;
			}

		};

		reload();
	}

	/**
	 * Clears current variables and loads new only if they were initially called with {@link #loadVariables()}
	 */
	public static void reload() {
		variables.clear();

		if (variableFileReader != null)
			variables.addAll(variableFileReader.load("variables/javascript.txt"));
	}

	// ------------------------------------------------------------------------------------------------------------
	// Replacing
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Replaces variables in the message using the message sender as an object to replace
	 * player-related placeholders.
	 *
	 * We also support PlaceholderAPI and MvdvPlaceholderAPI.
	 *
	 * @param message
	 * @param sender
	 * @return
	 */
	public static String replace(String message, CommandSender sender) {
		return replace(false, message, sender);
	}

	/**
	 * Replaces variables in the message using the message sender as an object to replace
	 * player-related placeholders.
	 *
	 * We also support PlaceholderAPI and MvdvPlaceholderAPI.
	 *
	 * @param replaceCustom should we use variables/javascript.txt file? If exists
	 * @param message
	 * @param sender
	 * @return
	 */
	public static String replace(boolean replaceCustom, String message, CommandSender sender) {
		if (message == null || message.isEmpty())
			return "";

		final String original = message;
		final boolean living = sender instanceof Player;

		if (living) {
			// Already cached ? Return.
			final Map<String, String> cached = customCache.get(sender.getName());

			if (cached != null && cached.containsKey(message))
				return cached.get(message);

			// Custom
			if (SimplePlugin.getInstance().isVariablesEnabled() && replaceCustom && !variables.isEmpty())
				message = replaceJavascriptVariables0(message, sender);

			// PlaceholderAPI.
			message = HookManager.replacePlaceholders((Player) sender, message);
		}

		// Default
		message = replaceHardVariables0(sender, message);
		message = Common.colorize(message);

		if (living) {
			final Map<String, String> map = customCache.get(sender.getName());

			if (map != null)
				map.put(original, message);
			else
				customCache.put(sender.getName(), Common.newHashMap(original, message));
		}

		return message;
	}

	/**
	 * Replaces javascript variables in the message
	 *
	 * @param message
	 * @param cast
	 * @return
	 */
	private static String replaceJavascriptVariables0(String message, CommandSender cast) {
		for (final ScriptVariable var : variables)
			try {
				message = var.replace(message, cast);

			} catch (final Throwable t) {
				Common.throwError(t,
						"Failed to replace a custom variable!",
						"Message: " + message,
						"Variable: " + var.getVariable(),
						"%error");
			}

		return message;
	}

	/**
	 * Replaces our hardcoded variables in the message, using a cache for better performance
	 *
	 * @param sender
	 * @param message
	 * @return
	 */
	private static String replaceHardVariables0(CommandSender sender, String message) {
		final Matcher matcher = Variables.BRACKET_PLACEHOLDER_PATTERN.matcher(message);
		final Player player = sender instanceof Player ? (Player) sender : null;

		while (matcher.find()) {
			final String variable = matcher.group(1);

			final boolean isSenderCached = cache.contains(sender.getName());
			boolean makeCache = true;

			String value = null;

			// Player is cached
			if (isSenderCached) {
				final Map<String, String> speciCache = cache.get(sender.getName());
				final String storedVariable = speciCache.get(variable);

				// This specific variable is cached
				if (storedVariable != null) {
					value = storedVariable;
					makeCache = false;
				}

			}

			if (makeCache) {
				value = replaceVariable0(variable, player, sender);

				if (value != null) {
					final Map<String, String> speciCache = cache.getOrPut(sender.getName(), makeNewCache());

					speciCache.put(variable, value);
				}
			}

			if (value != null)
				message = message.replace("{" + variable + "}", Common.colorize(value));
		}

		return message;
	}

	/**
	 * Replaces the given variable with a few hardcoded within the plugin, see below
	 *
	 * Also, if the variable ends with +, we insert a space after it if it is not empty
	 *
	 * @param variable
	 * @param player
	 * @param console
	 * @return
	 */
	private static String replaceVariable0(String variable, Player player, CommandSender console) {
		final boolean insertSpace = variable.endsWith("+");

		if (insertSpace)
			variable = variable.substring(0, variable.length() - 1); // Remove the + symbol

		final String found = lookupVariable0(player, console, variable);

		return found == null ? null : found + (insertSpace && !found.isEmpty() ? " " : "");
	}

	/**
	 * Replaces the given variable with a few hardcoded within the plugin, see below
	 *
	 * @param player
	 * @param console
	 * @param variable
	 * @return
	 */
	private static String lookupVariable0(Player player, CommandSender console, String variable) {
		GeoResponse geoResponse = null;

		if (player != null && Arrays.asList("country_code", "country_name", "region_name", "isp").contains(variable))
			geoResponse = GeoAPI.getCountry(player.getAddress());

		switch (variable) {
			case "server_name":
				return SimpleSettings.SERVER_NAME;
			case "nms_version":
				return MinecraftVersion.getServerVersion();
			case "timestamp":
				return TimeUtil.getFormattedDate();

			case "town":
				return player == null ? "" : HookManager.getTownName(player);
			case "nation":
				return player == null ? "" : HookManager.getNation(player);
			case "faction":
				return player == null ? "" : HookManager.getFaction(player);

			case "world":
				return player == null ? "" : HookManager.getWorldAlias(player.getWorld());
			case "health":
				return player == null ? "" : formatHealth0(player) + ChatColor.RESET;

			case "player":
				return player == null ? Common.resolveSenderName(console) : player.getName();
			case "tab_name":
				return player == null ? Common.resolveSenderName(console) : player.getPlayerListName();
			case "display_name":
				return player == null ? Common.resolveSenderName(console) : player.getDisplayName();
			case "nick":
				return player == null ? Common.resolveSenderName(console) : HookManager.getNick(player);

			case "pl_prefix":
				return player == null ? "" : HookManager.getPlayerPrefix(player);
			case "pl_suffix":
				return player == null ? "" : HookManager.getPlayerSuffix(player);
			case "pl_group":
				return player == null ? "" : HookManager.getPlayerPermissionGroup(player);
			case "pl_primary_group":
				return player == null ? "" : HookManager.getPlayerPrimaryGroup(player);
			case "pl_address":
				return player == null ? "" : formatIp0(player);

			case "country_code":
				return player == null ? "" : geoResponse.getCountryCode();
			case "country_name":
				return player == null ? "" : geoResponse.getCountryName();
			case "region_name":
				return player == null ? "" : geoResponse.getRegionName();
			case "isp":
				return player == null ? "" : geoResponse.getIsp();
		}

		return null;
	}

	/**
	 * Formats the {health} variable
	 *
	 * @param player
	 * @return
	 */
	private static String formatHealth0(Player player) {
		final int hp = Remain.getHealth(player);

		return (hp > 10 ? ChatColor.DARK_GREEN : hp > 5 ? ChatColor.GOLD : ChatColor.RED) + "" + hp;
	}

	/**
	 * Formats the {pl_address} variable for the player
	 *
	 * @param player
	 * @return
	 */
	private static String formatIp0(Player player) {
		try {
			return player.getAddress().toString().split("\\:")[0];
		} catch (final Throwable t) {
			return player.getAddress() != null ? player.getAddress().toString() : "";
		}
	}

	// ------------------------------------------------------------------------------------------------------------
	// Cache making
	// ------------------------------------------------------------------------------------------------------------

	/**
	 * Create a new expiring map with 5 millisecond expiration
	 *
	 * @return
	 */
	private static Map<String, Map<String, String>> makeNewFastCache() {
		return ExpiringMap.builder()
				.maxSize(300)
				.expiration(5, TimeUnit.MILLISECONDS)
				.build();
	}

	/**
	 * Create a new expiring map with 1 second expiration, used to cache player-related
	 * variables that are called 10x after each other to save performance
	 *
	 * @return
	 */
	private static Map<String, String> makeNewCache() {
		return ExpiringMap.builder()
				.maxSize(300)
				.expiration(1, TimeUnit.SECONDS)
				.build();
	}
}

/**
 * Represents a Javascript variable
 */
class ScriptVariable {

	/**
	 * The placeholder we are looking for
	 */
	@Getter(value = AccessLevel.PROTECTED)
	private final String variable;

	/**
	 * The script
	 */
	private String script;

	ScriptVariable(String variable) {
		this.variable = variable;
	}

	protected void setScript(List<String> lines) {
		Valid.checkBoolean(this.script == null, "Script already set ");

		this.script = StringUtils.join(lines.toArray(), "\n");
	}

	protected boolean hasScript() {
		return script != null;
	}

	/**
	 * Replaces this Javascript variable in the given message for the given sender
	 *
	 * @param message
	 * @param sender
	 * @return
	 * @throws Exception
	 */
	protected String replace(String message, CommandSender sender) throws Exception {
		if (message.contains(variable)) {

			final Map<String, Object> variables = new HashMap<>();

			if (sender instanceof Player)
				variables.put("player", sender);
			else
				variables.put("cast", sender);

			String script = this.script;

			if (SimplePlugin.getInstance().replaceVariablesInCustom() && sender instanceof CommandSender)
				script = Variables.replace(false, script, sender);

			if (SimplePlugin.getInstance().replaceScriptVariablesInCustom() && sender instanceof Player) {
				Debugger.debug("variables", "# Replacing own variables in script " + script);

				for (final ScriptVariable var : Variables.getVariables()) {
					if (var.variable.equals(variable)) {
						Debugger.debug("variables", "Ignoring " + var.variable);

						continue;
					}

					try {
						Debugger.debug("variables", "Replacing variable " + var.variable + " with " + variables);

						variables.put(var.variable, JavaScriptExecutor.run(var.script, variables));
					} catch (final Throwable t) {
						Common.throwError(t,
								"Failed to replace " + var.variable + " in script! ",
								"Script: " + script,
								"%error");
					}
				}
			}

			return message.replace(variable, Common.colorize(JavaScriptExecutor.run(script, variables).toString()));
		}

		return message;
	}

	@Override
	public String toString() {
		return "Variable {\n"
				+ "  Placeholder: '" + variable + "'\n"
				+ "  Script: <<\n    " + script + "\n  >>"
				+ "\n}";
	}
}
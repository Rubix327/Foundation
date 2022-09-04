package org.mineacademy.fo.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Place this annotation over any of the following classes to make Foundation
 * automatically register it when the plugin starts, and properly reload it.
 *
 * Supported classes:
 * <ul>
 * <li>SimpleListener</li>
 * <li>PacketListener</li>
 * <li>BungeeListener</li>
 * <li>DiscordListener</li>
 * <li>SimpleCommand</li>
 * <li>SimpleCommandGroup</li>
 * <li>SimpleExpansion</li>
 * <li>YamlConfig (we will load your config when the plugin starts and reload it properly)</li>
 * <li>any class that "implements Listener"</li>
 * </ul>
 *
 * In addition, the following classes will self-register automatically regardless
 * if you place this annotation on them or not:
 * <ul>
 * <li>Tool (and its derivates such as Rocket)</li>
 * <li>- SimpleEnchantment</li>
 * </ul>
 */
@Retention(RUNTIME)
@Target(TYPE)
public @interface AutoRegister {

	/**
	 * When false, we wont print console warnings such as that registration failed
	 * because the server runs outdated MC version (example: SimpleEnchantment) or lacks
	 * necessary plugins to be hooked into (example: DiscordListener, PacketListener)
	 *
	 * @return
	 */
	boolean hideIncompatibilityWarnings() default false;
}

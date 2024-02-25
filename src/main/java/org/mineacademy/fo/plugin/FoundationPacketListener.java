package org.mineacademy.fo.plugin;

import com.comphenix.protocol.PacketType;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.bukkit.entity.Player;
import org.mineacademy.fo.model.PacketListener;
import org.mineacademy.fo.remain.Remain;

/**
 * Listens to and intercepts packets using Foundation inbuilt features
 */
@NoArgsConstructor(access = AccessLevel.PRIVATE)
final class FoundationPacketListener extends PacketListener {

	/**
	 * The singleton of this class to auto register it.
	 */
	@Getter(value = AccessLevel.MODULE)
	private static volatile PacketListener instance = new FoundationPacketListener();

	/**
	 * Registers our packet listener for some of the more advanced features of Foundation
	 */
	@Override
	public void onRegister() {

		// "Fix" a Folia bug preventing Conversation API from working properly
		if (Remain.isFolia()) {
			this.addReceivingListener(PacketType.Play.Client.CHAT, event -> {
				final String message = event.getPacket().getStrings().read(0);
				final Player player = event.getPlayer();

				if (player.isConversing()) {
					player.acceptConversationInput(message);

					event.setCancelled(true);
				}
			});
		}

	}
}
package net.tonbot.plugin.music.permissions;

import net.tonbot.common.TonbotBusinessException;

@SuppressWarnings("serial")
public class PermissionsException extends TonbotBusinessException {

	public PermissionsException(String message) {
		super(message);
	}
}

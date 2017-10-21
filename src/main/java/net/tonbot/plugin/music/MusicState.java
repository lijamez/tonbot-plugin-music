package net.tonbot.plugin.music;

import java.util.Optional;

import com.google.common.base.Preconditions;

import lombok.Data;
import net.tonbot.plugin.music.permissions.MusicPermissions;

@Data
class MusicState {

	private final MusicPermissions permissionManager;

	private AudioSession audioSession;

	public MusicState(MusicPermissions permissionManager) {
		this.permissionManager = Preconditions.checkNotNull(permissionManager);
	}

	public Optional<AudioSession> getAudioSession() {
		return Optional.ofNullable(audioSession);
	}
}

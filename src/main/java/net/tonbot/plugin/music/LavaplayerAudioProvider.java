package net.tonbot.plugin.music;

import com.google.common.base.Preconditions;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

import sx.blah.discord.handle.audio.AudioEncodingType;
import sx.blah.discord.handle.audio.IAudioProvider;

class LavaplayerAudioProvider implements IAudioProvider {

	private final AudioPlayer audioPlayer;
	
	public LavaplayerAudioProvider(AudioPlayer audioPlayer) {
		this.audioPlayer = Preconditions.checkNotNull(audioPlayer, "audioPlayer must be non-null.");
	}
	
	private AudioFrame lastFrame = null;

	@Override
	public AudioEncodingType getAudioEncodingType() {
		return AudioEncodingType.OPUS;
	}

	@Override
	public boolean isReady() {
		lastFrame = audioPlayer.provide();
		return lastFrame != null;
	}

	@Override
	public byte[] provide() {
		return lastFrame.data;
	}
}

package net.tonbot.plugin.music;

import java.util.concurrent.ConcurrentHashMap;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;

import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.audio.AudioEncodingType;
import sx.blah.discord.handle.audio.IAudioProvider;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;

class DiscordAudioPlayerManager {

	private final AudioPlayerManager apm;
	private final BotUtils botUtils;
	private final ConcurrentHashMap<Long, AudioSession> audioSessions;
	
	@Inject
	public DiscordAudioPlayerManager(AudioPlayerManager apm, BotUtils botUtils) {
		this.apm = Preconditions.checkNotNull(apm, "apm must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.audioSessions = new ConcurrentHashMap<>();
	}
	
	public void createPlayerFor(IGuild guild) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");
		
		AudioPlayer audioPlayer = apm.createPlayer();
		StandardAudioEventAdapter eventListener = new StandardAudioEventAdapter(audioPlayer, guild, botUtils);
		
		audioPlayer.addListener(eventListener);
		
		guild.getAudioManager().setAudioProvider(new IAudioProvider() {

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
			
		});
		
		AudioSession audioSession = AudioSession.builder()
				.audioPlayer(audioPlayer)
				.audioEventAdapter(eventListener)
				.build();
		
		audioSessions.put(guild.getLongID(), audioSession);
	}
	
	public void enqueue(String identifier, IChannel channel) {
		AudioSession audioSession = audioSessions.get(channel.getGuild().getLongID());
		Preconditions.checkNotNull(audioSession, "There is no audio session for this guild.");
		
		apm.loadItem(identifier, new AudioLoadResultHandler() {

			@Override
			public void trackLoaded(AudioTrack track) {
				audioSession.getAudioEventAdapter().enqueue(track, channel);
			}

			@Override
			public void playlistLoaded(AudioPlaylist playlist) {
				// TODO Add support for playlists
				botUtils.sendMessage(channel, "Playlist loading is not yet supported.");
				
			}

			@Override
			public void noMatches() {
				botUtils.sendMessage(channel, "I can't play that. :shrug:");
				
			}

			@Override
			public void loadFailed(FriendlyException exception) {
				botUtils.sendMessage(channel, "I couldn't load it. " + exception.getMessage());
			}
			
		});
	}
	
	public void stop(IChannel channel) {
		AudioSession audioSession = audioSessions.get(channel.getGuild().getLongID());
		Preconditions.checkNotNull(audioSession, "There is no audio session for this guild.");

		audioSession.getAudioPlayer().stopTrack();
	}
	
	public boolean togglePause(IChannel channel) {
		AudioSession audioSession = audioSessions.get(channel.getGuild().getLongID());
		Preconditions.checkNotNull(audioSession, "There is no audio session for this guild.");

		AudioPlayer audioPlayer = audioSession.getAudioPlayer();
		
		audioPlayer.setPaused(!audioPlayer.isPaused());
		
		return audioPlayer.isPaused();
	}
}

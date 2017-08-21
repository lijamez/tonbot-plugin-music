package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.List;

import com.google.common.base.Preconditions;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

import net.tonbot.common.BotUtils;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;


class StandardAudioEventAdapter extends AudioEventAdapter {
	
	private final AudioPlayer audioPlayer;
	private final IGuild guild;
	private final BotUtils botUtils;
	private List<AudioTrack> tracklist;
	
	
	public StandardAudioEventAdapter(AudioPlayer audioPlayer, IGuild guild, BotUtils botUtils) {
		this.audioPlayer = Preconditions.checkNotNull(audioPlayer, "audioPlayer must be non-null.");
		this.guild = Preconditions.checkNotNull(guild, "guild must be non-null.");
		this.botUtils = Preconditions.checkNotNull(botUtils, "botUtils must be non-null.");
		this.tracklist = new ArrayList<>();
	}
	
	@Override
	public void onPlayerPause(AudioPlayer player) {
		// Player was paused
	}

	@Override
	public void onPlayerResume(AudioPlayer player) {
		// Player was resumed
	}

	@Override
	public void onTrackStart(AudioPlayer player, AudioTrack track) {
		// A track started playing
	}

	@Override
	public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
		if (endReason.mayStartNext) {
			// Start next track
		}

		// endReason == FINISHED: A track finished or died by an exception (mayStartNext
		// = true).
		// endReason == LOAD_FAILED: Loading of a track failed (mayStartNext = true).
		// endReason == STOPPED: The player was stopped.
		// endReason == REPLACED: Another track started playing while this had not
		// finished
		// endReason == CLEANUP: Player hasn't been queried for a while, if you want you
		// can put a
		// clone of this back to your queue
	}

	@Override
	public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
		// An already playing track threw an exception (track end event will still be
		// received separately)
	}

	@Override
	public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
		// Audio track has been unable to provide us any audio, might want to just start
		// a new track
	}
	
	public void enqueue(AudioTrack track, IChannel channel) {
		Preconditions.checkNotNull(track, "track must be non-null.");
		Preconditions.checkNotNull(channel, "channel must be non-null.");
		
		tracklist.add(track);
		
		audioPlayer.playTrack(track); //TODO: This should immediately play the track.
		
		botUtils.sendMessage(channel, "Now playing track: " + track.getIdentifier());
	}
}

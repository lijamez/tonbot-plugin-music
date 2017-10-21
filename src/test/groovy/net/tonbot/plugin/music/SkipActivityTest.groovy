package net.tonbot.plugin.music

import com.google.common.base.Predicates
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo

import net.tonbot.common.BotUtils
import net.tonbot.common.TonbotBusinessException
import net.tonbot.plugin.music.permissions.MusicPermissions
import net.tonbot.plugin.music.permissions.PermissionsException
import net.tonbot.plugin.music.permissions.Action
import spock.lang.Specification
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent
import sx.blah.discord.handle.obj.IChannel
import sx.blah.discord.handle.obj.IGuild
import sx.blah.discord.handle.obj.IUser

class SkipActivityTest extends Specification {

	long guildId = 1234
	
	// The skipper user ID
	long userId = 5678
	
	GuildMusicManager mockedGuildMusicManager
	BotUtils mockedBotUtils
	AudioSession mockedAudioSession
	
	IGuild mockedGuild;
	IUser mockedUser;
	IChannel mockedChannel;
	MessageReceivedEvent mockedEvent;
	MusicPermissions mockedMusicPermissions;

	SkipActivity activity

	def setup() {
		
		this.mockedBotUtils = Mock(BotUtils)
		this.mockedAudioSession = Mock(AudioSession)
		
		this.mockedGuild = Mock(IGuild)
		mockedGuild.getLongID() >> guildId
		
		this.mockedUser = Mock(IUser)
		mockedUser.getLongID() >> userId
		
		this.mockedChannel = Mock(IChannel)
		
		this.mockedEvent = Mock(MessageReceivedEvent)
		mockedEvent.getGuild() >> mockedGuild
		mockedEvent.getAuthor() >> mockedUser
		mockedEvent.getChannel() >> mockedChannel
		
		this.mockedMusicPermissions = Mock(MusicPermissions)
		
		this.mockedGuildMusicManager = Mock(GuildMusicManager)
		mockedGuildMusicManager.getPermission(guildId) >> mockedMusicPermissions
		
		this.activity = new SkipActivity(mockedGuildMusicManager, mockedBotUtils)
	}

	def "skip own currently playing track - success"(String args) {
		given:
		AudioTrack mockedCurrentTrack = Mock()
		AudioSessionStatus status = AudioSessionStatus.builder()
			.nowPlaying(mockedCurrentTrack)
			.upcomingTracks([])
			.playMode(PlayMode.STANDARD)
			.repeatMode(RepeatMode.OFF)
			.build()
		
		when:
		activity.enactWithSession(mockedEvent, "", mockedAudioSession)
		
		then:
		1 * mockedAudioSession.getStatus() >> status
		1 * mockedCurrentTrack.getUserData() >> ExtraTrackInfo.builder()
			.addedByUserId(userId)
			.addTimestamp(0)
			.build()
		
		then:
		1 * mockedAudioSession.skip() >> Optional.of(mockedCurrentTrack)

		then:
		1 * mockedCurrentTrack.getInfo() >> new AudioTrackInfo(
				"Test Track Title",
				"Test Track Author",
				12345,
				"TestTrackId",
				false,
				"http://fake.com/resource")
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **Test Track Title**")

		where:
		args | _
		""   | _
		"  " | _
		"\t" | _
	}
	
	def "skip another user's currently playing track - success"(String args) {
		given:
		long otherUserId = 999999
		AudioTrack mockedCurrentTrack = Mock()
		AudioSessionStatus status = AudioSessionStatus.builder()
			.nowPlaying(mockedCurrentTrack)
			.upcomingTracks([])
			.playMode(PlayMode.STANDARD)
			.repeatMode(RepeatMode.OFF)
			.build()
		
		when:
		activity.enactWithSession(mockedEvent, "", mockedAudioSession)
		
		then:
		1 * mockedAudioSession.getStatus() >> status
		1 * mockedCurrentTrack.getUserData() >> ExtraTrackInfo.builder()
			.addedByUserId(otherUserId)
			.addTimestamp(0)
			.build()
			
		then: "the user does have permission to skip other users' tracks"
		1 * mockedMusicPermissions.checkPermission(mockedUser, Action.SKIP_OTHERS)
		
		then:
		1 * mockedAudioSession.skip() >> Optional.of(mockedCurrentTrack)

		then:
		1 * mockedCurrentTrack.getInfo() >> new AudioTrackInfo(
				"Test Track Title",
				"Test Track Author",
				12345,
				"TestTrackId",
				false,
				"http://fake.com/resource")
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **Test Track Title**")

		where:
		args | _
		""   | _
		"  " | _
		"\t" | _
	}
	
	def "skip another user's currently playing track - no permission"(String args) {
		given:
		long otherUserId = 999999
		AudioTrack mockedCurrentTrack = Mock()
		AudioSessionStatus status = AudioSessionStatus.builder()
			.nowPlaying(mockedCurrentTrack)
			.upcomingTracks([])
			.playMode(PlayMode.STANDARD)
			.repeatMode(RepeatMode.OFF)
			.build()
		
		when:
		activity.enactWithSession(mockedEvent, "", mockedAudioSession)
		
		then:
		1 * mockedAudioSession.getStatus() >> status
		1 * mockedCurrentTrack.getUserData() >> ExtraTrackInfo.builder()
			.addedByUserId(otherUserId)
			.addTimestamp(0)
			.build()
			
		then: "the user does not have permission to skip other users' tracks"
		1 * mockedMusicPermissions.checkPermission(mockedUser, Action.SKIP_OTHERS) >> { throw new PermissionsException("No permissions."); }
		
		then:
		thrown PermissionsException

		where:
		args | _
		""   | _
		"  " | _
		"\t" | _
	}

	def "skip currently playing track - no currently playing track"() {
		given:
		AudioSessionStatus status = AudioSessionStatus.builder()
			.nowPlaying(null)
			.upcomingTracks([])
			.playMode(PlayMode.STANDARD)
			.repeatMode(RepeatMode.OFF)
			.build()
		
		when:
		activity.enactWithSession(mockedEvent, "", mockedAudioSession)

		then:
		1 * mockedAudioSession.getStatus() >> status
		
		then:
		1 * mockedAudioSession.skip() >> Optional.empty()

		then:
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **0** tracks.")
	}

	def "skip all tracks"(String args) {
		given:
		AudioTrack mockedAudioTrack = Mock()

		when:
		activity.enactWithSession(mockedEvent, args, mockedAudioSession)

		then: "the user does have permission to skip all tracks"
		1 * mockedMusicPermissions.checkPermission(mockedUser, Action.SKIP_ALL)
		
		then:
		1 * mockedAudioSession.skip(Predicates.alwaysTrue()) >> [
			mockedAudioTrack,
			mockedAudioTrack
		]

		then:
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **2** tracks.")

		where:
		args    | _
		"all"   | _
		"All"   | _
		"ALL"   | _
	}

	def "skip my tracks"(String args) {
		given:
		long otherUserId = 999999
		AudioTrack mockedMyAudioTrack = Mock()
		AudioTrack mockedTheirAudioTrack = Mock()

		when:
		activity.enactWithSession(mockedEvent, args, mockedAudioSession)

		then:
		1 * mockedMyAudioTrack.getUserData() >> ExtraTrackInfo.builder().addedByUserId(userId).addTimestamp(0L).build()
		1 * mockedTheirAudioTrack.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		1 * mockedAudioSession.skip({it -> it.test(mockedMyAudioTrack) && !it.test(mockedTheirAudioTrack)}) >> [
			mockedMyAudioTrack,
			mockedMyAudioTrack
		]

		then:
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **2** tracks.")

		where:
		args    | _
		"mine"  | _
		"Mine"  | _
		"MINE"  | _
	}

	def "skip single track by index - success"() {
		given:
		long otherUserId = 999999
		String args = "2"
		AudioTrack mockedAudioTrack1 = Mock()
		AudioTrack mockedAudioTrack2 = Mock()
		AudioTrack mockedAudioTrack3 = Mock()

		AudioSessionStatus audioSessionStatus = AudioSessionStatus.builder()
				.nowPlaying(null)
				.upcomingTracks([
					mockedAudioTrack1,
					mockedAudioTrack2,
					mockedAudioTrack3
				])
				.playMode(PlayMode.STANDARD)
				.repeatMode(RepeatMode.OFF)
				.build()

		when:
		activity.enactWithSession(mockedEvent, args, mockedAudioSession)

		then:
		1 * mockedAudioSession.getStatus() >> audioSessionStatus
		
		then:
		_ * mockedAudioTrack1.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		_ * mockedAudioTrack2.getUserData() >> ExtraTrackInfo.builder().addedByUserId(userId).addTimestamp(0L).build()
		_ * mockedAudioTrack3.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		
		then:
		1 * mockedAudioSession.skip({it -> !it.test(mockedAudioTrack1) && it.test(mockedAudioTrack2) && !it.test(mockedAudioTrack3)}) >> [mockedAudioTrack2]

		then:
		1 * mockedAudioTrack2.getInfo() >> new AudioTrackInfo(
				"Test Track Title 2",
				"Test Track Author",
				userId,
				"TestTrackId",
				false,
				"http://fake.com/resource")
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **Test Track Title 2**")
	}
	
	def "skip single track by index - permissions check allows skip"() {
		given:
		long otherUserId = 999999
		String args = "2"
		AudioTrack mockedAudioTrack1 = Mock()
		AudioTrack mockedAudioTrack2 = Mock()
		AudioTrack mockedAudioTrack3 = Mock()

		AudioSessionStatus audioSessionStatus = AudioSessionStatus.builder()
				.nowPlaying(null)
				.upcomingTracks([
					mockedAudioTrack1,
					mockedAudioTrack2,
					mockedAudioTrack3
				])
				.playMode(PlayMode.STANDARD)
				.repeatMode(RepeatMode.OFF)
				.build()

		when:
		activity.enactWithSession(mockedEvent, args, mockedAudioSession)

		then:
		1 * mockedAudioSession.getStatus() >> audioSessionStatus
		
		then:
		_ * mockedAudioTrack1.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		_ * mockedAudioTrack2.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		_ * mockedAudioTrack3.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		1 * mockedMusicPermissions.checkPermission(mockedUser, Action.SKIP_OTHERS)
		
		then:
		1 * mockedAudioSession.skip({it -> !it.test(mockedAudioTrack1) && it.test(mockedAudioTrack2) && !it.test(mockedAudioTrack3)}) >> [mockedAudioTrack2]

		then:
		1 * mockedAudioTrack2.getInfo() >> new AudioTrackInfo(
				"Test Track Title 2",
				"Test Track Author",
				userId,
				"TestTrackId",
				false,
				"http://fake.com/resource")
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **Test Track Title 2**")
	}
	
	def "skip single track by index - permissions check disallows skip"() {
		given:
		long otherUserId = 999999
		String args = "2"
		AudioTrack mockedAudioTrack1 = Mock()
		AudioTrack mockedAudioTrack2 = Mock()
		AudioTrack mockedAudioTrack3 = Mock()

		AudioSessionStatus audioSessionStatus = AudioSessionStatus.builder()
				.nowPlaying(null)
				.upcomingTracks([
					mockedAudioTrack1,
					mockedAudioTrack2,
					mockedAudioTrack3
				])
				.playMode(PlayMode.STANDARD)
				.repeatMode(RepeatMode.OFF)
				.build()

		when:
		activity.enactWithSession(mockedEvent, args, mockedAudioSession)

		then:
		1 * mockedAudioSession.getStatus() >> audioSessionStatus
		
		then:
		_ * mockedAudioTrack1.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		_ * mockedAudioTrack2.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		_ * mockedAudioTrack3.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		1 * mockedMusicPermissions.checkPermission(mockedUser, Action.SKIP_OTHERS) >> { throw new PermissionsException("Not allowed!") }
		
		then:
		thrown PermissionsException
	}

	def "skip multiple tracks"(String args) {
		given:
		long otherUserId = 999999
		AudioTrack mockedAudioTrack1 = Mock()
		AudioTrack mockedAudioTrack2 = Mock()
		AudioTrack mockedAudioTrack3 = Mock()

		AudioSessionStatus audioSessionStatus = AudioSessionStatus.builder()
				.nowPlaying(null)
				.upcomingTracks([
					mockedAudioTrack1,
					mockedAudioTrack2,
					mockedAudioTrack3
				])
				.playMode(PlayMode.STANDARD)
				.repeatMode(RepeatMode.OFF)
				.build()

		when:
		activity.enactWithSession(mockedEvent, args, mockedAudioSession)

		then:
		1 * mockedAudioSession.getStatus() >> audioSessionStatus
		
		then:
		_ * mockedAudioTrack1.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		_ * mockedAudioTrack2.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		_ * mockedAudioTrack3.getUserData() >> ExtraTrackInfo.builder().addedByUserId(otherUserId).addTimestamp(0L).build()
		1 * mockedMusicPermissions.checkPermission(mockedUser, Action.SKIP_OTHERS)
		
		then:
		1 * mockedAudioSession.skip({it -> !it.test(mockedAudioTrack1) && it.test(mockedAudioTrack2) && it.test(mockedAudioTrack3)}) >> [
			mockedAudioTrack2,
			mockedAudioTrack3
		]

		then:
		1 * mockedEvent.getChannel() >> mockedChannel
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **2** tracks.")

		where:
		args            | _
		"2,3"           | _
		"2, 3"          | _
		" 2, 3 "        | _
		"0, 2, 3, 4"    | _
		"2-3"           | _
		"2-100"         | _
		"2, 3-4"        | _
		"2-2, 3-3, 4-4" | _
	}

	def "invalid inputs"(String args) {
		given:
		AudioTrack mockedAudioTrack1 = Mock()
		AudioTrack mockedAudioTrack2 = Mock()
		AudioTrack mockedAudioTrack3 = Mock()

		AudioSessionStatus audioSessionStatus = AudioSessionStatus.builder()
				.nowPlaying(null)
				.upcomingTracks([
					mockedAudioTrack1,
					mockedAudioTrack2,
					mockedAudioTrack3
				])
				.playMode(PlayMode.STANDARD)
				.repeatMode(RepeatMode.OFF)
				.build()

		when:
		activity.enactWithSession(mockedEvent, args, mockedAudioSession)

		then:
		1 * mockedAudioSession.getStatus() >> audioSessionStatus

		then:
		thrown TonbotBusinessException

		where:
		args        | _
		"5"         | _
		"5-6"       | _
		"0"         | _
		"blah"      | _
	}
}

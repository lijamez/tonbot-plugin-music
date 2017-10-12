package net.tonbot.plugin.music

import com.google.common.base.Predicates
import com.sedmelluq.discord.lavaplayer.track.AudioTrack
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo

import net.tonbot.common.BotUtils
import net.tonbot.common.TonbotBusinessException
import spock.lang.Specification
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent
import sx.blah.discord.handle.obj.IChannel
import sx.blah.discord.handle.obj.IUser

class SkipActivityTest extends Specification {

	DiscordAudioPlayerManager mockedDiscordAudioPlayerManager
	BotUtils mockedBotUtils
	AudioSession mockedAudioSession

	SkipActivity activity

	def setup() {
		this.mockedDiscordAudioPlayerManager = Mock(DiscordAudioPlayerManager)
		this.mockedBotUtils = Mock(BotUtils)
		this.mockedAudioSession = Mock(AudioSession)

		this.activity = new SkipActivity(mockedDiscordAudioPlayerManager, mockedBotUtils)
	}

	def "skip currently playing track - success"(String args) {
		given:
		MessageReceivedEvent mockedEvent = Mock()
		IChannel mockedChannel = Mock()
		AudioTrack mockedSkippedAudioTrack = Mock()

		when:
		activity.enactWithSession(mockedEvent, "", mockedAudioSession)

		then:
		1 * mockedAudioSession.skip() >> Optional.of(mockedSkippedAudioTrack)

		then:
		1 * mockedEvent.getChannel() >> mockedChannel
		1 * mockedSkippedAudioTrack.getInfo() >> new AudioTrackInfo(
				"Test Track Title",
				"Test Track Author",
				12345,
				"TestTrackId",
				false,
				"http://fake.com/resource")
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **Test Track Title**")

		then:
		0 * _

		where:
		args | _
		""   | _
		"  " | _
		"\t" | _
	}

	def "skip currently playing track - no currently playing track"() {
		given:
		MessageReceivedEvent mockedEvent = Mock()
		IChannel mockedChannel = Mock()

		when:
		activity.enactWithSession(mockedEvent, "", mockedAudioSession)

		then:
		1 * mockedAudioSession.skip() >> Optional.empty()

		then:
		1 * mockedEvent.getChannel() >> mockedChannel
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **0** tracks.")

		then:
		0 * _
	}

	def "skip all tracks"(String args) {
		given:
		MessageReceivedEvent mockedEvent = Mock()
		IChannel mockedChannel = Mock()
		AudioTrack mockedSkippedAudioTrack = Mock()

		when:
		activity.enactWithSession(mockedEvent, args, mockedAudioSession)

		then:
		1 * mockedAudioSession.skip(Predicates.alwaysTrue()) >> [
			mockedSkippedAudioTrack,
			mockedSkippedAudioTrack
		]

		then:
		1 * mockedEvent.getChannel() >> mockedChannel
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **2** tracks.")

		then:
		0 * _

		where:
		args    | _
		"all"   | _
		"All"   | _
		"ALL"   | _
	}

	def "skip my tracks"(String args) {
		given:
		MessageReceivedEvent mockedEvent = Mock()
		IUser mockedAuthor = Mock()
		IChannel mockedChannel = Mock()
		AudioTrack mockedMyAudioTrack = Mock()
		AudioTrack mockedTheirAudioTrack = Mock()

		when:
		activity.enactWithSession(mockedEvent, args, mockedAudioSession)

		then:
		1 * mockedEvent.getAuthor() >> mockedAuthor
		1 * mockedAuthor.getLongID() >> 1L
		1 * mockedMyAudioTrack.getUserData() >> ExtraTrackInfo.builder().addedByUserId(1L).addTimestamp(0L).build()
		1 * mockedTheirAudioTrack.getUserData() >> ExtraTrackInfo.builder().addedByUserId(2L).addTimestamp(0L).build()
		1 * mockedAudioSession.skip({it -> it.test(mockedMyAudioTrack) && !it.test(mockedTheirAudioTrack)}) >> [
			mockedMyAudioTrack,
			mockedMyAudioTrack
		]

		then:
		1 * mockedEvent.getChannel() >> mockedChannel
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **2** tracks.")

		then:
		0 * _

		where:
		args    | _
		"mine"  | _
		"Mine"  | _
		"MINE"  | _
	}

	def "skip single track by index"() {
		given:
		String args = "2"
		MessageReceivedEvent mockedEvent = Mock()
		IChannel mockedChannel = Mock()
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
		1 * mockedAudioSession.skip({it -> !it.test(mockedAudioTrack1) && it.test(mockedAudioTrack2) && !it.test(mockedAudioTrack3)}) >> [mockedAudioTrack2]

		then:
		1 * mockedEvent.getChannel() >> mockedChannel
		1 * mockedAudioTrack2.getInfo() >> new AudioTrackInfo(
				"Test Track Title 2",
				"Test Track Author",
				12345,
				"TestTrackId",
				false,
				"http://fake.com/resource")
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **Test Track Title 2**")

		then:
		0 * _
	}

	def "skip multiple tracks"(String args) {
		given:
		MessageReceivedEvent mockedEvent = Mock()
		IChannel mockedChannel = Mock()
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
		1 * mockedAudioSession.skip({it -> !it.test(mockedAudioTrack1) && it.test(mockedAudioTrack2) && it.test(mockedAudioTrack3)}) >> [
			mockedAudioTrack2,
			mockedAudioTrack3
		]

		then:
		1 * mockedEvent.getChannel() >> mockedChannel
		1 * mockedBotUtils.sendMessage(mockedChannel, "Skipped **2** tracks.")

		then:
		0 * _

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
		MessageReceivedEvent mockedEvent = Mock()
		IChannel mockedChannel = Mock()
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

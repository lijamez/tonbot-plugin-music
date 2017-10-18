package net.tonbot.plugin.music;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import net.tonbot.plugin.music.permissions.Action;
import net.tonbot.plugin.music.permissions.Rule;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.util.MessageTokenizer;
import sx.blah.discord.util.MessageTokenizer.RoleMentionToken;
import sx.blah.discord.util.MessageTokenizer.Token;

class PermissionsArgsParser {

	private final IDiscordClient discordClient;

	@Inject
	public PermissionsArgsParser(IDiscordClient discordClient) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
	}

	public Rule parseRule(String line) {
		if (StringUtils.isBlank(line)) {
			throw new IllegalArgumentException("You need to provide a role mention and an action.");
		}

		MessageTokenizer tokenizer = new MessageTokenizer(discordClient, line);

		IRole mentionedRole = null;

		if (tokenizer.hasNextMention()) {
			Token mentionToken = tokenizer.nextMention();
			if (mentionToken instanceof RoleMentionToken) {
				mentionedRole = ((RoleMentionToken) mentionToken).getMentionObject();
			} else {
				throw new IllegalArgumentException("The mention needs to be for a role.");
			}
		}

		if (mentionedRole == null) {
			throw new IllegalArgumentException("No role mention found.");
		}

		if (!tokenizer.hasNextWord()) {
			throw new IllegalArgumentException(
					"No action found. Must be one of: " + StringUtils.join(Action.values(), ", "));
		}

		Token wordToken = tokenizer.nextWord();
		Action action;
		try {
			action = Action.valueOf(wordToken.getContent());
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException(
					"Invalid action. Must be one of: " + StringUtils.join(Action.values(), ", "));
		}

		Rule rule = new Rule(mentionedRole.getLongID(), action);

		return rule;
	}
}

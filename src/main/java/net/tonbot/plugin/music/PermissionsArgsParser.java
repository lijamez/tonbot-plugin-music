package net.tonbot.plugin.music;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import com.google.common.base.Preconditions;
import com.google.inject.Inject;

import net.tonbot.plugin.music.permissions.Action;
import net.tonbot.plugin.music.permissions.Rule;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IRole;
import sx.blah.discord.util.MessageTokenizer;
import sx.blah.discord.util.MessageTokenizer.RoleMentionToken;
import sx.blah.discord.util.MessageTokenizer.Token;

class PermissionsArgsParser {

	private static final Pattern EVERYONE_PATTERN = Pattern.compile("@everyone");

	private final IDiscordClient discordClient;

	@Inject
	public PermissionsArgsParser(IDiscordClient discordClient) {
		this.discordClient = Preconditions.checkNotNull(discordClient, "discordClient must be non-null.");
	}

	/**
	 * Parses the line and converts them to a list of {@link Rule}. A line is
	 * expected to contain a role mention, followed by series of actions to allow
	 * for that role.<br/>
	 * For example:
	 * 
	 * <pre>
	 * {@literal @myRole} SKIP_ALL SKIP_OTHERS
	 * </pre>
	 * 
	 * That would generate two rules. If any of the given actions are not valid,
	 * they will be ignored.
	 * 
	 * @param line
	 *            The line that the user sent.
	 * @param guild
	 *            The {@link IGuild} applicable for the parsing. This is needed in
	 *            case the {@literal @everyone} role is mentioned. Non-null.
	 * @return A list of parsed rules.
	 */
	public List<Rule> parseRules(String line, IGuild guild) {
		Preconditions.checkNotNull(guild, "guild must be non-null.");
		
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
		} else if (tokenizer.hasNextWord() && tokenizer.hasNextRegex(EVERYONE_PATTERN)) {
			tokenizer.nextRegex(EVERYONE_PATTERN);
			mentionedRole = guild.getEveryoneRole();
		}

		if (mentionedRole == null) {
			throw new IllegalArgumentException("No role mention found.");
		}

		List<Rule> rules = new ArrayList<>();
		while (tokenizer.hasNextWord()) {
			try {
				Action action = Action.valueOf(tokenizer.nextWord().getContent());
				rules.add(new Rule(mentionedRole.getLongID(), action));
			} catch (IllegalArgumentException e) {
				// Ignore it.
			}
		}

		return rules;
	}
}

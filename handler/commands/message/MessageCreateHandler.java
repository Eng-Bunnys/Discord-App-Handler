package org.bunnys.handler.commands.message;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.bunnys.handler.GBF;
import org.bunnys.handler.commands.CommandUtils;
import org.bunnys.handler.commands.message.config.MessageCommand;
import org.bunnys.handler.commands.message.config.MessageCommandConfig;
import org.bunnys.handler.utils.Logger;

public class MessageCreateHandler {
    private final MessageReceivedEvent event;
    private final GBF client;

    public record CommandData(MessageCommand command, String commandName, String[] args) {}

    public MessageCreateHandler(MessageReceivedEvent event, GBF client) {
        this.client = client;
        this.event = event;
        if (event == null || client == null) {
            throw new IllegalArgumentException("Event and client cannot be null");
        }
    }

    public CommandData process() {
        if (event.getAuthor().isBot() || !event.getMessage().isFromGuild()) {
            return null;
        }

        MessageCommandParser.ParseResult parseResult = MessageCommandParser.parse(client, event);
        if (parseResult == null) {
            Logger.warning("No command parsed from event: " + event.getMessage().getContentRaw());
            return null;
        }

        MessageCommand command = parseResult.command();
        MessageCommandConfig options = command.CommandOptions();
        String commandName = parseResult.commandName();
        String userId = event.getAuthor().getId();
        String userMention = event.getAuthor().getAsMention();
        Guild guild = event.getGuild();

        if (guild == null) {
            Logger.error("Guild is null for event: " + event.getMessage().getContentRaw());
            return null;
        }

        if (!CommandUtils.validateNSFW(options, event.getMessage().getChannel(), userMention)) {
            return null;
        }

        if (!CommandUtils.validateDeveloperOnly(client, options, event.getMessage().getChannel(), userId, userMention)) {
            return null;
        }

        if (!CommandUtils.hasRequiredPermissions(guild.getMember(event.getAuthor()), event.getMessage().getChannel(),
                options.getUserPermissions(), userMention + ", you lack permissions: ")) {
            return null;
        }

        if (!CommandUtils.hasRequiredPermissions(guild.getSelfMember(), event.getMessage().getChannel(),
                options.getBotPermissions(), "I lack permissions: ")) {
            return null;
        }

        if (!CommandUtils.validateCooldown(client, options, commandName, event.getMessage().getChannel(), userId, userMention)) {
            return null;
        }

        return new CommandData(command, commandName, parseResult.args());
    }
}
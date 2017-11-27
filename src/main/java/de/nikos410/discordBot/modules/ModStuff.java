package de.nikos410.discordBot.modules;


import de.nikos410.discordBot.DiscordBot;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.annotations.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.annotations.CommandSubscriber;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;

import java.util.List;

@CommandModule(moduleName = "Modzeugs", commandOnly = true)
public class ModStuff {
    private final DiscordBot bot;

    public ModStuff (final DiscordBot bot) {
        this.bot = bot;
    }

    @CommandSubscriber(command = "kick", help = "Kickt den angegebenen Nutzer mit der angegeben Nachricht vom Server",
            pmAllowed = false, permissionLevel = CommandPermissions.EVERYONE)
    public void command_Kick(final IMessage message) {
        if (this.bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()) >= CommandPermissions.MODERATOR) {

            final List<IUser> mentions = message.getMentions();
            if (mentions.size() <1) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: Kein Nutzer angegeben!");
                return;
            }
            else if (mentions.size() > 1) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: In der Nachricht keine Nutzer erwähnen!");
                return;
            }

            final IUser kickUser = mentions.get(0);
            if (kickUser == null) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
                return;
            }

            String customMessage = Util.getContext(message.getContent(), 2);
            if (customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final String kickMessage = "**Du wurdest gekickt!** (Du kannst dem Server jedoch erneut beitreten)" +
                    "\nHinweis: _" + customMessage + '_';

            Util.sendPM(kickUser, kickMessage);
            message.getGuild().kickUser(kickUser);
            Util.sendMessage(message.getChannel(), ":door::arrow_left:");
        }
        else {
            message.getGuild().kickUser(message.getAuthor());
            Util.sendMessage(message.getChannel(), ":tja:");
            Util.sendPM(message.getAuthor(), "Netter Versuch ;)");
        }
    }

    @CommandSubscriber(command = "ban", help = "Bannt den angegebenen Nutzer mit der angegeben Nachricht vom Server",
            pmAllowed = false, permissionLevel = CommandPermissions.EVERYONE)
    public void command_Ban(final IMessage message) {
        if (this.bot.getUserPermissionLevel(message.getAuthor(), message.getGuild()) >= CommandPermissions.MODERATOR) {

            final List<IUser> mentions = message.getMentions();
            if (mentions.size() <1) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: Kein Nutzer angegeben!");
            }
            else if (mentions.size() > 1) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: In der Nachricht keine Nutzer erwähnen!");
            }

            final IUser banUser = mentions.get(0);
            if (banUser == null) {
                Util.sendMessage(message.getChannel(), ":x: Fehler: Nutzer nicht gefunden!");
                return;
            }

            String customMessage = Util.getContext(message.getContent(), 2);
            if (customMessage.isEmpty()) {
                customMessage = "kein";
            }

            final String banMessage = "**Du wurdest gebannt!** \nHinweis: _" + customMessage + '_';

            Util.sendPM(banUser, banMessage);
            message.getGuild().kickUser(banUser);
            Util.sendMessage(message.getChannel(), ":hammer:");
        }
        else {
            message.getGuild().banUser(message.getAuthor());
            Util.sendMessage(message.getChannel(), ":tja:");
            Util.sendPM(message.getAuthor(), "Netter Versuch ;)");
        }
    }
}

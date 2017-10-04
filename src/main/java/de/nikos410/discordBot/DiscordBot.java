package de.nikos410.discordBot;


import de.nikos410.discordBot.modules.ExampleModule;
import de.nikos410.discordBot.util.general.Authorization;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.Command;
import de.nikos410.discordBot.util.modular.CommandModule;
import de.nikos410.discordBot.util.modular.CommandPermissions;
import de.nikos410.discordBot.util.modular.CommandSubscriber;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedList;

import org.json.JSONObject;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

public class DiscordBot {
    private final static Path CONFIG_FILE = Paths.get("config/config.json");

    private final HashMap<String, Command> commands = new HashMap<>();
    private final LinkedList<Object> modules = new LinkedList<>();

    private final IDiscordClient client;
    private final String prefix;

    private final String modRoleID;
    private final String adminRoleID;
    private final String ownerID;

    /**
     * Richtet den Bot ein, lädt Konfiguration etc.
     */
    private DiscordBot() {
        final String configFileContent = Util.readFile(CONFIG_FILE);
        final JSONObject json = new JSONObject(configFileContent);

        final String token = json.getString("token");
        this.client = Authorization.createClient(token, true);

        this.prefix = json.getString("prefix");

        this.modRoleID = json.getString("modRole");
        this.adminRoleID = json.getString("adminRole");
        this.ownerID = json.getString("owner");

        try {
            this.client.getDispatcher().registerListener(this);
        }
        catch (NullPointerException e) {
            System.err.println("[Error] Could not get EventDispatcher: ");
            Util.error(e);
        }

        this.addModules();
    }

    /**
     * Module werden dem Bot hinzugefügt
     */
    private void addModules() {
        this.addModule(new ExampleModule());
    }

    /**
     * Ein Modul zum Bot hinzufügen
     *
     * @param module Das Modul
     */
    private void addModule(final Object module) {
        if (!module.getClass().isAnnotationPresent(CommandModule.class)) {
            System.err.println("Error: Class \"" + module.getClass().getName() + "\" is not a command module!");
            return;
        }

        this.modules.add(module);

        int numberOfCommands = 0;
        for (Method method : module.getClass().getMethods()) {

            if (method.isAnnotationPresent(CommandSubscriber.class)) {
                numberOfCommands++;

                final CommandSubscriber[] annotations = method.getDeclaredAnnotationsByType(CommandSubscriber.class);

                final String command = annotations[0].command();
                final String help = annotations[0].help();
                final boolean pmAllowed = annotations[0].pmAllowed();
                final int permissionLevel = annotations[0].permissionLevel();

                final Command cmd = new Command(module, method, help, pmAllowed, permissionLevel);

                this.commands.put(command.toLowerCase(), cmd);
            }
        }

        final CommandModule[] annotations = module.getClass().getDeclaredAnnotationsByType(CommandModule.class);

        if (!annotations[0].commandOnly()) {
            try {
                this.client.getDispatcher().registerListener(module);
            }
            catch (NullPointerException e) {
                System.err.println("[Error] Could not get EventDispatcher: ");
                Util.error(e);
            }
        }

        System.out.println("Loaded module \"" + annotations[0].moduleName() + "\" with " + numberOfCommands + " command(s).");
    }

    /**
     * Wird bei jeder erhaltenen Nachricht aufgerufen
     *
     * @param event Das Event der erhaltenen Nachricht
     */
    @EventSubscriber
    public void onMessageReceived(final MessageReceivedEvent event) {
        final IMessage message = event.getMessage();
        final String messageContent = message.getContent();

        // Message doesn't start with the prefix
        if (!messageContent.startsWith(this.prefix)) {
            return;
        }

        final String messageCommand = (messageContent.contains(" ") ?
                messageContent.substring(this.prefix.length(), messageContent.indexOf(' ')) :   // Message has Arguments
                messageContent.substring(this.prefix.length())).                                // Message doesn't have arguments
                toLowerCase();


        if (messageCommand.equalsIgnoreCase("help")) {
            this.command_help(message);
            return;
        }

        if (commands.containsKey(messageCommand)) {
            final Command command = commands.get(messageCommand);

            final int userPermissionLevel = this.getUserPermissionLevel(message.getAuthor(), message.getGuild());
            if (userPermissionLevel < command.permissionLevel) {
                Util.sendMessage(message.getChannel(), "Dieser Befehl ist für deine Gruppe (" +
                        CommandPermissions.getPermissionLevelName(userPermissionLevel) + ") nicht verfügbar.");
                return;
            }

            if (message.getChannel().isPrivate() && !command.pmAllowed) {
                Util.sendMessage(message.getChannel(), "Dieser Befehl ist nicht in Privatnachrichten verfügbar!");
                return;
            }

            try {
                command.method.invoke(command.object, message);
            } catch (Exception e) {
                Util.error(e, message);
            }
        }

    }

    private int getUserPermissionLevel(final IUser user, final IGuild guild) {
        if (user.getStringID().equals(this.ownerID)) {
            return CommandPermissions.OWNER;
        }
        else if (Util.hasRoleByID(user, this.adminRoleID, guild)) {
            return CommandPermissions.ADMIN;
        }
        else if (Util.hasRoleByID(user, this.modRoleID, guild)) {
            return CommandPermissions.MODERATOR;
        }
        else {
            return CommandPermissions.EVERYONE;
        }
    }

    private void command_help(final IMessage message) {
        final EmbedBuilder embedBuilder = new EmbedBuilder();

        for (Object module : this.modules) {
            String moduleHelp = "";

            for (Method method : module.getClass().getMethods()) {

                if (method.isAnnotationPresent(CommandSubscriber.class)) {
                    final CommandSubscriber[] annotations = method.getDeclaredAnnotationsByType(CommandSubscriber.class);

                    final String command = annotations[0].command();
                    final String help = annotations[0].help();

                    moduleHelp = moduleHelp + "`" + command + "` " + help + '\n';
                }
            }

            final CommandModule[] annotations = module.getClass().getDeclaredAnnotationsByType(CommandModule.class);
            final String moduleName = annotations[0].moduleName();

            embedBuilder.appendField(moduleName, moduleHelp, false);
        }

        final EmbedObject embedObject = embedBuilder.build();
        Util.sendBufferedEmbed(message.getAuthor().getOrCreatePMChannel(), embedObject);

        if (!message.getChannel().isPrivate()) {
            Util.sendMessage(message.getChannel(), ":mailbox_with_mail:");
        }
    }

    /**
     * Wird aufgerufen wenn der Bot fertig eingeloggt und bereit für Interaktionen mit der Discord-API ist
     */
    @EventSubscriber
    public void onStartup(final ReadyEvent event) {
        System.out.println("[INFO] Bot ready. Prefix: " + this.prefix);
        client.changePlayingText(this.prefix + "help | WIP");
    }

    public static void main(String[] args) {
        new DiscordBot();
    }
}

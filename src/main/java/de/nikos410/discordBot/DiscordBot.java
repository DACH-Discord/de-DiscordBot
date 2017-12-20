package de.nikos410.discordBot;

import de.nikos410.discordBot.util.general.Authorization;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.*;

import java.awt.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.Set;

import de.nikos410.discordBot.util.modular.annotations.AlwaysLoaded;
import de.nikos410.discordBot.util.modular.annotations.CommandModule;
import de.nikos410.discordBot.util.modular.annotations.CommandSubscriber;
import org.reflections.Reflections;
import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageUpdateEvent;
import sx.blah.discord.handle.obj.IChannel;
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

import org.json.JSONObject;
import org.json.JSONArray;

public class DiscordBot {
    private final static Path CONFIG_PATH = Paths.get("config/config.json");
    private JSONObject json;

    private final HashMap<String, Command> commands = new HashMap<>();
    private final HashMap<String, Object> unloadedModules = new HashMap<>();
    private final HashMap<String, Object> loadedModules = new HashMap<>();

    public final IDiscordClient client;

    public final String prefix;

    public final long modRoleID;
    public final long adminRoleID;
    public final long ownerID;

    /**
     * Richtet den Bot ein, lädt Konfiguration etc.
     */
    private DiscordBot() {
        final String configFileContent = Util.readFile(CONFIG_PATH);
        json = new JSONObject(configFileContent);

        final String token = json.getString("token");
        this.client = Authorization.createClient(token, true);

        this.prefix = json.getString("prefix");

        this.modRoleID = json.getLong("modRole");
        this.adminRoleID = json.getLong("adminRole");
        this.ownerID = json.getLong("owner");

        try {
            this.client.getDispatcher().registerListener(this);
        }
        catch (NullPointerException e) {
            System.err.println("[Error] Could not get EventDispatcher: ");
            Util.error(e);
        }

        this.addModules();
        this.makeCommandMap();
        System.out.println("Loaded " + this.loadedModules.size() + " module(s) with " + this.commands.size() + " command(s).");
    }

    /**
     * Module werden dem Bot hinzugefügt
     */
    private void addModules() {
        Reflections reflections = new Reflections("de.nikos410.discordBot.modules");
        Set<Class<?>> moduleClasses = reflections.getTypesAnnotatedWith(CommandModule.class);

        for (Class<?> moduleClass : moduleClasses) {
            try {
                Object moduleObject = null;
                try {
                    moduleObject = moduleClass.getDeclaredConstructor(DiscordBot.class).newInstance(this);
                }
                catch (NoSuchMethodException e) {
                    moduleObject = moduleClass.newInstance();
                }

                this.addModule(moduleObject);
            }
            catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
                System.err.println("Error loading module from class " + moduleClass.getName() + '\n' + e + " " + e.getMessage());
            }

        }
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

        final CommandModule moduleAnnotation = module.getClass().getDeclaredAnnotationsByType(CommandModule.class)[0];
        final String moduleName = moduleAnnotation.moduleName();


        final JSONArray jsonUnloadedModules = this.json.getJSONArray("unloadedModules");
        for (int i = 0; i < jsonUnloadedModules.length(); i++) {
            final String unloadedModuleName = jsonUnloadedModules.getString(i);
            if (moduleName.equals(unloadedModuleName)) {
                // Modul ist in der Liste der deaktivierten Module enthalten -> ist deaktiviert
                this.unloadedModules.put(moduleName, module);
                return;
            }
        }

        // Modul ist nicht deaktiviert
        this.loadedModules.put(moduleName, module);
    }

    private void makeCommandMap() {
        this.commands.clear();

        for (final String key : this.loadedModules.keySet()) {
            Object module = this.loadedModules.get(key);

            for (Method method : module.getClass().getMethods()) {

                if (method.isAnnotationPresent(CommandSubscriber.class)) {

                    final CommandSubscriber[] annotations = method.getDeclaredAnnotationsByType(CommandSubscriber.class);

                    final String command = annotations[0].command();
                    final String help = annotations[0].help();
                    final boolean pmAllowed = annotations[0].pmAllowed();
                    final int permissionLevel = annotations[0].permissionLevel();

                    final Command cmd = new Command(module, method, help, pmAllowed, permissionLevel);

                    this.commands.put(command.toLowerCase(), cmd);
                }
            }

            final CommandModule moduleAnnotation = module.getClass().getDeclaredAnnotationsByType(CommandModule.class)[0];
            if (!moduleAnnotation.commandOnly()) {
                try {
                    this.client.getDispatcher().registerListener(module);
                } catch (NullPointerException e) {
                    System.err.println("[Error] Could not get EventDispatcher: ");
                    Util.error(e);
                }
            }
        }
    }

    /**
     * Wird bei jeder erhaltenen Nachricht aufgerufen
     *
     * @param event Das Event der erhaltenen Nachricht
     */
    @EventSubscriber
    public void onMessageReceived(final MessageReceivedEvent event) {
        handleMessage(event.getMessage());
    }

    /**
     * Wird bei jeder geänderten Nachricht aufgerufen, bearbeitete Nachrichten die jünger als 20 Sekunden sind
     * werden auch als Befehl interpretiert
     *
     * @param event Das Event der geänderten Nachricht
     */
    @EventSubscriber
    public void onMessageEdited(final MessageUpdateEvent event) {
        final IMessage message = event.getNewMessage();

        if (message.getEditedTimestamp().isPresent()) {
            final LocalDateTime messageTimestamp = message.getTimestamp();
            final LocalDateTime editTimestamp = message.getEditedTimestamp().get();

            final long seconds = messageTimestamp.until(editTimestamp, ChronoUnit.SECONDS);

            if (seconds < 20) {
                handleMessage(message);
            }
        }
    }

    /**
     *  Erhaltene/geänderte Nachricht verarbeiten
     */
    public void handleMessage(final IMessage message) {
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
                final Throwable cause = e.getCause();

                cause.printStackTrace(System.err);
                System.err.println(cause.getMessage());

                final EmbedBuilder embedBuilder = new EmbedBuilder();

                embedBuilder.withColor(new Color(255, 42, 50));
                embedBuilder.appendField("Fehler aufgetreten", cause.toString(), false);
                embedBuilder.withFooterText("Mehr Infos in der Konsole");

                Util.sendEmbed(message.getChannel(), embedBuilder.build());
            }
        }

    }

    public int getUserPermissionLevel(final IUser user, final IGuild guild) {
        if (user.getLongID() == this.ownerID) {
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

        for (final String key : this.loadedModules.keySet()) {
            Object module = this.loadedModules.get(key);


            String moduleHelp = "";

            for (Method method : module.getClass().getMethods()) {

                if (method.isAnnotationPresent(CommandSubscriber.class)) {
                    final CommandSubscriber annotation = method.getDeclaredAnnotationsByType(CommandSubscriber.class)[0];

                    if (this.getUserPermissionLevel(message.getAuthor(), message.getGuild()) >= annotation.permissionLevel()) {
                        final String command = annotation.command();
                        final String help = annotation.help();

                        moduleHelp = moduleHelp + "`" + command + "` " + help + '\n';
                    }
                }
            }

            final CommandModule[] annotations = module.getClass().getDeclaredAnnotationsByType(CommandModule.class);
            final String moduleName = annotations[0].moduleName();

            if (!moduleHelp.isEmpty()) {
                embedBuilder.appendField(moduleName, moduleHelp, false);
            }
        }

        final EmbedObject embedObject = embedBuilder.build();
        Util.sendEmbed(message.getAuthor().getOrCreatePMChannel(), embedObject);

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

    public void listModules(final IChannel channel) {
        String loadedModulesString = "";
        for (final String key : this.loadedModules.keySet()) {
            loadedModulesString = loadedModulesString + key + '\n';
        }
        if (loadedModulesString.isEmpty()) {
            loadedModulesString = "_keine_";
        }

        String unloadedModulesString = "";
        for (final String key : this.unloadedModules.keySet()) {
            unloadedModulesString = unloadedModulesString + key + '\n';
        }
        if (unloadedModulesString.isEmpty()) {
            unloadedModulesString = "_keine_";
        }

        final EmbedBuilder builder = new EmbedBuilder();

        builder.appendField("Aktivierte Module", loadedModulesString, true);
        builder.appendField("Deaktivierte Module", unloadedModulesString, true);

        Util.sendEmbed(channel, builder.build());
    }

    public void loadModule(final String moduleName, final IChannel channel) {
        if (moduleName.isEmpty()) {
            Util.sendMessage(channel, "Fehler! Kein Modul angegeben.");
            return;
        }
        if (!this.unloadedModules.containsKey(moduleName)) {
            Util.sendMessage(channel, "Fehler! Modul `" + moduleName + "` ist bereits aktiviert oder existiert nicht.");
            return;
        }

        // Modul in andere Map übertragen und entfernen
        final Object module = this.unloadedModules.get(moduleName);
        this.loadedModules.put(moduleName, module);
        this.unloadedModules.remove(moduleName);
        this.makeCommandMap();

        // Modul aus JSON-Array entfernen
        final JSONArray jsonUnloadedModules = this.json.getJSONArray("unloadedModules");
        for (int i = 0; i < jsonUnloadedModules.length(); i++) {
            final String unloadedModuleName = jsonUnloadedModules.getString(i);
            if (unloadedModuleName.equals(moduleName)) {
                jsonUnloadedModules.remove(i);
            }
        }
        this.saveJSON();

        // EventListener aktivieren
        final CommandModule moduleAnnotation = module.getClass().getDeclaredAnnotationsByType(CommandModule.class)[0];
        if (!moduleAnnotation.commandOnly()) {
            try {
                this.client.getDispatcher().registerListener(module);
            } catch (NullPointerException e) {
                System.err.println("[Error] Could not get EventDispatcher: ");
                Util.error(e, channel);
            }
        }

        Util.sendMessage(channel, ":white_check_mark: Modul `" + moduleName + "` aktiviert.");
    }

    public void unloadModule(final String moduleName, final IChannel channel) {
        if (moduleName.isEmpty()) {
            Util.sendMessage(channel, "Fehler! Kein Modul angegeben.");
            return;
        }
        if (!this.loadedModules.containsKey(moduleName)) {
            Util.sendMessage(channel, "Fehler! Modul `" + moduleName + "` ist bereits deaktiviert oder existiert nicht.");
            return;
        }

        // Modul in andere Map übertragen und entfernen
        final Object module = this.loadedModules.get(moduleName);
        if (module.getClass().isAnnotationPresent(AlwaysLoaded.class)) {
            Util.sendMessage(channel, "Dieses Modul kann nicht deaktiviert werden.");
            return;
        }
        this.unloadedModules.put(moduleName, module);
        this.loadedModules.remove(moduleName);
        this.makeCommandMap();

        // Modul in JSON-Array speichern
        final JSONArray jsonUnloadedModules = this.json.getJSONArray("unloadedModules");
        jsonUnloadedModules.put(moduleName);
        this.saveJSON();

        // EventListener deaktivieren
        final CommandModule moduleAnnotation = module.getClass().getDeclaredAnnotationsByType(CommandModule.class)[0];
        if (!moduleAnnotation.commandOnly()) {
            try {
                this.client.getDispatcher().unregisterListener(module);
            } catch (NullPointerException e) {
                System.err.println("[Error] Could not get EventDispatcher: ");
                Util.error(e, channel);
            }
        }

        Util.sendMessage(channel, ":x: Modul `" + moduleName + "` deaktiviert.");

    }

    private void saveJSON() {
        final String jsonOutput = this.json.toString(4);
        Util.writeToFile(CONFIG_PATH, jsonOutput);

        this.json = new JSONObject(jsonOutput);
    }

    public static void main(String[] args) {
        new DiscordBot();
    }
}

package de.nikos410.discordBot;

import de.nikos410.discordBot.util.general.Authorization;
import de.nikos410.discordBot.util.general.Util;
import de.nikos410.discordBot.util.modular.*;

import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
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
import sx.blah.discord.handle.obj.IGuild;
import sx.blah.discord.handle.obj.IMessage;
import sx.blah.discord.handle.obj.IUser;
import sx.blah.discord.util.EmbedBuilder;

import org.json.JSONObject;
import org.json.JSONArray;

public class DiscordBot {
    private final HashMap<String, Command> commands = new HashMap<>();
    private final HashMap<String, Object> unloadedModules = new HashMap<>();
    private final HashMap<String, Object> loadedModules = new HashMap<>();

    public final IDiscordClient client;

    private final static Path CONFIG_PATH = Paths.get("config/config.json");
    public JSONObject configJSON;

    private final String prefix;
    private final long modRoleID;
    private final long adminRoleID;
    private final long ownerID;

    /**
     * Richtet den Bot ein, lädt Konfiguration etc.
     */
    private DiscordBot() {
        final String configFileContent = Util.readFile(CONFIG_PATH);
        this.configJSON = new JSONObject(configFileContent);

        final String token = configJSON.getString("token");
        this.client = Authorization.createClient(token, true);

        this.prefix = configJSON.getString("prefix");
        this.modRoleID = configJSON.getLong("modRole");
        this.adminRoleID = configJSON.getLong("adminRole");
        this.ownerID = configJSON.getLong("owner");

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
            catch (InstantiationException | IllegalAccessException e) {
                System.err.println("Error loading module from class " + moduleClass.getName() + '\n' + e + " " + e.getMessage());
            }
            catch (InvocationTargetException e) {
                System.err.println("Error loading module from class " + moduleClass.getName() + '\n' + e + " " + e.getCause().getMessage());
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


        final JSONArray jsonUnloadedModules = this.configJSON.getJSONArray("unloadedModules");
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
                    final int parameterCount = method.getParameterCount();

                    // Mindestens 1 (message), max 6 (message + 5 parameter)
                    if (parameterCount > 0 && parameterCount <= 6) {
                        final Command cmd = new Command(module, method, help, pmAllowed, permissionLevel, parameterCount-1);
                        this.commands.put(command.toLowerCase(), cmd);
                    }
                    else {
                        System.err.println("Ungültige Anzahl Parameter bei Befehl " + command);
                    }

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
    private void handleMessage(final IMessage message) {
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
                final int parameterCount = command.parameterCount;
                ArrayList<String> params = parseParameters(messageContent, parameterCount);
                switch (parameterCount) {
                    case 0: {
                        command.method.invoke(command.object, message);
                        break;
                    }
                    case 1: {
                        command.method.invoke(command.object, message, params.get(0));
                        break;
                    }
                    case 2: {
                        command.method.invoke(command.object, message, params.get(0), params.get(1));
                        break;
                    }
                    case 3: {
                        command.method.invoke(command.object, message, params.get(0), params.get(1), params.get(2));
                        break;
                    }
                    case 4: {
                        command.method.invoke(command.object, message, params.get(0), params.get(1), params.get(2), params.get(3));
                        break;
                    }
                    case 5: {
                        command.method.invoke(command.object, message, params.get(0), params.get(1), params.get(2), params.get(3), params.get(4));
                        break;
                    }
                    default: {
                        Util.error(new RuntimeException("Invalid number of parameters!"), message.getChannel());
                    }

                }

            }
            catch (Exception e) {
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

    private ArrayList<String> parseParameters(String messageContent, int parameterCount) {
        final int prefixLength = prefix.length();
        final String content = messageContent.substring(prefixLength);
        final String parameterContent = content.substring(content.indexOf(' ')+1);
        final ArrayList<String> parameters = new ArrayList<>();
        parseParameters(parameterContent, parameters, parameterCount);
        return parameters;
    }

    private void parseParameters(String parameterContent, ArrayList<String> parameters, int parameterCount) {
        if (parameterCount == 0) {
            return;
        }

        if (!parameterContent.contains(" ")) {
            parameters.add(parameterContent);
            parseParameters("", parameters, parameterCount-1);
        }
        else {
            final int index = parameterContent.indexOf(' ');
            parameters.add(parameterContent.substring(0, index));
            parseParameters(parameterContent.substring(index + 1), parameters, parameterCount - 1);
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

    public HashMap<String, Object> getLoadedModules() {
        return loadedModules;
    }
    public HashMap<String, Object> getUnloadedModules() {
        return unloadedModules;
    }

    public String loadModule(final String moduleName) throws NullPointerException {
        if (moduleName.isEmpty()) {
            return "Fehler! Kein Modul angegeben.";
        }
        if (!this.unloadedModules.containsKey(moduleName)) {
            return "Fehler! Modul `" + moduleName + "` ist bereits aktiviert oder existiert nicht.";
        }

        // Modul in andere Map übertragen und entfernen
        final Object module = this.unloadedModules.get(moduleName);
        this.loadedModules.put(moduleName, module);
        this.unloadedModules.remove(moduleName);
        this.makeCommandMap();

        // Modul aus JSON-Array entfernen
        final JSONArray jsonUnloadedModules = this.configJSON.getJSONArray("unloadedModules");
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
            }
            catch (NullPointerException e) {
                System.err.println("[Error] Could not get EventDispatcher!");
                throw e;
            }
        }

        return ":white_check_mark: Modul `" + moduleName + "` aktiviert.";
    }

    public String unloadModule(final String moduleName) throws NullPointerException{
        if (moduleName.isEmpty()) {
            return "Fehler! Kein Modul angegeben.";
        }
        if (!this.loadedModules.containsKey(moduleName)) {
            return "Fehler! Modul `" + moduleName + "` ist bereits deaktiviert oder existiert nicht.";
        }

        // Modul in andere Map übertragen und entfernen
        final Object module = this.loadedModules.get(moduleName);
        if (module.getClass().isAnnotationPresent(AlwaysLoaded.class)) {
            return "Dieses Modul kann nicht deaktiviert werden.";
        }
        this.unloadedModules.put(moduleName, module);
        this.loadedModules.remove(moduleName);
        this.makeCommandMap();

        // Modul in JSON-Array speichern
        final JSONArray jsonUnloadedModules = this.configJSON.getJSONArray("unloadedModules");
        jsonUnloadedModules.put(moduleName);
        this.saveJSON();

        // EventListener deaktivieren
        final CommandModule moduleAnnotation = module.getClass().getDeclaredAnnotationsByType(CommandModule.class)[0];
        if (!moduleAnnotation.commandOnly()) {
            try {
                this.client.getDispatcher().unregisterListener(module);
            }
            catch (NullPointerException e) {
                System.err.println("[Error] Could not get EventDispatcher!");
                throw e;
            }
        }

        return  ":x: Modul `" + moduleName + "` deaktiviert.";
    }

    private void saveJSON() {
        final String jsonOutput = this.configJSON.toString(4);
        Util.writeToFile(CONFIG_PATH, jsonOutput);

        this.configJSON = new JSONObject(jsonOutput);
    }

    public static void main(String[] args) {
        DiscordBot bot = new DiscordBot();

        for (String s : bot.parseParameters("%%test dies sind params", 5)) {
            System.out.println(s);
        }
    }
}

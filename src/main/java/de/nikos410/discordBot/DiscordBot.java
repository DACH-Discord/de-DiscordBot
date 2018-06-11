package de.nikos410.discordBot;

import java.awt.Color;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import de.nikos410.discordBot.modular.*;
import de.nikos410.discordBot.modular.annotations.*;
import de.nikos410.discordBot.util.discord.*;
import de.nikos410.discordBot.util.io.IOUtil;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.IDiscordClient;
import sx.blah.discord.api.events.EventDispatcher;
import sx.blah.discord.api.events.EventSubscriber;
import sx.blah.discord.api.internal.json.objects.EmbedObject;
import sx.blah.discord.handle.impl.events.ReadyEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageReceivedEvent;
import sx.blah.discord.handle.impl.events.guild.channel.message.MessageUpdateEvent;
import sx.blah.discord.handle.obj.*;
import sx.blah.discord.util.EmbedBuilder;

import org.json.JSONObject;
import org.json.JSONArray;

public class DiscordBot {
    private final List<String> unloadedModules = new ArrayList<>();
    private final Map<String, Object> loadedModules = new HashMap<>();

    private final Map<String, Command> commands = new HashMap<>();

    public final IDiscordClient client;

    private final static Path CONFIG_PATH = Paths.get("config/config.json");
    public JSONObject rolesJSON;
    private final static Path ROLES_PATH = Paths.get("data/roles.json");
    public JSONObject configJSON;

    private final String prefix;
    private final long ownerID;

    private Logger log = LoggerFactory.getLogger(DiscordBot.class);

    /**
     * Richtet den Bot ein, lädt Konfiguration etc.
     */
    private DiscordBot() {
        final String configFileContent = IOUtil.readFile(CONFIG_PATH);
        if (configFileContent == null) {
            log.error("Could not read configuration file.");
            System.exit(1);
        }
        this.configJSON = new JSONObject(configFileContent);

        final String rolesFileContent = IOUtil.readFile(ROLES_PATH);
        if (rolesFileContent == null) {
            log.error("Could not read roles file.");
            System.exit(1);
        }
        this.rolesJSON = new JSONObject(rolesFileContent);
        log.info(String.format("Loaded roles file for %s guilds.", rolesJSON.keySet().size()));

        final String token = configJSON.getString("token");
        this.client = Authorization.createClient(token, true);
        log.info("Bot authorized.");

        this.prefix = configJSON.getString("prefix");
        this.ownerID = configJSON.getLong("owner");

        try {
            this.client.getDispatcher().registerListener(this);
        }
        catch (NullPointerException e) {
            log.error("Could not get EventDispatcher", e);
        }

        // Deaktivierte Module einlesen
        final JSONArray unloadedModulesJSON = this.configJSON.getJSONArray("unloadedModules");

        for (int i = 0; i < unloadedModulesJSON.length(); i++) {
            final String unloadedModuleName = unloadedModulesJSON.getString(i);
            this.unloadedModules.add(unloadedModuleName);
        }

        this.loadModules();
    }

    /**
     * Module werden dem Bot hinzugefügt
     */
    private void loadModules() {
        log.debug("Loading modules");

        this.loadedModules.clear();

        final Reflections reflections = new Reflections("de.nikos410.discordBot.modules");
        final Set<Class<?>> moduleClasses = reflections.getTypesAnnotatedWith(CommandModule.class);

        log.info(String.format("Found %s total module(s).", moduleClasses.size()));

        for (final Class<?> moduleClass : moduleClasses) {
            loadModule(moduleClass);
        }

        this.makeCommandMap();
        log.info(String.format("%s module(s) with %s command(s) active.", this.loadedModules.size(), this.commands.size()));
    }

    private void loadModule(Class<?> moduleClass) {
        log.debug(String.format("Loading module information from class %s.", moduleClass));

        final CommandModule moduleAnnotation = moduleClass.getDeclaredAnnotationsByType(CommandModule.class)[0];
        final String moduleName = moduleAnnotation.moduleName();

        if (this.unloadedModules.contains(moduleName)) {
            log.info(String.format("Module \"%s\" is deactivated. Skipping.", moduleName));
            return;
        }

        log.debug(String.format("Loading module \"%s\".", moduleName));
        final Object moduleObject = makeModuleObject(moduleClass);
        if (moduleObject != null) {
            this.loadedModules.put(moduleName, moduleObject);

            // EventListener aktivieren
            if (!moduleAnnotation.commandOnly()) {
                final EventDispatcher dispatcher = this.client.getDispatcher();
                dispatcher.registerListener(moduleObject);
            }

            log.info(String.format("Loaded module \"%s\".", moduleName));
        }
    }

    private Object makeModuleObject (Class<?> moduleClass) {
        log.debug(String.format("Creating object from class %s.", moduleClass));

        try {
            try {
                return moduleClass.getDeclaredConstructor(DiscordBot.class).newInstance(this);
            }
            catch (NoSuchMethodException e) {
                return moduleClass.newInstance();
            }
        }
        catch (InstantiationException | IllegalAccessException e) {
            log.warn(String.format("Something went wrong while creating object from class \"%s\". Skipping.", moduleClass.getName()), e);
            return null;
        }
        catch (InvocationTargetException e) {
            log.warn(String.format("Something went wrong while creating object from class \"%s\". Skipping.", moduleClass.getName()), e.getCause());
            return null;
        }
    }

    private void makeCommandMap() {
        log.debug("Registering commands.");

        this.commands.clear();

        for (final String key : this.loadedModules.keySet()) {
            Object module = this.loadedModules.get(key);

            log.debug(String.format("Registering command(s) for module \"%s\".", key));

            for (final Method method : module.getClass().getMethods()) {

                if (method.isAnnotationPresent(CommandSubscriber.class)) {

                    final CommandSubscriber[] annotations = method.getDeclaredAnnotationsByType(CommandSubscriber.class);

                    final String command = annotations[0].command();
                    final String help = annotations[0].help();
                    final boolean pmAllowed = annotations[0].pmAllowed();
                    final int permissionLevel = annotations[0].permissionLevel();
                    final int parameterCount = method.getParameterCount();
                    final boolean passContext = annotations[0].passContext();

                    // Mindestens 1 (message), max 6 (message + 5 parameter)
                    if (parameterCount > 0 && parameterCount <= 6) {
                        final Command cmd = new Command(module, method, help, pmAllowed, permissionLevel, parameterCount-1, passContext);
                        this.commands.put(command.toLowerCase(), cmd);

                        log.debug(String.format("Registered command \"%s\".", command));
                    }
                    else {
                        log.warn(String.format("Command \"%s\" has an invalid number of arguments. Skipping"), command);
                    }
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
            log.info(String.format("User %s used command help", UserOperations.makeUserString(message.getAuthor(), message.getGuild())));
            this.command_help(message);
            return;
        }

        if (commands.containsKey(messageCommand)) {
            final Command command = commands.get(messageCommand);

            final int userPermissionLevel = this.getUserPermissionLevel(message.getAuthor(), message.getGuild());
            if (userPermissionLevel < command.permissionLevel) {
                DiscordIO.sendMessage(message.getChannel(), String.format("Dieser Befehl ist für deine Gruppe (%s) nicht verfügbar.",
                        CommandPermissions.getPermissionLevelName(userPermissionLevel)));
                return;
            }

            if (message.getChannel().isPrivate() && !command.pmAllowed) {
                DiscordIO.sendMessage(message.getChannel(), "Dieser Befehl ist nicht in Privatnachrichten verfügbar!");
                return;
            }

            log.info(String.format("User %s used command %s", UserOperations.makeUserString(message.getAuthor(), message.getGuild()), messageCommand));

            try {
                final int parameterCount = command.parameterCount;
                final boolean passContext = command.passContext;
                List<String> params = parseParameters(messageContent, parameterCount, passContext);

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
                        log.error(String.format("Command \"%s\" has an invalid number of arguments. This should never happen.", messageCommand));
                        DiscordIO.errorNotify("Befehl kann wegen einer ungültigen Anzahl an Argumenten nicht ausgeführt werden. Dies sollte niemals passieren!", message.getChannel());
                    }
                }
            }
            catch (IllegalAccessException | InvocationTargetException e) {
                final Throwable cause = e.getCause();

                log.error(String.format("Command \"%s\" could not be executed.", messageCommand), e.getCause());

                final EmbedBuilder embedBuilder = new EmbedBuilder();
                embedBuilder.withColor(new Color(255, 42, 50));
                embedBuilder.appendField("Fehler aufgetreten", cause.toString(), false);
                embedBuilder.withFooterText("Mehr Infos in der Konsole");

                DiscordIO.sendEmbed(message.getChannel(), embedBuilder.build());
            }
        }

    }

    private List<String> parseParameters(String messageContent, int parameterCount, boolean passContext) {
        final List<String> parameters = new ArrayList<>();

        if (parameterCount == 0) {
            return parameters;
        }

        final int prefixLength = prefix.length();
        final String content = messageContent.substring(prefixLength);
        final String parameterContent = content.substring(content.indexOf(' ')+1);
        parseParameters(parameterContent, parameters, parameterCount, passContext);
        return parameters;
    }

    private void parseParameters(String parameterContent, List<String> parameters, int parameterCount, boolean passContext) {
        if (parameterCount == 1) {
            if (passContext) {
                // Rest der Nachricht anhängen
                parameters.add(parameterContent);
            }
            else {
                // Rest der Nachricht weglassen
                if (parameterContent.contains(" ")) {
                    parameters.add(parameterContent.substring(0, parameterContent.indexOf(' ')));
                }
                else {
                    parameters.add(parameterContent);
                }
            }
            return;
        }

        if (parameterContent.contains(" ")) {
            final int index = parameterContent.indexOf(' ');
            parameters.add(parameterContent.substring(0, index));
            parseParameters(parameterContent.substring(index + 1), parameters, parameterCount - 1, passContext);
        }
        else {
            parameters.add(parameterContent);
            parseParameters("", parameters, parameterCount-1, passContext);
        }
    }

    public int getUserPermissionLevel(final IUser user, final IGuild guild) {
        if (user.getLongID() == this.ownerID) {
            return CommandPermissions.OWNER;
        }

        if (!rolesJSON.has(guild.getStringID())) {
            log.warn(String.format("Rollen für Server %s (ID: %s) nicht konfiguriert!", guild.getName(), guild.getStringID()));
            return 0;
        }

        final JSONObject serverRoles = rolesJSON.getJSONObject(guild.getStringID());

        final long adminRoleID = serverRoles.getLong("adminRole");
        if (UserOperations.hasRoleByID(user, adminRoleID, guild)) {
            return CommandPermissions.ADMIN;
        }

        final long modRoleID = serverRoles.getLong("modRole");
        if (UserOperations.hasRoleByID(user, modRoleID, guild)) {
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


            StringBuilder helpBuilder = new StringBuilder();

            for (final Method method : module.getClass().getMethods()) {

                if (method.isAnnotationPresent(CommandSubscriber.class)) {
                    final CommandSubscriber annotation = method.getDeclaredAnnotationsByType(CommandSubscriber.class)[0];

                    if (this.getUserPermissionLevel(message.getAuthor(), message.getGuild()) >= annotation.permissionLevel()) {
                        final String command = annotation.command();
                        final String help = annotation.help();

                        helpBuilder.append(String.format("`%s` %s", command, help));
                        helpBuilder.append('\n');
                    }
                }
            }

            final String moduleHelp = helpBuilder.toString();

            final CommandModule[] annotations = module.getClass().getDeclaredAnnotationsByType(CommandModule.class);
            final String moduleName = annotations[0].moduleName();

            if (!moduleHelp.isEmpty()) {
                embedBuilder.appendField(moduleName, moduleHelp, false);
            }
        }

        final EmbedObject embedObject = embedBuilder.build();
        DiscordIO.sendEmbed(message.getAuthor().getOrCreatePMChannel(), embedObject);

        if (!message.getChannel().isPrivate()) {
            DiscordIO.sendMessage(message.getChannel(), ":mailbox_with_mail:");
        }
    }

    /**
     * Wird aufgerufen wenn der Bot fertig eingeloggt und bereit für Interaktionen mit der Discord-API ist
     */
    @EventSubscriber
    public void onStartup(final ReadyEvent event) {
        log.info(String.format("[INFO] Bot ready. Prefix: %s", this.prefix));
        log.info(String.format("Add this bot to a server: https://discordapp.com/oauth2/authorize?client_id=%s&scope=bot", client.getApplicationClientID()));
        client.changePlayingText(String.format("%shelp | WIP", this.prefix));
    }

    public Map<String, Object> getLoadedModules() {
        return loadedModules;
    }
    public List<String> getUnloadedModules() {
        return unloadedModules;
    }

    public String loadModule(final String moduleName) {
        if (moduleName.isEmpty()) {
            return "Fehler! Kein Modul angegeben.";
        }
        if (!this.unloadedModules.contains(moduleName)) {
            return String.format("Fehler! Modul `%s` ist bereits aktiviert oder existiert nicht.", moduleName);
        }

        log.debug(String.format("Activating module \"%s\".", moduleName));

        // Modul aus unloaded Liste entfernen
        this.unloadedModules.remove(moduleName);

        // Modul aus JSON-Array entfernen
        final JSONArray jsonUnloadedModules = this.configJSON.getJSONArray("unloadedModules");
        for (int i = 0; i < jsonUnloadedModules.length(); i++) {
            if (jsonUnloadedModules.getString(i).equals(moduleName)) {
                jsonUnloadedModules.remove(i);
            }
        }
        this.saveConfig();

        log.info(String.format("Reloading modules to add %s", moduleName));
        this.loadModules();

        return String.format(":white_check_mark: Modul `%s` aktiviert.", moduleName);
    }

    public String unloadModule(final String moduleName) {
        if (moduleName.isEmpty()) {
            return "Fehler! Kein Modul angegeben.";
        }
        if (!this.loadedModules.containsKey(moduleName)) {
            return String.format("Fehler! Modul `%s` ist bereits deaktiviert oder existiert nicht.", moduleName);
        }

        log.debug(String.format("Deactivating module \"%s\".", moduleName));

        // EventListener deaktivieren
        final Object moduleObject = loadedModules.get(moduleName);
        final Class<?> moduleClass = moduleObject.getClass();

        final CommandModule moduleAnnotation = moduleClass.getDeclaredAnnotationsByType(CommandModule.class)[0];
        if (!moduleAnnotation.commandOnly()) {
            final EventDispatcher dispatcher = this.client.getDispatcher();
            dispatcher.registerListener(moduleObject);
        }

        // Modul in unloaded Liste speichern
        this.unloadedModules.add(moduleName);

        // Modul in JSON-Array speichern
        final JSONArray jsonUnloadedModules = this.configJSON.getJSONArray("unloadedModules");
        jsonUnloadedModules.put(moduleName);
        this.saveConfig();

        log.info(String.format("Reloading modules to remove %s", moduleName));
        this.loadModules();

        return String.format(":white_check_mark: Modul `%s` deaktiviert.", moduleName);
    }

    private void saveConfig() {
        log.debug("Saving config file.");

        final String jsonOutput = this.configJSON.toString(4);
        IOUtil.writeToFile(CONFIG_PATH, jsonOutput);
    }

    public void saveRoles() {
        log.debug("Saving roles file.");

        final String jsonOutput = this.rolesJSON.toString(4);
        IOUtil.writeToFile(ROLES_PATH, jsonOutput);
    }

    public static void main(String[] args) {
        new DiscordBot();
    }
}

package de.nikos410.discordBot;

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

import de.nikos410.discordBot.modular.*;
import de.nikos410.discordBot.modular.annotations.*;
import de.nikos410.discordBot.util.discord.*;
import de.nikos410.discordBot.util.io.IOUtil;

import org.reflections.Reflections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sx.blah.discord.api.IDiscordClient;
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
        log.debug(String.format("Loaded configuration file with %s entries.", configJSON.keySet().size()));

        final String token = configJSON.getString("token");
        this.client = Authorization.createClient(token, true);
        log.info("Bot authorized.");

        this.prefix = configJSON.getString("prefix");
        this.modRoleID = configJSON.getLong("modRole");
        this.adminRoleID = configJSON.getLong("adminRole");
        this.ownerID = configJSON.getLong("owner");

        try {
            this.client.getDispatcher().registerListener(this);
        }
        catch (NullPointerException e) {
            log.error("Could not get EventDispatcher", e);
        }

        this.addModules();
        this.makeCommandMap();

        log.info(String.format("%s module(s) total.", loadedModules.size() + unloadedModules.size()));
        log.info(String.format("%s module(s) with %s command(s) active.", loadedModules.size(), commands.size()));
    }

    /**
     * Module werden dem Bot hinzugefügt
     */
    private void addModules() {
        log.debug("Loading modules");

        Reflections reflections = new Reflections("de.nikos410.discordBot.modules");
        Set<Class<?>> moduleClasses = reflections.getTypesAnnotatedWith(CommandModule.class);

        for (Class<?> moduleClass : moduleClasses) {
            try { // TODO: Zuerst überprüfen, ob Modul aktiviert werden soll, danach erst Instanz erstellen
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
                log.warn(String.format("Something went wrong while loading module from class \"%s\". Skipping.", moduleClass.getName()), e);
            }
            catch (InvocationTargetException e) {
                log.warn(String.format("Something went wrong while loading module from class \"%s\". Skipping.", moduleClass.getName()), e.getCause());
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
            log.warn(String.format("Could not load module from class \"%s\". Skipping.", module.getClass().getName()));
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
                log.info(String.format("Module \"%s\" is deactivated. Skipping.", moduleName));
                return;
            }
        }

        // Modul ist nicht deaktiviert
        this.loadedModules.put(moduleName, module);
        log.info(String.format("Loaded module \"%s\".", moduleName));
    }

    private void makeCommandMap() {
        log.debug("Registering commands.");

        this.commands.clear();

        for (final String key : this.loadedModules.keySet()) {
            Object module = this.loadedModules.get(key);

            log.debug(String.format("Registering command(s) for module \"%s\".", key));

            for (Method method : module.getClass().getMethods()) {

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

            final CommandModule moduleAnnotation = module.getClass().getDeclaredAnnotationsByType(CommandModule.class)[0];
            if (!moduleAnnotation.commandOnly()) {
                try {
                    this.client.getDispatcher().registerListener(module);
                } catch (NullPointerException e) {
                    log.error("Could not get EventDispatcher", e);
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
            log.info(String.format("User %s used command %s", UserOperations.makeUserString(message.getAuthor(), message.getGuild()), "help"));
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

            try {
                final int parameterCount = command.parameterCount;
                final boolean passContext = command.passContext;
                ArrayList<String> params = parseParameters(messageContent, parameterCount, passContext);

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
                log.info(String.format("User %s used command %s", UserOperations.makeUserString(message.getAuthor(), message.getGuild()), messageCommand));
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

    private ArrayList<String> parseParameters(String messageContent, int parameterCount, boolean passContext) {
        final ArrayList<String> parameters = new ArrayList<>();

        if (parameterCount == 0) {
            return parameters;
        }

        final int prefixLength = prefix.length();
        final String content = messageContent.substring(prefixLength);
        final String parameterContent = content.substring(content.indexOf(' ')+1);
        parseParameters(parameterContent, parameters, parameterCount, passContext);
        return parameters;
    }

    private void parseParameters(String parameterContent, ArrayList<String> parameters, int parameterCount, boolean passContext) {
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
        else if (UserOperations.hasRoleByID(user, this.adminRoleID, guild)) {
            return CommandPermissions.ADMIN;
        }
        else if (UserOperations.hasRoleByID(user, this.modRoleID, guild)) {
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

            for (Method method : module.getClass().getMethods()) {

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
        client.changePlayingText(String.format("%shelp | WIP", this.prefix));
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
            return String.format("Fehler! Modul `%s` ist bereits aktiviert oder existiert nicht.", moduleName);
        }

        log.debug(String.format("Activating module \"%s\".", moduleName));


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
                log.error("Could not get EventDispatcher", e);
                return "Could not get EventDispatcher!";
            }
        }

        log.debug(String.format("Successfully activated module %s", moduleName));
        return String.format(":white_check_mark: Modul `%s` aktiviert.", moduleName);
    }

    public String unloadModule(final String moduleName) throws NullPointerException {
        if (moduleName.isEmpty()) {
            return "Fehler! Kein Modul angegeben.";
        }
        if (!this.loadedModules.containsKey(moduleName)) {
            return String.format("Fehler! Modul `%s` ist bereits deaktiviert oder existiert nicht.", moduleName);
        }

        log.debug(String.format("Deactivating module \"%s\".", moduleName));


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
                log.error("Could not get EventDispatcher", e);
                return "Could not get EventDispatcher!";
            }
        }

        log.debug(String.format("Successfully deactivated module %s", moduleName));
        return String.format(":x: Modul `%s` deaktiviert.", moduleName);
    }

    private void saveJSON() {
        log.debug("Saving config file.");

        final String jsonOutput = this.configJSON.toString(4);
        IOUtil.writeToFile(CONFIG_PATH, jsonOutput);

        this.configJSON = new JSONObject(jsonOutput);
    }

    public static void main(String[] args) {
        new DiscordBot();
    }
}

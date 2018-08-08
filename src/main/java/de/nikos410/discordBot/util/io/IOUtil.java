package de.nikos410.discordBot.util.io;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IOUtil {

    private final static Logger LOG = LoggerFactory.getLogger(IOUtil.class);

    public static String readFile(Path path) {
        try {
            return new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
        }
        catch (IOException | NullPointerException e){
            LOG.error(String.format("Could not read file from Path \"%s\"", path), e);
            return null;
        }
    }

    public static Path writeToFile(Path path, String text) {
        try {
            return Files.write(path, text.getBytes(StandardCharsets.UTF_8));
        }
        catch (IOException e) {
            LOG.error(String.format("Could not write to Path \"%s\"", path), e);
            return null;
        }
    }

}
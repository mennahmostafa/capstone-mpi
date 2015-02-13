package ca.mcmaster.capstone.util;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import lombok.NonNull;

public class FileUtil {

    public static List<String> getLines(@NonNull File file) throws IOException {
        final List<String> lines = new ArrayList<>();
        try (final BufferedReader reader = new BufferedReader(new FileReader(file))) {
            while (reader.ready()) {
                lines.add(reader.readLine());
            }
        }
        return lines;
    }

}

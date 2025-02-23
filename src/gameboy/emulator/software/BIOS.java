package gameboy.emulator.software;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

// The class for the Game Boy BIOS.
// The Game Boy BIOS is a fixed array, usually of 256 bytes, with startup instructions.
public class BIOS {
    public Path path;
    public byte[] data;

    public BIOS(String filename) {
        this.path = Paths.get(filename);

        try {
            this.data = Files.readAllBytes(path);
        }
        catch(IOException e) {
            throw new IllegalStateException(e);
        }
    }
}

package com.skystream.ssyoutube;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.Assert.*;

public class LogStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void rotatesBeforeLimitAndExportsBothFilesInOrder() throws Exception {
        File directory = temporary.newFolder("logs");
        LogStore store = new LogStore(directory);
        String first = new String(new char[LogStore.MAX_BYTES]).replace('\0', 'a');
        store.append(first);
        store.append("latest\n");
        assertEquals(LogStore.MAX_BYTES,
                new File(directory, "ssyoutube-previous.log").length());
        assertEquals(7, new File(directory, "ssyoutube.log").length());
        File snapshot = store.snapshot(new File(temporary.newFolder("share"), "export.log"));
        assertEquals(first + "latest\n", read(snapshot));
        store.append("after export\n");
        assertEquals(first + "latest\n", read(snapshot));
    }

    @Test
    public void clearDeletesActiveAndPreviousLogs() throws Exception {
        File directory = temporary.newFolder("logs");
        LogStore store = new LogStore(directory);
        store.append(new String(new char[LogStore.MAX_BYTES]).replace('\0', 'a'));
        store.append("next");
        store.clear();
        assertEquals(0, directory.list().length);
        assertNull(store.snapshot(new File(temporary.getRoot(), "empty.log")));
        store.clear();
    }

    @Test
    public void keepsOnlyOneBackup() throws Exception {
        File directory = temporary.newFolder("logs");
        LogStore store = new LogStore(directory);
        String entry = new String(new char[LogStore.MAX_BYTES]).replace('\0', 'a');
        store.append(entry);
        store.append(entry);
        store.append(entry);
        assertEquals(2, directory.list().length);
    }

    @Test(expected = IOException.class)
    public void oversizedEntriesCannotExceedStorageLimit() throws Exception {
        new LogStore(temporary.newFolder()).append(
                new String(new char[LogStore.MAX_BYTES + 1]));
    }

    @Test(expected = IOException.class)
    public void writeFailuresAreReported() throws Exception {
        new LogStore(temporary.newFile()).append("entry");
    }

    private static String read(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}

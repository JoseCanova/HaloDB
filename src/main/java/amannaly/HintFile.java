package amannaly;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.util.Iterator;
import java.util.Objects;

public class HintFile {

    private final int fileId;
    private final File dbDirectory;

    private FileChannel channel;

    private final HaloDBOptions options;

    private long unFlushedData = 0;

    private static final String nullMessage = "Hint file entry cannot be null";

    public HintFile(int fileId, File dbDirectory, HaloDBOptions options) {
        this.fileId = fileId;
        this.dbDirectory = dbDirectory;
        this.options = options;
    }

    public void open() throws IOException {
        File file = getHintFile();
        file.createNewFile();

        channel = new RandomAccessFile(file, "rw").getChannel();
    }

    public void close() throws IOException {
        if (channel != null) {
            channel.close();
        }
    }

    public void delete() throws IOException {
        if (channel != null && channel.isOpen())
            channel.close();

        getHintFile().delete();
    }

    public void write(HintFileEntry entry) throws IOException {
        Objects.requireNonNull(entry, nullMessage);

        ByteBuffer[] contents = entry.serialize();

        long toWrite = 0;
        for (ByteBuffer buffer : contents) {
            toWrite += buffer.remaining();
        }

        long written = 0;
        while (written < toWrite) {
            written += channel.write(contents);
        }

        unFlushedData += written;

        if (options.flushDataSizeBytes != -1 && unFlushedData > options.flushDataSizeBytes) {
            channel.force(false);
            unFlushedData = 0;
        }

    }

    public HintFileIterator newIterator() throws IOException {
        return new HintFileIterator();
    }

    private File getHintFile() {
        return Paths.get(dbDirectory.getPath(), fileId + ".hint").toFile();
    }

    public class HintFileIterator implements Iterator<HintFileEntry> {

        private final ByteBuffer buffer;

        //TODO: hint files are not that large, need to check the
        // performance since we are memory mapping it.
        public HintFileIterator() throws IOException {
            buffer = channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size());
        }

        @Override
        public boolean hasNext() {
            return buffer.hasRemaining();
        }

        @Override
        public HintFileEntry next() {
            if (hasNext()) {
                return HintFileEntry.deserialize(buffer);
            }

            return null;
        }
    }
}

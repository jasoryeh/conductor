package net.vectormc.conductor.downloaders;

import lombok.Getter;
import lombok.Setter;
import net.vectormc.conductor.downloaders.authentication.Credentials;
import net.vectormc.conductor.downloaders.exceptions.RetrievalException;

import java.io.File;
import java.nio.file.FileSystems;

public abstract class Downloader {
    @Getter
    @Setter
    private static File tempFolder;

    static {
        if(tempFolder == null || !tempFolder.isDirectory()) {
            File f = new File("launcher_tmp");
            boolean done = f.exists() && f.isDirectory() || f.mkdir();
            if(!done) f = new File(FileSystems.getDefault().getPath(".").toAbsolutePath().toString());
            tempFolder = f;
        }
    }


    @Getter
    protected DownloaderType downloaderType;
    @Getter
    protected String fileName;
    @Getter
    @Setter
    protected boolean replaceIfExists;
    @Getter
    protected File downloadedFile;
    @Getter
    protected boolean downloaded;
    @Getter
    @Setter
    protected Credentials credentials;

    public abstract void download() throws RetrievalException;

    public boolean setFileName(String newName) {
        return downloadedFile.renameTo(new File(newName));
    }

    public boolean deleteFile() {
        return downloadedFile.delete();
    }

    public boolean fileExists() {
        return downloadedFile.exists();
    }

    public boolean fileIsDirectory() {
        return downloadedFile.isDirectory();
    }

    public String getFileName() {
        return downloadedFile.getName();
    }

    enum DownloaderType {
        URL,
        JENKINS,
        OTHER
    }
}

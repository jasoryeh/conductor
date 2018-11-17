package net.vectormc.conductor.downloaders;

import lombok.Getter;
import lombok.Setter;
import net.vectormc.conductor.downloaders.authentication.Credentials;
import net.vectormc.conductor.downloaders.exceptions.RetrievalException;
import net.vectormc.conductor.util.Utility;

import java.io.File;

public abstract class Downloader {
    @Setter
    private static File tempFolder = new File(Utility.getCWD() + File.separator + "launcher_tmp");

    public static File getTempFolder() {
        tempFolder.mkdirs();
        return tempFolder;
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

    public String getDLFileName() {
        return downloadedFile.getName();
    }

    enum DownloaderType {
        URL,
        JENKINS,
        OTHER
    }
}

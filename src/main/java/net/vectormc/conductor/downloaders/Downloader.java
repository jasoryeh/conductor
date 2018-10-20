package net.vectormc.conductor.downloaders;

import lombok.Getter;

import java.io.BufferedInputStream;
import java.io.File;

public abstract class Downloader {
    @Getter
    protected DownloaderType downloaderType;
    @Getter
    protected String fileName;
    @Getter
    protected boolean replaceIfExists;
    @Getter
    protected File downloadedFile;
    @Getter
    protected boolean downloaded;

    public abstract void download();

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

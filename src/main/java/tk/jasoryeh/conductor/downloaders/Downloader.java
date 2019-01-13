package tk.jasoryeh.conductor.downloaders;

import lombok.Getter;
import lombok.Setter;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.downloaders.exceptions.RetrievalException;
import tk.jasoryeh.conductor.util.Utility;

import java.io.File;

/**
 * A template for other downloader types such as the JsonDownloader and the URLDownloader
 */
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

package tk.jasoryeh.conductor.downloaders;

import lombok.Getter;
import lombok.Setter;
import tk.jasoryeh.conductor.log.Logger;

import java.io.File;

/**
 * A template for other downloader types such as the JsonDownloader and the URLDownloader
 */
public abstract class Downloader {

    @Getter
    protected Logger logger;
    @Getter
    protected File destination;
    @Getter
    @Setter
    protected boolean overwrite;

    public Downloader(File downloadTo, boolean overwrite) {
        this.logger = new Logger(this.getClass().getSimpleName());
        this.destination = downloadTo;
        if (this.overwrite && this.destination.exists()) {
            if (!this.destination.delete()) {
                throw new IllegalStateException(String.format("Unable to delete file to make way for overwriting at %s", this.destination.getAbsolutePath()));
            }
        }
    }

    public boolean isDownloaded() {
        return destination.exists();
    }

    public abstract boolean download();
}

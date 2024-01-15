package tk.jasoryeh.conductor.plugins;

import tk.jasoryeh.conductor.V2FileSystemObject;
import tk.jasoryeh.conductor.downloaders.URLDownloader;
import tk.jasoryeh.conductor.log.Logger;
import tk.jasoryeh.conductor.secrets.HttpPluginSecret;
import tk.jasoryeh.conductor.util.Assert;

import java.io.File;
import java.net.URL;

public class HttpPlugin extends Plugin {
    private HttpPluginSecret secrets;
    private URL url;

    public HttpPlugin(V2FileSystemObject object, HttpPluginSecret secrets, URL url) {
        super(object);
        this.secrets = secrets;
        this.url = url;
    }

    @Override
    public void prepare() {
        this.logger.info("HttpPlugin is attempting to retrieve " + this.url);
        File tempFile = this.getFsObject().getTemporary();
        File parentFile = tempFile.getParentFile();
        Assert.isTrue(parentFile.exists() || parentFile.mkdirs(), "mkdirs - http");
        URLDownloader downloader = new URLDownloader(
                this.getFsObject().getTemporary(),
                true,
                this.url.toString(),
                this.secrets
        );
        downloader.download();
        this.logger.info("-");
    }

    @Override
    public void execute() {
        // download is complete, we don't really have anything to do here, the FileObject should copy the final file from
        //   the temporary directory to the appropriate place.
    }
}

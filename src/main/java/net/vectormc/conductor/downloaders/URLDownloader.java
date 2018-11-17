package net.vectormc.conductor.downloaders;

import lombok.Getter;
import net.vectormc.conductor.downloaders.authentication.Credentials;
import net.vectormc.conductor.downloaders.exceptions.RetrievalException;

import java.io.File;
import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Map;
import java.util.function.BiConsumer;

public class URLDownloader extends Downloader {
    @Getter
    private String url;

    public URLDownloader(String url, String fileName, boolean replaceIfExists, Credentials credentials) {
        this.url = url;
        this.fileName = fileName;
        this.replaceIfExists = replaceIfExists;
        this.downloaded = false;
        this.credentials = credentials;
    }

    @Override
    public void download() throws RetrievalException {
        try {
            URL u = new URL(this.url);
            HttpURLConnection huc = (HttpURLConnection) u.openConnection();
            credentials.credentials.forEach((credentialType, stringStringMap) -> stringStringMap.forEach(huc::setRequestProperty));

            ReadableByteChannel i = Channels.newChannel(huc.getInputStream());
            FileOutputStream o = new FileOutputStream(new File(getTempFolder() + File.separator + this.fileName));
            o.getChannel().transferFrom(i, 0, Long.MAX_VALUE);

            o.close();
            i.close();
        } catch(Exception e) {
            throw new RetrievalException(e);
        }
    }
}

package net.vectormc.conductor.downloaders;

import lombok.Getter;

public class URLDownloader extends Downloader {
    @Getter
    private String url;

    public URLDownloader(String url, String fileName, boolean replaceIfExists) {
        this.url = url;
        this.fileName = fileName;
        this.replaceIfExists = replaceIfExists;
        this.downloaded = false;
    }

    public void addCredentials() {

    }

    @Override
    public void download() {

    }
}

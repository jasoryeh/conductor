package net.vectormc.conductor.processor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.vectormc.conductor.Conductor;
import net.vectormc.conductor.config.Configuration;
import net.vectormc.conductor.config.ServerConfig;
import net.vectormc.conductor.debug.DebugException;
import net.vectormc.conductor.downloaders.JenkinsDownloader;
import net.vectormc.conductor.downloaders.URLDownloader;
import net.vectormc.conductor.downloaders.authentication.Credentials;
import net.vectormc.conductor.downloaders.exceptions.RetrievalException;
import net.vectormc.conductor.log.Logger;
import net.vectormc.conductor.util.Utility;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ServerJsonConfigProcessor {
    public static ServerConfig process(final JsonObject jsonObject) {
        String name = jsonObject.get("templateName").getAsString();
        ServerType type = ServerType.valueOf(jsonObject.get("type").getAsString().toUpperCase());

        String launchFile = jsonObject.get("launch").getAsString();
        String launchOptions = jsonObject.get("launchOptions").getAsString();

        boolean overwrite = jsonObject.get("overwrite").getAsBoolean();

        // Files
        JsonObject tree = jsonObject.get("tree").getAsJsonObject();

        ServerConfig config = new ServerConfig(name, type, launchFile, launchOptions, overwrite, tree);

        if (!processTree(tree, config, "", true)) {
            Logger.getLogger().error("Something went wrong, shutting down.");
            new DebugException().printStackTrace();
            Conductor.getInstance().shutdown(true);
        }

        return config;
    }

    /**
     * Process trees of files
     * @param jsonObject object of tree
     * @param conf Server configuration
     * @param parents Parents of tree
     * @param recursive whether to not to go through all of them
     * @return
     */
    private static boolean processTree(final JsonObject jsonObject, ServerConfig conf, String parents, boolean recursive) {
        for (Map.Entry<String, JsonElement> stringJsonElementEntry : jsonObject.entrySet()) {
            if(!stringJsonElementEntry.getValue().isJsonObject()) continue;

            // if(stringJsonElementEntry.getKey().equals(conf.getLaunchFile())) conf.setLaunchFilePresent(true);

            processObject(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue().getAsJsonObject(), conf, parents, recursive);
        }
        return true;
    }

    /**
     * Process files/folders in `tree`
     * @param fileName name of file
     * @param obj json object of the file/folder
     * @param conf server configuration object
     * @param parents folder to put the file/folder under ex. plugins/Some/Secret/Folder <- will place for example file test.txt in NO slashes beginning
     * @param recursive go through each objects under all the other trees
     * @return success or not
     */
    private static boolean processObject(String fileName, JsonObject obj, ServerConfig conf, String parents, boolean recursive) {
        String path = Utility.getCWD() + File.separator +
                (parents.equalsIgnoreCase("") ? "" : File.separator + parents + File.separator) + fileName;
        File f = new File(path);

        if(f.exists() && conf.isOverwrite()) {
            if(!f.delete()) {
                Logger.getLogger().error("[OVERWRITE] UNABLE TO DELETE " + f.getAbsolutePath());
            } else {
                Logger.getLogger().info("[OVERWRITE] OVERWRITE: " + f.getAbsolutePath() + " (Deleted)");
            }
        }

        Logger.getLogger().info("[WRITE] Create " + f.getAbsolutePath());

        if(obj.get("type").getAsString().equalsIgnoreCase("folder")) {
            if (!f.mkdirs()) {
                return false;
            }

            JsonObject retrieval = obj.get("retrieval").getAsJsonObject();
            if (!retrieval.get("retrieve").getAsBoolean()) {
                if(obj.get("contents") != null) {
                    for (Map.Entry<String, JsonElement> contents : obj.get("contents").getAsJsonObject().entrySet()) {
                        // Tree only, folders can't have "text content"
                        if(contents.getKey().equalsIgnoreCase("tree")) {
                            processTree(contents.getValue().getAsJsonObject(), conf, parents + File.separator + fileName, recursive);
                        }
                    }
                }
                return true;
            } else {
                RetrieveType type = RetrieveType.valueOf(obj.get("method").getAsString());

                Credentials credentials = new Credentials();
                Credentials.CredentialType ct = Credentials.CredentialType.valueOf(obj.get("requestType").getAsString());

                for (Map.Entry<String, JsonElement> authDetails : obj.get("authDetails").getAsJsonObject().entrySet()) {
                    // (should) do nothing if auth details aren't present
                    credentials.addToRequiredCredentials(ct, authDetails.getKey(), authDetails.getValue().getAsString());
                }

                switch(type) {
                    case URL:
                        try {
                            URLDownloader ud = new URLDownloader(obj.get("url").getAsString(), fileName, conf.isOverwrite(), credentials);
                            ud.download();
                            // Unzip
                            if (obj.get("unzipRequired").getAsBoolean()) {
                                FileInputStream fis = new FileInputStream(ud.getDownloadedFile().getAbsolutePath());

                                ZipInputStream zis = new ZipInputStream(fis);
                                ZipEntry ze = zis.getNextEntry();

                                while(ze != null) {
                                    File nf = new File(f.getAbsolutePath() + File.separator + ze.getName());
                                    nf.getParentFile().mkdirs();

                                    Logger.getLogger().info("De-zipping to " + nf.getAbsolutePath());


                                    FileOutputStream fos = new FileOutputStream(nf);
                                    int len;
                                    byte[] buffer = new byte[4096];
                                    while((len = zis.read()) > 0) {
                                        fos.write(buffer, 0, len);
                                    }

                                    fos.close();

                                    zis.closeEntry();
                                    ze = zis.getNextEntry();
                                }

                                zis.closeEntry();
                                zis.close();
                                fis.close();

                            } else {
                                Files.copy(ud.getDownloadedFile().toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            }
                            // Finish unzip

                            // ... move on
                        } catch(RetrievalException re) {
                            re.printStackTrace();
                            Conductor.getInstance().shutdown(true);
                        } catch(FileNotFoundException fnfe) {
                            fnfe.printStackTrace();
                            Conductor.getInstance().shutdown(true);
                        } catch(IOException ioe) {
                            ioe.printStackTrace();
                            Conductor.getInstance().shutdown(true);
                        }
                        break;
                    case JENKINS:
                        // TODO: Jenkins WIP
                        throw new UnsupportedOperationException();
                    case SPECIFIED:
                        // Specified not supposed to land here
                        throw new UnsupportedOperationException();
                    default:
                        // Other types
                        throw new UnsupportedOperationException();
                }
            }
        } else if(obj.get("type").getAsString().equalsIgnoreCase("file")) {
            JsonObject retrieval = obj.get("retrieval").getAsJsonObject();
            if (!retrieval.get("retrieve").getAsBoolean()) {
                try {
                    if (!f.createNewFile()) {
                        throw new IOException("Unable to create new file " + f.getAbsolutePath());
                    }
                    for (Map.Entry<String, JsonElement> contents : obj.get("contents").getAsJsonObject().entrySet()) {
                        // Tree only, folders can't have "text content"
                        if(contents.getKey().equalsIgnoreCase("content")) {
                            FileOutputStream fos = new FileOutputStream(f);
                            fos.write(contents.getValue().getAsString().getBytes());
                            fos.close();
                        }
                    }
                } catch(IOException io) {
                    io.printStackTrace();
                }
                return true;
            } else {
                RetrieveType type = RetrieveType.valueOf(retrieval.get("method").getAsString().toUpperCase());

                Credentials credentials = new Credentials();
                Credentials.CredentialType ct = Credentials.CredentialType.valueOf(retrieval.get("requestType").getAsString());

                for (Map.Entry<String, JsonElement> authDetails : retrieval.get("authDetails").getAsJsonObject().entrySet()) {
                    // (should) do nothing if auth details aren't present
                    credentials.addToRequiredCredentials(ct, authDetails.getKey(), authDetails.getValue().getAsString());
                }

                switch(type) {
                    case URL:
                        try {
                            URLDownloader ud = new URLDownloader(retrieval.get("url").getAsString(), fileName, conf.isOverwrite(), credentials);
                            ud.download();

                            Files.copy(
                                    ud.getDownloadedFile().toPath(),
                                    f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            // ... move on
                        } catch(RetrievalException re) {
                            re.printStackTrace();
                            Conductor.getInstance().shutdown(true);
                        } catch(FileNotFoundException fnfe) {
                            fnfe.printStackTrace();
                            Conductor.getInstance().shutdown(true);
                        } catch(IOException ioe) {
                            ioe.printStackTrace();
                            Conductor.getInstance().shutdown(true);
                        }
                        break;
                    case JENKINS:
                        try {
                            Configuration config = Conductor.getInstance().getConfig();
                            boolean endNoAuth = !config.entryExists("jenkinsHost")
                                    || !config.entryExists("jenkinsUsername")
                                    || !config.entryExists("jenkinsPasswordOrToken");
                            if(!retrieval.get("auth").getAsBoolean() && endNoAuth) {
                                Logger.getLogger().error("Jenkins Authentication not present, not continuing.");
                                Conductor.getInstance().shutdown(true);
                            }

                            JsonObject jenkinsAuth = retrieval.get("jenkinsAuth").getAsJsonObject();

                            boolean useSpecifiedAuth = retrieval.get("auth").getAsBoolean();

                            boolean endNoInfo = (jenkinsAuth.get("job") == null) || (jenkinsAuth.get("artifact") == null) || (jenkinsAuth.get("number") == null);
                            if(endNoInfo) {
                                Logger.getLogger().error("Jenkins artifact information not present, not continuing.");
                                Conductor.getInstance().shutdown(true);
                            }

                            String job = jenkinsAuth.get("job").getAsString();
                            String artifact = jenkinsAuth.get("artifact").getAsString();
                            int number = jenkinsAuth.get("number").getAsInt();

                            String host = useSpecifiedAuth ? jenkinsAuth.get("host").getAsString() : config.getString("jenkinsHost");
                            String username = useSpecifiedAuth ? (jenkinsAuth.get("username") == null ? "" : jenkinsAuth.get("username").getAsString()) : config.getString("jenkinsUsername");
                            String passwordOrToken = useSpecifiedAuth ? (jenkinsAuth.get("passwordOrToken") == null ? "" : jenkinsAuth.get("passwordOrToken").getAsString()) : config.getString("jenkinsPasswordOrToken");


                            JenkinsDownloader jenkinsDownloader = new JenkinsDownloader(host, job, artifact, number, username, passwordOrToken, fileName, true, new Credentials());
                            jenkinsDownloader.download();

                            Files.copy(
                                    jenkinsDownloader.getDownloadedFile().toPath(),
                                    f.toPath(), StandardCopyOption.REPLACE_EXISTING);
                        } catch(RetrievalException re) {
                            re.printStackTrace();
                            Conductor.getInstance().shutdown(true);
                        } catch(IOException io) {
                            io.printStackTrace();
                            Conductor.getInstance().shutdown(true);
                        }
                        break;
                    case SPECIFIED:
                        // Specified not supposed to land here
                        throw new UnsupportedOperationException();
                    default:
                        // Other types
                        throw new UnsupportedOperationException();
                }
            }
        }
        return true;
    }

    public enum RetrieveType {
        URL("url"), JENKINS("jenkins"), SPECIFIED("specified");

        @Getter
        private final String equivalent;

        RetrieveType(String equivalent) {
            this.equivalent = equivalent;
        }
    }
    public enum ServerType {
        JAVA("java");

        @Getter
        private final String equivalent;

        ServerType(String equivalent) {
            this.equivalent = equivalent;
        }
    }
}

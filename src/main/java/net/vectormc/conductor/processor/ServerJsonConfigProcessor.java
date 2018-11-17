package net.vectormc.conductor.processor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;
import net.vectormc.conductor.Conductor;
import net.vectormc.conductor.config.ServerConfig;
import net.vectormc.conductor.downloaders.URLDownloader;
import net.vectormc.conductor.downloaders.authentication.Credentials;
import net.vectormc.conductor.downloaders.exceptions.RetrievalException;
import net.vectormc.conductor.log.Logger;
import net.vectormc.conductor.util.Utility;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ServerJsonConfigProcessor {
    public static boolean process(final JsonObject jsonObject) {
        String name = jsonObject.get("templateName").getAsString();
        ServerType type = ServerType.valueOf(jsonObject.get("type").toString());

        String launchFile = jsonObject.get("launch").getAsString();
        String launchOptions = jsonObject.get("launchOptions").getAsString();

        boolean overwrite = jsonObject.get("overwrite").getAsBoolean();

        // Files
        JsonObject tree = jsonObject.get("tree").getAsJsonObject();

        ServerConfig config = new ServerConfig(name, type, launchFile, launchOptions, overwrite, tree);

        return processTree(tree, config, "",true);
    }

    /**
     * Process trees of files
     * @param jsonObject object of tree
     * @param conf Server configuration
     * @param parents Parents of tree
     * @param recursive whether to not to go through all of them
     * @return
     */
    public static boolean processTree(final JsonObject jsonObject, ServerConfig conf, String parents, boolean recursive) {
        // TODO: I'll come back

        for (Map.Entry<String, JsonElement> stringJsonElementEntry : jsonObject.entrySet()) {
            if(!stringJsonElementEntry.getValue().isJsonObject()) continue;

            if(stringJsonElementEntry.getKey().equals(conf.getLaunchFile())) conf.setLaunchFilePresent(true);

            processObject(stringJsonElementEntry.getKey(), stringJsonElementEntry.getValue().getAsJsonObject(), conf, parents, recursive);
        }

        return false;
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
                Logger.getLogger().error("UNABLE TO DELETE " + f.getAbsolutePath());
            } else {
                Logger.getLogger().info("OVERWRITE: " + f.getAbsolutePath() + "(Deleted)");
            }
        }

        if(obj.get("type").getAsString().equalsIgnoreCase("folder")) {
            if(!f.mkdir()) {
                Logger.getLogger().error("Unable to mkdir " + f.getAbsolutePath());
            } else {
                Logger.getLogger().info("Made directory " + f.getAbsolutePath());
            }

            JsonObject retrieval = obj.get("retrieval").getAsJsonObject();
            if (!retrieval.get("retrieve").getAsBoolean()) {
                for (Map.Entry<String, JsonElement> contents : obj.get("contents").getAsJsonObject().entrySet()) {
                    if(contents.getKey().equalsIgnoreCase("tree")) {
                        processTree(contents.getValue().getAsJsonObject(), conf, parents + File.separator + fileName, recursive);
                    }
                }
                return true;
            } else {
                RetrieveType type = RetrieveType.valueOf(obj.get("method").getAsString());

                Credentials credentials = new Credentials();
                Credentials.CredentialType ct = Credentials.CredentialType.valueOf(obj.get("requestType").getAsString());

                for (Map.Entry<String, JsonElement> authDetails : obj.get("authDetails").getAsJsonObject().entrySet()) {
                    credentials.addToRequiredCredentials(ct, authDetails.getKey(), authDetails.getValue().getAsString());
                }

                switch(type) {
                    case URL:
                        try {
                            URLDownloader ud = new URLDownloader(obj.get("url").getAsString(), fileName, conf.isOverwrite(), credentials);
                            ud.download();
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
                            }
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

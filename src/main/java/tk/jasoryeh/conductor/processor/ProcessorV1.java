package tk.jasoryeh.conductor.processor;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import org.zeroturnaround.zip.ZipUtil;
import tk.jasoryeh.conductor.Conductor;
import tk.jasoryeh.conductor.Log;
import tk.jasoryeh.conductor.config.JenkinsConfiguration;
import tk.jasoryeh.conductor.config.LauncherConfiguration;
import tk.jasoryeh.conductor.config.ServerConfig;
import tk.jasoryeh.conductor.downloaders.JenkinsDownloader;
import tk.jasoryeh.conductor.downloaders.URLDownloader;
import tk.jasoryeh.conductor.downloaders.authentication.Credentials;
import tk.jasoryeh.conductor.util.FileUtils;
import tk.jasoryeh.conductor.util.Utility;

public class ProcessorV1 extends ServerConfigurationProcessor {

  private final static int VERSION = 1;

  public ProcessorV1() {
    super(VERSION);
  }

  @Override
  public ServerConfig process(JsonObject jsonObject) {
    // Server configuration (name, type, overwrite, etc.)
    ServerConfig config = new ServerConfig(jsonObject);

    // Server file trees to process
    ArrayList<JsonObject> trees = new ArrayList<>();
    // Variables to replace in files
    HashMap<String, String> vars = new HashMap<>();

    {
      // main configuration

      // Add main server configuration
      trees.add(jsonObject);
      // Add variables
      if (jsonObject.has("variables")) {
        JsonObject variables = jsonObject.get("variables").getAsJsonObject();
        for (Map.Entry<String, JsonElement> var : variables.entrySet()) {
          String key = var.getKey();
          // variables may include references to other variables, we resolve them here.
          String val = VariableResolver.resolveVariables(vars, var.getValue().getAsString(), true);
          vars.put(key, val);
        }
      }

    }

        /* Check for other server configurations to also process, these includes are not to overwrite current server
        configuration and files unless otherwise specified in each included file's "overwrite" property

        Includes are just like another server_cnf but doesnt require as many properties

        (Required) Includes must have: "iam", "tree"
        (Optional) Additional properties that can be set: "variables", "overwrite_variables"
         */
    if (jsonObject.has("includes")) {
      // has "includes", trying to get all of them now
      for (JsonElement include : jsonObject.get("includes").getAsJsonArray()) {
        // get each include's location (usually a url, or local file)
        // also replace variables here too.
        String where = VariableResolver.resolveVariables(vars, include.getAsString(), true);

        Log.get("v1processor").info(String.format("Reading [%s]", where));
        try {
          //               if remote,            then: download remote to this var;  otherwise: read the file.
          String includeContent = Utility.isRemote(where) ? Utility.remoteFileToString(where)
              : Utility.readToString(Utility.determineFileFromPath(where));

          // Parse include string to json element -> object, and add it to what we will process
          JsonObject importedInclude = new JsonParser().parse(includeContent).getAsJsonObject();
          trees.add(importedInclude);

          // import variables from these includes
          if (importedInclude.has("variables")) {
            // check if we want included variables to overwrite our main server configuration's
            boolean overwriteVariables = importedInclude.has("overwrite_variables")
                && importedInclude.get("overwrite_variables").getAsBoolean();
            JsonObject variables = importedInclude.get("variables").getAsJsonObject();
            for (Map.Entry<String, JsonElement> var : variables.entrySet()) {
              String key = var.getKey();

              // we resolve variables to previously existing ones.
              String val = VariableResolver
                  .resolveVariables(vars, var.getValue().getAsString(), true);

              // skip existing/or not
              if (!vars.containsKey(key) || overwriteVariables) {
                vars.put(key, val);
              } else {
                Log.get("v1processor").info("Skipping include variable (duplicate): " + key + "=>" + val);
              }
            }
          }

          Log.get("v1processor").info(String.format("Read and loaded [%s]", where));
        } catch (Exception e) {
          Log.get("v1processor").error(String.format("Include [%s] cannot be loaded!", where));
          e.printStackTrace();
          Conductor.shutdown(true);
          return null;
        }
      }
    }

    Log.get("v1processor").info("Final vars: " + vars.toString());

    // Files, by numbers because we want to go in order (our main configuration should be first)
    for (int i = 0; i < trees.size(); i++) {
      JsonObject jsonConfig = trees.get(i);
      Log.get("v1processor").info("Working on job " + i + "... now in progress...");

      boolean isInclude = jsonConfig.has("iam") || i != 0;
      boolean processedSuccessfully = processTree(jsonConfig.get("tree").getAsJsonObject(),
          config, "", true, vars, isInclude);

      Log.get("v1processor").info("Job finished, returned success value: " + processedSuccessfully);

      if (!processedSuccessfully) {
        Log.get("v1processor").error("Something went wrong here, we are playing it safe and shutting down this updater so you can check what's going on.");
        Log.get("v1processor").error("Failed to process a job, details follow:");
        Log.get("v1processor").error("Job at index: " + i);
        Log.get("v1processor").error("Is include: " + isInclude);
        if (isInclude) {
          Log.get("v1processor").error("Include: " + (jsonConfig.has("iam") ? jsonConfig.get("iam").toString()
              : "<no name>"));
          Log.get("v1processor").error("Include output: " + jsonConfig.toString());
        } else {
          Log.get("v1processor").error("Failed at a main configuration file. This means you should check the main configuration for errors.");
        }

        Conductor.shutdown(true);
      }
    }

    return config;
  }

  @Override
  public boolean processTree(JsonObject jsonObject, ServerConfig conf, String parents,
      boolean recursive, Map<String, String> vars, boolean isInclude) {
    // loop elements in tree/folder
    for (Map.Entry<String, JsonElement> treeEntry : jsonObject.entrySet()) {
      String entryKey = treeEntry.getKey();
      JsonElement entryValue = treeEntry.getValue();

      if (!entryValue.isJsonObject()) {
        Log.get("v1processor").warn("Skipping non JSON entry: " + entryKey + " in " + parents);
      } else {
        // process this element
        Log.get("v1processor").info("On JSON entry: " + entryKey + " in " + parents);
        boolean success = processObject(treeEntry.getKey(), entryValue.getAsJsonObject(), conf,
            parents, recursive, vars, isInclude);
        if (!success) {
          // not well.
          Log.get("v1processor").error("Failed at " + entryKey + " in " + parents);
          return false;
        }
      }
    }

    // if everything goes well, we should get back to here
    return true;
  }

  @Override
  public boolean processObject(String fileName, JsonObject obj, ServerConfig conf, String parents,
      boolean recursive, Map<String, String> vars, boolean isInclude) {
    String objectCWD = Utility.cwdAndSep() + // current working directory
        (parents.isEmpty() ? ""
            : (File.separator + parents + File.separator)); // directory we are in
    File objectFile = new File(objectCWD + fileName);

    Log.get("v1processor").info(""); // newline, secretly identify separate objects.
    Log.get("v1processor").info("[File] Now working on... " + objectFile.getAbsolutePath());

    try {
      boolean processedAndReady = prepareFile(
          objectFile,
          conf.isOverwrite(),
          isInclude ? (obj.has("overwrite") && obj.get("overwrite").getAsBoolean())
              : (!obj.has("overwrite") || obj.get("overwrite").getAsBoolean()),
          isInclude);

      if (!processedAndReady) {
        Log.get("v1processor").info("[File] Overwrite rule say we should skip " + objectFile.getAbsolutePath());
        return true;
      }
    } catch (IOException e) {
      Log.get("v1processor").error("[File] Something happened while preparing " + objectFile.getAbsolutePath()
          + " for overwrite, this is just to be safe.");
      return false;
    }

    String fileType = obj.get("type").getAsString();
    Log.get("v1processor").info("[File|W] Processing " + objectFile.getAbsolutePath() + " as a \"" + fileType + "\"");
    if (fileType.equalsIgnoreCase("folder")) {
      Log.get("v1processor").info("Working on folder contents for " + objectFile.getAbsolutePath());
      if (!objectFile.exists()) {
        // doesn't exist, make the folder
        if (!objectFile.mkdirs()) {
          Log.get("v1processor").warn("[File|W] Unable to create folder(s) " + objectFile.getAbsolutePath() +
              " folder doesn't exist, so anything else in this folder is impossible.");
          return false;
        } else {
          Log.get("v1processor").info("[File|W] Created folder " + objectFile.getAbsolutePath());
        }
      } else {
        Log.get("v1processor").warn("[File|W] Folder has existed, this shouldn't be a problem.");
      }

      JsonObject retrieval = obj.get("retrieval").getAsJsonObject();
      if (!retrieval.get("retrieve").getAsBoolean()) {
        // Don't retrieve from a remote source, tree is defined in the configuration
        if (obj.has("contents") && (obj.get("contents") instanceof JsonObject)) {
          Log.get("v1processor").info("[File] Processing content configuration for " + objectFile.getAbsolutePath() + " "
              + fileName + "]");
          for (Map.Entry<String, JsonElement> contents : obj.get("contents").getAsJsonObject()
              .entrySet()) {
            // Tree only, folders can't have "text content"
            if (contents.getKey().equalsIgnoreCase("tree")) {
              Log.get("v1processor").info("[File] Processing configuration... [" + fileName + "]");
              boolean success = processTree(contents.getValue().getAsJsonObject(), conf,
                  parents.equals("") ? fileName : parents + File.separator + fileName, recursive,
                  vars, isInclude);
              if (!success) {
                Log.get("v1processor").error("[File] Error processing [" + fileName + "]");
                return false;
              }
              Log.get("v1processor").info("[File] Done processing [" + fileName + "]");
            }
          }
        }
        return true;
      } else {
        RetrieveType type = RetrieveType
            .valueOf(retrieval.get("method").getAsString().toUpperCase());

        Credentials credentials = new Credentials();
        Credentials.CredentialType ct = Credentials.CredentialType
            .valueOf(obj.get("requestType") == null ? "DEFAULT"
                : obj.get("requestType").getAsString().toUpperCase());
        if (obj.get("authDetails") != null) {
          for (Map.Entry<String, JsonElement> authDetails : obj.get("authDetails").getAsJsonObject()
              .entrySet()) {
            // (should) do nothing if auth details aren't present
            credentials.addToRequiredCredentials(ct, authDetails.getKey(),
                authDetails.getValue().getAsString());
          }
        }

        switch (type) {
          case URL:
            try {
              URLDownloader ud = new URLDownloader(
                  VariableResolver.resolveVariables(vars, retrieval.get("url").getAsString(), true),
                  fileName,
                  conf.isOverwrite(), credentials);
              ud.download();
              // Unzip
              if (retrieval.has("unzipRequired") && retrieval.get("unzipRequired").getAsBoolean()) {
                Log.get("v1processor").info("[File|W/UZ] Unzipping to" + objectFile.getAbsolutePath());
                objectFile.mkdirs();
                ZipUtil.unpack(ud.getDownloadedFile(), objectFile);
                //System.gc();
              } else {
                Log.get("v1processor").info("[File|W] Copying files to " + objectFile.toPath());
                Files.copy(ud.getDownloadedFile().toPath(), objectFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING);
              }

              // ... move on
            } catch (IOException ioe) {
              ioe.printStackTrace();
              return false;
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
        return true;
      }
    } else if (fileType.equalsIgnoreCase("file")) {
      Log.get("v1processor").info("Working on file " + objectFile.getAbsolutePath());
      if (objectFile.exists()) {
        Log.get("v1processor").error("[File] Duplicate file, this is probably due to a missing overwrite property on this file.");
        if (isInclude) {
          Log.get("v1processor").error("[File] This is probably because this is a include, if you want to overwrite " +
              "existing files in an include you must set overwrite to true.");
        }
        return false;
      }

      JsonObject retrieval = obj.get("retrieval").getAsJsonObject();
      if (!retrieval.get("retrieve").getAsBoolean()) {
        // If defined in configuration, we can only write text based file types
        try {
          if (objectFile.createNewFile()
              && (obj.has("contents")
              && obj.get("contents").getAsJsonObject().has("content"))) {
            // Tree only, folders can't have "text content"
            FileOutputStream fos = new FileOutputStream(objectFile);
            boolean applyVars =
                !obj.has("applyVariables") || obj.get("applyVariables").getAsBoolean();
            String rawContent = obj.get("contents").getAsJsonObject().get("content").getAsString();

            fos.write(VariableResolver.resolveVariables(vars, rawContent, applyVars).getBytes());
            fos.close();
          }
          return true;
        } catch (IOException io) {
          io.printStackTrace();
          return false;
        }
        // verify is unreachable return true;
      } else {
        RetrieveType type = RetrieveType
            .valueOf(retrieval.get("method").getAsString().toUpperCase());

        Credentials credentials = new Credentials();
        Credentials.CredentialType ct = Credentials.CredentialType
            .valueOf((!retrieval.has("requestType") ? "DEFAULT"
                : retrieval.get("requestType").getAsString()));
        if (retrieval.get("authDetails") != null) {
          for (Map.Entry<String, JsonElement> authDetails : retrieval.get("authDetails")
              .getAsJsonObject().entrySet()) {
            // (should) do nothing if auth details aren't present
            credentials.addToRequiredCredentials(ct, authDetails.getKey(),
                authDetails.getValue().getAsString());
          }
        }

        switch (type) {
          case URL:
            try {
              URLDownloader ud = new URLDownloader(
                  VariableResolver.resolveVariables(vars, retrieval.get("url").getAsString(), true),
                  fileName,
                  conf.isOverwrite(),
                  credentials);
              ud.download();

              Files.copy(
                  ud.getDownloadedFile().toPath(),
                  objectFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
              // ... move on
              return true;
            } catch (IOException ioe) {
              ioe.printStackTrace();
              return false;
            }
          case JENKINS:
            try {
              if (!retrieval.has("jenkinsAuth")) {
                Log.get("v1processor").error("No jenkins job details specified for " + fileName);
                return false;
              }
              JsonObject jenkinsAuth = retrieval.get("jenkinsAuth").getAsJsonObject();

              String job, artifact;
              int jobNum = -1;
              if (!jenkinsAuth.has("job") || !jenkinsAuth.has("artifact")) {
                Log.get("v1processor").error("No job or artifact specified for " + fileName);
                return false;
              } else {
                job = jenkinsAuth.get("job").getAsString();
                artifact = jenkinsAuth.get("artifact").getAsString();
                jobNum = jenkinsAuth.has("number") ? jenkinsAuth.get("number").getAsInt() : jobNum;
              }

              JenkinsConfiguration jenkinsConfig;
              if (jenkinsAuth.has("host") && jenkinsAuth.has("username")
                  && jenkinsAuth.has("passwordOrToken")) {
                jenkinsConfig = new JenkinsConfiguration(
                    VariableResolver
                        .resolveVariables(vars, jenkinsAuth.get("host").getAsString(), true),
                    jenkinsAuth.get("username").getAsString(),
                    jenkinsAuth.get("passwordOrToken").getAsString());
              } else {
                jenkinsConfig = LauncherConfiguration.get().getJenkins();
              }

              JenkinsDownloader jenkinsDownloader = new JenkinsDownloader(
                  jenkinsConfig,
                  VariableResolver.resolveVariables(vars, job, true),
                  VariableResolver.resolveVariables(vars, artifact, true),
                  jobNum,
                  fileName,
                  true);
              boolean jenkinsOK = jenkinsDownloader.download();
              if (!jenkinsOK) {
                Log.get("v1processor").error("Unable to download artifact for " + objectFile.getAbsolutePath());
                return false;
              }

              Files.copy(
                  jenkinsDownloader.getDownloadedFile().toPath(),
                  objectFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

              //... move on
              return true;
            } catch (IOException ioe) {
              ioe.printStackTrace();
              return false;
            }
          case SPECIFIED:
            // Specified not supposed to land here
            throw new UnsupportedOperationException();
          default:
            // Other types
            throw new UnsupportedOperationException();
        }
        // verify is unreachable: return false;
      }
    }

    Log.get("v1processor").error("\"" + fileType + "\" not found! Please fix this before continuing!");
    return false;
  }

  /**
   * @param file                      Object being processed
   * @param mainOverwrite             What main configuration told us to do about overwrites
   * @param configIndividualOverwrite What the individual file's overwrite rule is
   * @param isInclude                 Is this file from an include?
   * @return true - should continue (ready to write to); false - don't process this file any further
   * (shouldn't write to)
   * @throws IOException thrown if we should stop everything, equivalent to a false, but worse
   */
  private boolean prepareFile(File file, boolean mainOverwrite, boolean configIndividualOverwrite,
      boolean isInclude) throws IOException {
    if (!file.exists()) {
      // do nothing if it already is empty
      Log.get("v1processor").info("The file " + file.getAbsolutePath() + " doesn't exist, and we didn't delete anything " +
          "here so it's ready to write to!" + " (doesn't exist, <doesn't matter>, " + mainOverwrite
          + ", "
          + configIndividualOverwrite + ", include)");
      return true;
    }

    // if it's a include, we only look at our individual include file's overwrite rules if it wants
    // to change stuff that main might have, then it will force itself, otherwise if it doesn't say
    // to overwrite then if it exists we should skip by returning false
    // success should return true

    // exception thrown here if we fail to overwrite because we should overthrow everything!
    if (isInclude) {
      // includes, we only look at individual file overwrite property
      if (configIndividualOverwrite) {
        if (file.isDirectory()) {
          if (FileUtils.delete(file)) {
            Log.get("v1processor").info("[File|D] Deleted folder: " + file.getAbsolutePath()
                + " (previously existed, folder, " + mainOverwrite + ", "
                + configIndividualOverwrite + ", include)");
            return true;
          } else {
            Log.get("v1processor").error("[File|D] Unable to delete folder! " + file.getAbsolutePath()
                + " (previously existed, folder, " + mainOverwrite + ", "
                + configIndividualOverwrite + ", include)");
            throw new IOException("Cannot prepare file!");
          }
        } else {
          if (FileUtils.delete(file)) {
            Log.get("v1processor").info("[File|D] Deleted file: " + file.getAbsolutePath()
                + " (previously existed, file, " + mainOverwrite + ", "
                + configIndividualOverwrite + ", include)");
            return true;
          } else {
            Log.get("v1processor").error("[File|D] Unable to delete file! " + file.getAbsolutePath()
                + " (previously existed, file, " + mainOverwrite + ", "
                + configIndividualOverwrite + ", include)");
            throw new IOException("Cannot prepare file!");
          }
        }
      }
    } else {
      // main configuration, also look at main config
      // if main says no nothing goes
      // if main says yes individual says no, just that doesn't go
      // if main says no individual says no, still can't go

      if (!configIndividualOverwrite) {
        // respect individual file properties in case below doesn't follow through
        Log.get("v1processor").info("The file " + file.getAbsolutePath() + " doesn't want to be overwritten so we won't " +
            "delete it!" + " (previously existed, " + (file.isDirectory() ? "directory" : "file")
            + ", "
            + mainOverwrite + ", " + configIndividualOverwrite + ", include)");
        return false;
      }

      if (mainOverwrite) {
        if (file.isDirectory()) {
          if (FileUtils.delete(file)) {
            Log.get("v1processor").info("[File|D] Deleted folder: " + file.getAbsolutePath()
                + " (previously existed, folder, " + mainOverwrite + ", "
                + configIndividualOverwrite + ", main)");
            return true;
          } else {
            Log.get("v1processor").error("[File|D] Unable to delete folder! " + file.getAbsolutePath()
                + " (previously existed, folder, " + mainOverwrite + ", "
                + configIndividualOverwrite + ", main)");
            throw new IOException("Cannot prepare file!");
          }
        } else {
          if (FileUtils.delete(file)) {
            Log.get("v1processor").info("[File|D] Deleted file: " + file.getAbsolutePath() +
                " (previously existed, file, " + mainOverwrite + ", "
                + configIndividualOverwrite + ", main)");
            return true;
          } else {
            Log.get("v1processor").error("[File|D] Unable to delete file! " + file.getAbsolutePath()
                + " (previously existed, file, " + mainOverwrite + ", "
                + configIndividualOverwrite + ", main)");
            throw new IOException("Cannot prepare file!");
          }
        }
      }
    }

    if (file.isDirectory()) {
      Log.get("v1processor").warn("[File|D] Configuration told us to leave existing files alone, so we weren't sure what to do with "
          + file.getAbsolutePath() + " (previously existed, file, " + (isInclude ? "include"
          : "main") + " )"
          + " however this is a folder, so we will assume things will be done to the files in the folder.");
      return true;
    } else {
      Log.get("v1processor").warn("[File|D] Configuration told us to leave existing files alone, so we weren't sure what to do with "
          + file.getAbsolutePath() + " (previously existed, folder, " + mainOverwrite + ", "
          + configIndividualOverwrite + ", " + (isInclude ? "include" : "main") + " )");
      Log.get("v1processor").warn("[File|D] We will be skipping this file, " + file.getAbsolutePath());
      // we just skip this file, don't throw exception since it is safe to skip and continue
      return false;
    }
  }
}

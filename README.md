# conductor
A way to update, download, remove servers with one jar file. The configuration defines the layout of a program's filesystem by text as well as where to locate resources for each file in the filesystem.

Requires Jave 8 and above (8+)

## Why?
1. Simplify updates across networks/in container builds
2. Well-defined known-working filesystem layout that can be reproduced and easily shared may be useful in some use cases (such as in ephemeral applications)
3. For fun, think of this like a "game launcher" but for servers

## Configuration
### serverlauncher.properties
```properties
# Conductor updater options
name=Untitled
# File to be read when conductor starts. Links, and file locations work too
config=server_cnf.json
# (OPTIONAL) Configuration source, where to find this configuration [filesystem, url]
#   If not set, defaults to filesystem
config.source=filesystem
# Self-update - will try to download the latest version at every boot
#   falls back to the currently running version if unable.
# Requires update.source and update.location to be set.
update=true
# [jenkins, url]
update.source=jenkins
# jenkins: job;build# OR latest OR -1;artifact
# url: scheme://domain/path/to/conductor.jar
update.location=conductor;-1;conductor-1.0-SNAPSHOT-jar-with-dependencies.jar

# Jenkins secrets
jenkins.host=http://test.com:9090
jenkins.user=admin
jenkins.auth=passwordortokenexample
```
### <server_cnf_location>.json
```json
{
  "_conductor": {
    "version": "1",
    "name": "Example Template",
    "description": "(optional) description goes here",
    "secrets": {
      "jenkins_1": {
        "type": "jenkins",
        "jenkins_host": "https://build.somewhere.org",
        "jenkins_user": "username",
        "jenkins_auth": "password/auth_token"
      },
      "http_auth_1": {
        "type": "http",
        "http_headers": {
          "Authorization": "Bearer xxxyyy",
          "X-Some-Secret-Header": "secret123"
        }
      }
    },
    "variables": {
      "SOME_VARIABLE1": "some value",
      "SOME_VARIABLE2": "some other value..."
    }
  },
  "filesystem": {
    "local-file.ext": {
      "type": "file",
      "content": "line1\nline2\nline3\nor{NEWLINE}also{NEWLINE}works too!!!\n"
    },
    "local-file-alternative.ext": {
      "type": "file",
      "content": [
        "line 1",
        "line 2",
        "line that ends the file"
      ]
    },
    "download-file-direct.ext": {
      "type": "file",
      "content": {
        "plugins": "http",
        "http": "https://static.somewhere.org/file/file-to-download-and-some-variable-{{SOME_VARIABLE1}}.txt",
        "http_secret": "http_auth_1"
      }
    },
    "download-file-zipped.ext": {
      "type": "file",
      "content": {
        "plugins": ["http"],
        "http": "https://static.somewhere.org/file/file-to-download.txt",
        "http_secret": "http_auth_1",
        "unzip_location": "/sub/directory/to/file.txt"
      }
    },
    "jenkins-file.ext": {
      "type": "file",
      "content": {
        "plugins": "jenkins",
        "jenkins_job": "JobName",
        "jenkins_artifact": "artifact-file-versionSomething.jar",
        "jenkins_secret": "jenkins_1"
      }
    },
    "folder-defined": {
      "type": "folder",
      "content": {
        "...1": {},
        "...2": {}
      }
    },
    "zip-download": {
      "type": "file",
      "content": {
        "plugins": "http",
        "http": "https://static.somewhere.org/file/archive-to-download.zip",
        "http_secret": "http_auth_1"
      }
    }
  }
}
```
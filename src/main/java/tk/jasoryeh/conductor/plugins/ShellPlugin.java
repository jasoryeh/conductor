package tk.jasoryeh.conductor.plugins;

import lombok.SneakyThrows;
import tk.jasoryeh.conductor.V2FileSystemObject;
import tk.jasoryeh.conductor.util.FileUtils;

import java.io.File;

public class ShellPlugin extends Plugin {
    private final String prepCmd;
    private final String execCmd;
    private final int temporaries;

    public ShellPlugin(V2FileSystemObject fsObject, String prepCmd, String execCmd, int temporaries) {
        super(fsObject);
        this.prepCmd = prepCmd;
        this.execCmd = execCmd;
        this.temporaries = temporaries;

        if (this.prepCmd == null && this.execCmd == null) {
            logger.warn("'PrepCmd' or 'ExecCmd' is not specified- potential configuration error?");
        }
    }

    @SneakyThrows
    private int exec(String cmd) {
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.directory(this.getFsObject().getTemplate().getTemporaryDirectory());
        // todo: cross-platform support
        processBuilder.command("sh", "-c", cmd);
        for (int i = 0; i < this.temporaries; i++) {
            processBuilder.environment().put("TEMPORARY_" + i, this.getTemporary(String.valueOf(i)).getAbsolutePath());
        }
        processBuilder.environment().put("TEMPORARY_FILE", this.getFsObject().getTemporary().getAbsolutePath());
        processBuilder.environment().put("FINAL_FILE", this.getFsObject().getFile().getAbsolutePath());

        Process process = processBuilder.redirectError(ProcessBuilder.Redirect.INHERIT)
                .redirectOutput(ProcessBuilder.Redirect.INHERIT)
                .redirectInput(ProcessBuilder.Redirect.INHERIT)
                .start();
        return process.waitFor();
    }

    @Override
    public void prepare() {
        for (int i = 0; i < temporaries; i++) {
            File temporary = this.getTemporary(String.valueOf(i));
            if (temporary.exists()) {
                logger.debug("Deleting temporary file from prior runs: " + temporary.getAbsolutePath());
                FileUtils.delete(temporary);
            }
        }
        if (this.prepCmd != null) {
            int status = this.exec(this.prepCmd);
            if (status != 0) {
                throw new RuntimeException("Shell execution at prepare-time was non-zero: " + status);
            }
        }
    }

    @Override
    public void execute() {
        if (this.execCmd != null) {
            int status = this.exec(this.execCmd);
            if (status != 0) {
                throw new RuntimeException("Shell execution at prepare-time was non-zero: " + status);
            }
        }
    }
}

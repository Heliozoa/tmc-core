/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package fi.helsinki.cs.tmc.core;

/**
 *
 * @author sasami-san
 */
public class ExecutionResult {
    boolean success;
    int exitValue;
    String stdout;
    String stderr;

    public ExecutionResult(int exitValue, String stdout, String stderr) {
        this.success = exitValue == 0;
        this.exitValue = exitValue;
        this.stdout = stdout;
        this.stderr = stderr;
    }

    public String getStdout() {
        return this.stdout;
    }

    public boolean getSuccess() {
        return this.success;
    }
}

package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.TmcCore;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.configuration.TmcSettings;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.exceptions.TmcCoreException;
import fi.helsinki.cs.tmc.core.exceptions.TmcInterruptionException;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;

import com.google.common.base.Preconditions;

import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * A task that can be completed by the TMC-Core.
 *
 * <p>
 * Third parties should use these via {@link fi.helsinki.cs.tmc.core.TmcCore}.
 */
public abstract class Command<E> implements Callable<E> {

    private static final Logger logger = LoggerFactory.getLogger(Command.class);

    protected TmcSettings settings;
    protected ProgressObserver observer;

    protected TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory;

    /**
     * Constructs a Command object.
     */
    public Command(ProgressObserver observer) {
        this(TmcSettingsHolder.get(), observer);
    }

    /**
     * Constructs a Command object with an associated {@link TmcSettings} and
     * {@link ProgressObserver}.
     */
    public Command(TmcSettings settings, ProgressObserver observer) {
        this(settings, observer, new TmcServerCommunicationTaskFactory());
    }

    public Command(ProgressObserver observer, TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory) {
        this(TmcSettingsHolder.get(), observer, tmcServerCommunicationTaskFactory);
    }

    public Command(TmcSettings settings, ProgressObserver observer,
            TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory) {
        this.settings = settings;
        Preconditions.checkNotNull(observer);
        this.observer = observer;
        Preconditions.checkNotNull(tmcServerCommunicationTaskFactory);
        this.tmcServerCommunicationTaskFactory = tmcServerCommunicationTaskFactory;
    }

    /**
     * Informs an associated {@link ProgressObserver} about the current status of
     * the command.
     *
     * <p>
     * If no progress observer is assigned, nothing happens.
     */
    protected void informObserver(double percentageDone, String message) {
        informObserver(0, percentageDone, message);
    }

    /**
     * Informs an associated {@link ProgressObserver} about the current status of
     * the command.
     *
     * <p>
     * If no progress observer is assigned, nothing happens.
     */
    protected void informObserver(long id, double percentageDone, String message) {
        observer.progress(id, percentageDone, message);
    }

    /**
     * Informs an associated {@link ProgressObserver} about the current status of
     * the command.
     *
     * <p>
     * The provided values are converted into a percentage before passing to the
     * observer.
     *
     * <p>
     * If no progress observer is assigned, nothing happens.
     */
    protected void informObserver(int currentProgress, int maxProgress, String message) {
        logger.info("Received notification of " + message + "[" + currentProgress + "/" + maxProgress + "]");
        double percent = ((double) currentProgress) * 100 / maxProgress;
        informObserver(percent, message);
    }

    protected void checkInterrupt() throws TmcInterruptionException {
        if (Thread.currentThread().isInterrupted()) {
            logger.info("Noticed interruption, throwing TmcInterruptionException");
            throw new TmcInterruptionException();
        }
    }

    // executes tmc-langs-cli core --email {email} [args] and writes the password
    // into stdin
    protected ExecutionResult execute(String[] args) throws TmcCoreException {
        if (TmcCore.getCliPath() == null) {
            throw new IllegalStateException("tmc core command used before cliPath was set");
        }

        List<String> cmd = new ArrayList<String>();
        cmd.add(TmcCore.getCliPath());
        TmcSettings settings = TmcSettingsHolder.get();
        String email = settings.getEmail().get();
        String password = settings.getPassword().get();
        String[] coreArgs = { "core", "--email", email };
        cmd.addAll(Arrays.asList(coreArgs));
        cmd.addAll(Arrays.asList(args));

        try {
            Runtime rt = Runtime.getRuntime();
            logger.info("executing {}", cmd);

            Process ps = rt.exec(cmd.toArray(new String[0]));

            // write password to stdin
            OutputStream outputStream = ps.getOutputStream();
            byte[] passwordBytes = (password + "\n").getBytes(StandardCharsets.UTF_8.name());
            outputStream.write(passwordBytes);
            outputStream.close();
            ps.waitFor();

            int exitValue = ps.exitValue();
            InputStream inputStream = ps.getInputStream();
            String stdout = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
            InputStream errorStream = ps.getErrorStream();
            String stderr = IOUtils.toString(errorStream, StandardCharsets.UTF_8.name());
            logger.trace("exit code: {}", exitValue);
            logger.trace("stdout: {}", stdout);
            logger.trace("stderr: {}", stderr);
            ExecutionResult result = new ExecutionResult(exitValue, stdout, stderr);
            if (!result.getSuccess()) {
                throw new TmcCoreException("Command '" + args[0] + "' exited with nonzero status");
            }
            return result;
        } catch (Exception e) {
            throw new TmcCoreException("Failed to execute core command", e);
        }
    }
}

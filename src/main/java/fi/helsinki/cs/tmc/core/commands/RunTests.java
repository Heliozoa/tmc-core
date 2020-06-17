package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.exceptions.TmcCoreException;
import fi.helsinki.cs.tmc.core.holders.TmcLangsHolder;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;
import fi.helsinki.cs.tmc.langs.domain.NoLanguagePluginFoundException;
import fi.helsinki.cs.tmc.langs.domain.RunResult;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

import com.google.gson.Gson;

/**
 * A {@link Command} for running test for an exercise.
 */
public class RunTests extends Command<RunResult> {

    private static final Logger logger = LoggerFactory.getLogger(RunTests.class);

    private Exercise exercise;

    public RunTests(ProgressObserver observer, Exercise exercise) {
        super(observer);
        this.exercise = exercise;
    }

    @Override
    public RunResult call() throws TmcCoreException {
        observer.progress(1, 0.0, "Running tests");

        Path path = exercise.getExerciseDirectory(TmcSettingsHolder.get().getTmcProjectDirectory());
        ExecutionResult result = this.execute(new String[] { "run-tests", "--exercisePath", path.toString() });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        RunResult runResult = gson.fromJson(result.getStdout(), RunResult.class);
        observer.progress(1, 1.0, "Ran tests");

        return runResult;
    }
}

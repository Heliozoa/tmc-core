package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.exceptions.TmcCoreException;
import fi.helsinki.cs.tmc.core.holders.TmcLangsHolder;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;
import fi.helsinki.cs.tmc.langs.abstraction.ValidationResult;
import fi.helsinki.cs.tmc.langs.domain.NoLanguagePluginFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.Locale;

import com.google.gson.Gson;

/**
 * A {@link Command} for running code style validations on an exercise.
 */
public class RunCheckStyle extends Command<ValidationResult> {

    private static final Logger logger = LoggerFactory.getLogger(RunCheckStyle.class);

    private Exercise exercise;

    public RunCheckStyle(ProgressObserver observer, Exercise exercise) {
        super(observer);
        this.exercise = exercise;
    }

    @Override
    public ValidationResult call() throws TmcCoreException {
        observer.progress(1, 0.0, "Running code style validation");

        Path tmcRoot = TmcSettingsHolder.get().getTmcProjectDirectory();
        Path projectPath = exercise.getExerciseDirectory(tmcRoot);
        Locale locale = TmcSettingsHolder.get().getLocale();
        ExecutionResult result = this.execute(new String[] { "run-checkstyle", "--exercisePath", projectPath.toString(),
                "--locale", locale.toString() });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        ValidationResult validationResult = gson.fromJson(result.getStdout(), ValidationResult.class);
        observer.progress(1, 1.0, "Ran code style validation");

        return validationResult;
    }
}

package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.Progress;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.exceptions.ExerciseDownloadFailedException;
import fi.helsinki.cs.tmc.core.exceptions.ExtractingExericeFailedException;
import fi.helsinki.cs.tmc.core.exceptions.TmcCoreException;
import fi.helsinki.cs.tmc.core.exceptions.TmcInterruptionException;
import fi.helsinki.cs.tmc.core.holders.TmcLangsHolder;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;

import com.google.common.annotations.VisibleForTesting;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A {@link Command} for downloading exercises.
 */
public class DownloadOrUpdateExercises extends ExerciseDownloadingCommand<List<Exercise>> {

    private static final Logger logger = LoggerFactory.getLogger(DownloadOrUpdateExercises.class);

    private List<Exercise> exercises;

    public DownloadOrUpdateExercises(ProgressObserver observer, List<Exercise> exercises) {
        super(observer);
        this.exercises = exercises;
    }

    @VisibleForTesting
    DownloadOrUpdateExercises(ProgressObserver observer, List<Exercise> exercises,
            TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory) {
        super(observer, tmcServerCommunicationTaskFactory);
        this.exercises = exercises;
    }

    @Override
    public List<Exercise> call() throws TmcInterruptionException {
        observer.progress(1, 0.0, "Downloading exercises");
        Path target = TmcSettingsHolder.get().getTmcProjectDirectory();

        List<String> args = new ArrayList<String>();
        args.add("download-or-update-exercises");
        for (Exercise exercise : exercises) {
            args.add("--exercise");
            args.add(String.valueOf(exercise.getId()));
            args.add(target.resolve(Paths.get(exercise.getCourseName(), exercise.getName())).toString());
        }
        observer.progress(1, 0.5, "Prepared arguments");

        ExecutionResult result = this.execute(args.toArray(new String[0]));
        // TODO: check failure
        observer.progress(1, 1.0, "Downloaded exercises");

        return exercises;
    }

}

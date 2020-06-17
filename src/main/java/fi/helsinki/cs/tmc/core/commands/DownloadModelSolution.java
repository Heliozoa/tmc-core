package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.Progress;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;

import com.google.common.annotations.VisibleForTesting;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.concurrent.Callable;

public class DownloadModelSolution extends ExerciseDownloadingCommand<Exercise> {

    private static final Logger logger = LoggerFactory.getLogger(DownloadModelSolution.class);

    private Exercise exercise;

    public DownloadModelSolution(ProgressObserver observer, Exercise exercise) {
        super(observer);
        this.exercise = exercise;
    }

    @VisibleForTesting
    DownloadModelSolution(ProgressObserver observer,
            TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory, Exercise exercise) {
        super(observer, tmcServerCommunicationTaskFactory);
        this.exercise = exercise;
    }

    @Override
    public Exercise call() throws Exception {
        observer.progress(1, 0.0, "Downloading model solution");

        Path target = exercise.getExtractionTarget(TmcSettingsHolder.get().getTmcProjectDirectory());
        ExecutionResult result = this.execute(new String[] { "download-model-solution", "--solutionDownloadUrl",
                exercise.getSolutionDownloadUrl().toString(), "--target", target.toString() });
        // TODO: check result
        observer.progress(1, 1.0, "Downloaded model solution");

        return exercise;
    }
}

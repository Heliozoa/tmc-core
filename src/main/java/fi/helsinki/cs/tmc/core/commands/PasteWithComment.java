package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory.SubmissionResponse;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Command} for sending a paste to the server with an attached comment.
 */
public class PasteWithComment extends AbstractSubmissionCommand<URI> {

    private static final Logger logger = LoggerFactory.getLogger(PasteWithComment.class);

    private Exercise exercise;
    private String message;

    public PasteWithComment(ProgressObserver observer, Exercise exercise, String message) {
        super(observer);
        this.exercise = exercise;
        this.message = message;
    }

    @VisibleForTesting
    PasteWithComment(ProgressObserver observer, Exercise exercise, String message,
            TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory) {
        super(observer, tmcServerCommunicationTaskFactory);
        this.exercise = exercise;
        this.message = message;
    }

    @Override
    public URI call() throws Exception {
        observer.progress(1, 0.0, "Submitting to pastebin");

        Path tmcRoot = TmcSettingsHolder.get().getTmcProjectDirectory();
        Path projectPath = exercise.getExerciseDirectory(tmcRoot);
        ExecutionResult result = this
                .execute(new String[] { "paste-with-comment", "--exerciseId", String.valueOf(exercise.getId()),
                        "--submissionPath", projectPath.toString(), "--pasteMessage", message });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        SubmissionResponse submissionResponse = gson.fromJson(result.getStdout(), SubmissionResponse.class);
        observer.progress(1, 1.0, "Submit to pastebin");

        return submissionResponse.pasteUrl;
    }
}

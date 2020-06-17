package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory.SubmissionResponse;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;

import com.google.common.base.Strings;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/**
 * A {@link Command} for requesting a code review for code with a message.
 */
public class RequestCodeReview extends AbstractSubmissionCommand<TmcServerCommunicationTaskFactory.SubmissionResponse> {

    private static final Logger logger = LoggerFactory.getLogger(RequestCodeReview.class);
    private final Exercise exercise;
    private final String message;

    public RequestCodeReview(ProgressObserver observer, Exercise exercise, String message) {
        super(observer);
        this.exercise = exercise;
        this.message = message;
    }

    @Override
    public TmcServerCommunicationTaskFactory.SubmissionResponse call() throws Exception {
        observer.progress(1, 0.0, "Requesting code review");

        Path target = exercise.getExtractionTarget(TmcSettingsHolder.get().getTmcProjectDirectory());
        ExecutionResult result = this.execute(new String[] { "request-code-review", "--submissionUrl",
                exercise.getSolutionDownloadUrl().toString(), "--target", target.toString() });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        SubmissionResponse submissionResponse = gson.fromJson(result.getStdout(), SubmissionResponse.class);
        observer.progress(1, 1.0, "Requested code review");

        return submissionResponse;
    }
}

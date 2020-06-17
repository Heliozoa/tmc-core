package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.domain.submission.FeedbackAnswer;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.JsonParser;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Command} for sending user feedback to the server.
 */
public class SendFeedback extends Command<Boolean> {

    private static final Logger logger = LoggerFactory.getLogger(SendFeedback.class);

    private List<FeedbackAnswer> answers;
    private URI feedbackUri;

    public SendFeedback(ProgressObserver observer, List<FeedbackAnswer> answers, URI feedbackUri) {
        super(observer);
        this.answers = answers;
        this.feedbackUri = feedbackUri;
    }

    @VisibleForTesting
    SendFeedback(ProgressObserver observer, List<FeedbackAnswer> answers, URI feedbackUri,
            TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory) {
        super(observer, tmcServerCommunicationTaskFactory);
        this.answers = answers;
        this.feedbackUri = feedbackUri;
    }

    @Override
    public Boolean call() throws Exception {
        observer.progress(1, 0.0, "Sending feedback");

        List<String> args = new ArrayList<String>();
        args.add("send-feedback");
        args.add("--feedbackUrl");
        args.add(feedbackUri.toString());
        for (FeedbackAnswer answer : answers) {
            args.add("--feedback");
            args.add(String.valueOf(answer.getQuestion().getId()));
            args.add(answer.getAnswer());
        }

        ExecutionResult result = this.execute(args.toArray(new String[0]));
        // TODO: check success
        observer.progress(1, 1.0, "Sent feedback");
        return result.getSuccess();
    }

    private boolean respondedSuccessfully(String response) {
        return new JsonParser().parse(response).getAsJsonObject().get("status").getAsString().equals("ok");
    }
}

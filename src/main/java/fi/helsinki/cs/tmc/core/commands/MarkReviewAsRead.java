package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.domain.Review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MarkReviewAsRead extends Command<Void> {

    private static final Logger logger = LoggerFactory.getLogger(MarkReviewAsRead.class);

    private final Review review;

    public MarkReviewAsRead(ProgressObserver observer, Review review) {
        super(observer);
        this.review = review;
    }

    MarkReviewAsRead(ProgressObserver observer, Review review,
            TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory) {
        super(observer, tmcServerCommunicationTaskFactory);
        this.review = review;
    }

    @Override
    public Void call() throws Exception {
        observer.progress(1, 0.0, "Marking review as read");

        ExecutionResult result = this
                .execute(new String[] { "mark-review-as-read", "--reviewUpdateUrl", review.getUpdateUrl().toString() });
        // TODO: check result
        observer.progress(1, 1.0, "Marked review as read");
        return null;
    }
}

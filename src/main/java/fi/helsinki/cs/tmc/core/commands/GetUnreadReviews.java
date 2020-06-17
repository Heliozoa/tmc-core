package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.domain.Review;
import fi.helsinki.cs.tmc.core.exceptions.TmcCoreException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Type;
import java.util.concurrent.Callable;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * A {@link Command} for retrieving unread code reviews of a course from the TMC
 * server.
 */
public class GetUnreadReviews extends Command<List<Review>> {

    private static final Logger logger = LoggerFactory.getLogger(GetUnreadReviews.class);
    private Course course;

    /**
     * Constructs a new get unread code review command that fetches unread code
     * review for {@code course} using {@code handler}.
     */
    public GetUnreadReviews(ProgressObserver observer, Course course) {
        super(observer);
        this.course = course;
    }

    /**
     * Entry point for launching this command.
     */
    @Override
    public List<Review> call() throws Exception {
        observer.progress(1, 0.0, "Getting unread reviews");

        ExecutionResult result = this
                .execute(new String[] { "get-unread-reviews", "--reviewsUrl", course.getReviewsUrl().toString() });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<Review>>() {
        }.getType();
        List<Review> reviews = gson.fromJson(result.getStdout(), listType);
        observer.progress(1, 1.0, "Got unread reviews");

        return reviews;
    }
}

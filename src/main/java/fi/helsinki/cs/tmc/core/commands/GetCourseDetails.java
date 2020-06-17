package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.exceptions.NotLoggedInException;
import fi.helsinki.cs.tmc.core.exceptions.TmcCoreException;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;

/**
 * A {@link Command} for retrieving course details from TMC server.
 */
public class GetCourseDetails extends Command<Course> {

    private static final Logger logger = LoggerFactory.getLogger(GetCourseDetails.class);

    private Course course;

    public GetCourseDetails(ProgressObserver observer, Course course) {
        super(observer);
        this.course = course;
    }

    GetCourseDetails(ProgressObserver observer, Course course,
            TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory) {
        super(observer, tmcServerCommunicationTaskFactory);
        this.course = course;
    }

    @Override
    public Course call() throws TmcCoreException, URISyntaxException {
        observer.progress(1, 0.0, "Fetching course details");

        ExecutionResult result = this
                .execute(new String[] { "get-course-details", "--courseId", String.valueOf(course.getId()) });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        Course courseDetails = gson.fromJson(result.getStdout(), Course.class);
        observer.progress(1, 1.0, "Fetched course details");

        return courseDetails;
    }
}

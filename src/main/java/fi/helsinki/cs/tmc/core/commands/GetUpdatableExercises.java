package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.exceptions.TmcCoreException;
import fi.helsinki.cs.tmc.core.utilities.ServerErrorHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.gson.Gson;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A {@link Command} for retrieving exercise updates from TMC server.
 */
public class GetUpdatableExercises extends Command<GetUpdatableExercises.UpdateResult> {

    private static final Logger logger = LoggerFactory.getLogger(GetUpdatableExercises.class);

    private final Course course;

    public class UpdateResult {
        private final List<Exercise> created;
        private final List<Exercise> updated;

        private UpdateResult(List<Exercise> created, List<Exercise> updated) {
            this.created = created;
            this.updated = updated;
        }

        public List<Exercise> getNewExercises() {
            return created;
        }

        public List<Exercise> getUpdatedExercises() {
            return updated;
        }
    }

    public GetUpdatableExercises(ProgressObserver observer, Course course) {
        super(observer);
        Preconditions.checkNotNull(course);
        this.course = course;
    }

    @VisibleForTesting
    GetUpdatableExercises(ProgressObserver observer,
            TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory, Course course) {
        super(observer, tmcServerCommunicationTaskFactory);
        this.course = course;
    }

    @Override
    public UpdateResult call() throws TmcCoreException {
        observer.progress(1, 0.0, "Getting exercise updates");

        List<String> args = new ArrayList<String>();
        args.add("get-exercise-updates");
        args.add("--courseId");
        args.add(String.valueOf(course.getId()));
        for (Exercise exercise : course.getExercises()) {
            args.add("--exercise");
            args.add(String.valueOf(exercise.getId()));
            args.add(exercise.getChecksum());
        }
        ExecutionResult result = this.execute(args.toArray(new String[0]));
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        GetUpdatableExercises.UpdateResult updateResult = gson.fromJson(result.getStdout(),
                GetUpdatableExercises.UpdateResult.class);
        observer.progress(1, 1.0, "Got exercise updates");

        return updateResult;
    }
}

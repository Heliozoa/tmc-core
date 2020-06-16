package fi.helsinki.cs.tmc.core;

import fi.helsinki.cs.tmc.core.commands.AuthenticateUser;
import fi.helsinki.cs.tmc.core.commands.DownloadCompletedExercises;
import fi.helsinki.cs.tmc.core.commands.DownloadModelSolution;
import fi.helsinki.cs.tmc.core.commands.DownloadOrUpdateExercises;
import fi.helsinki.cs.tmc.core.commands.GetCourseDetails;
import fi.helsinki.cs.tmc.core.commands.GetUnreadReviews;
import fi.helsinki.cs.tmc.core.commands.GetUpdatableExercises;
import fi.helsinki.cs.tmc.core.commands.ListCourses;
import fi.helsinki.cs.tmc.core.commands.MarkReviewAsRead;
import fi.helsinki.cs.tmc.core.commands.PasteWithComment;
import fi.helsinki.cs.tmc.core.commands.RequestCodeReview;
import fi.helsinki.cs.tmc.core.commands.RunCheckStyle;
import fi.helsinki.cs.tmc.core.commands.RunTests;
import fi.helsinki.cs.tmc.core.commands.SendDiagnostics;
import fi.helsinki.cs.tmc.core.commands.SendFeedback;
import fi.helsinki.cs.tmc.core.commands.SendSnapshotEvents;
import fi.helsinki.cs.tmc.core.commands.Submit;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory.SubmissionResponse;
import fi.helsinki.cs.tmc.core.communication.oauth2.Oauth;
import fi.helsinki.cs.tmc.core.configuration.TmcSettings;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.Organization;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.domain.Review;
import fi.helsinki.cs.tmc.core.domain.submission.FeedbackAnswer;
import fi.helsinki.cs.tmc.core.domain.submission.SubmissionResult;
import fi.helsinki.cs.tmc.core.holders.TmcLangsHolder;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;
import fi.helsinki.cs.tmc.core.utilities.ExceptionTrackingCallable;
import fi.helsinki.cs.tmc.core.utilities.TmcServerAddressNormalizer;
import fi.helsinki.cs.tmc.langs.abstraction.ValidationResult;
import fi.helsinki.cs.tmc.langs.domain.RunResult;
import fi.helsinki.cs.tmc.langs.util.TaskExecutor;
import fi.helsinki.cs.tmc.snapshots.LoggableEvent;

import com.google.common.annotations.Beta;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;
import java.lang.Runtime;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import org.apache.commons.io.IOUtils;

public class TmcCore {

    private static final Logger logger = LoggerFactory.getLogger(TmcCore.class);

    private static TmcCore instance;

    private static String cliPath;

    // Singleton
    @Beta
    public static TmcCore get() {
        if (TmcCore.instance == null) {
            throw new IllegalStateException("tmc core singleton used before initialized");
        }
        return TmcCore.instance;
    }

    // Singleton
    @Beta
    public static void setInstance(TmcCore instance) {
        if (TmcCore.instance != null) {
            throw new IllegalStateException("Multiple instanciations of tmc-core");
        }
        TmcCore.instance = instance;
    }

    public static void setCliPath(String cliPath) {
        TmcCore.cliPath = cliPath;
    }

    // TODO: remember to remind to instantiate Settings and Langs holders...
    @Beta
    public TmcCore() {
    }

    public TmcCore(TmcSettings settings, TaskExecutor tmcLangs) {
        TmcSettingsHolder.set(settings);
        TmcLangsHolder.set(tmcLangs);
        TmcServerAddressNormalizer normalizer = new TmcServerAddressNormalizer();
        normalizer.normalize();
        normalizer.selectOrganizationAndCourse();
    }

    // executes tmc-langs-cli core --email {email} [args] and writes the password
    // into stdin
    private ExecutionResult execute(String[] args) {
        if (TmcCore.cliPath == null) {
            throw new IllegalStateException("tmc core command used before cliPath was set");
        }

        List<String> cmd = new ArrayList<String>();
        cmd.add(TmcCore.cliPath);
        TmcSettings settings = TmcSettingsHolder.get();
        String email = settings.getEmail().get();
        String password = settings.getPassword().get();
        String[] coreArgs = { "core", "--email", email };
        cmd.addAll(Arrays.asList(coreArgs));
        cmd.addAll(Arrays.asList(args));

        try {
            Runtime rt = Runtime.getRuntime();
            logger.info("executing {}", cmd);

            Process ps = rt.exec(cmd.toArray(new String[0]));

            // write password to stdin
            OutputStream outputStream = ps.getOutputStream();
            byte[] passwordBytes = (password + "\n").getBytes(StandardCharsets.UTF_8.name());
            outputStream.write(passwordBytes);
            outputStream.close();
            ps.waitFor();

            int exitValue = ps.exitValue();
            InputStream inputStream = ps.getInputStream();
            String stdout = IOUtils.toString(inputStream, StandardCharsets.UTF_8.name());
            InputStream errorStream = ps.getErrorStream();
            String stderr = IOUtils.toString(errorStream, StandardCharsets.UTF_8.name());
            logger.trace("exit code: {}", exitValue);
            logger.trace("stdout: {}", stdout);
            logger.trace("stderr: {}", stderr);
            return new ExecutionResult(exitValue, stdout, stderr);
        } catch (Exception e) {
            // todo
            throw new RuntimeException("Error running command " + e.getMessage());
        }
    }

    public List<Organization> getOrganizations(ProgressObserver observer) {
        observer.progress(1, 0.0, "Fetching organizations");

        ExecutionResult result = this.execute(new String[] { "get-organizations" });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<Organization>>() {
        }.getType();
        ArrayList<Organization> orgs = gson.fromJson(result.stdout, listType);
        logger.debug("found {} organizations", orgs.size());
        observer.progress(1, 1.0, "Fetched organizations");
        return orgs;
    }

    // ?
    public Callable<Void> authenticate(ProgressObserver observer, String password) {
        logger.info("Creating new AuthenticateUser command");
        return new AuthenticateUser(observer, password, Oauth.getInstance());
    }

    // low priority
    public Callable<Void> sendDiagnostics(ProgressObserver observer) {
        logger.info("Creating new SendDiagnostics command");
        return new SendDiagnostics(observer);
    }

    public List<Exercise> downloadOrUpdateExercises(ProgressObserver observer, List<Exercise> exercises) {
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

    public Course getCourseDetails(ProgressObserver observer, int courseId) {
        observer.progress(1, 0.0, "Fetching course details");

        ExecutionResult result = this
                .execute(new String[] { "get-course-details", "--courseId", String.valueOf(courseId) });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        Course courseDetails = gson.fromJson(result.stdout, Course.class);
        observer.progress(1, 1.0, "Fetched course details");

        return courseDetails;
    }

    public List<Course> listCourses(ProgressObserver observer) {
        observer.progress(1, 0.0, "Fetching course details");

        // TODO: handle no org selected
        String organizationSlug = TmcSettingsHolder.get().getOrganization().get().getSlug();
        ExecutionResult result = this.execute(new String[] { "list-courses", "--organization", organizationSlug });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<Course>>() {
        }.getType();
        List<Course> courses = gson.fromJson(result.stdout, listType);
        observer.progress(1, 1.0, "Fetched course details");

        return courses;
    }

    public URI pasteWithComment(ProgressObserver observer, Exercise exercise, String message) {
        observer.progress(1, 0.0, "Submitting to pastebin");

        Path tmcRoot = TmcSettingsHolder.get().getTmcProjectDirectory();
        Path projectPath = exercise.getExerciseDirectory(tmcRoot);
        ExecutionResult result = this
                .execute(new String[] { "paste-with-comment", "--exerciseId", String.valueOf(exercise.getId()),
                        "--submissionPath", projectPath.toString(), "--pasteMessage", message });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        SubmissionResponse submissionResponse = gson.fromJson(result.stdout, SubmissionResponse.class);
        observer.progress(1, 1.0, "Submit to pastebin");

        return submissionResponse.pasteUrl;
    }

    public ValidationResult runCheckStyle(ProgressObserver observer, Exercise exercise) {
        observer.progress(1, 0.0, "Running checkstyle");

        Path tmcRoot = TmcSettingsHolder.get().getTmcProjectDirectory();
        Path projectPath = exercise.getExerciseDirectory(tmcRoot);
        ExecutionResult result = this.execute(new String[] { "run-checkstyle", "--exerciseId",
                String.valueOf(exercise.getId()), "--submissionPath", projectPath.toString(), "--locale", "en" });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        ValidationResult validationResult = gson.fromJson(result.stdout, ValidationResult.class);
        observer.progress(1, 1.0, "Ran checkstyle");

        return validationResult;
    }

    public RunResult runTests(ProgressObserver observer, Exercise exercise) {
        observer.progress(1, 0.0, "Running tests");

        Path path = exercise.getExerciseDirectory(TmcSettingsHolder.get().getTmcProjectDirectory());
        ExecutionResult result = this.execute(new String[] { "run-tests", "--exercisePath", path.toString() });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        RunResult runResult = gson.fromJson(result.stdout, RunResult.class);
        observer.progress(1, 1.0, "Ran tests");

        return runResult;
    }

    public Boolean sendFeedback(ProgressObserver observer, List<FeedbackAnswer> answers, URI feedbackUri) {
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
        return result.success;
    }

    // low priority
    public Callable<Void> sendSnapshotEvents(final ProgressObserver observer, final Course currentCourse,
            final List<LoggableEvent> events) {
        logger.info("Creating new SendSnapshotEvents command");
        return new ExceptionTrackingCallable<>(new SendSnapshotEvents(observer, currentCourse, events));

    }

    public Callable<SubmissionResult> submit(ProgressObserver observer, Exercise exercise) {
        logger.info("Creating new Submit command");
        return new ExceptionTrackingCallable<>(new Submit(observer, exercise));
    }

    public Callable<SubmissionResult> submit(ProgressObserver observer, Exercise exercise,
            Consumer<SubmissionResponse> initialSubmissionResult) {
        logger.info("Creating new Submit command");
        return new ExceptionTrackingCallable<>(new Submit(observer, exercise, initialSubmissionResult));
    }

    public GetUpdatableExercises.UpdateResult getExerciseUpdates(ProgressObserver observer, Course course) {
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
        GetUpdatableExercises.UpdateResult updateResult = gson.fromJson(result.stdout,
                GetUpdatableExercises.UpdateResult.class);
        observer.progress(1, 1.0, "Got exercise updates");

        return updateResult;
    }

    public Callable<Void> markReviewAsRead(ProgressObserver observer, Review review) {
        logger.info("Creating new MarkReviewAsRead command");
        return new ExceptionTrackingCallable<>(new MarkReviewAsRead(observer, review));
    }

    public Callable<List<Review>> getUnreadReviews(ProgressObserver observer, Course course) {
        logger.info("Creating new GetUnreadReviews command");
        return new ExceptionTrackingCallable<>(new GetUnreadReviews(observer, course));
    }

    public SubmissionResponse requestCodeReview(ProgressObserver observer, Exercise exercise,
            String messageForReviewer) {
        observer.progress(1, 0.0, "Requesting code review");

        Path target = exercise.getExtractionTarget(TmcSettingsHolder.get().getTmcProjectDirectory());
        ExecutionResult result = this.execute(new String[] { "request-code-review", "--submissionUrl",
                exercise.getSolutionDownloadUrl().toString(), "--target", target.toString() });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        SubmissionResponse submissionResponse = gson.fromJson(result.stdout, SubmissionResponse.class);
        observer.progress(1, 1.0, "Requested code review");

        return submissionResponse;
    }

    public Exercise downloadModelSolution(ProgressObserver observer, Exercise exercise) {
        observer.progress(1, 0.0, "Downloading model solution");

        Path target = exercise.getExtractionTarget(TmcSettingsHolder.get().getTmcProjectDirectory());
        ExecutionResult result = this.execute(new String[] { "download-model-solution", "--solutionDownloadUrl",
                exercise.getSolutionDownloadUrl().toString(), "--target", target.toString() });
        // TODO: check result
        observer.progress(1, 1.0, "Downloaded model solution");

        return exercise;
    }

    /**
     * NOT IMPLEMENTED!
     *
     * <p>
     * TARGET: CORE MILESTONE 2.
     */
    public Callable<Void> downloadCompletedExercises(ProgressObserver observer) {
        logger.info("Creating new DownloadCompletedExercises command");
        return new ExceptionTrackingCallable<>(new DownloadCompletedExercises(observer));
    }
}

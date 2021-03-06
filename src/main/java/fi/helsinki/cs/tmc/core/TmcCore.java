package fi.helsinki.cs.tmc.core;

import fi.helsinki.cs.tmc.core.commands.AuthenticateUser;
import fi.helsinki.cs.tmc.core.commands.DownloadCompletedExercises;
import fi.helsinki.cs.tmc.core.commands.DownloadModelSolution;
import fi.helsinki.cs.tmc.core.commands.DownloadOrUpdateExercises;
import fi.helsinki.cs.tmc.core.commands.GetCourseDetails;
import fi.helsinki.cs.tmc.core.commands.GetOrganizations;
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.function.Consumer;

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

    // TODO: remember to remind to instantiate Settings and Langs holders and CLI
    // path...
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

    public static String getCliPath() {
        if (TmcCore.cliPath == null) {
            throw new IllegalStateException("tmc core singleton used before initialized");
        }
        return TmcCore.cliPath;
    }

    public Callable<List<Organization>> getOrganizations(ProgressObserver observer) {
        logger.info("Creating new GetOrganizations command");
        return new GetOrganizations(observer);
    }

    // unnecessary until a library is used instead of a CLI
    public Callable<Void> authenticate(ProgressObserver observer, String password) {
        logger.info("Creating new AuthenticateUser command");
        return new AuthenticateUser(observer, password, Oauth.getInstance());
    }

    // low priority
    public Callable<Void> sendDiagnostics(ProgressObserver observer) {
        logger.info("Creating new SendDiagnostics command");
        return new SendDiagnostics(observer);
    }

    public Callable<List<Exercise>> downloadOrUpdateExercises(ProgressObserver observer, List<Exercise> exercises) {
        logger.info("Creating new DownloadOrUpdateExercises command");
        return new ExceptionTrackingCallable<>(new DownloadOrUpdateExercises(observer, exercises));
    }

    public Callable<Course> getCourseDetails(ProgressObserver observer, Course course) {
        logger.info("Creating new GetCourseDetails command");
        return new ExceptionTrackingCallable<>(new GetCourseDetails(observer, course));
    }

    public Callable<List<Course>> listCourses(ProgressObserver observer) {
        logger.info("Creating new ListCourses command");
        return new ExceptionTrackingCallable<>(new ListCourses(observer));
    }

    public Callable<URI> pasteWithComment(ProgressObserver observer, Exercise exercise, String message) {
        logger.info("Creating new PasteWithComment command");
        return new ExceptionTrackingCallable<>(new PasteWithComment(observer, exercise, message));
    }

    public Callable<ValidationResult> runCheckStyle(ProgressObserver observer, Exercise exercise) {
        logger.info("Creating new RunCheckStyle command");
        return new ExceptionTrackingCallable<>(new RunCheckStyle(observer, exercise));
    }

    public Callable<RunResult> runTests(ProgressObserver observer, Exercise exercise) {
        logger.info("Creating new RunTests command");
        return new ExceptionTrackingCallable<>(new RunTests(observer, exercise));
    }

    public Callable<Boolean> sendFeedback(ProgressObserver observer, List<FeedbackAnswer> answers, URI feedbackUri) {
        logger.info("Creating new SendFeedback command");
        return new ExceptionTrackingCallable<>(new SendFeedback(observer, answers, feedbackUri));
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

    public Callable<GetUpdatableExercises.UpdateResult> getExerciseUpdates(ProgressObserver observer, Course course) {
        logger.info("Creating new GetUpdatableExercises command");
        return new ExceptionTrackingCallable<>(new GetUpdatableExercises(observer, course));
    }

    public Callable<Void> markReviewAsRead(ProgressObserver observer, Review review) {
        logger.info("Creating new MarkReviewAsRead command");
        return new ExceptionTrackingCallable<>(new MarkReviewAsRead(observer, review));
    }

    public Callable<List<Review>> getUnreadReviews(ProgressObserver observer, Course course) {
        logger.info("Creating new GetUnreadReviews command");
        return new ExceptionTrackingCallable<>(new GetUnreadReviews(observer, course));
    }

    public Callable<SubmissionResponse> requestCodeReview(ProgressObserver observer, Exercise exercise,
            String messageForReviewer) {
        logger.info("Creating new RequestCodeReview command");
        return new ExceptionTrackingCallable<>(new RequestCodeReview(observer, exercise, messageForReviewer));
    }

    public Callable<Exercise> downloadModelSolution(ProgressObserver observer, Exercise exercise) {
        logger.info("Creating new DownloadModelSolution command");
        return new ExceptionTrackingCallable<>(new DownloadModelSolution(observer, exercise));
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

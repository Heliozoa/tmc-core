package fi.helsinki.cs.tmc.core.communication;

import fi.helsinki.cs.tmc.core.communication.http.HttpTasks;
import fi.helsinki.cs.tmc.core.communication.http.UriUtils;
import fi.helsinki.cs.tmc.core.communication.oauth2.Oauth;
import fi.helsinki.cs.tmc.core.communication.serialization.ByteArrayGsonSerializer;
import fi.helsinki.cs.tmc.core.communication.serialization.CourseInfoParser;
import fi.helsinki.cs.tmc.core.communication.serialization.CourseListParser;
import fi.helsinki.cs.tmc.core.communication.serialization.ReviewListParser;
import fi.helsinki.cs.tmc.core.configuration.TmcSettings;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.OauthCredentials;
import fi.helsinki.cs.tmc.core.domain.Organization;
import fi.helsinki.cs.tmc.core.domain.Review;
import fi.helsinki.cs.tmc.core.domain.UserInfo;
import fi.helsinki.cs.tmc.core.domain.submission.FeedbackAnswer;
import fi.helsinki.cs.tmc.core.exceptions.ConnectionFailedException;
import fi.helsinki.cs.tmc.core.exceptions.FailedHttpResponseException;
import fi.helsinki.cs.tmc.core.exceptions.NotLoggedInException;
import fi.helsinki.cs.tmc.core.exceptions.ObsoleteClientException;
import fi.helsinki.cs.tmc.core.exceptions.TmcCoreException;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;
import fi.helsinki.cs.tmc.core.utilities.JsonMaker;
import fi.helsinki.cs.tmc.core.utilities.JsonMakerGsonSerializer;
import fi.helsinki.cs.tmc.snapshots.LoggableEvent;

import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import org.apache.oltu.oauth2.common.exception.OAuthProblemException;
import org.apache.oltu.oauth2.common.exception.OAuthSystemException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.URI;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.zip.GZIPOutputStream;

/**
 * A frontend for the server.
 */
public class TmcServerCommunicationTaskFactory {

    private static final Logger LOG = LoggerFactory.getLogger(TmcServerCommunicationTaskFactory.class.getName());
    public static final int API_VERSION = 8;

    private TmcSettings settings;
    private Oauth oauth;
    private CourseListParser courseListParser;
    private CourseInfoParser courseInfoParser;
    private ReviewListParser reviewListParser;
    private String clientVersion;

    public TmcServerCommunicationTaskFactory() {
        this(TmcSettingsHolder.get(), Oauth.getInstance());
    }

    public TmcServerCommunicationTaskFactory(TmcSettings settings, Oauth oauth) {
        this(settings, oauth, new CourseListParser(), new CourseInfoParser(), new ReviewListParser());
    }

    public TmcServerCommunicationTaskFactory(TmcSettings settings, Oauth oauth, CourseListParser courseListParser,
            CourseInfoParser courseInfoParser, ReviewListParser reviewListParser) {
        this.settings = settings;
        this.oauth = oauth;
        this.courseListParser = courseListParser;
        this.courseInfoParser = courseInfoParser;
        this.reviewListParser = reviewListParser;
        this.clientVersion = getClientVersion();
    }

    private static String getClientVersion() {
        return TmcSettingsHolder.get().clientVersion();
    }

    public void setSettings(TmcSettings settings) {
        this.settings = settings;
    }

    /**
     * Returns a Callable that calls the given Callable.
     *
     * <p>
     * If the call fails once, the oauth token is refreshed and the call is done
     * again.
     * </p>
     *
     * @param <T>      return type of the callable
     * @param callable Callable to be wrapped
     * @return The given Callable wrapped in another Callable
     */
    private <T> Callable<T> wrapWithNotLoggedInException(final Callable<T> callable) {
        return new Callable<T>() {
            @Override
            public T call() throws Exception {
                try {
                    return callable.call();
                } catch (FailedHttpResponseException e) {
                    LOG.error("Communication with the server failed!");
                    throw new NotLoggedInException();
                } catch (IOException e) {
                    if (e.getMessage().contains("Connection timed out")) {
                        throw new ConnectionFailedException(
                                "Connection failed! Please check your internet connection via browser.");
                    }
                    throw e;
                }
            }
        };
    }

    private URI getCourseListUrl() throws OAuthSystemException, OAuthProblemException, TmcCoreException {
        String serverAddress = settings.getServerAddress();
        String url;
        Optional<Organization> organization = settings.getOrganization();
        if (!organization.isPresent()) {
            throw new TmcCoreException("Organization not selected");
        }
        String urlLastPart = "api/v" + API_VERSION + "/core/org/" + organization.get().getSlug() + "/courses.json";
        if (serverAddress.endsWith("/")) {
            url = serverAddress + urlLastPart;
        } else {
            url = serverAddress + "/" + urlLastPart;
        }
        return addApiCallQueryParameters(URI.create(url));
    }

    private URI addApiCallQueryParameters(URI url) throws NotLoggedInException {
        url = UriUtils.withQueryParam(url, "client", settings.clientName());
        url = UriUtils.withQueryParam(url, "client_version", clientVersion);
        url = UriUtils.withQueryParam(url, "access_token", oauth.getToken());
        return url;
    }

    public Callable<List<Course>> getDownloadingCourseListTask() {
        return wrapWithNotLoggedInException(new Callable<List<Course>>() {
            @Override
            public List<Course> call() throws Exception {
                try {
                    Callable<String> download = HttpTasks.getForText(getCourseListUrl());
                    String text = download.call();
                    return courseListParser.parseFromJson(text);
                } catch (FailedHttpResponseException ex) {
                    return checkForObsoleteClient(ex);
                }
                // TODO: Cancellable?
            }
        });
    }

    public Callable<Optional<Course>> getCourseByIdTask(final int id) {
        return wrapWithNotLoggedInException(new Callable<Optional<Course>>() {
            @Override
            public Optional<Course> call() throws Exception {
                try {
                    Callable<String> download = HttpTasks.getForText(getCourseListUrl());
                    String text = download.call();
                    List<Course> courses = courseListParser.parseFromJson(text);
                    for (Course course : courses) {
                        if (course.getId() == id) {
                            return Optional.of(course);
                        }
                    }
                    return Optional.absent();
                } catch (FailedHttpResponseException ex) {
                    return checkForObsoleteClient(ex);
                }
            }
        });
    }

    public Optional<Course> getCourseFromAllCoursesByIdTask(final int id) throws Exception {
        String url;
        String serverAddress = settings.getServerAddress();
        String apiVersion = "api/v" + API_VERSION;
        if (serverAddress.endsWith("/")) {
            url = settings.getServerAddress() + apiVersion;
        } else {
            url = serverAddress + "/" + apiVersion;
        }
        url = url + "/courses/" + id;
        URI courseUrl = this.addApiCallQueryParameters(URI.create(url));
        String response = HttpTasks.getForText(courseUrl).call();
        Course course = new Gson().fromJson(response, new TypeToken<Course>() {
        }.getType());
        return Optional.fromNullable(course);
    }

    public Callable<Course> getFullCourseInfoTask(final Course courseStub) {
        return wrapWithNotLoggedInException(new Callable<Course>() {
            @Override
            public Course call() throws Exception {
                try {
                    URI url = addApiCallQueryParameters(courseStub.getDetailsUrl());
                    final Callable<String> download = HttpTasks.getForText(url);
                    String text = download.call();
                    return courseInfoParser.parseFromJson(text);
                } catch (FailedHttpResponseException ex) {
                    return checkForObsoleteClient(ex);
                }
            }

            // TODO: Cancellable?
        });
    }

    public Callable<Void> getUnlockingTask(final Course course) {
        final Map<String, String> params = Collections.emptyMap();
        return wrapWithNotLoggedInException(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                try {
                    final Callable<String> download = HttpTasks.postForText(getUnlockUrl(course), params);
                    download.call();
                    return null;
                } catch (FailedHttpResponseException ex) {
                    return checkForObsoleteClient(ex);
                }
            }

            // TODO: Cancellable?
        });
    }

    private URI getUnlockUrl(Course course) throws NotLoggedInException {
        return addApiCallQueryParameters(course.getUnlockUrl());
    }

    public Callable<byte[]> getDownloadingExerciseZipTask(Exercise exercise) throws NotLoggedInException {
        URI zipUrl = exercise.getDownloadUrl();
        return HttpTasks.getForBinary(addApiCallQueryParameters(zipUrl));
    }

    public Callable<byte[]> getDownloadingExerciseSolutionZipTask(Exercise exercise) throws NotLoggedInException {
        URI zipUrl = exercise.getSolutionDownloadUrl();
        return HttpTasks.getForBinary(addApiCallQueryParameters(zipUrl));
    }

    public Callable<SubmissionResponse> getSubmittingExerciseTask(final Exercise exercise, final byte[] sourceZip,
            Map<String, String> extraParams) {

        final Map<String, String> params = new LinkedHashMap<>();
        params.put("client_time", "" + (System.currentTimeMillis() / 1000L));
        params.put("client_nanotime", "" + System.nanoTime());
        params.putAll(extraParams);

        return wrapWithNotLoggedInException(new Callable<SubmissionResponse>() {
            @Override
            public SubmissionResponse call() throws Exception {
                String response;
                try {
                    final URI submitUrl = addApiCallQueryParameters(exercise.getReturnUrl());
                    final Callable<String> upload = HttpTasks.uploadFileForTextDownload(submitUrl, params,
                            "submission[file]", sourceZip);
                    response = upload.call();
                } catch (FailedHttpResponseException ex) {
                    return checkForObsoleteClient(ex);
                }

                JsonObject respJson = new JsonParser().parse(response).getAsJsonObject();
                if (respJson.get("error") != null) {
                    throw new RuntimeException("Server responded with error: " + respJson.get("error"));
                } else if (respJson.get("submission_url") != null) {
                    try {
                        URI submissionUrl = addApiCallQueryParameters(
                                new URI(respJson.get("submission_url").getAsString()));
                        URI pasteUrl = new URI(respJson.get("paste_url").getAsString());
                        URI showSubmissionUrl = new URI(respJson.get("show_submission_url").getAsString());
                        return new SubmissionResponse(submissionUrl, pasteUrl, showSubmissionUrl);
                    } catch (Exception e) {
                        throw new RuntimeException("Server responded with malformed " + "submission url");
                    }
                } else {
                    throw new RuntimeException("Server returned unknown response");
                }
            }

            // TODO: Cancellable?
        });
    }

    public static class SubmissionResponse {

        public final URI submissionUrl;
        public final URI pasteUrl;
        public final URI showSubmissionUrl;

        public SubmissionResponse(URI submissionUrl, URI pasteUrl, URI showSubmissionUrl) {
            this.submissionUrl = submissionUrl;
            this.pasteUrl = pasteUrl;
            this.showSubmissionUrl = showSubmissionUrl;
        }
    }

    public Callable<String> getSubmissionFetchTask(URI submissionUrl) {
        return HttpTasks.getForText(submissionUrl);
    }

    public Callable<List<Review>> getDownloadingReviewListTask(final Course course) {
        return wrapWithNotLoggedInException(new Callable<List<Review>>() {
            @Override
            public List<Review> call() throws Exception {
                try {
                    URI url = addApiCallQueryParameters(course.getReviewsUrl());
                    final Callable<String> download = HttpTasks.getForText(url);
                    String text = download.call();
                    return reviewListParser.parseFromJson(text);
                } catch (FailedHttpResponseException ex) {
                    return checkForObsoleteClient(ex);
                }
            }

            // TODO: Cancellable?
        });
    }

    public Callable<Void> getMarkingReviewAsReadTask(final Review review, boolean read) {
        final Map<String, String> params = new HashMap<>();
        params.put("_method", "put");
        if (read) {
            params.put("mark_as_read", "1");
        } else {
            params.put("mark_as_unread", "1");
        }

        return wrapWithNotLoggedInException(new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                URI url = addApiCallQueryParameters(URI.create(review.getUpdateUrl() + ".json"));
                final Callable<String> task = HttpTasks.postForText(url, params);
                task.call();
                return null;
            }

            // TODO: Cancellable?
        });
    }

    public Callable<String> getFeedbackAnsweringJob(final URI answerUrl, List<FeedbackAnswer> answers) {
        final Map<String, String> params = new HashMap<>();
        for (int i = 0; i < answers.size(); ++i) {
            String keyPrefix = "answers[" + i + "]";
            FeedbackAnswer answer = answers.get(i);
            params.put(keyPrefix + "[question_id]", "" + answer.getQuestion().getId());
            params.put(keyPrefix + "[answer]", answer.getAnswer());
        }

        return wrapWithNotLoggedInException(new Callable<String>() {
            @Override
            public String call() throws Exception {
                try {
                    final URI submitUrl = addApiCallQueryParameters(answerUrl);
                    final Callable<String> upload = HttpTasks.postForText(submitUrl, params);
                    return upload.call();
                } catch (FailedHttpResponseException ex) {
                    return checkForObsoleteClient(ex);
                }
            }

            // TODO: Cancellable?
        });
    }

    public Callable<Object> getSendEventLogJob(final URI snapshotServerUrl, List<LoggableEvent> events)
            throws NotLoggedInException {
        final Map<String, String> extraHeaders = new LinkedHashMap<>();
        String username = settings.getUsername().isPresent() ? settings.getUsername().get() : "Username missing";
        extraHeaders.put("X-Tmc-Version", "1");
        extraHeaders.put("X-Tmc-Username", username);
        extraHeaders.put("X-Tmc-SESSION-ID", oauth.getToken());

        final byte[] data;
        try {
            data = eventListToPostBody(events);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return wrapWithNotLoggedInException(new Callable<Object>() {
            @Override
            public Object call() throws Exception {
                URI url = addApiCallQueryParameters(snapshotServerUrl);
                final Callable<String> upload = HttpTasks.rawPostForText(url, data, extraHeaders);
                upload.call();
                return null;
            }

            // TODO: Cancellable?
        });
    }

    public void fetchOauthCredentialsTask() throws Exception {
        URI credentialsUrl;
        if (settings.getServerAddress().endsWith("/")) {
            credentialsUrl = URI.create(settings.getServerAddress() + "api/v" + API_VERSION + "/application/"
                    + settings.clientName() + "/credentials");
        } else {
            credentialsUrl = URI.create(settings.getServerAddress() + "/api/v" + API_VERSION + "/application/"
                    + settings.clientName() + "/credentials");
        }
        String response = HttpTasks.getForText(credentialsUrl).call();
        OauthCredentials credentials = new Gson().fromJson(response, OauthCredentials.class);
        settings.setOauthCredentials(Optional.fromNullable(credentials));
    }

    public List<Organization> getOrganizationListTask() throws Exception {
        String url;
        String serverAddress = settings.getServerAddress();
        String urlLastPart = "api/v" + API_VERSION + "/org";
        if (serverAddress.endsWith("/")) {
            url = settings.getServerAddress() + urlLastPart;
        } else {
            url = serverAddress + "/" + urlLastPart;
        }
        URI organizationUrl = URI.create(url);
        String response = HttpTasks.getForText(organizationUrl).call();
        List<Organization> organizations = new Gson().fromJson(response, new TypeToken<List<Organization>>() {
        }.getType());
        return organizations;
    }

    public List<Exercise> getExercisesForCourse(int id) throws Exception {
        String url;
        String serverAddress = settings.getServerAddress();
        String urlLastPart = "api/v" + API_VERSION + "/courses/" + id + "/exercises";
        if (serverAddress.endsWith("/")) {
            url = settings.getServerAddress() + urlLastPart;
        } else {
            url = serverAddress + "/" + urlLastPart;
        }
        URI organizationUrl = this.addApiCallQueryParameters(URI.create(url));
        String response = HttpTasks.getForText(organizationUrl).call();
        List<Exercise> exercises = new Gson().fromJson(response, new TypeToken<List<Exercise>>() {
        }.getType());
        return exercises;
    }

    public Organization getOrganizationBySlug(String slug) throws Exception {
        String url;
        String serverAddress = settings.getServerAddress();
        String urlLastPart = "api/v" + API_VERSION;
        if (serverAddress.endsWith("/")) {
            url = settings.getServerAddress() + urlLastPart;
        } else {
            url = serverAddress + "/" + urlLastPart;
        }
        if (slug.startsWith("/")) {
            url = url + "/org" + slug + ".json";
        } else {
            url = url + "/org/" + slug + ".json";
        }
        URI organizationUrl = URI.create(url);
        String response = HttpTasks.getForText(organizationUrl).call();
        Organization organization = new Gson().fromJson(response, new TypeToken<Organization>() {
        }.getType());
        return organization;
    }

    public UserInfo getUserInfo() throws Exception {
        String url;
        String serverAddress = settings.getServerAddress();
        String urlLastPart = "api/v8/users/current";
        if (serverAddress.endsWith("/")) {
            url = serverAddress + urlLastPart;
        } else {
            url = serverAddress + "/" + urlLastPart;
        }

        URI userInfoUrl = this.addApiCallQueryParameters(URI.create(url));
        String response = HttpTasks.getForText(userInfoUrl).call();
        UserInfo userInfo = new Gson().fromJson(response, new TypeToken<UserInfo>() {
        }.getType());
        return userInfo;
    }

    private byte[] eventListToPostBody(List<LoggableEvent> events) throws IOException {
        ByteArrayOutputStream bufferBos = new ByteArrayOutputStream();
        GZIPOutputStream gzos = new GZIPOutputStream(bufferBos);
        OutputStreamWriter bufferWriter = new OutputStreamWriter(gzos, Charset.forName("UTF-8"));

        Gson gson = new GsonBuilder().registerTypeAdapter(byte[].class, new ByteArrayGsonSerializer())
                .registerTypeAdapter(JsonMaker.class, new JsonMakerGsonSerializer()).create();

        gson.toJson(events, new TypeToken<List<LoggableEvent>>() {
        }.getType(), bufferWriter);
        bufferWriter.close();
        gzos.close();

        return bufferBos.toByteArray();
    }

    private <T> T checkForObsoleteClient(FailedHttpResponseException ex)
            throws ObsoleteClientException, FailedHttpResponseException {
        if (ex.getStatusCode() == 404) {
            boolean obsolete;
            try {
                obsolete = new JsonParser().parse(ex.getEntityAsString()).getAsJsonObject().get("obsolete_client")
                        .getAsBoolean();
            } catch (Exception ex2) {
                obsolete = false;
            }
            if (obsolete) {
                throw new ObsoleteClientException();
            }
        }

        throw ex;
    }
}

package fi.helsinki.cs.tmc.core;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.doReturn;

import fi.helsinki.cs.tmc.core.commands.GetCourseDetails;
import fi.helsinki.cs.tmc.core.commands.GetUnreadReviews;
import fi.helsinki.cs.tmc.core.commands.ListCourses;
import fi.helsinki.cs.tmc.core.commands.MarkReviewAsRead;
import fi.helsinki.cs.tmc.core.commands.SendFeedback;
import fi.helsinki.cs.tmc.core.commands.SendSnapshotEvents;
import fi.helsinki.cs.tmc.core.commands.Submit;
import fi.helsinki.cs.tmc.core.communication.oauth2.Oauth;
import fi.helsinki.cs.tmc.core.configuration.TmcSettings;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.Organization;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.domain.Review;
import fi.helsinki.cs.tmc.core.domain.submission.FeedbackAnswer;
import fi.helsinki.cs.tmc.core.exceptions.NotLoggedInException;
import fi.helsinki.cs.tmc.core.holders.TmcLangsHolder;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;
import fi.helsinki.cs.tmc.core.utils.MockSettings;
import fi.helsinki.cs.tmc.langs.util.TaskExecutor;
import fi.helsinki.cs.tmc.snapshots.LoggableEvent;
import io.github.cdimascio.dotenv.Dotenv;

import com.google.common.base.Optional;
import fi.helsinki.cs.tmc.core.domain.OauthCredentials;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Scanner;
import java.lang.reflect.Field;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import javax.sound.sampled.SourceDataLine;

import org.apache.http.impl.conn.SystemDefaultRoutePlanner;
import org.apache.log4j.BasicConfigurator;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import static org.mockito.Mockito.when;

public class TmcCoreTest {

    @Mock
    ProgressObserver observer;

    @Spy
    Course course = new Course();

    @Mock
    Review review;

    @Mock
    Exercise exercise;

    @Spy
    TmcSettings settings = new MockSettings();

    @Mock
    TaskExecutor tmcLangs;

    @Mock
    Path path;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        this.settings.setOrganization(Optional.of(new Organization("testOrganization", "testOrganization",
                "testOrganization", "testOrganization", false)));
        TmcSettingsHolder.set(settings);
        TmcLangsHolder.set(tmcLangs);
        Locale locale = new Locale("a", "b", "c");
        doReturn(URI.create("testUrl")).when(course).getReviewsUrl();
        doReturn(URI.create("testUrl")).when(course).getDetailsUrl();
        doReturn(path).when(settings).getTmcProjectDirectory();
        doReturn(locale).when(settings).getLocale();
        doReturn(URI.create("testUrl")).when(exercise).getReturnUrl();
    }

    @After
    public void tearDown() throws NoSuchFieldException, IllegalAccessException {
        Field oauth = Oauth.class.getDeclaredField("oauth");
        oauth.setAccessible(true);
        oauth.set(null, null);
    }

    @Test(expected = NotLoggedInException.class)
    public void commandListCoursesThrowsNotLoggedInExceptionWhenHasNoToken() throws Exception {
        new ListCourses(observer).call();
    }

    @Test(expected = NotLoggedInException.class)
    public void commandGetUnreadReviewsThrowsNotLoggedInExceptionWhenHasNoToken() throws Exception {
        new GetUnreadReviews(observer, course).call();
    }

    @Test(expected = NotLoggedInException.class)
    public void commandSendFeedbackThrowsNotLoggedInExceptionWhenHasNoToken() throws Exception {
        new SendFeedback(observer, new ArrayList<FeedbackAnswer>(), new URI("test")).call();
    }

    @Test(expected = NotLoggedInException.class)
    public void commandGetCourseDetailsThrowsNotLoggedInExceptionWhenHasNoToken() throws Exception {
        new GetCourseDetails(observer, course).call();
    }

    @Test(expected = NotLoggedInException.class)
    public void commandMarkReviewsAsReadThrowsNotLoggedInExceptionWhenHasNoToken() throws Exception {
        new MarkReviewAsRead(observer, review).call();
    }

    @Test(expected = NotLoggedInException.class)
    public void commandSendSpywareEventsThrowsNotLoggedInExceptionWhenHasNoToken() throws Exception {
        List<URI> spywareUrls = new ArrayList<>();
        spywareUrls.add(URI.create("test"));
        course.setSpywareUrls(spywareUrls);
        new SendSnapshotEvents(observer, course, new ArrayList<LoggableEvent>()).call();
    }

    @Test(expected = NotLoggedInException.class)
    public void commandSubmitThrowsNotLoggedInExceptionWhenHasNoToken() throws Exception {
        new Submit(observer, exercise).call();
    }

    @Test
    public void getOrganizations() {
        BasicConfigurator.configure();
        Logger.getLogger(TmcCore.class).setLevel(Level.DEBUG);
        Dotenv dotenv = Dotenv.load();
        String email = dotenv.get("EMAIL");
        String password = dotenv.get("PASSWORD");
        String cli = dotenv.get("CLI");

        MockitoAnnotations.initMocks(this);
        when(this.settings.getEmail()).thenReturn(Optional.of(email));
        when(this.settings.getPassword()).thenReturn(Optional.of(password));
        TmcCore core = new TmcCore();
        String cliPath = Paths.get(cli).toAbsolutePath().toString();
        TmcCore.setCliPath(cliPath);
        List<Organization> orgs = core.getOrganizations();
        assertFalse(orgs.isEmpty());
    }
}

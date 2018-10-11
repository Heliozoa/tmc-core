package fi.helsinki.cs.tmc.core.commands;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory.SubmissionResponse;
import fi.helsinki.cs.tmc.core.configuration.TmcSettings;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Exercise;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.domain.submission.SubmissionResult;
import fi.helsinki.cs.tmc.core.holders.TmcLangsHolder;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;
import fi.helsinki.cs.tmc.core.utils.MockSettings;
import fi.helsinki.cs.tmc.core.utils.TestUtils;
import fi.helsinki.cs.tmc.langs.util.TaskExecutor;
import fi.helsinki.cs.tmc.langs.util.TaskExecutorImpl;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.net.URI;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Callable;

public class SubmitTest {

    @Rule public TemporaryFolder testFolder = new TemporaryFolder();

    @Mock ProgressObserver mockObserver;
    @Spy TmcSettings settings = new MockSettings();
    @Mock TmcServerCommunicationTaskFactory factory;
    @Mock Course mockCourse;
    @Mock Exercise mockExercise;

    private static final URI PASTE_URI = URI.create("http://example.com/paste");
    private static final URI SUBMISSION_URI = URI.create("http://example.com/submission");
    private static final URI SHOW_SUBMISSION_URI = URI.create("http://example.com/show_submission");
    private static final TmcServerCommunicationTaskFactory.SubmissionResponse STUB_RESPONSE =
            new TmcServerCommunicationTaskFactory.SubmissionResponse(SUBMISSION_URI, PASTE_URI, SHOW_SUBMISSION_URI);

    private static final String STUB_PROSESSING_RESPONSE = "{\"status\": \"processing\"}";
    private static final String STUB_PROSESSING_DONE_RESPONSE = "{\"status\": \"ok\"}";

    private Command<SubmissionResult> command;
    private Path arithFuncsTempDir;
    private TaskExecutor langs;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        TmcSettingsHolder.set(settings);

        langs = spy(new TaskExecutorImpl());
        TmcLangsHolder.set(langs);

        command = new Submit(mockObserver, mockExercise, factory);

        arithFuncsTempDir = TestUtils.getProject(this.getClass(), "arith_funcs");
        when(mockExercise.getExerciseDirectory(any(Path.class))).thenReturn(arithFuncsTempDir);
        when(settings.getLocale()).thenReturn(new Locale("FI"));
    }

    @Test(timeout = 10000)
    public void testCall() throws Exception {
        verifyZeroInteractions(mockObserver);
        doReturn(new byte[0]).when(langs).compressProject(any(Path.class));
        when(
                        factory.getSubmittingExerciseTask(
                                any(Exercise.class), any(byte[].class), any(Map.class)))
                .thenReturn(
                    (Callable<SubmissionResponse>) () -> STUB_RESPONSE);
        when(factory.getSubmissionFetchTask(any(URI.class)))
                .thenReturn(
                    () -> STUB_PROSESSING_RESPONSE)
                .thenReturn(
                    () -> STUB_PROSESSING_DONE_RESPONSE);

        SubmissionResult result = command.call();
        assertEquals(SubmissionResult.Status.OK, result.getStatus());
    }
}

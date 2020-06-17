package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.domain.Course;
import fi.helsinki.cs.tmc.core.domain.Organization;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;
import fi.helsinki.cs.tmc.core.exceptions.ConnectionFailedException;
import fi.helsinki.cs.tmc.core.exceptions.NotLoggedInException;
import fi.helsinki.cs.tmc.core.exceptions.ShowToUserException;
import fi.helsinki.cs.tmc.core.exceptions.TmcCoreException;
import fi.helsinki.cs.tmc.core.holders.TmcSettingsHolder;
import fi.helsinki.cs.tmc.core.utilities.ServerErrorHelper;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Optional;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import org.slf4j.Logger;
import java.lang.reflect.Type;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Command} for retrieving the course list from the server.
 */
public class ListCourses extends Command<List<Course>> {

    private static final Logger logger = LoggerFactory.getLogger(ListCourses.class);

    public ListCourses(ProgressObserver observer) {
        super(observer);
    }

    @VisibleForTesting
    ListCourses(ProgressObserver observer, TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory) {
        super(observer, tmcServerCommunicationTaskFactory);
    }

    @Override
    public List<Course> call() throws TmcCoreException, ShowToUserException {
        observer.progress(1, 0.0, "Fetching course details");

        Optional<Organization> organization = TmcSettingsHolder.get().getOrganization();
        if (!organization.isPresent()) {
            throw new TmcCoreException("Organization not selected");
        }

        String organizationSlug = organization.get().getSlug();
        ExecutionResult result = this.execute(new String[] { "list-courses", "--organization", organizationSlug });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<Course>>() {
        }.getType();
        List<Course> courses = gson.fromJson(result.getStdout(), listType);
        observer.progress(1, 1.0, "Fetched course details");

        return courses;
    }
}

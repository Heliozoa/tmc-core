package fi.helsinki.cs.tmc.core.commands;

import fi.helsinki.cs.tmc.core.ExecutionResult;
import fi.helsinki.cs.tmc.core.communication.TmcServerCommunicationTaskFactory;
import fi.helsinki.cs.tmc.core.domain.Organization;
import fi.helsinki.cs.tmc.core.domain.ProgressObserver;

import com.google.common.annotations.VisibleForTesting;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Type;

public class GetOrganizations extends Command<List<Organization>> {

    public GetOrganizations(ProgressObserver observer) {
        super(observer);
    }

    @VisibleForTesting
    GetOrganizations(ProgressObserver observer, TmcServerCommunicationTaskFactory tmcServerCommunicationTaskFactory) {
        super(observer, tmcServerCommunicationTaskFactory);
    }

    @Override
    public List<Organization> call() throws Exception {
        observer.progress(1, 0.0, "Fetching organizations");

        ExecutionResult result = super.execute(new String[] { "get-organizations" });
        observer.progress(1, 0.5, "Executed command");

        Gson gson = new Gson();
        Type listType = new TypeToken<ArrayList<Organization>>() {
        }.getType();
        ArrayList<Organization> orgs = gson.fromJson(result.getStdout(), listType);
        observer.progress(1, 1.0, "Fetched organizations");
        return orgs;
    }
}

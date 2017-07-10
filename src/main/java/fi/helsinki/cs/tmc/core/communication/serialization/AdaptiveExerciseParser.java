package fi.helsinki.cs.tmc.core.communication.serialization;

import fi.helsinki.cs.tmc.core.domain.Exercise;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;

public class AdaptiveExerciseParser {

    private static final Logger logger = LoggerFactory.getLogger(AdaptiveExerciseParser.class);

    public Exercise parseFromJson(String json) {
        if (json == null) {
            throw new NullPointerException("Json string is null");
        }
        if (json.trim().isEmpty()) {
            throw new IllegalArgumentException("Empty input");
        }
        try {
            Gson gson = new GsonBuilder().create();
            Exercise adaptive = gson.fromJson(json, Exercise.class);
            adaptive.setAdaptive(true);
            if (adaptive.isAvailable()) {
                String parsedUrl = adaptive.getZipUrl().toString();
                adaptive.setZipUrl(URI.create("http://" + parsedUrl));
                return adaptive;
            }
            logger.info("The gson parsed adaptive exercise is not available.");
            return null;
        } catch (RuntimeException ex) {
            logger.warn("Failed to parse an adaptive course from URL", ex);
            throw new RuntimeException("Failed to parse an adaptive course from URL: "
                    + ex.getMessage(), ex);
        }
    }

}

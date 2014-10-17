package org.sexyideas.moosificator;

import io.keen.client.java.KeenClient;
import io.keen.client.java.exceptions.KeenException;

import javax.inject.Singleton;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * @author spencer.firestone
 */
@Singleton
public final class MooseLogger {
    private static final Logger LOGGER = Logger.getLogger(MooseLogger.class.getName());

    private static final String MOOSE_RETRIEVAL_EVENT = "moose_retrieval";
    private static final String NEW_MOOSE_EVENT = "new_moose";
    private static final String ERROR_MOOSIFICATING_EVENT = "error_moosificating";

    public static void logEventForMooseRetrieval(URL sourceImage) {
        try {
            Map<String, Object> event = new HashMap<String, Object>();
            event.put("sourceImage", sourceImage);
            KeenClient.client().addEvent(MOOSE_RETRIEVAL_EVENT, event);
        } catch (KeenException e) {
            LOGGER.log(Level.WARNING,
                    format("Error storing event for retrieval of event for source image [%s]", sourceImage), e);
        }
    }

    public static void logEventForNewMooseSource(URL sourceImage) {
        try {
            Map<String, Object> event = new HashMap<String, Object>();
            event.put("sourceImage", sourceImage);
            KeenClient.client().addEvent(NEW_MOOSE_EVENT, event);
        } catch (KeenException e) {
            LOGGER.log(Level.WARNING,
                    format("Error storing event for retrieval of event for source image [%s]", sourceImage), e);
        }
    }

    public static void logEventForErrorMoosificating(MooseRequest mooseRequest, Throwable exception) {
        try {
            String errorMessage;
            if (exception.getMessage() == null) {
                errorMessage = exception.getClass().getName();
            } else {
                errorMessage = exception.getMessage();
            }

            Map<String, Object> event = new HashMap<String, Object>();
            event.put("sourceImage", mooseRequest.getOriginalImageUrl());
            event.put("error", errorMessage);
            KeenClient.client().addEvent(ERROR_MOOSIFICATING_EVENT, event);
        } catch (KeenException e) {
            LOGGER.log(Level.WARNING,
                    format("Error storing event for retrieval of event for source image [%s]",
                            mooseRequest.getOriginalImageUrl()), e);
        }
    }

    public static Logger getLogger() {
        return LOGGER;
    }
}

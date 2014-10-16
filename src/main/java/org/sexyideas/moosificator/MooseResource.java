package org.sexyideas.moosificator;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Ordering;
import io.keen.client.java.KeenClient;
import io.keen.client.java.exceptions.KeenException;
import jjil.algorithm.RgbAvgGray;
import jjil.core.Rect;
import jjil.core.RgbImage;
import jjil.j2se.RgbImageJ2se;

import javax.imageio.ImageIO;
import javax.inject.Singleton;
import javax.ws.rs.*;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.String.format;

/**
 * Moosificator service. No words necessary.
 *
 * @author alexandre.normand
 */
@Path("moose")
@Singleton
public class MooseResource {
    private static final Logger LOGGER = Logger.getLogger(MooseResource.class.getName());

    private static final int MAX_IMAGE_SIZE_IN_PIXELS = 2073600;
    private static final float MOOSE_HEAD_LEFT_OFFSET = 138.f;
    private static final float MOOSE_HEAD_TOP_OFFSET = 120.f;
    private static final float MOOSE_HEAD_WIDTH = 115.f;
    private static final float MOOSE_HEAD_HEIGHT = 115.f;

    private static final String MOOSE_RETRIEVAL_EVENT = "moose_retrieval";
    private static final String NEW_MOOSE_EVENT = "new_moose";
    private static final String ERROR_MOOSIFICATING_EVENT = "error_moosificating";

    private BufferedImage mooseOverlay;
    private BufferedImage noFaceFoundExceptionOverlay;
    private BufferedImage badUrlExceptionImage;
    private BufferedImage serverErrorMoose;
    private LoadingCache<MooseRequest, Optional<BufferedImage>> imageCache;
    private float mooseProportionRatio;
    private float noFaceOverlayRatio;
    private float magnifyingFactor;

    public void initializeIfRequired() {
        // This is an imperfect solution but it should be fine. If multiple requests come in at the same
        // time and there's a race condition, we'll just have wasted a few extra resources for those requests.
        if (this.mooseOverlay == null) {
            try {
                this.mooseOverlay = ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/moose.png"));
                this.noFaceFoundExceptionOverlay = ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/NoFaceFoundException.png"));
                this.badUrlExceptionImage = ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/BadUrlException.png"));
                this.serverErrorMoose = ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/ServerErrorMoose.png"));
                this.mooseProportionRatio = (float) this.mooseOverlay.getWidth() / (float) this.mooseOverlay.getHeight();
                this.noFaceOverlayRatio = (float) this.noFaceFoundExceptionOverlay.getWidth() / (float) this.noFaceFoundExceptionOverlay.getHeight();
                this.magnifyingFactor = this.mooseOverlay.getHeight() / MOOSE_HEAD_HEIGHT;

            } catch (IOException e) {
                throw Throwables.propagate(e);
            }

            this.imageCache = CacheBuilder.<MooseRequest, Optional<BufferedImage>>newBuilder()
                    .maximumSize(20)
                    .expireAfterWrite(1, TimeUnit.DAYS)
                    .build(new MoosificatorCacheLoader());
        }
    }

    @GET
    @Produces("image/png")
    public Response moosificate(@QueryParam("image") String sourceImage, @QueryParam("debug") String debug) {
        initializeIfRequired();
        URL imageUrl;
        try {
            imageUrl = new URL(sourceImage);
            logEventForMooseRetrieval(imageUrl);
        } catch (MalformedURLException e) {
            return Response.ok(this.badUrlExceptionImage).build();
        }

        try {
            MooseRequest mooseRequest = MooseRequest.newBuilder()
                    .withOriginalImageUrl(imageUrl)
                    .withDebug(debug)
                    .build();
            final Optional<BufferedImage> moosificationResult = this.imageCache.get(mooseRequest);

            if (!moosificationResult.isPresent()) {
                return Response.ok(this.serverErrorMoose).build();
            } else {
                StreamingOutput stream = new StreamingOutput() {
                    @Override
                    public void write(OutputStream os) throws IOException,
                            WebApplicationException {
                        ImageIO.write(moosificationResult.get(), "PNG", os);
                    }
                };

                return Response.ok(stream).build();
            }
        } catch (Throwable error) {
            throw Throwables.propagate(error);
        }
    }

    private void logEventForMooseRetrieval(URL sourceImage) {
        try {
            Map<String, Object> event = new HashMap<String, Object>();
            event.put("sourceImage", sourceImage);
            KeenClient.client().addEvent(MOOSE_RETRIEVAL_EVENT, event);
        } catch (KeenException e) {
            LOGGER.log(Level.WARNING,
                    format("Error storing event for retrieval of event for source image [%s]", sourceImage), e);
        }
    }

    private void logEventForNewMooseSource(URL sourceImage) {
        try {
            Map<String, Object> event = new HashMap<String, Object>();
            event.put("sourceImage", sourceImage);
            KeenClient.client().addEvent(NEW_MOOSE_EVENT, event);
        } catch (KeenException e) {
            LOGGER.log(Level.WARNING,
                    format("Error storing event for retrieval of event for source image [%s]", sourceImage), e);
        }
    }

    private void logEventForErrorMoosificating(MooseRequest mooseRequest, Throwable exception) {
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


    public class MoosificatorCacheLoader extends CacheLoader<MooseRequest, Optional<BufferedImage>> {
        @Override
        public Optional<BufferedImage> load(MooseRequest mooseRequest) throws Exception {
            try {
                return Optional.of(moosificateImage(mooseRequest));
            } catch (Throwable e) {
                logEventForErrorMoosificating(mooseRequest, e);
                LOGGER.log(Level.WARNING, format("Error generating image for url [%s]", mooseRequest.getOriginalImageUrl()
                        .toExternalForm()), e);
                return Optional.absent();
            }
        }
    }


    /**
     * Transforms a source image to a moosificated version of it.
     *
     * @param mooseRequest mooseRequest containing URL of the image to moosificate
     * @return the moosificated image
     * @throws jjil.core.Error
     * @throws IOException
     */
    private BufferedImage moosificateImage(MooseRequest mooseRequest) throws jjil.core.Error, IOException {
        logEventForNewMooseSource(mooseRequest.getOriginalImageUrl());

        BufferedImage sourceImage = ImageIO.read(mooseRequest.getOriginalImageUrl());
        RgbImage rgbImage = RgbImageJ2se.toRgbImage(sourceImage);
        RgbAvgGray toGray = new RgbAvgGray();
        toGray.push(rgbImage);

        InputStream profileInputStream = MoosificatorApp.class.getResourceAsStream("/profiles/HCSB.txt");
        Gray8DetectHaarMultiScale detectHaar = new Gray8DetectHaarMultiScale(profileInputStream, 1, 30);

        // We either keep the source size if small enough or we cap it to be fewer pixels than our max
        int canvasWidth = sourceImage.getWidth();
        int canvasHeight = sourceImage.getHeight();
        double originalSurface = sourceImage.getWidth() * sourceImage.getHeight();
        if (originalSurface > MAX_IMAGE_SIZE_IN_PIXELS) {
            double resizeFactor = Math.sqrt(MAX_IMAGE_SIZE_IN_PIXELS / originalSurface);
            canvasWidth = (int) (sourceImage.getWidth() * resizeFactor);
            canvasHeight = (int) (sourceImage.getHeight() * resizeFactor);
        }

        BufferedImage combined = new BufferedImage(canvasWidth, canvasHeight,
                BufferedImage.TYPE_INT_ARGB);
        List<Rect> rectangles = detectHaar.pushAndReturn(toGray.getFront());
        Graphics g = combined.getGraphics();

        g.drawImage(sourceImage, 0, 0, canvasWidth, canvasHeight, null);

        // Overlay a nice NoFaceFound on the image and return that
        if (rectangles.isEmpty()) {
            int overlayWidth;
            int overlayHeight;
            // Set the size of our overlay according to the largest side of the source image
            if (canvasWidth > canvasHeight) {
                overlayWidth = (int) (canvasWidth * 0.5);
                overlayHeight = (int) (overlayWidth / this.noFaceOverlayRatio);
            } else {
                overlayHeight = (int) (canvasWidth * 0.5);
                overlayWidth = (int) (overlayHeight * this.noFaceOverlayRatio);
            }

            g.drawImage(this.noFaceFoundExceptionOverlay, (int) ((canvasWidth - overlayWidth) / 2.f),
                    (int) ((canvasHeight - overlayHeight) / 2.), overlayWidth, overlayHeight, null);
        } else {
            List<Rect> uniqueRectangles = findUniqueRectangles(rectangles);
            for (Rect rectangle : uniqueRectangles) {

                if (mooseRequest.isDebug()) {
                    // Add debug rectangle around the face
                    g.drawRect(rectangle.getLeft(), rectangle.getTop(), rectangle.getWidth(), rectangle.getHeight());
                } else {
                    float effectiveHeight = rectangle.getHeight() * this.magnifyingFactor;
                    float effectiveWidth = effectiveHeight * this.mooseProportionRatio;

                    float effectiveTop = rectangle.getTop() - MOOSE_HEAD_TOP_OFFSET * effectiveHeight / this.mooseOverlay.getHeight();
                    float effectiveLeft = rectangle.getLeft() - MOOSE_HEAD_LEFT_OFFSET * effectiveWidth / this.mooseOverlay.getWidth();
                    // Add a moose on the original image to overlay that region
                    g.drawImage(this.mooseOverlay, (int) effectiveLeft,
                            (int) effectiveTop, (int) effectiveWidth, (int) effectiveHeight, null);
                }
            }
        }

        return combined;
    }

    // TODO : Find unique rectangles. That is, detection returns multiple rectangles for the same face and we
    // should keep only the largest one (right?) and skip all others contained within it.
    private List<Rect> findUniqueRectangles(List<Rect> rectangles) {
        List<Rect> uniqueRectangles = new ArrayList<Rect>();
        Ordering<Rect> ordering = Ordering.from(new AreaComparator());
        Rect largest = ordering.max(rectangles);
        uniqueRectangles.add(largest);
        return uniqueRectangles;
    }

    public class AreaComparator implements Comparator<Rect> {
        @Override
        public int compare(Rect o1, Rect o2) {
            return o1.getArea() - o2.getArea();
        }

    }
}

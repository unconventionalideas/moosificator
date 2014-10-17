package org.sexyideas.moosificator;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Ordering;
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
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.lang.String.format;

/**
 * Moosificator service. No words necessary.
 *
 * @author alexandre.normand
 */
@Singleton
@Produces("image/png")
@Path("/")
public class MooseResource {

    private static final int MAX_IMAGE_SIZE_IN_PIXELS = 2073600;
    private static final String DEFAULT_MOOSE_NAME = "moose";

    private BufferedImage noFaceFoundExceptionOverlay;
    private BufferedImage unrecognizedMooseImage;
    private BufferedImage badUrlExceptionImage;
    private BufferedImage serverErrorMoose;
    private BufferedImage leftAntler;
    private BufferedImage rightAntler;
    private HashMap<String, MooseImage> namedMooseOverlays = new HashMap<>();
    private LoadingCache<MooseRequest, Optional<BufferedImage>> imageCache;
    private float noFaceOverlayRatio;

    private static Pattern PNG_PATTERN = Pattern.compile("^(\\w+)-(\\d+)-(\\d+)-(\\d+)\\.png$");

    public void initializeIfRequired() {
        // This is an imperfect solution but it should be fine. If multiple requests come in at the same
        // time and there's a race condition, we'll just have wasted a few extra resources for those requests.
        if (this.namedMooseOverlays.isEmpty()) {
            try {
                this.leftAntler = ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/LeftAntler.png"));
                this.rightAntler = ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/RightAntler.png"));
                this.unrecognizedMooseImage = ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/UnrecognizedMoose.png"));
                this.noFaceFoundExceptionOverlay = ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/NoFaceFoundException.png"));
                this.badUrlExceptionImage = ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/BadUrlException.png"));
                this.serverErrorMoose = ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/ServerErrorMoose.png"));
                this.noFaceOverlayRatio = (float) this.noFaceFoundExceptionOverlay.getWidth() / (float) this.noFaceFoundExceptionOverlay.getHeight();

                String dir = MoosificatorApp.class.getResource("/moose/named/").getPath();
                try (DirectoryStream<java.nio.file.Path> directoryStream = Files.newDirectoryStream(Paths.get(dir))) {
                    for (java.nio.file.Path path : directoryStream) {
                        Matcher matcher = PNG_PATTERN.matcher(path.getFileName().toString());
                        if (matcher.matches()) {
                            namedMooseOverlays.put(matcher.group(1), new MooseImage(
                                    ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/named/" + path.getFileName())),
                                    Float.parseFloat(matcher.group(2)),
                                    Float.parseFloat(matcher.group(3)),
                                    Float.parseFloat(matcher.group(4))));
                        }
                    }
                }

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
    @Path("antler")
    public Response antlerificate(@QueryParam("image") String sourceImage,
                                  @QueryParam("debug") String debug) {
        return processRequest(MooseRequest.newBuilder()
                .withRequestType(MooseRequest.RequestType.ANTLER)
                .withOriginalImageUrl(sourceImage)
                .withDebug(debug));
    }

    @GET
    @Path("moose")
    public Response moosificate(@QueryParam("image") String sourceImage,
                                @QueryParam("debug") String debug) {
        return processRequest(MooseRequest.newBuilder()
                        .withRequestType(MooseRequest.RequestType.MOOSE)
                        .withOriginalImageUrl(sourceImage)
                        .withOverlayImageName(DEFAULT_MOOSE_NAME)
                        .withDebug(debug));
    }

    @GET
    @Path("moose/{name}")
    public Response moosificateByName(@PathParam("name") String name,
                                      @QueryParam("image") String sourceImage,
                                      @QueryParam("debug") String debug) {
        return processRequest(MooseRequest.newBuilder()
                .withRequestType(MooseRequest.RequestType.NAMED)
                .withOriginalImageUrl(sourceImage)
                .withOverlayImageName(name)
                .withDebug(debug));
    }

    @GET
    @Path("remoose")
    public Response remoosificate(@QueryParam("image") String sourceImage,
                                  @QueryParam("overlayImage") String overlayImageUrl,
                                  @QueryParam("debug") String debug) {
        return processRequest(MooseRequest.newBuilder()
                .withRequestType(MooseRequest.RequestType.MOOSE)
                .withOriginalImageUrl(sourceImage)
                .withOverlayImageUrl(overlayImageUrl)
                .withDebug(debug));
    }
    private Response processRequest(MooseRequest.MooseRequestBuilder mooseRequestBuilder) {
        initializeIfRequired();

        try {
            MooseRequest mooseRequest = mooseRequestBuilder.build();

            // Validate image overlay
            if (mooseRequest.getOverlayImageName() != null) {
                if (this.namedMooseOverlays.get(mooseRequest.getOverlayImageName()) == null) {
                    throw new MooseException(MooseException.MooseExceptionType.INVALID_MOOSE_NAME);
                }
            }

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
        } catch (MooseException e) {
            switch (e.getMooseExceptionType()) {
                case INVALID_SOURCE_URL:
                case INVALID_RE_MOOSE_URL:
                    return Response.ok(this.badUrlExceptionImage).build();
                case INVALID_MOOSE_NAME:
                    return Response.ok(this.unrecognizedMooseImage).build();
                case MISSING_REQUEST_TYPE:
                case MISSING_SOURCE_URL:
                case MISSING_MOOSE_NAME:
                case MISSING_RE_MOOSE_URL:
                default:
                    return Response.ok(this.serverErrorMoose).build();
            }
        } catch (Throwable error) {
            throw Throwables.propagate(error);
        }
    }

    public class MoosificatorCacheLoader extends CacheLoader<MooseRequest, Optional<BufferedImage>> {
        @Override
        public Optional<BufferedImage> load(MooseRequest mooseRequest) throws Exception {
            try {
                return Optional.of(moosificateImage(mooseRequest));
            } catch (Throwable e) {
                MooseLogger.logEventForErrorMoosificating(mooseRequest, e);
                MooseLogger.getLogger().log(Level.WARNING, format("Error generating image for url [%s]",
                        mooseRequest.getOriginalImageUrl().toExternalForm()), e);
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
        MooseLogger.logEventForNewMooseSource(mooseRequest.getOriginalImageUrl());

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
            List<Rect> uniqueRectangles = findDistinctFaces(rectangles);
            for (Rect rectangle : uniqueRectangles) {

                if (mooseRequest.isDebug()) {
                    // Add debug rectangle around the face
                    g.drawRect(rectangle.getLeft(), rectangle.getTop(), rectangle.getWidth(), rectangle.getHeight());
                }

                if (mooseRequest.hasNamedOverlayImage()) {
                    // Add a named moose on the original image to overlay that region
                    this.namedMooseOverlays.get(mooseRequest.getOverlayImageName()).drawImage(g, rectangle);
                } else {
                    if (mooseRequest.hasOverlayImageFromUrl()) {
                        // Add overlay image from URL
                        addOverlayImage(g, rectangle);
                    }
                }

                if (mooseRequest.hasAntlers()) {
                    // Add antlers to the face
                    addAntlers(g, rectangle);
                }
            }
        }

        return combined;
    }

    private void addOverlayImage(Graphics g, Rect rectangle) {
        // TODO: Implement
    }

    private void addAntlers(Graphics g, Rect rectangle) {
        float magnifyingFactor = rectangle.getHeight() / (float) this.rightAntler.getHeight() * 0.5f;
        float widthOffset =  0.25f * rectangle.getWidth();
        float heightOffset = 0.25f * rectangle.getHeight();
        int leftAntlerWidth = (int) (this.leftAntler.getWidth() * magnifyingFactor);
        int leftAntlerHeight = (int) (this.leftAntler.getHeight() * magnifyingFactor);
        int rightAntlerWidth = (int) (this.rightAntler.getWidth() * magnifyingFactor);
        int rightAntlerHeight = (int) (this.rightAntler.getHeight() * magnifyingFactor);

        g.drawImage(this.leftAntler,
                rectangle.getLeft() + (int) widthOffset - leftAntlerWidth,
                rectangle.getTop() + (int) heightOffset - leftAntlerHeight,
                leftAntlerWidth,
                leftAntlerHeight,
                null);

        g.drawImage(this.rightAntler,
                rectangle.getRight() - (int) widthOffset,
                rectangle.getTop() + (int) heightOffset - rightAntlerHeight,
                rightAntlerWidth,
                rightAntlerHeight,
                null);
    }

    private List<Rect> findDistinctFaces(List<Rect> rectangles) {
        List<Rect> uniqueRectangles = new ArrayList<>();
        Ordering<Rect> ordering = Ordering.from(new AreaComparator());
        uniqueRectangles.add(ordering.max(rectangles));

        for (Rect rectangle : rectangles) {
            if (outsideAlreadyIncluded(uniqueRectangles, rectangle)) {
                uniqueRectangles.add(rectangle);
            }
        }

        return uniqueRectangles;
    }

    /**
     * @return true if the given rectangle is outside of all currently selected rectangles.
     */
    private boolean outsideAlreadyIncluded(List<Rect> selectedRegions, Rect tentativeRegion) {
        for (Rect goodToDate : selectedRegions) {
            if (tentativeRegion.overlaps(goodToDate)) {
                return false;
            }
        }

        return true;
    }

    public class AreaComparator implements Comparator<Rect> {
        @Override
        public int compare(Rect o1, Rect o2) {
            return o1.getArea() - o2.getArea();
        }

    }
}

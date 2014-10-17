package org.sexyideas.moosificator;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorModel;
import java.awt.image.WritableRaster;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageOutputStream;
import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;

import com.google.common.base.Optional;
import com.google.common.base.Throwables;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.cache.LoadingCache;
import com.google.common.collect.Ordering;
import com.sun.imageio.plugins.gif.GIFImageReader;
import com.sun.imageio.plugins.gif.GIFImageReaderSpi;
import com.sun.imageio.plugins.gif.GIFImageWriter;
import com.sun.imageio.plugins.gif.GIFImageWriterSpi;
import jjil.algorithm.RgbAvgGray;
import jjil.core.Rect;
import jjil.core.RgbImage;
import jjil.j2se.RgbImageJ2se;

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
    private LoadingCache<MooseRequest, Optional<byte[]>> gifCache;
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

            this.gifCache = CacheBuilder.<MooseRequest, Optional<BufferedImage>>newBuilder()
                    .maximumSize(5)
                    .expireAfterWrite(1, TimeUnit.DAYS)
                    .build(new MoosificatorGifCacheLoader());
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

            URL imageUrl = mooseRequest.getOriginalImageUrl();
            HttpURLConnection connection = (HttpURLConnection) imageUrl.openConnection();
            String contentType;
            try {
                connection.setRequestMethod("HEAD");
                connection.connect();
                contentType = connection.getContentType();
            }
            finally {
                connection.disconnect();
            }
            if (!contentType.toLowerCase().endsWith("gif")) {
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
            } else {
                final Optional<byte[]> moosificationResult = this.gifCache.get(mooseRequest);
                if (!moosificationResult.isPresent()) {
                    return Response.ok(this.serverErrorMoose).build();
                } else {
                    StreamingOutput stream = new StreamingOutput() {
                        @Override
                        public void write(OutputStream os) throws IOException,
                                WebApplicationException {
                            os.write(moosificationResult.get());
                        }
                    };
                    return Response.ok(stream).build();
                }
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

    public class MoosificatorGifCacheLoader extends CacheLoader<MooseRequest, Optional<byte[]>> {
        @Override
        public Optional<byte[]> load(MooseRequest mooseRequest) throws Exception {

            try {
                final ArrayList<IIOImage> frames = new ArrayList<>();
                final ImageReader ir = new GIFImageReader(new GIFImageReaderSpi());
                ir.setInput(ImageIO.createImageInputStream(mooseRequest.getOriginalImageUrl().openStream()));
                for (int i = 0; i < ir.getNumImages(true); i++) {
                    frames.add(ir.readAll(i, null));
                }

                final GIFImageWriter gifImageWriter = new GIFImageWriter(new GIFImageWriterSpi());
                final ByteArrayOutputStream bos = new ByteArrayOutputStream();
                ImageOutputStream imageOutputStream = ImageIO.createImageOutputStream(bos);
                gifImageWriter.setOutput(imageOutputStream);
                gifImageWriter.prepareWriteSequence(ir.getStreamMetadata());
                for (IIOImage image : frames) {
                    BufferedImage bufferedImage = (BufferedImage) image.getRenderedImage();
                    try {
                        BufferedImage moosificated = moosificateFrame(bufferedImage, mooseRequest, false);
                        image.setRenderedImage(moosificated);
                    } catch (Exception|jjil.core.Error e) {
                        throw Throwables.propagate(e);
                    }
                    gifImageWriter.writeToSequence(image, null);
                }
                gifImageWriter.endWriteSequence();
                imageOutputStream.close();

                return Optional.of(bos.toByteArray());
            } catch (Throwable e) {
                MooseLogger.logEventForErrorMoosificating(mooseRequest, e);
                MooseLogger.getLogger().log(Level.WARNING, format("Error generating animated gif for url [%s]",
                        mooseRequest.getOriginalImageUrl().toExternalForm()), e);
                return Optional.absent();
            }
        }
    }

    private BufferedImage moosificateFrame(BufferedImage frame, MooseRequest mooseRequest, boolean throwsException)
            throws jjil.core.Error, IOException {
        RgbImage rgbImage = RgbImageJ2se.toRgbImage(frame);
        RgbAvgGray toGray = new RgbAvgGray();
        toGray.push(rgbImage);

        InputStream profileInputStream = MoosificatorApp.class.getResourceAsStream("/profiles/HCSB.txt");
        Gray8DetectHaarMultiScale detectHaar = new Gray8DetectHaarMultiScale(profileInputStream, 1, 30);

        // We either keep the source size if small enough or we cap it to be fewer pixels than our max
        int canvasWidth = frame.getWidth();
        int canvasHeight = frame.getHeight();
        double originalSurface = frame.getWidth() * frame.getHeight();
        if (originalSurface > MAX_IMAGE_SIZE_IN_PIXELS) {
            double resizeFactor = Math.sqrt(MAX_IMAGE_SIZE_IN_PIXELS / originalSurface);
            canvasWidth = (int) (frame.getWidth() * resizeFactor);
            canvasHeight = (int) (frame.getHeight() * resizeFactor);
        }

        BufferedImage combined = deepCopy(frame);
        List<Rect> rectangles = detectHaar.pushAndReturn(toGray.getFront());
        Graphics g = combined.getGraphics();

        g.drawImage(frame, 0, 0, canvasWidth, canvasHeight, null);

        if (!rectangles.isEmpty()) {
            List<Rect> uniqueRectangles = findDistinctFaces(rectangles);
            for (Rect rectangle : uniqueRectangles) {

                if (mooseRequest.isDebug()) {
                    // Add debug rectangle around the face
                    g.drawRect(rectangle.getLeft(), rectangle.getTop(), rectangle.getWidth(), rectangle.getHeight());
                }

                if (!mooseRequest.isDebugOnly()) {
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
        } else if (throwsException) {
            // Overlay a nice NoFaceFound on the image and return that
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
        }

        return combined;
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
        BufferedImage singleFrame = ImageIO.read(mooseRequest.getOriginalImageUrl());
        return moosificateFrame(singleFrame, mooseRequest, true);
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
            if (!outsideAlreadyIncluded(uniqueRectangles, rectangle)) {
                uniqueRectangles.add(rectangle);
            }
        }

        return uniqueRectangles;
    }

    /**
     * @return true if the given rectangle completely inside one of the other rectangles.
     */
    private boolean outsideAlreadyIncluded(List<Rect> selectedRegions, Rect tentativeRegion) {
        for (Rect goodToDate : selectedRegions) {
            if (isInside(goodToDate, tentativeRegion)) {
                return true;
            }
        }

        return false;
    }

    public class AreaComparator implements Comparator<Rect> {
        @Override
        public int compare(Rect o1, Rect o2) {
            return o1.getArea() - o2.getArea();
        }

    }

    public boolean isInside(Rect outer, Rect inner) {
        return outer.getLeft() <= inner.getLeft()
                && outer.getRight() >= inner.getRight()
                && outer.getTop() <= inner.getTop()
                && outer.getBottom() >= inner.getBottom();
    }

    /**
     * Creates a deep copy of an {@link BufferedImage}
     * @param bi    the BufferedImage to copy
     * @return      a deep copy of bi
     */
    static BufferedImage deepCopy(BufferedImage bi) {
        ColorModel cm = bi.getColorModel();
        boolean isAlphaPremultiplied = cm.isAlphaPremultiplied();
        WritableRaster raster = bi.copyData(null);
        return new BufferedImage(cm, raster, isAlphaPremultiplied, null);
    }
}

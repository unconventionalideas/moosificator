package org.sexyideas.moosificator;

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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static java.lang.String.format;

/**
 * Moosificator service. No words necessary.
 *
 * @author alexandre.normand
 */
@Path("moose")
@Singleton
public class MooseResource {
    public static final float MOOSE_HEAD_LEFT_OFFSET = 138.f;
    public static final float MOOSE_HEAD_TOP_OFFSET = 120.f;
    public static final float MOOSE_HEAD_WIDTH = 115.f;
    public static final float MOOSE_HEAD_HEIGHT = 115.f;
    private BufferedImage mooseOverlay;
    private LoadingCache<URL, BufferedImage> imageCache;
    private float mooseProportionRatio;
    private float magnifyingFactor;

    public void initializeIfRequired() {
        // This is an imperfect solution but it should be fine. If multiple requests come in at the same
        // time and there's a race condition, we'll just have wasted a few extra resources for those requests.
        if (this.mooseOverlay == null) {
            try {
                this.mooseOverlay = ImageIO.read(MoosificatorApp.class.getResourceAsStream("/moose/moose.png"));
                this.mooseProportionRatio = (float) this.mooseOverlay.getWidth() / (float) this.mooseOverlay.getHeight();
                this.magnifyingFactor = this.mooseOverlay.getHeight() / MOOSE_HEAD_HEIGHT;

            } catch (IOException e) {
                throw Throwables.propagate(e);
            }

            this.imageCache = CacheBuilder.<URL, BufferedImage>newBuilder()
                    .maximumSize(20)
                    .expireAfterWrite(1, TimeUnit.DAYS)
                    .build(new MoosificatorCacheLoader());
        }
    }

    @GET
    @Produces("image/png")
    public Response moosificate(@QueryParam("image") String sourceImage) {
        initializeIfRequired();

        URL imageUrl;
        try {
            imageUrl = new URL(sourceImage);
        } catch (MalformedURLException e) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(format("%s is not a valid url", sourceImage))
                    .build();
        }

        try {
            final BufferedImage combined = this.imageCache.get(imageUrl);

            StreamingOutput stream = new StreamingOutput() {
                @Override
                public void write(OutputStream os) throws IOException,
                        WebApplicationException {
                    ImageIO.write(combined, "PNG", os);
                }
            };

            return Response.ok(stream).build();
        } catch (Throwable error) {
            throw Throwables.propagate(error);
        }
    }


    public class MoosificatorCacheLoader extends CacheLoader<URL, BufferedImage> {
        @Override
        public BufferedImage load(URL key) throws Exception {
            try {
                return moosificateImage(key);
            } catch (jjil.core.Error error) {
                throw Throwables.propagate(error);
            }
        }
    }


    /**
     * Transforms a source image to a moosificated version of it.
     *
     * @param sourceImageURL URL of the image to moosificate
     * @return the moosificated image
     * @throws jjil.core.Error
     * @throws IOException
     */
    private BufferedImage moosificateImage(URL sourceImageURL) throws jjil.core.Error, IOException {
        BufferedImage sourceImage = ImageIO.read(sourceImageURL);
        RgbImage rgbImage = RgbImageJ2se.toRgbImage(sourceImage);
        RgbAvgGray toGray = new RgbAvgGray();
        toGray.push(rgbImage);

        InputStream profileInputStream = MoosificatorApp.class.getResourceAsStream("/profiles/HCSB.txt");
        Gray8DetectHaarMultiScale detectHaar = new Gray8DetectHaarMultiScale(profileInputStream, 1, 30);

        BufferedImage combined = new BufferedImage(sourceImage.getWidth(), sourceImage.getHeight(),
                BufferedImage.TYPE_INT_ARGB);
        List<Rect> rectangles = detectHaar.pushAndReturn(toGray.getFront());

        Graphics g = combined.getGraphics();
        g.drawImage(sourceImage, 0, 0, null);

        List<Rect> uniqueRectangles = findUniqueRectangles(rectangles);
        for (Rect rectangle : uniqueRectangles) {
            float effectiveHeight = rectangle.getHeight() * this.magnifyingFactor;
            float effectiveWidth = effectiveHeight * this.mooseProportionRatio;

            float effectiveTop = rectangle.getTop() - MOOSE_HEAD_TOP_OFFSET * effectiveHeight / this.mooseOverlay.getHeight();
            float effectiveLeft = rectangle.getLeft() - MOOSE_HEAD_LEFT_OFFSET * effectiveWidth / this.mooseOverlay.getWidth();

            // Add a moose on the original image to overlay that region
            g.drawImage(this.mooseOverlay, (int) effectiveLeft,
                    (int) effectiveTop, (int) effectiveWidth, (int) effectiveHeight, null);

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

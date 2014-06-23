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
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRenderedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class MoosificatorServlet extends HttpServlet {
    public static final float MOOSE_HEAD_LEFT_OFFSET = 138.f;
    public static final float MOOSE_HEAD_TOP_OFFSET = 120.f;
    public static final float MOOSE_HEAD_WIDTH = 115.f;
    public static final float MOOSE_HEAD_HEIGHT = 115.f;
    private BufferedImage mooseOverlay;
    private LoadingCache<URL, BufferedImage> imageCache;
    private float mooseProportionRatio;
    private float magnifyingFactor;


    @Override
    public void init() throws ServletException {
        super.init();
        try {
            this.mooseOverlay = ImageIO.read(MoosificatorServlet.class.getResourceAsStream("/moose/moose.png"));
            this.mooseProportionRatio = (float) this.mooseOverlay.getWidth() / (float) this.mooseOverlay.getHeight();
            this.magnifyingFactor = this.mooseOverlay.getHeight() / MOOSE_HEAD_HEIGHT;

        } catch (IOException e) {
            throw new ServletException("Failed to load moose image to initialize moosificator", e);
        }

        this.imageCache = CacheBuilder.<URL, BufferedImage>newBuilder()
                .maximumSize(20)
                .expireAfterWrite(1, TimeUnit.DAYS)
                .build(new MoosificatorCacheLoader());
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String imageUrlParameter = req.getParameter("image");
        if (imageUrlParameter == null) {
            resp.setStatus(400);
            resp.getWriter().write("Missing paramater: [image]");
            return;
        }

        URL imageUrl;
        try {
            imageUrl = new URL(imageUrlParameter);
        } catch (MalformedURLException e) {
            resp.setStatus(400);
            resp.getWriter().write("Bad URL");
            return;
        }

        try {
            BufferedImage combined = this.imageCache.get(imageUrl);

            resp.setContentType("image/png");
            ImageIO.write(combined, "PNG", resp.getOutputStream());
        } catch (Throwable error) {
            resp.setStatus(500);
            error.printStackTrace(new PrintWriter(resp.getWriter()));
        }
    }

    /**
     * Transforms a source image to a moosificated version of it.
     * @param sourceImageURL
     * @return
     * @throws jjil.core.Error
     * @throws IOException
     */
    private BufferedImage moosificateImage(URL sourceImageURL) throws jjil.core.Error, IOException {
        BufferedImage sourceImage = ImageIO.read(sourceImageURL);
        RgbImage rgbImage = RgbImageJ2se.toRgbImage(sourceImage);
        RgbAvgGray toGray = new RgbAvgGray();
        toGray.push(rgbImage);

        InputStream profileInputStream = MoosificatorServlet.class.getResourceAsStream("/profiles/HCSB.txt");
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

    private static WritableRenderedImage toImage(RgbImage rgb) {
        WritableRenderedImage im = new BufferedImage(
                rgb.getWidth(),
                rgb.getHeight(),
                BufferedImage.TYPE_INT_RGB);
        DataBufferInt dbi = new DataBufferInt(
                rgb.getData(),
                rgb.getHeight() * rgb.getWidth());
        Raster r = Raster.createRaster(
                im.getSampleModel(),
                dbi,
                null);
        im.setData(r);
        return im;
    }

    public class AreaComparator implements Comparator<Rect> {
        @Override
        public int compare(Rect o1, Rect o2) {
            return o1.getArea() - o2.getArea();
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

    public static void main(String[] args) throws Exception {
        Server server = new Server(Integer.valueOf(System.getenv("PORT")));
        ServletContextHandler context = new ServletContextHandler(ServletContextHandler.SESSIONS);
        context.setContextPath("/");
        server.setHandler(context);
        context.addServlet(new ServletHolder(new MoosificatorServlet()), "/*");
        server.start();
        server.join();
    }
}
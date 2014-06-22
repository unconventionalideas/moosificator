package org.sexyideas.moosificator;

import jjil.algorithm.Gray8Rgb;
import jjil.algorithm.RgbAvgGray;
import jjil.core.Image;
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
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.awt.image.Raster;
import java.awt.image.WritableRenderedImage;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.List;

public class MoosificatorServlet extends HttpServlet {
    @Override
    public void init() throws ServletException {
        super.init();
    }

    @Override
    public void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        String imageUrlParameter = req.getParameter("image");
        if (imageUrlParameter == null) {
            resp.setStatus(400);
            return;
        }

        try {
            URL imageUrl = new URL(imageUrlParameter);
            BufferedImage sourceImage = ImageIO.read(imageUrl);
            RgbImage rgbImage = RgbImageJ2se.toRgbImage(sourceImage);
            RgbAvgGray toGray = new RgbAvgGray();
            toGray.push(rgbImage);

            InputStream profileInputStream = MoosificatorServlet.class.getResourceAsStream("/profiles/HCSB.txt");
            Gray8DetectHaarMultiScale detectHaar = new Gray8DetectHaarMultiScale(profileInputStream, 1, 40);

            List<Rect> rectangles = detectHaar.pushAndReturn(toGray.getFront());
            for (Rect rectangle : rectangles) {
                // Add a moose on the original image to overlay that region
            }

            // step #6 - retrieve resulting face detection mask
            Image i = detectHaar.getFront();
            // finally convert back to RGB finalImage to write out to .jpg file
            Gray8Rgb g2rgb = new Gray8Rgb();
            g2rgb.push(i);

            RgbImage finalImage = (RgbImage) g2rgb.getFront();
            resp.setContentType("image/jpeg");
            ImageIO.write(toImage(finalImage), "JPG", resp.getOutputStream());
        } catch (jjil.core.Error error) {
            resp.setStatus(500);
            error.printStackTrace(new PrintWriter(resp.getWriter()));
        }
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
package org.sexyideas.moosificator;

import java.net.URL;
import java.util.Objects;

/**
 * @author spencer.firestone
 */
public final class MooseRequest {

    private URL originalImageUrl;
    private boolean debug;
    private URL overlayImageUrl;
    private String overlayImageName;

    private MooseRequest() {
    }

    public static MooseRequestBuilder newBuilder() {
        return new MooseRequestBuilder();
    }

    public URL getOriginalImageUrl() {
        return originalImageUrl;
    }

    public URL getOverlayImageUrl() {
        return overlayImageUrl;
    }

    public String getOverlayImageName() {
        return overlayImageName;
    }

    public boolean isDebug() {
        return debug;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != getClass()) {
            return false;
        }
        if (obj == this) {
            return true;
        }
        MooseRequest rhs = (MooseRequest) obj;
        return Objects.equals(this.originalImageUrl, rhs.originalImageUrl)
                && this.debug == rhs.debug
                && Objects.equals(this.overlayImageUrl, rhs.overlayImageUrl)
                && Objects.equals(this.overlayImageName, rhs.overlayImageName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(this.originalImageUrl, this.debug, this.overlayImageUrl, this.overlayImageName);
    }

    public static class MooseRequestBuilder {

        private MooseRequest mooseRequest = new MooseRequest();

        public MooseRequestBuilder withOriginalImageUrl(URL url) {
            this.mooseRequest.originalImageUrl = url;
            return this;
        }

        public MooseRequestBuilder withDebug(String debug) {
            this.mooseRequest.debug = debug != null && ("true".equalsIgnoreCase(debug) || "y".equalsIgnoreCase(debug));
            return this;
        }

        public MooseRequestBuilder withOverlayImage(URL imageUrl) {
            this.mooseRequest.overlayImageUrl = imageUrl;
            this.mooseRequest.overlayImageName = null;
            return this;
        }

        public MooseRequestBuilder withOverlayImage(String imageName) {
            this.mooseRequest.overlayImageName = imageName;
            this.mooseRequest.overlayImageUrl = null;

            return this;
        }

        public MooseRequest build() {
            return this.mooseRequest;
        }
    }
}

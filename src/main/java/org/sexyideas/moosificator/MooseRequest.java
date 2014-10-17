package org.sexyideas.moosificator;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.Objects;

/**
 * @author spencer.firestone
 */
public final class MooseRequest {

    public enum RequestType {
        ANTLER, // Add antlers to a face
        MOOSE, // Overlay a moose on a face
        NAMED, // Overlay a different, named moose on a face
        RE_MOOSE // Extract a face from an image, overlay it on a face, and antlerificate it
    }

    private URL originalImageUrl;
    private boolean debug;
    private URL overlayImageUrl;
    private String overlayImageName;
    private RequestType requestType;

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

    public RequestType getRequestType() {
        return requestType;
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
        private String originalImageUrlString;
        private String overlayImageUrlString;

        private MooseRequest mooseRequest = new MooseRequest();

        public MooseRequestBuilder withRequestType(RequestType requestType) {
            this.mooseRequest.requestType = requestType;
            return this;
        }

        public MooseRequestBuilder withOriginalImageUrl(String url) {
            this.originalImageUrlString = url;
            return this;
        }

        public MooseRequestBuilder withDebug(String debug) {
            this.mooseRequest.debug = debug != null && ("true".equalsIgnoreCase(debug) || "y".equalsIgnoreCase(debug));
            return this;
        }

        public MooseRequestBuilder withOverlayImageUrl(String overlayImageUrlString) {
            this.overlayImageUrlString = overlayImageUrlString;
            return this;
        }

        public MooseRequestBuilder withOverlayImageName(String overlayImageName) {
            this.mooseRequest.overlayImageName = overlayImageName;

            return this;
        }

        public MooseRequest build() throws MooseException {
            if (this.mooseRequest.requestType == null) {
                throw new MooseException(MooseException.MooseExceptionType.MISSING_REQUEST_TYPE);
            }

            if (this.originalImageUrlString == null) {
                throw new MooseException(MooseException.MooseExceptionType.MISSING_SOURCE_URL);
            }

            try {
                this.mooseRequest.originalImageUrl = new URL(originalImageUrlString);
                MooseLogger.logEventForMooseRetrieval(this.mooseRequest.originalImageUrl);
            } catch (MalformedURLException e) {
                throw new MooseException(MooseException.MooseExceptionType.INVALID_SOURCE_URL);
            }

            switch(mooseRequest.requestType) {
                case ANTLER:
                    // No other validations
                    break;
                case MOOSE:
                    // No other validations
                    break;
                case NAMED:
                    if (mooseRequest.overlayImageName == null) {
                        throw new MooseException(MooseException.MooseExceptionType.MISSING_MOOSE_NAME);
                    }
                    break;
                case RE_MOOSE:
                    if (mooseRequest.getOverlayImageUrl() == null) {
                        throw new MooseException(MooseException.MooseExceptionType.MISSING_RE_MOOSE_URL);
                    }
                    try {
                        this.mooseRequest.overlayImageUrl = new URL(this.overlayImageUrlString);
                        MooseLogger.logEventForMooseRetrieval(this.mooseRequest.overlayImageUrl);
                    } catch (MalformedURLException e) {
                        throw new MooseException(MooseException.MooseExceptionType.INVALID_RE_MOOSE_URL);
                    }
                    break;
            }
            return this.mooseRequest;
        }
    }
}

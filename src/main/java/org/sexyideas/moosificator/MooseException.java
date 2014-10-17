package org.sexyideas.moosificator;

/**
 * @author spencer.firestone
 */
public class MooseException extends Exception {

    public enum MooseExceptionType {
        MISSING_REQUEST_TYPE("Missing moose request type"),
        MISSING_SOURCE_URL("Missing source URL"),
        INVALID_SOURCE_URL("Invalid source URL"),
        MISSING_MOOSE_NAME("Missing moose name"),
        INVALID_MOOSE_NAME("Invalid moose name"),
        MISSING_RE_MOOSE_URL("Missing re-moose URL"),
        INVALID_RE_MOOSE_URL("Invalid re-moose URL");

        private String message;

        private MooseExceptionType(String message) {
            this.message = message;
        }

        public String getMessage() {
            return message;
        }
    }

    private MooseExceptionType type;

    public MooseException(MooseExceptionType type) {
        super(type.getMessage());
        this.type = type;
    }

    public MooseExceptionType getMooseExceptionType() {
        return type;
    }
}

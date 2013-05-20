package org.jboss.pressgang.ccms.utils.common;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionUtilities {
    /**
     * A standard function to get the stack trace from a
     * thrown Exception
     *
     * @param ex The thrown exception
     * @return The stack trace from the exception
     */
    public static String getStackTrace(final Exception ex) {
        final StringWriter sw = new StringWriter();
        final PrintWriter pw = new PrintWriter(sw, true);
        ex.printStackTrace(pw);
        pw.flush();
        sw.flush();
        return sw.toString();
    }
}

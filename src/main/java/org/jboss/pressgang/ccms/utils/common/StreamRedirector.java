/*
  Copyright 2011-2014 Red Hat, Inc

  This file is part of PressGang CCMS.

  PressGang CCMS is free software: you can redistribute it and/or modify
  it under the terms of the GNU Lesser General Public License as published by
  the Free Software Foundation, either version 3 of the License, or
  (at your option) any later version.

  PressGang CCMS is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU Lesser General Public License for more details.

  You should have received a copy of the GNU Lesser General Public License
  along with PressGang CCMS.  If not, see <http://www.gnu.org/licenses/>.
*/

package org.jboss.pressgang.ccms.utils.common;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is used to monitor the various streams managed by a Process in
 * order to prevent a buffer overrun for long running Processes.
 */
class StreamRedirector extends Thread {
    private static final Logger LOG = LoggerFactory.getLogger(StreamRedirector.class);

    /**
     * A stream to poll for data
     */
    private InputStream is;
    /**
     * A stream to print the data polled from is
     */
    private OutputStream os;
    /**
     * A collection of strings that should not be printed
     */
    private List<String> doNotPrintStrings;

    /**
     * @param is The stream to poll for data
     */
    public StreamRedirector(final InputStream is) {
        this(is, null, new ArrayList<String>());
    }

    /**
     * @param is       The stream to poll for data
     * @param redirect A stream to print the data that has been read from is
     */
    public StreamRedirector(final InputStream is, final OutputStream redirect) {
        this(is, redirect, new ArrayList<String>());
    }

    /**
     * @param is                The stream to poll for data
     * @param redirect          A stream to print the data that has been read from is
     * @param doNotPrintStrings A collection of strings that should not be printed or redirected (like passwords etc)
     */
    public StreamRedirector(final InputStream is, final OutputStream redirect, final List<String> doNotPrintStrings) {
        this.is = is;
        this.os = redirect;
        this.doNotPrintStrings = doNotPrintStrings;
    }

    @Override
    public void run() {
        try {
            final PrintWriter pw = os != null ? new PrintWriter(os) : null;
            final InputStreamReader isr = new InputStreamReader(is);
            final BufferedReader br = new BufferedReader(isr);

            String line = null;

            while ((line = br.readLine()) != null) {
                String sanatizedLine = line;
                for (String replace : doNotPrintStrings)
                    sanatizedLine = sanatizedLine.replace(replace, "****");
                System.out.println(sanatizedLine);

                if (pw != null) {
                    pw.println(line);
                }
            }

            if (pw != null) pw.flush();
        } catch (final IOException ex) {
            LOG.debug("Unable to read from Input Stream", ex);
        }
    }
}

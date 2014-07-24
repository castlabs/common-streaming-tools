/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.castlabs.csf;

import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.InputStream;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public abstract class AbstractCommand implements Command {
    @Option(name = "--verbose", aliases = "-v", usage = "use switch to produce log output")
    protected boolean verbose = false;

    protected Logger setupLogger() {
        Logger logger = Logger.getLogger("dash");
        InputStream stream;
        if (verbose) {
            stream = AbstractCommand.class.getResourceAsStream("/log-verbose.properties");
        } else {
            stream = AbstractCommand.class.getResourceAsStream("/log.properties");
        }
        try {
            LogManager.getLogManager().readConfiguration(stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        logger.setLevel(Level.FINE);
        logger.addHandler(new java.util.logging.ConsoleHandler());
        logger.setUseParentHandlers(false);

        return logger;
    }

}

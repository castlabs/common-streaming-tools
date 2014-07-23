package com.castlabs.csf;

import java.io.FileNotFoundException;
import java.io.IOException;

/**
 * Created by user on 23.07.2014.
 */
public interface Command {
    public int run() throws Exception;

    public class CommandAbortException extends Exception {
        public CommandAbortException() {
        }

        public CommandAbortException(String message) {
            super(message);
        }


    }
}

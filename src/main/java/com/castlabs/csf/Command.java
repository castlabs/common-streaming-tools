/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.castlabs.csf;

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

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.castlabs.csf.manifest;

import com.castlabs.csf.AbstractCommand;
import mpegDashSchemaMpd2011.MPDDocument;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.spi.FileOptionHandler;

import java.io.File;
import java.util.List;
import java.util.logging.Logger;


public class CreateManifestForFileset extends AbstractCommand {
    @Argument(required = true, multiValued = true, handler = FileOptionHandler.class, usage = "MP4 and bitstream input files", metaVar = "vid1.mp4, vid2.mp4, aud1.mp4, aud2.ec3 ...")
    protected List<File> files;
    Logger logger;

    public int run() throws Exception {
        logger = setupLogger();
        MPDDocument mpd = new SegmentListManifestWriterImpl(files).getManifest();
        mpd.save(new File("Manifest.xml"));
        return 0;
    }
}

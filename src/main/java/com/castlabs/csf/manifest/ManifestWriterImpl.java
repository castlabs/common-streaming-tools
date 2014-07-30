/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.castlabs.csf.manifest;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.MovieHeaderBox;
import com.coremedia.iso.boxes.TrackHeaderBox;
import com.coremedia.iso.boxes.fragment.MovieExtendsHeaderBox;
import com.googlecode.mp4parser.util.Path;
import mpegDashSchemaMpd2011.AdaptationSetType;
import mpegDashSchemaMpd2011.PeriodType;
import mpegDashSchemaMpd2011.RepresentationType;
import mpegDashSchemaMpd2011.SegmentBaseType;
import org.apache.xmlbeans.GDuration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

import static com.castlabs.csf.manifest.ManifestHelper.createRepresentation;


/**
 * Creates a single SIDX manifest.
 */
public class ManifestWriterImpl extends AbstractManifestWriter {
    Map<String, IsoFile> primaryVideo = new HashMap<String, IsoFile>();
    Map<String, IsoFile> secondaryVideo = new HashMap<String, IsoFile>();
    Map<String, IsoFile> mainAudio = new HashMap<String, IsoFile>();
    Map<String, IsoFile> secondaryAudio = new HashMap<String, IsoFile>();
    Map<String, IsoFile> tertiaryAudio = new HashMap<String, IsoFile>();
    Map<String, IsoFile> mainSubtitle = new HashMap<String, IsoFile>();
    Map<String, IsoFile> secondarySubtitle = new HashMap<String, IsoFile>();
    Logger logger;

    public ManifestWriterImpl(List<File> files, Logger logger) throws IOException {
        this.logger = logger;
        for (File file : files) {
            IsoFile uv = new IsoFile(file.getAbsolutePath());
            assert Path.getPaths(uv, "moov[0]/trak").size() == 1 : "Only one track per file allowed";
            TrackHeaderBox tkhd = (TrackHeaderBox) Path.getPath(uv, "moov[0]/trak[0]/tkhd[0]");
            long t = tkhd.getTrackId();
            String template = "Adding %30s to %16s - trackId = %d";
            if (t < 50) {
                primaryVideo.put(file.getName(), uv);
                logger.info(String.format(template, file.getName(), "primary videos", t));
            } else if (t < 100) {
                secondaryVideo.put(file.getName(), uv);
                logger.info(String.format(template, file.getName(), "secondary videos", t));
            } else if (t < 1000) {
                mainAudio.put(file.getName(), uv);
                logger.info(String.format(template, file.getName(), "main audio", t));
            } else if (t < 2000) {
                secondaryAudio.put(file.getName(), uv);
                logger.info(String.format(template, file.getName(), "secondary audio", t));
            } else if (t < 10000) {
                tertiaryAudio.put(file.getName(), uv);
                logger.info(String.format(template, file.getName(), "tertiary audio", t));
            } else if (t < 11000) {
                mainSubtitle.put(file.getName(), uv);
                logger.info(String.format(template, file.getName(), "main subtitles", t));
            } else {
                secondarySubtitle.put(file.getName(), uv);
                logger.info(String.format(template, file.getName(), "secondary subtitles", t));
            }
        }

    }


    protected void createPeriod(PeriodType periodType) throws IOException {

        double maxDurationInSeconds = -1;

        maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, primaryVideo, 1);
        if (!secondaryVideo.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, secondaryVideo, 2);
        }
        if (!mainAudio.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, mainAudio, 5);
        }
        if (!secondaryAudio.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, secondaryAudio, 6);
        }
        if (!tertiaryAudio.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, tertiaryAudio, 7);
        }
        if (!mainSubtitle.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, mainSubtitle, 3);
        }
        if (!secondarySubtitle.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, secondarySubtitle, 4);
        }
        //adaptationSetVid.setPar();

        periodType.setDuration(new GDuration(
                1, 0, 0, 0, (int) (maxDurationInSeconds / 3600),
                (int) ((maxDurationInSeconds % 3600) / 60),
                (int) (maxDurationInSeconds % 60), BigDecimal.ZERO));


    }

    long getBitrate(IsoFile file) {
        MovieExtendsHeaderBox mehd = (MovieExtendsHeaderBox) Path.getPath(file, "/moov[0]/mvex[0]/mehd[0]");
        MovieHeaderBox mvhd = (MovieHeaderBox) Path.getPath(file, "/moov[0]/mvhd[0]");
        double durationInSeconds = (double) mehd.getFragmentDuration() / mvhd.getTimescale();
        return (long) (file.getSize() * 8 / durationInSeconds);
    }

    private double createAdaptationSet(PeriodType periodType, double maxDurationInSeconds, Map<String, IsoFile> files, int group) {
        Set<Long> trackIds = new HashSet<Long>();
        for (IsoFile isoFile : files.values()) {
            // get all trackIDs
            TrackHeaderBox tkhd = (TrackHeaderBox) Path.getPath(isoFile, "moov[0]/trak[0]/tkhd[0]");
            trackIds.add(tkhd.getTrackId());
        }
        for (Long trackId : trackIds) {
            // one adaptationset per trackId - iterate over all trackId

            Map<String, IsoFile> tracksWithCurrentTracksId = new HashMap<String, IsoFile>();
            logger.info("Starting AdaptationSet");
            for (Map.Entry<String, IsoFile> stringIsoFileEntry : files.entrySet()) {
                // find all tracks with current track id
                TrackHeaderBox tkhd = (TrackHeaderBox) Path.getPath(stringIsoFileEntry.getValue(), "moov[0]/trak[0]/tkhd[0]");
                if (tkhd.getTrackId() == trackId) {
                    tracksWithCurrentTracksId.put(stringIsoFileEntry.getKey(), stringIsoFileEntry.getValue());
                    logger.info(String.format("-- Adding %25s to AdaptationSet with groupId %d", stringIsoFileEntry.getKey(), group));
                }
            }
            logger.info("AdaptationSet Done");

            AdaptationSetType adaptationSet = createAdaptationSet(periodType, tracksWithCurrentTracksId.values());
            adaptationSet.setId(trackId);
            adaptationSet.setGroup(group);
            for (Map.Entry<String, IsoFile> e : tracksWithCurrentTracksId.entrySet()) {
                IsoFile oneTrackFile = e.getValue();
                String filename = e.getKey();
                MovieHeaderBox mvhd = (MovieHeaderBox) Path.getPath(oneTrackFile, "/moov[0]/mvhd[0]");
                MovieExtendsHeaderBox mehd = (MovieExtendsHeaderBox) Path.getPath(oneTrackFile, "/moov[0]/mvex[0]/mehd[0]");
                RepresentationType representation = createRepresentation(adaptationSet, oneTrackFile);
                SegmentBaseType segBaseType = representation.addNewSegmentBase();
                createInitialization(segBaseType.addNewInitialization(), oneTrackFile);

                segBaseType.setTimescale(mvhd.getTimescale());
                segBaseType.setIndexRangeExact(true);
                Box sidx = Path.getPath(oneTrackFile, "/sidx[0]");
                segBaseType.setIndexRange(sidx.getOffset() + "-" + (sidx.getOffset() + sidx.getSize() - 1));

                representation.setBandwidth(getBitrate(oneTrackFile));
                representation.addNewBaseURL().setStringValue(filename);


                double durationInSeconds = (double) mehd.getFragmentDuration() / mvhd.getTimescale();
                maxDurationInSeconds = Math.max(maxDurationInSeconds, durationInSeconds);


            }
        }
        return maxDurationInSeconds;
    }

}

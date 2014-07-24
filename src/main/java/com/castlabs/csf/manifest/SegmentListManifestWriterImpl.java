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
import com.coremedia.iso.boxes.fragment.MovieFragmentBox;
import com.coremedia.iso.boxes.fragment.TrackRunBox;
import com.googlecode.mp4parser.util.Path;
import mpegDashSchemaMpd2011.*;
import org.apache.xmlbeans.GDuration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import static com.castlabs.csf.manifest.ManifestHelper.createRepresentation;


/**
 * Creates a single SIDX manifest.
 */
public class SegmentListManifestWriterImpl extends AbstractManifestWriter {
    Map<String, IsoFile> primaryVideo = new HashMap<String, IsoFile>();
    Map<String, IsoFile> secondaryVideo = new HashMap<String, IsoFile>();
    Map<String, IsoFile> mainAudio = new HashMap<String, IsoFile>();
    Map<String, IsoFile> secondaryAudio = new HashMap<String, IsoFile>();
    Map<String, IsoFile> tertiaryAudio = new HashMap<String, IsoFile>();
    Map<String, IsoFile> mainSubtitle = new HashMap<String, IsoFile>();
    Map<String, IsoFile> secondarySubtitle = new HashMap<String, IsoFile>();


    public SegmentListManifestWriterImpl(List<File> files) throws IOException {

        for (File file : files) {
            IsoFile uv = new IsoFile(file.getAbsolutePath());
            assert Path.getPaths(uv, "moov[0]/trak").size() == 1 : "Only one track per file allowed";
            TrackHeaderBox tkhd = (TrackHeaderBox) Path.getPath(uv, "moov[0]/trak[0]/tkhd[0]");
            long t = tkhd.getTrackId();
            if (t < 50) {
                primaryVideo.put(file.getName(), uv);
            } else if (t < 100) {
                secondaryVideo.put(file.getName(), uv);
            } else if (t < 1000) {
                mainAudio.put(file.getName(), uv);
            } else if (t < 2000) {
                secondaryAudio.put(file.getName(), uv);
            } else if (t < 10000) {
                tertiaryAudio.put(file.getName(), uv);
            } else if (t < 11000) {
                mainSubtitle.put(file.getName(), uv);
            } else {
                secondarySubtitle.put(file.getName(), uv);
            }
        }

    }


    protected void createPeriod(PeriodType periodType) throws IOException {

        double maxDurationInSeconds = -1;

        maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, primaryVideo);
        if (!secondaryVideo.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, secondaryVideo);
        }
        if (!mainAudio.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, mainAudio);
        }
        if (!secondaryAudio.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, secondaryAudio);
        }
        if (!tertiaryAudio.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, tertiaryAudio);
        }
        if (!mainSubtitle.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, mainSubtitle);
        }
        if (!secondarySubtitle.isEmpty()) {
            maxDurationInSeconds = createAdaptationSet(periodType, maxDurationInSeconds, secondarySubtitle);
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

    private double createAdaptationSet(PeriodType periodType, double maxDurationInSeconds, Map<String, IsoFile> files) {

        TrackHeaderBox tkhd = (TrackHeaderBox) Path.getPath(files.values().iterator().next(), "moov[0]/trak[0]/tkhd[0]");
        long t = tkhd.getTrackId();

        for (IsoFile oneTrackFile : files.values()) {
            MovieExtendsHeaderBox mehd = (MovieExtendsHeaderBox) Path.getPath(oneTrackFile, "/moov[0]/mvex[0]/mehd[0]");
            MovieHeaderBox mvhd = (MovieHeaderBox) Path.getPath(oneTrackFile, "/moov[0]/mvhd[0]");
            double durationInSeconds = (double) mehd.getFragmentDuration() / mvhd.getTimescale();
            maxDurationInSeconds = Math.max(maxDurationInSeconds, durationInSeconds);
        }
        AdaptationSetType adaptationSet = createAdaptationSet(periodType, files.values());
        adaptationSet.setId(t);

        for (Map.Entry<String, IsoFile> e : files.entrySet()) {
            IsoFile oneTrackFile = e.getValue();
            String filename = e.getKey();
            RepresentationType representation = createRepresentation(adaptationSet, oneTrackFile);
            MovieExtendsHeaderBox mehd = (MovieExtendsHeaderBox) Path.getPath(oneTrackFile, "/moov[0]/mvex[0]/mehd[0]");
            MovieHeaderBox mvhd = (MovieHeaderBox) Path.getPath(oneTrackFile, "/moov[0]/mvhd[0]");


            representation.setBandwidth(getBitrate(oneTrackFile));
            representation.addNewBaseURL().setStringValue(filename);
            long offset = 0;


            SegmentListType segmentList = representation.addNewSegmentList();
            segmentList.setTimescale(mvhd.getTimescale());
            SegmentTimelineType segmentTimeline = segmentList.addNewSegmentTimeline();
            createInitialization(segmentList.addNewInitialization(), oneTrackFile);
            long time = 0;

            Iterator<Box> boxes = oneTrackFile.getBoxes().iterator();
            while (boxes.hasNext()) {
                Box b = boxes.next();
                if ("moof".equals(b.getType())) {

                    SegmentTimelineType.S s = segmentTimeline.addNewS();
                    MovieFragmentBox moof = (MovieFragmentBox) b;
                    assert moof.getTrackRunBoxes().size() == 1 : "Ouch - doesn't with mutiple trun";

                    TrackRunBox trun = moof.getTrackRunBoxes().get(0);
                    long segmentDuration = 0;
                    for (TrackRunBox.Entry entry : trun.getEntries()) {
                        segmentDuration += entry.getSampleDuration();
                    }
                    s.setD((BigInteger.valueOf(segmentDuration)));
                    s.setT(BigInteger.valueOf(time));
                    time += segmentDuration;

                    SegmentURLType segmentURL = segmentList.addNewSegmentURL();
                    Box mdat = boxes.next();
                    segmentURL.setMediaRange(
                            String.format("%s-%s",
                                    offset, offset + moof.getSize() + mdat.getSize())
                    );

                    offset += moof.getSize() + mdat.getSize();
                } else {
                    offset += b.getSize();
                }
            }

        }
        return maxDurationInSeconds;
    }

}

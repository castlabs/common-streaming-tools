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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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


    public ManifestWriterImpl(List<File> files) throws IOException {

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
        return maxDurationInSeconds;
    }

}

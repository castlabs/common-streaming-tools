/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.castlabs.csf.manifest;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.*;
import com.coremedia.iso.boxes.fragment.*;
import com.googlecode.mp4parser.boxes.basemediaformat.TrackEncryptionBox;
import com.googlecode.mp4parser.util.Path;
import mpegCenc2013.DefaultKIDAttribute;
import mpegDashSchemaMpd2011.*;
import org.apache.xmlbeans.GDuration;

import java.io.File;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.castlabs.csf.manifest.ManifestHelper.createRepresentation;


/**
 * Creates a single SIDX manifest.
 */
public class ManifestWriterImpl {
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
                logger.info(String.format(template, file.getName(), "primary video", t));
            } else if (t < 100) {
                secondaryVideo.put(file.getName(), uv);
                logger.info(String.format(template, file.getName(), "secondary video", t));
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
                logger.info(String.format(template, file.getName(), "main subtitle", t));
            } else {
                secondarySubtitle.put(file.getName(), uv);
                logger.info(String.format(template, file.getName(), "secondary subtitle", t));
            }
        }


    }


    protected Collection<Container> getAllSingleTrackFiles() {
        Set<Container> containers = new HashSet<Container>();
        containers.addAll(primaryVideo.values());
        containers.addAll(secondaryVideo.values());
        containers.addAll(mainAudio.values());
        containers.addAll(secondaryAudio.values());
        containers.addAll(tertiaryAudio.values());
        containers.addAll(mainSubtitle.values());
        containers.addAll(secondarySubtitle.values());
        return containers;
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

    protected void createInitialization(URLType urlType, IsoFile isoFile) {
        long offset = 0;
        for (Box box : isoFile.getBoxes()) {
            if ("moov".equals(box.getType())) {
                urlType.setRange(String.format("%s-%s", offset, offset + box.getSize() - 1));
                break;
            }
            offset += box.getSize();
        }
    }

    protected AdaptationSetType createAdaptationSet(PeriodType periodType, Collection<IsoFile> files) {
        UUID keyId = null;
        String language = null;
        for (IsoFile track : files) {
            TrackEncryptionBox tenc = (TrackEncryptionBox)
                    Path.getPath(track, "/moov[0]/trak[0]/mdia[0]/minf[0]/stbl[0]/stsd[0]/encv[0]/sinf[0]/schi[0]/tenc[0]");
            if (tenc != null) {
                if (keyId != null && !keyId.equals(UUID.fromString(tenc.getDefault_KID()))) {
                    throw new RuntimeException("The ManifestWriter cannot deal with more than ONE cek per adaptation set.");
                }
                keyId = UUID.fromString(tenc.getDefault_KID());
            }
            MediaHeaderBox mdhd = (MediaHeaderBox) Path.getPath(track, "/moov[0]/trak[0]/mdia[0]/mdhd[0]");
            if (language != null && !language.endsWith(mdhd.getLanguage())) {
                throw new RuntimeException("The ManifestWriter cannot deal with more than ONE language " +
                        "per adaptation set. (" + language + " vs. " + mdhd.getLanguage() + ")");
            }

            language = mdhd.getLanguage();
        }


        AdaptationSetType adaptationSet = periodType.addNewAdaptationSet();
        adaptationSet.setSegmentAlignment(true);
        adaptationSet.setStartWithSAP(1);
        adaptationSet.setLang(language);
        adaptationSet.setBitstreamSwitching(true);
        String handler = ((HandlerBox) Path.getPath(files.iterator().next(), "/moov[0]/trak[0]/mdia[0]/hdlr[0]")).getHandlerType();
        if (handler.equals("soun")) {
//            adaptationSet.setMimeType("video/vnd.dece.audio");
            adaptationSet.setMimeType("audio/mp4");
        } else if (handler.equals("vide")) {
//            adaptationSet.setMimeType("video/vnd.dece.video");
            adaptationSet.setMimeType("video/mp4");
        } else if (handler.equals("subt")) {
            adaptationSet.setMimeType("video/vnd.dece.ttml+xml");
        } else {
            throw new RuntimeException("Don't know what to do with handler type = " + handler);
        }

        if (keyId != null) {
            DescriptorType contentProtection = adaptationSet.addNewContentProtection();
            final DefaultKIDAttribute defaultKIDAttribute = DefaultKIDAttribute.Factory.newInstance();
            defaultKIDAttribute.setDefaultKID(Collections.singletonList(keyId.toString()));
            contentProtection.set(defaultKIDAttribute);
            contentProtection.setSchemeIdUri("urn:mpeg:dash:mp4protection:2011");
            contentProtection.setValue("cenc");
        }
        return adaptationSet;
    }

    public MPDDocument getManifest() throws IOException {

        MPDDocument mdd = MPDDocument.Factory.newInstance();
        MPDtype mpd = mdd.addNewMPD();
        PeriodType periodType = mpd.addNewPeriod();
        periodType.setId("0");
        periodType.setStart(new GDuration(1, 0, 0, 0, 0, 0, 0, BigDecimal.ZERO));

        ProgramInformationType programInformationType = mpd.addNewProgramInformation();
        programInformationType.setMoreInformationURL("www.castLabs.com");


        createPeriod(periodType);


        mpd.setProfiles("urn:mpeg:dash:profile:isoff-on-demand:2011");
        mpd.setType(PresentationType.STATIC); // no mpd update strategy implemented yet, could be dynamic
        mpd.setMinBufferTime(getMinBufferTime());
        mpd.setMediaPresentationDuration(periodType.getDuration());

        return mdd;
    }


    class Buffer {
        final long bandwidth;
        final long timescale;

        public Buffer(long bandwidth, long timescale) {
            this.bandwidth = bandwidth;
            this.timescale = timescale;
        }

        long currentBufferFullness = 0;
        long minBufferFullness = 0;

        void simPlayback(long size, long videoTime) {
            currentBufferFullness -= size;
            currentBufferFullness += (double) videoTime / timescale * bandwidth / 8;
            if (currentBufferFullness < minBufferFullness) {
                minBufferFullness = currentBufferFullness;
            }

        }

    }

    long getBitrate(Container file) {
        MovieExtendsHeaderBox mehd = (MovieExtendsHeaderBox) Path.getPath(file, "/moov[0]/mvex[0]/mehd[0]");
        MovieHeaderBox mvhd = (MovieHeaderBox) Path.getPath(file, "/moov[0]/mvhd[0]");
        double durationInSeconds = (double) mehd.getFragmentDuration() / mvhd.getTimescale();

        long size = 0;
        for (Box box : file.getBoxes()) {
            size += box.getSize();
        }

        return (long) (size * 8 / durationInSeconds);
    }


    public GDuration getMinBufferTime() {
        int requiredTimeInS = 0;
        for (Container c : getAllSingleTrackFiles()) {
            long bitrate = getBitrate(c);
            long timescale = ((MediaHeaderBox) Path.getPath(c, "/moov[0]/trak[0]/mdia[0]/mdhd[0]")).getTimescale();
            long requiredBuffer = 0;
            Iterator<Box> iterator = c.getBoxes().iterator();
            while (iterator.hasNext()) {

                Box moofCand = iterator.next();
                if (!moofCand.getType().equals("moof")) {
                    continue;
                }
                MovieFragmentBox moof = (MovieFragmentBox) moofCand;
                Box mdat = iterator.next();

                Buffer currentBuffer = new Buffer(bitrate, timescale);
                currentBuffer.simPlayback(moof.getSize(), 0);
                for (TrackRunBox trun : moof.getTrackRunBoxes()) {
                    for (TrackRunBox.Entry entry : trun.getEntries()) {


                        long sampleDuration;
                        if (trun.isSampleDurationPresent()) {
                            sampleDuration = trun.getEntries().get(0).getSampleDuration();
                        } else {
                            TrackFragmentHeaderBox tfhd = (TrackFragmentHeaderBox) Path.getPath(moof, "traf[0]/tfhd[0]");
                            if (tfhd.hasDefaultSampleDuration()) {
                                sampleDuration = tfhd.getDefaultSampleDuration();
                            } else {
                                TrackExtendsBox trex = (TrackExtendsBox) Path.getPath(c, "/moov[0]/mvex[0]/trex[0]");
                                sampleDuration = trex.getDefaultSampleDuration();
                            }
                        }


                        long size;
                        if (trun.isSampleSizePresent()) {
                            size = entry.getSampleSize();
                        } else {
                            TrackFragmentHeaderBox tfhd = (TrackFragmentHeaderBox) Path.getPath(moof, "traf[0]/tfhd[0]");
                            if (tfhd.hasDefaultSampleSize()) {
                                size = tfhd.getDefaultSampleSize();
                            } else {
                                TrackExtendsBox trex = (TrackExtendsBox) Path.getPath(c, "/moov[0]/mvex[0]/trex[0]");
                                size = trex.getDefaultSampleSize();
                            }
                        }
                        currentBuffer.simPlayback(size, sampleDuration);
                    }

                }
                requiredBuffer = Math.max(requiredBuffer, -(currentBuffer.minBufferFullness + currentBuffer.currentBufferFullness));

            }
            logger.log(Level.INFO, c.toString() + " has minBufferTime of " + Math.ceil((double) requiredBuffer / (bitrate / 8)));
            requiredTimeInS = (int) Math.max(requiredTimeInS, Math.ceil((double) requiredBuffer / (bitrate / 8)));
        }
        return new GDuration(1, 0, 0, 0, 0, 0, requiredTimeInS, BigDecimal.ZERO);
    }

}

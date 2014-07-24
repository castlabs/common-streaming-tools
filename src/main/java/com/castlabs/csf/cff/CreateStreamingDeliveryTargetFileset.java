/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.castlabs.csf.cff;

import com.castlabs.csf.AbstractCommand;
import com.coremedia.iso.boxes.Container;
import com.coremedia.iso.boxes.OriginalFormatBox;
import com.coremedia.iso.boxes.sampleentry.AbstractSampleEntry;
import com.googlecode.mp4parser.FileDataSourceImpl;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.FragmentIntersectionFinder;
import com.googlecode.mp4parser.authoring.container.mp4.MovieCreator;
import com.googlecode.mp4parser.authoring.tracks.*;
import org.apache.commons.io.FilenameUtils;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.spi.FileOptionHandler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;


public class CreateStreamingDeliveryTargetFileset extends AbstractCommand {
    @Argument(required = true, multiValued = true, handler = FileOptionHandler.class, usage = "MP4 and bitstream input files", metaVar = "vid1.mp4, vid2.mp4, aud1.mp4, aud2.ec3 ...")
    protected List<File> inputFiles;
    Logger logger;
    @Option(name = "--codec", aliases = {"-c"})
    String codec;

    @Option(name = "--profile", aliases = {"-p"}, required = true)
    String profile;

    @Option(name = "--track-id", aliases = {"-t"})
    long trackId = -1;


    @Override
    public int run() throws Exception {
        logger = setupLogger();
        Map<Track, String> trackOriginalFilename = setupTracks();
        FragmentIntersectionFinder intersectionFinder = getFragmentStartSamples(trackOriginalFilename);
        Map<Track, String> filenames = generateFilenames(trackOriginalFilename);


        StreamingDeliveryTargetMp4Builder mp4Builder = new StreamingDeliveryTargetMp4Builder();
        mp4Builder.setIntersectionFinder(intersectionFinder);

        mp4Builder.setProfile(profile);

        Movie m = new Movie();
        for (Map.Entry<Track, String> e : trackOriginalFilename.entrySet()) {
            m.setTracks(Collections.singletonList(e.getKey()));
            mp4Builder.setApid("urn:dece:apid:org:castlabs:" + FilenameUtils.getBaseName(e.getValue()));
            Container c = mp4Builder.build(m);
            String filename = filenames.get(e.getKey());
            FileOutputStream fos = new FileOutputStream(filename);
            c.writeContainer(fos.getChannel());
            fos.close();
        }
        return 0;
    }

    private FragmentIntersectionFinder getFragmentStartSamples(Map<Track, String> trackOriginalFilename) throws CommandAbortException {

        Set<Long> syncSamples = null;
        int numSamples = -1;
        for (Track track : trackOriginalFilename.keySet()) {
            if (numSamples < 0) {
                numSamples = track.getSamples().size();
            }
            if (numSamples != track.getSamples().size()) {
                throw new CommandAbortException("All Tracks need the same number of samples");
            }

        }


        for (Track track : trackOriginalFilename.keySet()) {
            if (track.getSyncSamples() != null && track.getSyncSamples().length > 0) {
                if (syncSamples == null) {
                    syncSamples = new HashSet<Long>();
                    for (long l : track.getSyncSamples()) {
                        syncSamples.add(l);
                    }
                } else {
                    Set<Long> syncSamples2 = new HashSet<Long>();
                    for (long l : track.getSyncSamples()) {
                        syncSamples2.add(l);
                    }
                    syncSamples.retainAll(syncSamples2);
                }
            }
        }
        if (syncSamples != null) {

            List<Long> syncSampleList = new ArrayList<Long>();
            syncSampleList.addAll(syncSamples);
            Collections.sort(syncSampleList);

            final long[] fragmentStartSamples = new long[syncSamples.size()];
            for (int i = 0; i < fragmentStartSamples.length; i++) {
                fragmentStartSamples[i] = syncSampleList.get(i);
            }


            return new FragmentIntersectionFinder() {
                @Override
                public long[] sampleNumbers(Track track) {
                    return fragmentStartSamples;
                }
            };
        } else {
            // they have all the same amount of samples ... easy
            Track t = trackOriginalFilename.keySet().iterator().next();
            double durationInSeconds = t.getDuration() / t.getTrackMetaData().getTimescale();
            int numberOfSamples = t.getSamples().size();
            int numberOfSamplesPer5Seconds = (int) (numberOfSamples / durationInSeconds * 5);
            final long[] fragmentStartSamples = new long[(int) Math.ceil(numberOfSamples / numberOfSamplesPer5Seconds) + 1];
            for (int i = 0; i < fragmentStartSamples.length; i++) {
                fragmentStartSamples[i] = i * numberOfSamplesPer5Seconds + 1;
            }

            return new FragmentIntersectionFinder() {
                @Override
                public long[] sampleNumbers(Track track) {
                    return fragmentStartSamples;
                }
            };
        }
    }


    public Map<Track, String> setupTracks() throws IOException, CommandAbortException {
        Map<Track, String> track2File = new HashMap<Track, String>();
        for (File inputFile : inputFiles) {
            if (inputFile.getName().endsWith("mp4")) {
                Movie movie = MovieCreator.build(new FileDataSourceImpl(inputFile));
                for (Track track : movie.getTracks()) {
                    if (checkCodec(track)) {
                        track2File.put(track, inputFile.getName());
                    }
                }
            } else {
                Track track = null;
                if (inputFile.getName().endsWith(".aac")) {
                    track = new AACTrackImpl(new FileDataSourceImpl(inputFile));
                    logger.fine("Created AAC Track from " + inputFile.getName());
                } else if (inputFile.getName().endsWith(".h264")) {
                    track = new H264TrackImpl(new FileDataSourceImpl(inputFile));

                    logger.fine("Created H264 Track from " + inputFile.getName());
                } else if (inputFile.getName().endsWith(".ac3")) {
                    track = new AC3TrackImpl(new FileDataSourceImpl(inputFile));
                    logger.fine("Created AC3 Track from " + inputFile.getName());
                } else if (inputFile.getName().endsWith(".ec3")) {
                    track = new EC3TrackImpl(new FileDataSourceImpl(inputFile));
                    logger.fine("Created EC3 Track from " + inputFile.getName());
                } else if (inputFile.getName().endsWith(".dtshd")) {
                    track = new DTSTrackImpl(new FileDataSourceImpl(inputFile));
                    logger.fine("Created DTS HD Track from " + inputFile.getName());
                } else {
                    logger.warning("Cannot identify type of " + inputFile + ". Extensions mp4, aac, ac3, ec3 or dtshd are known.");
                }
                if (track != null) {
                    if ((checkCodec(track))) {
                        track2File.put(track, inputFile.getName());
                    } else {
                        logger.warning("Skipping " + getFormat(track.getSampleDescriptionBox().getSampleEntry()) + " track extracted from " + inputFile.getName());
                    }
                }
            }
        }
        return track2File;
    }

    private boolean checkCodec(Track track) throws CommandAbortException {
        AbstractSampleEntry sampleEntry = track.getSampleDescriptionBox().getSampleEntry();
        if (codec == null) {
            if (trackId == -1) {
                if (track.getHandler().equals("vide")) {
                    if (track.getTrackMetaData().getTrackId() >= 1 && track.getTrackMetaData().getTrackId() <= 49) {
                        trackId = track.getTrackMetaData().getTrackId();
                    } else {
                        trackId = 1;
                    }
                } else if (track.getHandler().equals("soun")) {
                    if (track.getTrackMetaData().getTrackId() >= 100 && track.getTrackMetaData().getTrackId() <= 999) {
                        trackId = track.getTrackMetaData().getTrackId();
                    } else {
                        trackId = 100;
                    }
                } else if (track.getHandler().equals("subt")) {
                    if (track.getTrackMetaData().getTrackId() >= 10000 && track.getTrackMetaData().getTrackId() <= 10999) {
                        trackId = track.getTrackMetaData().getTrackId();
                    } else {
                        trackId = 10000;
                    }
                } else {
                    throw new CommandAbortException("Don't know which trackId to assign for handler=" + track.getHandler());
                }
            }
            codec = getFormat(sampleEntry); // if not set in cmd line first track encountered sets the format for this adaptation set
        }
        return codec.equals(getFormat(sampleEntry));
    }

    protected String getFormat(AbstractSampleEntry se) {
        String type = se.getType();
        if (type.startsWith("enc")) {
            OriginalFormatBox frma = se.getBoxes(OriginalFormatBox.class, true).get(0);
            type = frma.getDataFormat();
        }
        return type;
    }

    private Map<Track, String> generateFilenames(Map<Track, String> trackOriginalFilename) {
        HashMap<Track, String> filenames = new HashMap<Track, String>();
        for (Track track : trackOriginalFilename.keySet()) {
            String originalFilename = trackOriginalFilename.get(track);
            originalFilename = originalFilename.replace(".mp4", "");
            originalFilename = originalFilename.replace(".aac", "");
            originalFilename = originalFilename.replace(".ec3", "");
            originalFilename = originalFilename.replace(".ac3", "");
            originalFilename = originalFilename.replace(".dtshd", "");
            for (Track track1 : filenames.keySet()) {
                if (track1 != track &&
                        trackOriginalFilename.get(track1).equals(trackOriginalFilename.get(track))) {
                    // ouch multiple tracks point to same file
                    originalFilename += "_" + track.getTrackMetaData().getTrackId();
                }
            }
            if (track.getHandler().equals("soun")) {
                filenames.put(track, String.format("%s.uva", originalFilename));
            } else if (track.getHandler().equals("vide")) {
                filenames.put(track, String.format("%s.uvv", originalFilename));
            } else if (track.getHandler().equals("subt")) {
                filenames.put(track, String.format("%s.uvt", originalFilename));
            }

        }
        return filenames;
    }

}

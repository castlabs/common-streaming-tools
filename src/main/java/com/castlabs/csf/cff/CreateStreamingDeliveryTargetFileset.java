/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.castlabs.csf.cff;

import com.castlabs.csf.AbstractCommand;
import com.coremedia.iso.Hex;
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
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.logging.Logger;


public class CreateStreamingDeliveryTargetFileset extends AbstractCommand {
    Logger logger;

    private static final String DEFAULT_LANG = "eng";

    @Argument(required = true, multiValued = true, handler = FileOptionHandler.class, usage = "MP4 and bitstream input files", metaVar = "vid1.mp4, vid2.mp4, aud1.mp4, aud2.ec3 ...")
    protected List<File> inputFiles;


    @Option(name = "--codec", aliases = {"-c"})
    String codec;

    @Option(name = "--language", aliases = {"-l"})
    String language;

    @Option(name = "--track-id", aliases = {"-t"})
    long trackId = -1;

    protected UUID keyid;
    protected SecretKey cek;

    @Option(name = "--uuid",
            aliases = "-u",
            usage = "UUID (KeyID)",
            depends = {"--content-encryption-key"}
    )
    protected String encKid = null;

    @Option(name = "--content-encryption-key",
            aliases = "-k",
            usage = "Content Encryption Key",
            depends = {"--uuid"}

    )
    protected String encKeySecretKey = null;


    @Override
    public int run() throws Exception {
        logger = setupLogger();
        if (encKid != null) {
            this.keyid = UUID.fromString(this.encKid);
            this.cek = new SecretKeySpec(Hex.decodeHex(this.encKeySecretKey), "AES");
        }

        Map<Track, String> trackOriginalFilename = setupTracks();
        FragmentIntersectionFinder intersectionFinder = getFragmentStartSamples(trackOriginalFilename);
        Map<Track, String> filenames = generateFilenames(trackOriginalFilename);


        StreamingDeliveryTargetMp4Builder mp4Builder = new StreamingDeliveryTargetMp4Builder();
        mp4Builder.setIntersectionFinder(intersectionFinder);


        Movie m = new Movie();
        for (Map.Entry<Track, String> e : trackOriginalFilename.entrySet()) {
            if (keyid != null) {
                m.setTracks(Collections.<Track>singletonList(new CencEncryptingTrackImpl(e.getKey(), keyid, cek)));
            } else {
                m.setTracks(Collections.<Track>singletonList(e.getKey()));
            }
            String apid = "urn:dece:apid:org:castlabs:" + FilenameUtils.getBaseName(e.getValue());
            mp4Builder.setApid(apid);
            Container c = mp4Builder.build(m);
            String filename = filenames.get(e.getKey());
            FileOutputStream fos = new FileOutputStream(filename);
            logger.info(String.format("Writing %s (track_ID=%d, apid=%s)",
                    filename, e.getKey().getTrackMetaData().getTrackId(), apid));
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
            int numberOfSamplesPer5Seconds = (int) Math.ceil(numberOfSamples / durationInSeconds * 5);
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


    public Map<Track, String> setupTracks() throws IOException, CommandAbortException, XPathExpressionException, SAXException, ParserConfigurationException {
        Map<Track, String> track2File = new HashMap<Track, String>();
        List<File> xmls = new ArrayList<File>();
        for (File inputFile : inputFiles) {
            if (inputFile.getName().endsWith("mp4")) {
                Movie movie = MovieCreator.build(new FileDataSourceImpl(inputFile));
                for (Track track : movie.getTracks()) {
                    if (checkCodecAndLanguage(track, inputFile.getName())) {
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
                } else if (inputFile.getName().endsWith(".xml")) {
                    xmls.add(inputFile);

                } else {
                    logger.warning("Cannot identify type of " + inputFile + ". Extensions mp4, aac, ac3, ec3 or dtshd are known.");
                }
                if (track != null) {
                    if (language != null) {
                        track.getTrackMetaData().setLanguage(language);
                    } else {
                        logger.fine("No language given for raw track - defaulting to " + DEFAULT_LANG);
                        track.getTrackMetaData().setLanguage(DEFAULT_LANG);
                    }
                    if ((checkCodecAndLanguage(track, inputFile.getName()))) {
                        track2File.put(track, inputFile.getName());
                    }
                }
            }
        }

        if (!xmls.isEmpty()) {
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            HashMap<String, List<File>> languageGrouped = new HashMap<String, List<File>>();
            final HashMap<File, Long> startTimes = new HashMap<File, Long>();
            for (File xml : xmls) {
                Document doc = dBuilder.parse(xml);
                String lang = SMPTETTTrackImpl.getLanguage(doc);
                List<File> sameLangFiles = languageGrouped.get(lang);
                if (sameLangFiles == null) {
                    sameLangFiles = new ArrayList<File>();
                    languageGrouped.put(lang, sameLangFiles);
                }
                sameLangFiles.add(xml);
                startTimes.put(xml, SMPTETTTrackImpl.earliestTimestamp(doc));
            }

            for (Map.Entry<String, List<File>> stringListEntry : languageGrouped.entrySet()) {
                String lang = stringListEntry.getKey();
                List<File> sameLangFiles = stringListEntry.getValue();
                Collections.sort(sameLangFiles, new Comparator<File>() {
                    @Override
                    public int compare(File o1, File o2) {
                        return (int) (startTimes.get(o1) - startTimes.get(o2));
                    }
                });
                track2File.put(new SMPTETTTrackImpl(sameLangFiles.toArray(new File[sameLangFiles.size()])), sameLangFiles.get(0).getName());
                logger.fine("Created SMPTE-TT Track from " + sameLangFiles + " in " + lang);
            }
        }
        return track2File;
    }

    private boolean checkCodecAndLanguage(Track track, String origFile) throws CommandAbortException {
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
                        String codec = track.getSampleDescriptionBox().getSampleEntry().getType();
                        if ("mp4a".equals(codec)) {
                            trackId = 100;
                        } else if ("mlpa".equals(codec)) {
                            trackId = 200;
                        } else if ("dtsl".equals(codec)) {
                            trackId = 300;
                        } else if ("dtsh".equals(codec)) {
                            trackId = 300;
                        } else if ("dtsl".equals(codec)) {
                            trackId = 400;
                        } else if ("dtse".equals(codec)) {
                            trackId = 500;
                        } else if ("ac-3".equals(codec)) {
                            trackId = 600;
                        } else if ("ec-3".equals(codec)) {
                            trackId = 700;
                        } else {
                            throw new RuntimeException("Don't know which track number to assign");
                        }
                        trackId += track.getTrackMetaData().getLanguage().hashCode() % 100;
                        // this is not bullet-proof but should make that each codec/language combo gets its own
                        // track id without collisions.
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
        track.getTrackMetaData().setTrackId(trackId);
        if (language == null) {
            language = track.getTrackMetaData().getLanguage();
        }
        if (!codec.equals(getFormat(sampleEntry))) {
            logger.warning("Skipping " + getFormat(track.getSampleDescriptionBox().getSampleEntry()) + " track extracted from " + origFile + " as it is not the same codec as previously processed tracks");
            return false;
        }
        if (!language.equals(track.getTrackMetaData().getLanguage())) {
            logger.warning("Skipping " + getFormat(track.getSampleDescriptionBox().getSampleEntry()) + " track extracted from " + origFile + " as it is not the same languae as previously processed tracks");
            return false;
        }
        return true;
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
            originalFilename = originalFilename.replace(".xml", "");
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

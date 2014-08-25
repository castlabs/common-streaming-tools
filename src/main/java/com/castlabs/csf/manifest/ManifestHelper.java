/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.castlabs.csf.manifest;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.HandlerBox;
import com.coremedia.iso.boxes.MediaHeaderBox;
import com.coremedia.iso.boxes.fragment.TrackExtendsBox;
import com.coremedia.iso.boxes.fragment.TrackFragmentHeaderBox;
import com.coremedia.iso.boxes.fragment.TrackRunBox;
import com.coremedia.iso.boxes.sampleentry.AudioSampleEntry;
import com.coremedia.iso.boxes.sampleentry.DashHelper;
import com.coremedia.iso.boxes.sampleentry.VisualSampleEntry;
import com.googlecode.mp4parser.boxes.dece.ContentInformationBox;
import com.googlecode.mp4parser.util.Path;
import mpegDashSchemaMpd2011.AdaptationSetType;
import mpegDashSchemaMpd2011.DescriptorType;
import mpegDashSchemaMpd2011.RepresentationType;
import org.apache.commons.lang.math.Fraction;

/**
 * Some conversion from Track representation to Manifest specifics shared by DASH manifests of all kinds.
 */
public class ManifestHelper {

    public static String convertFramerate(double vrate) {
        vrate = (double) Math.round(vrate * 1000) / 1000;
        String frameRate = null;
        if ((vrate > 14) && (vrate < 15)) {
            frameRate = "15000/1001";
        } else if ((vrate == 15)) {
            frameRate = "15000/1000";
        } else if ((vrate > 23) && (vrate < 24)) {
            frameRate = "24000/1001";
        } else if (vrate == 24) {
            frameRate = "24000/1000";
        } else if ((vrate > 24) && ((vrate < 25) || (vrate == 25))) {
            frameRate = "25000/1000";
        } else if ((vrate > 29) && (vrate < 30)) {
            frameRate = "30000/1001";
        } else if (vrate == 30) {
            frameRate = "30000/1000";
        } else if (vrate == 50) {
            frameRate = "50000/1000";
        } else if ((vrate > 59) && (vrate < 60)) {
            frameRate = "60000/1001";
        } else if (vrate == 60) {
            frameRate = "60000/1000";
        } else {
            System.out.println("Framerate " + vrate + " is not supported");
            System.exit(1);
        }
        return frameRate;
    }


    /**
     * Creates a representation and adjusts the AdaptionSet's attributes maxFrameRate, maxWidth, maxHeight.
     * Also creates AudioChannelConfiguration.
     */
    public static RepresentationType createRepresentation(AdaptationSetType adaptationSet, IsoFile track) {
        RepresentationType representation = adaptationSet.addNewRepresentation();
        ContentInformationBox contentInformationBox = Path.getPath(track, "/moov[0]/cinf[0]");
        representation.setId(contentInformationBox.getIdEntries().get("urn:dece:asset_id"));

        String handler = ((HandlerBox) Path.getPath(track, "/moov[0]/trak[0]/mdia[0]/hdlr[0]")).getHandlerType();
        if (handler.equals("vide")) {
            VisualSampleEntry vse = Path.getPath(track, "/moov[0]/trak[0]/mdia[0]/minf[0]/stbl[0]/stsd[0]/....[0]");
            long videoHeight = (long) vse.getHeight();
            long videoWidth = (long) vse.getWidth();

            TrackRunBox trun = Path.getPath(track, "/moof[0]/traf[0]/trun[0]");
            MediaHeaderBox mdhd = Path.getPath(track, "/moov[0]/trak[0]/mdia[0]/mdhd[0]");
            // assuming constant framerate
            long sampleDuration;
            if (trun.isSampleDurationPresent()) {
                sampleDuration = trun.getEntries().get(0).getSampleDuration();
            } else {
                TrackFragmentHeaderBox tfhd = Path.getPath(track, "/moof[0]/traf[0]/tfhd[0]");
                if (tfhd.hasDefaultSampleDuration()) {
                    sampleDuration = tfhd.getDefaultSampleDuration();
                } else {
                    TrackExtendsBox trex = Path.getPath(track, "/moov[0]/mvex[0]/trex[0]");
                    sampleDuration = trex.getDefaultSampleDuration();
                }
            }


            double framesPerSecond = mdhd.getTimescale() / sampleDuration;

            adaptationSet.setMaxFrameRate(convertFramerate(
                    Math.max(adaptationSet.isSetMaxFrameRate() ? Fraction.getFraction(adaptationSet.getMaxFrameRate()).doubleValue() : 0,
                            framesPerSecond)
            ));

            adaptationSet.setMaxWidth(Math.max(adaptationSet.isSetMaxWidth() ? adaptationSet.getMaxWidth() : 0,
                    videoWidth));
            adaptationSet.setMaxHeight(Math.max(adaptationSet.isSetMaxHeight() ? adaptationSet.getMaxHeight() : 0,
                    videoHeight));

            adaptationSet.setPar("1:1");
            // too hard to find it out. Ignoring even though it should be set according to DASH-AVC-264-v2.00-hd-mca.pdf

            representation.setCodecs(DashHelper.getRfc6381Codec(vse));
            representation.setWidth(videoWidth);
            representation.setHeight(videoHeight);
            representation.setFrameRate(convertFramerate(framesPerSecond));
            representation.setSar("1:1");
            // too hard to find it out. Ignoring even though it should be set according to DASH-AVC-264-v2.00-hd-mca.pdf
        }

        if (handler.equals("soun")) {
            AudioSampleEntry ase = Path.getPath(track, "/moov[0]/trak[0]/mdia[0]/minf[0]/stbl[0]/stsd[0]/....[0]");
            representation.setCodecs(DashHelper.getRfc6381Codec(ase));
            representation.setAudioSamplingRate(String.valueOf(ase.getSampleRate()));

            DescriptorType audio_channel_conf = representation.addNewAudioChannelConfiguration();
            DashHelper.ChannelConfiguration cc = DashHelper.getChannelConfiguration(ase);
            audio_channel_conf.setSchemeIdUri(cc.schemeIdUri);
            audio_channel_conf.setValue(cc.value);

        }
        return representation;
    }

}

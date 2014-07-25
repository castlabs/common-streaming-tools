/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package com.castlabs.csf.manifest;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.boxes.Box;
import com.coremedia.iso.boxes.HandlerBox;
import com.coremedia.iso.boxes.MediaHeaderBox;
import com.googlecode.mp4parser.boxes.basemediaformat.TrackEncryptionBox;
import com.googlecode.mp4parser.util.Path;
import mpegCenc2013.DefaultKIDAttribute;
import mpegDashSchemaMpd2011.*;
import org.apache.xmlbeans.GDuration;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.Collections;
import java.util.UUID;

public abstract class AbstractManifestWriter {


    public AbstractManifestWriter() {


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
        mpd.setMinBufferTime(new GDuration(1, 0, 0, 0, 0, 0, 2, BigDecimal.ZERO));
        mpd.setMediaPresentationDuration(periodType.getDuration());

        return mdd;
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
                throw new RuntimeException("The ManifestWriter cannot deal with more than ONE language per adaptation set.");
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

    abstract protected void createPeriod(PeriodType periodType) throws IOException;

}

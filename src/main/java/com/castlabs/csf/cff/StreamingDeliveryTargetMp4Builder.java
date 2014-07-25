/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */
package com.castlabs.csf.cff;

import com.coremedia.iso.boxes.*;
import com.coremedia.iso.boxes.fragment.MovieFragmentBox;
import com.coremedia.iso.boxes.fragment.TrackFragmentBaseMediaDecodeTimeBox;
import com.coremedia.iso.boxes.fragment.TrackFragmentBox;
import com.coremedia.iso.boxes.fragment.TrackRunBox;
import com.coremedia.iso.boxes.h264.AvcConfigurationBox;
import com.coremedia.iso.boxes.sampleentry.VisualSampleEntry;
import com.googlecode.mp4parser.BasicContainer;
import com.googlecode.mp4parser.authoring.Movie;
import com.googlecode.mp4parser.authoring.Track;
import com.googlecode.mp4parser.authoring.builder.FragmentedMp4Builder;
import com.googlecode.mp4parser.authoring.tracks.CencEncyprtedTrack;
import com.googlecode.mp4parser.boxes.basemediaformat.AvcNalUnitStorageBox;
import com.googlecode.mp4parser.boxes.threegpp26244.SegmentIndexBox;
import com.googlecode.mp4parser.boxes.ultraviolet.AssetInformationBox;
import com.googlecode.mp4parser.util.Path;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.googlecode.mp4parser.util.CastUtils.l2i;


public class StreamingDeliveryTargetMp4Builder extends FragmentedMp4Builder {

    private String apid = "";
    private String profile;
    private String metaXml = "";

    private List<ByteBuffer> idatContents = new ArrayList<ByteBuffer>();

    /**
     * Unifies all buffer by creating a new ByteBuffer with the capacity
     * of the sum of all buffers and writing all buffers completely into the new one.
     * Nothing for the given buffers is changed.
     *
     * @param buffers buffers to union
     * @return a unioned buffer
     */
    public static ByteBuffer union(ByteBuffer... buffers) {
        int capacity = sizeByCapacity(buffers);
        ByteBuffer buffer = ByteBuffer.allocate(capacity);

        for (ByteBuffer b : buffers) {
            buffer.put(b.array());
        }

        buffer.rewind();
        return buffer;
    }

    public static ByteBuffer union(List<ByteBuffer> buffers) {
        return union(buffers.toArray(new ByteBuffer[buffers.size()]));
    }

    /**
     * Returns the sum of capacities of all buffers.
     *
     * @param buffers the buffers to sum up
     * @return the capacity
     */
    public static int sizeByCapacity(List<ByteBuffer> buffers) {
        int size = 0;

        for (ByteBuffer buffer : buffers) {
            size += buffer.capacity();
        }

        return size;
    }

    public static int sizeByCapacity(ByteBuffer... buffers) {
        return sizeByCapacity(Arrays.asList(buffers));
    }

    public void addIdatContent(ByteBuffer idatContent) {
        this.idatContents.add(idatContent);
    }

    public void setApid(String apid) {
        this.apid = apid;
    }

    public void setMetaXml(String metaXml) {
        this.metaXml = metaXml;
    }

    public void setProfile(String profile) {
        this.profile = profile;
    }

    @Override
    public List<String> getAllowedHandlers() {
        return Arrays.asList("soun", "vide", "subt");
    }

    @Override
    public Box createFtyp(Movie movie) {
        return new FileTypeBox("ccff", 0, Arrays.asList("isom", "avc1", "iso6"));
    }

    @Override
    protected Box createMoov(Movie movie) {
        MovieBox movieBox = new MovieBox();

        movieBox.addBox(createMvhd(movie));
        movieBox.addBox(createAinf(movie));
        movieBox.addBox(createMeta());


        for (Track track : movie.getTracks()) {
            movieBox.addBox(createTrak(track, movie));
        }
        movieBox.addBox(createMvex(movie));
        // metadata here
        return movieBox;

    }

    protected Box createAinf(Movie movie) {
        AssetInformationBox ainf = new AssetInformationBox();
        ainf.setProfileVersion(profile);
        ainf.setApid(apid);
        return ainf;
    }

    protected void createTfdt(long startSample, Track track, TrackFragmentBox parent) {
        TrackFragmentBaseMediaDecodeTimeBox tfdt = new TrackFragmentBaseMediaDecodeTimeBox();
        tfdt.setVersion(0);
        long startTime = 0;
        long[] times = track.getSampleDurations();
        for (int i = 1; i < startSample; i++) {
            startTime += times[i];
        }
        tfdt.setBaseMediaDecodeTime(startTime);
        parent.addBox(tfdt);
    }

    protected void createTraf(long startSample, long endSample, Track track, int sequenceNumber, MovieFragmentBox parent) {
        TrackFragmentBox traf = new TrackFragmentBox();
        parent.addBox(traf);
        createTfhd(startSample, endSample, track, sequenceNumber, traf);
        createTfdt(startSample, track, traf);
        createTrun(startSample, endSample, track, sequenceNumber, traf);
        if (track.getSampleDescriptionBox().getSampleEntry() instanceof VisualSampleEntry) {
            List<AvcConfigurationBox> avccs = track.getSampleDescriptionBox().getSampleEntry().getBoxes(AvcConfigurationBox.class);
            for (AvcConfigurationBox avcc : avccs) {
                traf.addBox(new AvcNalUnitStorageBox(avcc));
            }
        }

        if (track instanceof CencEncyprtedTrack) {
            createSenc(startSample, endSample, (CencEncyprtedTrack) track, sequenceNumber, traf);
            createSaio(startSample, endSample, (CencEncyprtedTrack) track, sequenceNumber, traf);
            createSaiz(startSample, endSample, (CencEncyprtedTrack) track, sequenceNumber, traf);
        }


    }

    @Override
    protected void createSaio(long startSample, long endSample, CencEncyprtedTrack track, int sequenceNumber, TrackFragmentBox parent) {
        super.createSaio(startSample, endSample, track, sequenceNumber, parent);
        ((SampleAuxiliaryInformationOffsetsBox) parent.getBoxes().get(parent.getBoxes().size() - 1)).setFlags(0);
    }

    @Override
    protected Box createMdiaHdlr(Track track, Movie movie) {
        HandlerBox hdlr = new HandlerBox();
        hdlr.setHandlerType(track.getHandler());
        if (track.getHandler().equals("vide")) {
            hdlr.setName("Video Track");
        }
        return hdlr;
    }

    protected Box createMeta() {
        MetaBox metaBox = new MetaBox();
        HandlerBox hdlr = new HandlerBox();
        hdlr.setHandlerType("cfmd");
        hdlr.setName("Required Metadata");
        metaBox.addBox(hdlr);
        XmlBox xmlBox = new XmlBox();
        xmlBox.setXml(metaXml + "\u0000");
        metaBox.addBox(xmlBox);

        createIlocIdat(metaBox);

        return metaBox;
    }

    protected void createIlocIdat(MetaBox metaBox) {


        class MyItemDataBox extends ItemDataBox {


            public long getOffset() {
                Box b = this;
                long offset = -4; // the calculation is systematically wrong by 4, I don't want to debug why. Just a quick correction --san 14.May.13
                do {
                    Container parent = b.getParent();
                    for (Box box : parent.getBoxes()) {
                        if (b == box) {
                            break;
                        }
                        offset += box.getSize();
                    }
                    if (parent instanceof Box) {
                        b = (Box) parent;
                    }
                } while (b.getParent() instanceof Box);
                return offset;
            }
        }

        final ItemDataBox idat = new MyItemDataBox();


        ItemLocationBox iloc = new ItemLocationBox() {
            @Override
            protected void getContent(ByteBuffer bb) {
                long itemSizes = 0;
                for (Item item : items) {
                    final Extent extent = item.extents.get(0);
                    extent.extentOffset = itemSizes;
                    itemSizes += extent.extentLength;
                }

                super.getContent(bb);
            }
        };
        
        iloc.setVersion(1);
        int valueLength = 8;
        iloc.setIndexSize(0);
        iloc.setOffsetSize(valueLength);
        iloc.setLengthSize(valueLength);
        iloc.setBaseOffsetSize(0);

        List<ItemLocationBox.Item> items = new ArrayList<ItemLocationBox.Item>();
        for (int i = 0; i < idatContents.size(); i++) {
            ByteBuffer idatContent = idatContents.get(i);
            final ItemLocationBox.Extent extent = iloc.createExtent(0, idatContent.limit(), 0);
            ItemLocationBox.Item item = iloc.createItem(i + 1, 1, 0, 0, Collections.singletonList(extent));
            items.add(item);
        }
        iloc.setItems(items);
        metaBox.addBox(iloc);

        idat.setData(union(idatContents));
        metaBox.addBox(idat);
    }

    @Override
    public Container build(Movie movie) {
        if (movie.getTracks().size() != 1) {
            throw new RuntimeException("Only onetrack allowed");
        }


        BasicContainer isoFile = new BasicContainer();
        isoFile.addBox(createFtyp(movie));
        isoFile.addBox(createMoov(movie));
        List<Box> moofMdats = createMoofMdat(movie);

        isoFile.addBox(createSidx(isoFile, moofMdats, 0));

        for (Box box : moofMdats) {
            isoFile.addBox(box);
        }
        isoFile.addBox(createMfra(movie, isoFile));

        return isoFile;
    }

    private SegmentIndexBox createSidx(BasicContainer isoFile, List<Box> moofMdats, long offsetBetweenSidxAndFirstMoof) {
        SegmentIndexBox sidx = new SegmentIndexBox();
        sidx.setVersion(0);
        sidx.setFlags(0);
        sidx.setReserved(0);
        sidx.setFirstOffset(offsetBetweenSidxAndFirstMoof);
        List<SegmentIndexBox.Entry> entries = sidx.getEntries();
        MovieFragmentBox firstMoof = null;
        for (Box moofMdat : moofMdats) {
            if (moofMdat.getType().equals("moof")) {
                firstMoof = (MovieFragmentBox) moofMdat;
                entries.add(new SegmentIndexBox.Entry());
            }
        }
        TrackHeaderBox tkhd = (TrackHeaderBox) Path.getPath(isoFile, "/moov[0]/trak[0]/tkhd[0]");
        MediaHeaderBox mdhd = (MediaHeaderBox) Path.getPath(isoFile, "/moov[0]/trak[0]/mdia[0]/mdhd[0]");
        sidx.setReferenceId(tkhd.getTrackId());
        sidx.setTimeScale(mdhd.getTimescale());
        // we only have one
        TrackRunBox trun = firstMoof.getTrackRunBoxes().get(0);
        long[] ptss = getPtss(trun);
        Arrays.sort(ptss); // index 0 has now the earliest presentation time stamp!
        long timeMappingEdit = getTimeMappingEditTime(isoFile);
        sidx.setEarliestPresentationTime(ptss[0] - timeMappingEdit);


        // ugly code ...

        int size = 0;
        int i = 0;
        MovieFragmentBox lassMoof = null;
        for (Box moofMdat : moofMdats) {

            if (moofMdat.getType().equals("moof") && size > 0) {
                SegmentIndexBox.Entry entry = entries.get(i++);
                entry.setReferencedSize(size);
                ptss = getPtss(lassMoof.getTrackRunBoxes().get(0));
                entry.setSapType(getFirstFrameSapType(ptss));
                entry.setSubsegmentDuration(getTrunDuration(lassMoof.getTrackRunBoxes().get(0)));
                entry.setStartsWithSap((byte) 1); // we know it - no need to lookup
                size = l2i(moofMdat.getSize());
            } else {
                size += l2i(moofMdat.getSize());
            }
            if (moofMdat.getType().equals("moof")) {
                lassMoof = (MovieFragmentBox) moofMdat;
            }

        }
        SegmentIndexBox.Entry entry = entries.get(i);
        ptss = getPtss(lassMoof.getTrackRunBoxes().get(0));
        entry.setSapType(getFirstFrameSapType(ptss));
        entry.setSubsegmentDuration(getTrunDuration(lassMoof.getTrackRunBoxes().get(0)));
        entry.setReferencedSize(size);
        entry.setStartsWithSap((byte) 1); // we know it - no need to lookup

        return sidx;
    }

    protected byte getFirstFrameSapType(long[] ptss) {
        long idrTimeStamp = ptss[0];
        Arrays.sort(ptss);
        if (idrTimeStamp > ptss[0]) {
            return 0;
        } else {
            return 1;
        }
    }


    private long getTimeMappingEditTime(Container file) {
        final EditListBox editList = (EditListBox) Path.getPath(file, "/moov[0]/trak[0]/edts[0]/elst[0]");
        if (editList != null) {
            final List<EditListBox.Entry> entries = editList.getEntries();
            if (entries.size() > 1) {
                throw new RuntimeException("CSF shouldn't have more than one entry and I don't know how to deal with it.");
            }
            for (EditListBox.Entry entry : entries) {
                if (entry.getMediaTime() > 0) {
                    return entry.getMediaTime();
                }
            }
        }
        return 0;
    }


    private long[] getPtss(TrackRunBox trun) {
        long currentTime = 0;
        long[] ptss = new long[trun.getEntries().size()];
        for (int j = 0; j < ptss.length; j++) {
            ptss[j] = currentTime + trun.getEntries().get(j).getSampleCompositionTimeOffset();
            currentTime += trun.getEntries().get(j).getSampleDuration();
        }
        return ptss;
    }

    protected long getTrunDuration(TrackRunBox trun) {
        final List<TrackRunBox.Entry> trunEntries = trun.getEntries();
        long duration = 0;
        for (TrackRunBox.Entry trunEntry : trunEntries) {
            duration += trunEntry.getSampleDuration();
        }
        return duration;
    }


}




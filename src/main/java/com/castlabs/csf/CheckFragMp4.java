package com.castlabs.csf;

import com.coremedia.iso.IsoFile;
import com.coremedia.iso.IsoTypeReader;
import com.coremedia.iso.boxes.SampleAuxiliaryInformationOffsetsBox;
import com.coremedia.iso.boxes.SampleAuxiliaryInformationSizesBox;
import com.coremedia.iso.boxes.fragment.MovieFragmentBox;
import com.googlecode.mp4parser.boxes.cenc.CencSampleAuxiliaryDataFormat;
import com.googlecode.mp4parser.util.Path;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * Created by user on 06.08.2014.
 */
public class CheckFragMp4 {
    public static void main(String[] args) throws IOException {
        IsoFile isoFile = new IsoFile("C:\\Users\\user\\Documents\\test1.mp4");
        SampleAuxiliaryInformationOffsetsBox saio = Path.getPath(isoFile, "/moof[0]/traf[0]/saio[0]");
        SampleAuxiliaryInformationSizesBox saiz = Path.getPath(isoFile, "/moof[0]/traf[0]/saiz[0]");
        int defaultInfoSize = saiz.getDefaultSampleInfoSize();
        MovieFragmentBox moof = Path.getPath(isoFile, "/moof[0]");

        ByteBuffer bb = isoFile.getByteBuffer(moof.getOffset() + saio.getOffsets()[0], defaultInfoSize * saiz.getSampleCount());
        for (int i = 0; i < saiz.getSampleCount(); i++) {
            CencSampleAuxiliaryDataFormat cencSampleAuxiliaryDataFormat = parseCencAuxDataFormat(8, bb, defaultInfoSize);
            System.err.println(cencSampleAuxiliaryDataFormat);

        }
    }

    private static CencSampleAuxiliaryDataFormat parseCencAuxDataFormat(int ivSize, ByteBuffer chunksCencSampleAuxData, long auxInfoSize) {
        CencSampleAuxiliaryDataFormat cadf = new CencSampleAuxiliaryDataFormat();
        cadf.iv = new byte[ivSize];
        chunksCencSampleAuxData.get(cadf.iv);
        if (auxInfoSize > ivSize) {
            int numOfPairs = IsoTypeReader.readUInt16(chunksCencSampleAuxData);
            cadf.pairs = new CencSampleAuxiliaryDataFormat.Pair[numOfPairs];
            for (int i = 0; i < cadf.pairs.length; i++) {
                cadf.pairs[i] = cadf.createPair(
                        IsoTypeReader.readUInt16(chunksCencSampleAuxData),
                        IsoTypeReader.readUInt32(chunksCencSampleAuxData));
            }
        }
        return cadf;
    }
}

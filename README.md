# Common Streaming Tools

Creating CSF streams is a two stage process. The first stage createsCFF files as described in "Annex C.5 Streaming Delivery 
Target" of the CFF Media Format 1.2 Draft specification. These files are then processed with a second stage to be 
deliverable with standard HTTP server. 

Please note that this tool doesn't encode video nor audio. It takes correctly encoded content and packages it appropriately.

The command line is always

```
java -jar common-streaming-tools-[version].jar [command] [options] [arguments]
```

## Example Files

[AAC Stereo Track](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/PR_testfiles/PR_BBB_audio.mp4)
[H264 HD Best Quality](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/PR_testfiles/PR_BBB_hd1.mp4)
[H264 HD Better Quality](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/PR_testfiles/PR_BBB_hd2.mp4)
[H264 HD Good Quality](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/PR_testfiles/PR_BBB_hd3.mp4)
[H264 HD Low Quality](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/PR_testfiles/PR_BBB_hd4.mp4)
[H264 SD Best Quality](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/PR_testfiles/PR_BBB_sd1.mp4)
[H264 SD Better Quality](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/PR_testfiles/PR_BBB_sd2.mp4)
[H264 SD Good Quality](https://castlabs-dl.s3.amazonaws.com/public/drmtoday/sample_media_files_for_packager/PR_testfiles/PR_BBB_sd3.mp4)


## Create CSF/CFF file

The 'streaming-target' command creates CFF files. You should always package all files of an AdaptationSet with one run of
the tool so that the fragments are created at same time even if certain quality have additional I frames.

The profile needs to be given with the `-p` option until the ainf box version 1 is implmented. 


```
java -jar common-streaming-tools-[version].jar streaming-target -p h107 PR_BBB_sd1.mp4 PR_BBB_sd2.mp4 PR_BBB_sd3.mp4 PR_BBB_hd1.mp4 PR_BBB_hd2.mp4 PR_BBB_hd3.mp4 PR_BBB_hd4.mp4 
java -jar common-streaming-tools-[version].jar streaming-target -p h107 PR_BBB_audio.mp4 

```

The result is always written into the current directory with appropriate extensions (uva, uvv, uvt). 

## Repackage CSF File for Streaming

### Most Simple Case: CSF file stays as is - Manifest uses media ranges

```
java -jar common-streaming-tools-[version].jar streaming-target  create-simple-manifest PR_BBB_sd1.uvv PR_BBB_sd2.uvv PR_BBB_sd3.uvv PR_BBB_hd1.uvv PR_BBB_hd2.uvv PR_BBB_hd3.uvv PR_BBB_hd4.uvv PR_BBB_audio.uva
```

A `Manifest.xml` will be written into the current directory. 

